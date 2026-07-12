package com.example.budgetapp.ui;

import android.util.Log;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Spanned;
import android.view.GestureDetector;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ArrayAdapter;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.example.budgetapp.database.AppDatabase;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.content.ContextCompat;
import androidx.documentfile.provider.DocumentFile;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.budgetapp.R;

import com.example.budgetapp.drawable.TabShadowDrawable;
import com.example.budgetapp.database.Transaction;
import com.example.budgetapp.utils.CategoryManager;
import com.example.budgetapp.viewmodel.TransactionViewModel;
import com.example.budgetapp.utils.SwipeHelper;
import com.example.budgetapp.widget.WidgetUtils;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import eightbitlab.com.blurview.BlurView;
import eightbitlab.com.blurview.RenderScriptBlur;
import com.qmdeve.liquidglass.widget.LiquidGlassView;

import java.text.SimpleDateFormat;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class RecordFragment extends Fragment {
    private TransactionViewModel viewModel;
    private CalendarAdapter adapter;
    private YearMonth currentMonth;
    private LocalDate selectedDate;

    // UI 控件
    private TextView tvMonthTitle;

    private LinearLayout layoutDailyTransactions;
    private TextView tvDailyDateTitle;
    private TextView tvDailySummary;
    private View layoutEmptyState;

    // 提取全局 RecyclerView 以便执行动画
    private RecyclerView calendarRecycler;
    private RecyclerView dailyTransactionsRecycler;
    private TransactionListAdapter dailyTransactionsAdapter;

    // 手势检测器
    private GestureDetector gestureDetector;

    // Activity Result Launcher
    private ActivityResultLauncher<Intent> yearCalendarLauncher;

    // 新增：记录当前的过滤模式
    private int currentFilterMode = 0;

    // 新增：用于记录当前请求的时间范围，防止无限循环查询
    private long currentStartMillis = 0;
    private long currentEndMillis = 0;

    // FAB 材质效果相关字段
    private FrameLayout fabContainerQuick;
    private FrameLayout fabContainerBatch;
    private BlurView blurFabQuick;
    private BlurView blurFabBatch;
    private LiquidGlassView liquidFabQuick;
    private LiquidGlassView liquidFabBatch;
    private boolean fabLiquidGlassBound = false;
    private boolean fabBlurSetupDone = false;
    @SuppressWarnings("deprecation")
    private RenderScriptBlur fabRenderScriptBlur;
    private final FabSettingsSnapshot lastFabSettings = new FabSettingsSnapshot();

    // 聚光灯引导相关
    private final Handler spotlightHandler = new Handler(Looper.getMainLooper());
    private SpotlightGuideView currentSpotlightGuide;

    /**
     * 核心跟手引擎：接管日历的水平方向滑动
     * 注意：SwipeHelper.setup 在滑动超过阈值时已自动调用 finishSwipeAnimation 完成动画，
     * 因此回调中只需更新数据，不能再触发二次动画。
     */
    private void setupFollowHandSwipe(RecyclerView recyclerView) {
        SwipeHelper.setup(recyclerView, direction -> {
            if (direction == -1) {
                // 右滑 → 上个月
                currentMonth = currentMonth.minusMonths(1);
            } else {
                // 左滑 → 下个月
                currentMonth = currentMonth.plusMonths(1);
            }
            updateCalendar(0);
        });
    }

    /**
     * 供按钮点击触发的自动滑动封装
     */
    private void changeCalendarPage(int yearOffset, int monthOffset) {
        if (calendarRecycler != null) {
            float screenWidth = calendarRecycler.getWidth();
            if (screenWidth == 0) screenWidth = 1080;
            float targetX = (yearOffset > 0 || monthOffset > 0) ? -screenWidth : screenWidth;
            SwipeHelper.finishSwipeAnimation(calendarRecycler, targetX, (monthOffset > 0 || yearOffset > 0) ? 1 : -1, direction -> {
                if (yearOffset != 0) currentMonth = currentMonth.plusYears(yearOffset);
                if (monthOffset != 0) currentMonth = currentMonth.plusMonths(monthOffset);
                updateCalendar(0);
            });
        }
    }

    private void fetchDataForCurrentMonth() {
        LocalDate firstDay = currentMonth.atDay(1);
        int offset = firstDay.getDayOfWeek().getValue() - 1;
        LocalDate startOfGrid = firstDay.minusDays(offset);
        LocalDate endOfGrid = currentMonth.atEndOfMonth().plusDays(14); // 留点缓冲天数

        long startMillis = startOfGrid.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
        long endMillis = endOfGrid.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() - 1;

        // 如果时间范围发生变化，才通知 ViewModel 去数据库查询
        if (currentStartMillis != startMillis || currentEndMillis != endMillis) {
            currentStartMillis = startMillis;
            currentEndMillis = endMillis;
            viewModel.setDateRange(startMillis, endMillis);
        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        yearCalendarLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                        int year = result.getData().getIntExtra("year", -1);
                        int month = result.getData().getIntExtra("month", -1);
                        if (year != -1 && month != -1) {
                            currentMonth = YearMonth.of(year, month);
                            updateCalendar(0);
                        }
                    }
                }
        );
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_record, container, false);
        viewModel = new ViewModelProvider(requireActivity()).get(TransactionViewModel.class);

        if (currentMonth == null) {
            currentMonth = YearMonth.now();
        }

        // 【新增】在创建视图时立即设置背景色，避免切换时闪烁
        SharedPreferences prefs = requireContext().getSharedPreferences("app_prefs", Context.MODE_PRIVATE);
        int themeMode = prefs.getInt("theme_mode", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
        
        // 需要设置背景的所有View
        View rootLayout = view.findViewById(R.id.root_layout_record);
        View topBarLayout = view.findViewById(R.id.layout_top_bar);
        View weekHeaderLayout = view.findViewById(R.id.layout_week_header);
        
        if (themeMode == 3) {
            // 自定义主题：所有区域都设置透明背景，显示用户设置的背景图片
            if (rootLayout != null) {
                rootLayout.setBackgroundColor(android.graphics.Color.TRANSPARENT);
            }
            if (topBarLayout != null) {
                topBarLayout.setBackgroundColor(android.graphics.Color.TRANSPARENT);
            }
            if (weekHeaderLayout != null) {
                weekHeaderLayout.setBackgroundColor(android.graphics.Color.TRANSPARENT);
            }
        } else {
            // 日间/夜间模式：使用资源文件中定义的背景色（会自动适配主题）
            int bgColor = getResources().getColor(R.color.bar_background, null);
            if (rootLayout != null) {
                rootLayout.setBackgroundColor(bgColor);
            }
            if (topBarLayout != null) {
                topBarLayout.setBackgroundColor(bgColor);
            }
            if (weekHeaderLayout != null) {
                weekHeaderLayout.setBackgroundColor(bgColor);
            }
        }

//        initGestureDetector();

        tvMonthTitle = view.findViewById(R.id.tv_month_title);

        // 绑定当日记账记录区域的控件
        layoutDailyTransactions = view.findViewById(R.id.layout_daily_transactions);
        tvDailyDateTitle = view.findViewById(R.id.tv_daily_date_title);
        tvDailySummary = view.findViewById(R.id.tv_daily_summary);
        layoutEmptyState = view.findViewById(R.id.layout_empty_state);
        dailyTransactionsRecycler = view.findViewById(R.id.rv_daily_transactions);

        if (dailyTransactionsRecycler != null) {
            dailyTransactionsRecycler.setLayoutManager(new LinearLayoutManager(getContext()));
            dailyTransactionsRecycler.setNestedScrollingEnabled(false);
            FadeInItemAnimator recordAnimator = new FadeInItemAnimator();
            recordAnimator.setReduceMotion(AnimUtils.shouldReduceAnimations(getContext()));
            dailyTransactionsRecycler.setItemAnimator(recordAnimator);
            dailyTransactionsAdapter = new TransactionListAdapter(transaction -> {
                LocalDate transDate = Instant.ofEpochMilli(transaction.date).atZone(ZoneId.systemDefault()).toLocalDate();
                showAddOrEditDialog(transaction, transDate);
            });
            dailyTransactionsRecycler.setAdapter(dailyTransactionsAdapter);
        }

        // 绑定 FAB 材质效果容器
        fabContainerQuick = view.findViewById(R.id.fab_container_quick);
        fabContainerBatch = view.findViewById(R.id.fab_container_batch);
        blurFabQuick = view.findViewById(R.id.blur_fab_quick);
        blurFabBatch = view.findViewById(R.id.blur_fab_batch);
        liquidFabQuick = view.findViewById(R.id.liquid_fab_quick);
        liquidFabBatch = view.findViewById(R.id.liquid_fab_batch);

        FloatingActionButton btnQuickRecord = view.findViewById(R.id.btn_quick_record);
        if (btnQuickRecord != null) {
            btnQuickRecord.setOnClickListener(v -> {
                v.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK);
                showAddOrEditDialog(null, LocalDate.now());
            });

            // 长按按钮进入设置页面
            btnQuickRecord.setOnLongClickListener(v -> {
                v.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS);
                Intent intent = new Intent(requireContext(), com.example.budgetapp.ui.SettingsActivity.class);
                startActivity(intent);
                return true;
            });

            // 触摸反馈动画作用于外层容器，使材质层同步缩放
            btnQuickRecord.setOnTouchListener(new View.OnTouchListener() {
                @Override
                public boolean onTouch(View v, MotionEvent event) {
                    View container = fabContainerQuick != null ? fabContainerQuick : v;
                    switch (event.getAction()) {
                        case MotionEvent.ACTION_DOWN:
                            container.animate()
                                    .scaleX(0.9f)
                                    .scaleY(0.9f)
                                    .setDuration(100)
                                    .start();
                            break;
                        case MotionEvent.ACTION_UP:
                        case MotionEvent.ACTION_CANCEL:
                            container.animate()
                                    .scaleX(1.0f)
                                    .scaleY(1.0f)
                                    .setDuration(100)
                                    .start();
                            break;
                    }
                    return false;
                }
            });
        }

        FloatingActionButton btnBatchRecord = view.findViewById(R.id.btn_batch_record);
        if (btnBatchRecord != null) {
            btnBatchRecord.setOnClickListener(v -> {
                v.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK);
                showBatchDialog();
            });

            btnBatchRecord.setOnLongClickListener(v -> {
                v.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS);
                startActivity(new Intent(requireContext(), AccountSettingsActivity.class));
                return true;
            });

            btnBatchRecord.setOnTouchListener(new View.OnTouchListener() {
                @Override
                public boolean onTouch(View v, MotionEvent event) {
                    View container = fabContainerBatch != null ? fabContainerBatch : v;
                    switch (event.getAction()) {
                        case MotionEvent.ACTION_DOWN:
                            container.animate().scaleX(0.9f).scaleY(0.9f).setDuration(100).start();
                            break;
                        case MotionEvent.ACTION_UP:
                        case MotionEvent.ACTION_CANCEL:
                            container.animate().scaleX(1.0f).scaleY(1.0f).setDuration(100).start();
                            break;
                    }
                    return false;
                }
            });
        }

        // 首次应用 FAB 材质效果
        applyFabEffectSettings(view);

        calendarRecycler = view.findViewById(R.id.calendar_recycler);
        GridLayoutManager layoutManager = new GridLayoutManager(getContext(), 7) {
            @Override
            public boolean canScrollVertically() { return false; }
        };
        calendarRecycler.setLayoutManager(layoutManager);
        calendarRecycler.setOverScrollMode(View.OVER_SCROLL_NEVER);
        calendarRecycler.setNestedScrollingEnabled(false);

        // 🌟 新增 1：关闭日历刷新时的自带闪烁动画
        calendarRecycler.setItemAnimator(null);

        adapter = new CalendarAdapter(date -> {
            selectedDate = date;
            adapter.setSelectedDate(date);
            showDailyTransactions(date);
        });
        calendarRecycler.setAdapter(adapter);

        // 🌟 新增 2：挂载丝滑物理跟手引擎
        setupFollowHandSwipe(calendarRecycler);

        int defaultMode = prefs.getInt("default_record_mode", 0);

        switchFilterMode(defaultMode);

        tvMonthTitle.setOnClickListener(v -> {
            v.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK);
            showCustomDatePicker();
        });

        // 🌟 替换：使用物理动画引擎来切换月份
        view.findViewById(R.id.btn_prev_month).setOnClickListener(v -> {
            changeCalendarPage(0, -1); // 往左滑出，减 1 月
        });
        view.findViewById(R.id.btn_next_month).setOnClickListener(v -> {
            changeCalendarPage(0, 1);  // 往右滑出，加 1 月
        });

        // 【优化】不再全量观察，只观察当前月份按需请求的数据
        viewModel.getRangeTransactions().observe(getViewLifecycleOwner(), list -> {
            updateCalendar(0);
            if (selectedDate != null && layoutDailyTransactions != null && layoutDailyTransactions.getVisibility() == View.VISIBLE) {
                showDailyTransactions(selectedDate);
            }
        });

        viewModel.getBackupFailureMessage().observe(getViewLifecycleOwner(), msg -> {
            if (msg != null && !msg.isEmpty()) {
                Toast.makeText(requireContext(), msg, Toast.LENGTH_LONG).show();
            }
        });

        updateCalendar(0);
        return view;
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        // 延迟显示首次使用引导，确保 Fragment 与按钮已完成布局
        view.postDelayed(this::showSpotlightGuideIfNeeded, 800);
    }

    @Override
    public void onResume() {
        super.onResume();

        // 【新增】：根据模式动态调整本界面透明度
        SharedPreferences prefs = requireContext().getSharedPreferences("app_prefs", Context.MODE_PRIVATE);
        int themeMode = prefs.getInt("theme_mode", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
        updateFragmentTransparency(themeMode == 3);

        // 应用 FAB 材质效果（与 TAB 栏同步）
        applyFabEffectSettings(getView());

        // 【新增】：自动选中当日并显示当天的账单信息
        if (selectedDate == null) {
            selectedDate = LocalDate.now();
            if (adapter != null) {
                adapter.setSelectedDate(selectedDate);
            }
            showDailyTransactions(selectedDate);
        }

        // 【修复】Tab 切换回来时，强制刷新日历主题颜色，防止字体变灰
        if (adapter != null && getContext() != null) {
            adapter.refreshThemeColors(getContext());
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        // 重置 LiquidGlassView 绑定标志，避免下次进入时重复 bind 导致崩溃
        fabLiquidGlassBound = false;
        fabBlurSetupDone = false;
        // 释放 RenderScript 资源
        if (fabRenderScriptBlur != null) {
            fabRenderScriptBlur.destroy();
            fabRenderScriptBlur = null;
        }
        // 移除聚光灯引导回调与视图
        spotlightHandler.removeCallbacksAndMessages(null);
        if (currentSpotlightGuide != null && currentSpotlightGuide.getParent() != null) {
            ((ViewGroup) currentSpotlightGuide.getParent()).removeView(currentSpotlightGuide);
            currentSpotlightGuide = null;
        }
        // 清空控件引用，防止内存泄漏
        fabContainerQuick = null;
        fabContainerBatch = null;
        blurFabQuick = null;
        blurFabBatch = null;
        liquidFabQuick = null;
        liquidFabBatch = null;
        lastFabSettings.reset();
    }

    /**
     * 应用 FAB 材质效果，与 TAB 栏风格同步。
     * 防御性设计：
     * 1. LiquidGlassView 绑定 scroll_container（不含 FAB 自身）避免自采样闪退
     * 2. bind() 使用 post 延迟到布局完成
     * 3. FabSettingsSnapshot 缓存参数避免 onResume 重复创建 Drawable
     * 4. 圆形裁剪使用匿名内部类 outline.setOval()，不使用 OVAL 常量
     */
    @SuppressWarnings("deprecation")
    private void applyFabEffectSettings(View view) {
        if (view == null) view = getView();
        if (view == null) return;
        if (blurFabQuick == null || blurFabBatch == null
                || liquidFabQuick == null || liquidFabBatch == null) return;

        Context ctx = getContext();
        if (ctx == null) return;

        SharedPreferences prefs = ctx.getSharedPreferences("app_prefs", Context.MODE_PRIVATE);
        int effectMode = prefs.getInt(TabPreferenceKeys.TAB_EFFECT_MODE, TabEffectMode.NONE);
        // 低版本系统不支持液态玻璃，自动回退为无效果
        if (effectMode == TabEffectMode.LIQUID_GLASS
                && android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.TIRAMISU) {
            effectMode = TabEffectMode.NONE;
        }
        int blurLevel = prefs.getInt(TabPreferenceKeys.TAB_BLUR_LEVEL, 5);
        int opacity = prefs.getInt(TabPreferenceKeys.TAB_OPACITY, 80);
        int refractionHeight = prefs.getInt(TabPreferenceKeys.TAB_LIQUID_REFRACTION_HEIGHT, 15);
        int dispersion = prefs.getInt(TabPreferenceKeys.TAB_LIQUID_DISPERSION, 50);
        int shadowSize = prefs.getInt(TabPreferenceKeys.TAB_SHADOW_SIZE, 1);
        int shadowOpacity = prefs.getInt(TabPreferenceKeys.TAB_SHADOW_OPACITY, 25);

        // FAB 背景色统一管理（每次都执行，确保与材质层状态一致，消除闪烁）
        // 非 NONE 模式：设为透明让材质层显示
        // NONE 模式：设为 bottom_bar_background，与 Tab 栏背景色一致
        FloatingActionButton fabQuick = view.findViewById(R.id.btn_quick_record);
        FloatingActionButton fabBatch = view.findViewById(R.id.btn_batch_record);
        int fabColor = (effectMode != TabEffectMode.NONE)
                ? android.graphics.Color.TRANSPARENT
                : ContextCompat.getColor(ctx, R.color.bottom_bar_background);
        if (fabQuick != null) {
            fabQuick.setBackgroundTintList(ColorStateList.valueOf(fabColor));
            fabQuick.setCompatElevation(0f);
        }
        if (fabBatch != null) {
            fabBatch.setBackgroundTintList(ColorStateList.valueOf(fabColor));
            fabBatch.setCompatElevation(0f);
        }

        // 参数未变化时跳过材质层配置，避免 onResume 重复创建 Drawable
        if (lastFabSettings.equals(effectMode, blurLevel, opacity, refractionHeight, dispersion,
                shadowSize, shadowOpacity)) {
            return;
        }

        float density = getResources().getDisplayMetrics().density;
        // view 本身就是 root_layout_record（inflate 的根布局），findViewById 不搜索自身，直接用 view
        ViewGroup rootLayoutRecord = (view.getId() == R.id.root_layout_record)
                ? (ViewGroup) view : (ViewGroup) view.findViewById(R.id.root_layout_record);
        ViewGroup scrollView = (ViewGroup) view.findViewById(R.id.scroll_container);
        // 【关键修复】采样源使用 scroll_container 的直接子元素（LinearLayout）而非 NestedScrollView 本身。
        // 原因：NestedScrollView 只负责滚动其子视图，本身不渲染内容像素；
        // LiquidGlassView.bind() 需要真正渲染内容的 ViewGroup 才能采样到画面，否则呈现透明背景。
        ViewGroup liquidSampleSource = scrollView;
        if (scrollView != null && scrollView.getChildCount() > 0
                && scrollView.getChildAt(0) instanceof ViewGroup) {
            liquidSampleSource = (ViewGroup) scrollView.getChildAt(0);
        }

        if (effectMode == TabEffectMode.LIQUID_GLASS) {
            // 液态玻璃效果：BlurView（模糊背景）+ LiquidGlassView（液态玻璃质感）叠加
            // 与 Tab 栏一致：底层模糊 + 上层液态玻璃，形成"模糊+透明液态玻璃"的叠加质感
            blurFabQuick.setVisibility(View.VISIBLE);
            blurFabBatch.setVisibility(View.VISIBLE);
            liquidFabQuick.setVisibility(View.VISIBLE);
            liquidFabBatch.setVisibility(View.VISIBLE);

            // 初始化 BlurView（首次调用 setupWith，复用 RenderScript）
            if (!fabBlurSetupDone && rootLayoutRecord != null) {
                if (fabRenderScriptBlur == null) {
                    fabRenderScriptBlur = new RenderScriptBlur(ctx);
                }
                blurFabQuick.setupWith(rootLayoutRecord, fabRenderScriptBlur);
                blurFabBatch.setupWith(rootLayoutRecord, fabRenderScriptBlur);
                applyOvalOutline(blurFabQuick);
                applyOvalOutline(blurFabBatch);
                fabBlurSetupDone = true;
            }
            blurFabQuick.setBlurEnabled(true);
            blurFabQuick.setBlurRadius(blurLevel);
            blurFabBatch.setBlurEnabled(true);
            blurFabBatch.setBlurRadius(blurLevel);
            // 液态玻璃模式下 BlurView 使用透明覆盖色，仅提供模糊采样
            blurFabQuick.setBackground(createOvalBackground(0));
            blurFabBatch.setBackground(createOvalBackground(0));

            // 绑定内容容器（LinearLayout）作为采样源，确保采样到真实渲染像素
            if (!fabLiquidGlassBound && liquidSampleSource != null) {
                bindLiquidGlassSafely(liquidFabQuick, liquidSampleSource, true);
                bindLiquidGlassSafely(liquidFabBatch, liquidSampleSource, false);
            }

            // 将 setter 延迟到消息队列后，确保 bind/ensureGlass 已创建 glass。
            liquidFabQuick.post(() -> {
                try {
                    configureLiquidGlassFab(liquidFabQuick, blurLevel, opacity, refractionHeight, dispersion, density);
                    configureLiquidGlassFab(liquidFabBatch, blurLevel, opacity, refractionHeight, dispersion, density);
                } catch (Exception e) {
                    Log.e("RecordFragment", "Failed to configure LiquidGlassView for FAB", e);
                }
            });
        } else if (effectMode == TabEffectMode.BLUR) {
            // 模糊效果：仅使用 BlurView
            liquidFabQuick.setVisibility(View.GONE);
            liquidFabBatch.setVisibility(View.GONE);
            blurFabQuick.setVisibility(View.VISIBLE);
            blurFabBatch.setVisibility(View.VISIBLE);

            // 首次初始化 BlurView（setupWith 只调用一次，避免重复创建 RenderScript）
            if (!fabBlurSetupDone && rootLayoutRecord != null) {
                if (fabRenderScriptBlur == null) {
                    fabRenderScriptBlur = new RenderScriptBlur(ctx);
                }
                blurFabQuick.setupWith(rootLayoutRecord, fabRenderScriptBlur);
                blurFabBatch.setupWith(rootLayoutRecord, fabRenderScriptBlur);
                applyOvalOutline(blurFabQuick);
                applyOvalOutline(blurFabBatch);
                fabBlurSetupDone = true;
            }

            blurFabQuick.setBlurEnabled(true);
            blurFabQuick.setBlurRadius(blurLevel);
            blurFabBatch.setBlurEnabled(true);
            blurFabBatch.setBlurRadius(blurLevel);

            // BlurView 只提供模糊采样，背景透明
            blurFabQuick.setBackground(createOvalBackground(0));
            blurFabBatch.setBackground(createOvalBackground(0));

            // 在 FAB 上叠加白色半透明背景层，确保白色半透明层在最上层、不被阴影压住
            int alphaInt = (int) (opacity / 100f * 255);
            int overlayColor = (alphaInt << 24) | 0x00FFFFFF;
            if (fabQuick != null) fabQuick.setBackgroundTintList(ColorStateList.valueOf(overlayColor));
            if (fabBatch != null) fabBatch.setBackgroundTintList(ColorStateList.valueOf(overlayColor));
        } else {
            // 无效果：隐藏所有材质层，FAB 显示纯色
            blurFabQuick.setVisibility(View.GONE);
            blurFabBatch.setVisibility(View.GONE);
            liquidFabQuick.setVisibility(View.GONE);
            liquidFabBatch.setVisibility(View.GONE);
        }

        // 【FAB 阴影】与 Tab 栏完全一致：使用 TabShadowDrawable（isOval=true）+ padding 外发散
        // 阴影独立于材质效果模式，所有模式下都应用，确保视觉一致
        applyFabShadow(shadowSize, shadowOpacity, density);

        lastFabSettings.update(effectMode, blurLevel, opacity, refractionHeight, dispersion,
                shadowSize, shadowOpacity);
    }

    /**
     * 为两个 FAB 容器应用与 Tab 栏完全一致的阴影效果。
     * 使用 TabShadowDrawable(isOval=true) 绘制圆形外发散阴影，通过 padding 让阴影向外发散。
     * 参数与 Tab 栏完全一致：shadowSize 控制大小（shadowSizeDp = shadowSize * 0.5f），
     * shadowOpacity 控制浓淡。shadowSize=0 时彻底隐藏阴影。
     */
    private void applyFabShadow(int shadowSize, int shadowOpacity, float density) {
        if (fabContainerQuick == null || fabContainerBatch == null) return;

        if (shadowSize > 0) {
            float shadowSizeDp = shadowSize * 0.5f;
            int shadowPx = (int) (shadowSizeDp * density);
            int shadowAlpha = (int) (shadowOpacity / 100f * 255);
            // FAB 为圆形，使用 isOval=true；cornerRadius 对圆形无意义，传 0
            TabShadowDrawable shadowDrawable = new TabShadowDrawable(0, shadowPx, shadowAlpha, true);
            fabContainerQuick.setBackground(shadowDrawable);
            fabContainerQuick.setPadding(shadowPx, shadowPx, shadowPx, shadowPx);
            fabContainerBatch.setBackground(new TabShadowDrawable(0, shadowPx, shadowAlpha, true));
            fabContainerBatch.setPadding(shadowPx, shadowPx, shadowPx, shadowPx);
        } else {
            fabContainerQuick.setBackground(null);
            fabContainerQuick.setPadding(0, 0, 0, 0);
            fabContainerBatch.setBackground(null);
            fabContainerBatch.setPadding(0, 0, 0, 0);
        }
    }

    /**
     * 安全绑定 LiquidGlassView 到采样源，使用 post 延迟到布局完成。
     * 防御性设计：
     * 1. 使用字段引用（liquidFabQuick/Batch）而非局部变量，确保 onDestroyView 置 null 后检查生效
     * 2. 检查 isAttachedToWindow()，避免在已分离视图上 bind 导致崩溃
     * 3. 检查 sampleSource.isAttachedToWindow()，确保采样源有效
     * 4. try-catch 兜底，防止 LiquidGlassView 内部异常导致闪退
     */
    private void bindLiquidGlassSafely(LiquidGlassView liquidView, ViewGroup sampleSource, boolean isQuick) {
        if (liquidView == null || sampleSource == null) return;
        // 视图与采样源都已 attach 时直接 bind，确保在后续 layout/ensureGlass 之前 customSource 已设置；
        // 否则延迟到 attach 后再执行。
        if (liquidView.isAttachedToWindow() && sampleSource.isAttachedToWindow()) {
            try {
                liquidView.bind(sampleSource);
                fabLiquidGlassBound = true;
            } catch (Exception e) {
                Log.e("RecordFragment", "Failed to bind LiquidGlassView for FAB", e);
            }
        } else {
            liquidView.post(() -> {
                try {
                    // 双重检查：字段引用可能已在 onDestroyView 中被置 null
                    LiquidGlassView target = isQuick ? liquidFabQuick : liquidFabBatch;
                    if (target == null || target != liquidView) return; // 视图已销毁或已替换
                    if (!target.isAttachedToWindow() || !sampleSource.isAttachedToWindow()) return;
                    target.bind(sampleSource);
                    fabLiquidGlassBound = true;
                } catch (Exception e) {
                    Log.e("RecordFragment", "Failed to bind LiquidGlassView for FAB", e);
                }
            });
        }
    }

    /**
     * 配置 LiquidGlassView 的圆形参数（FAB 为圆形，cornerRadius 设为半径）。
     */
    private void configureLiquidGlassFab(LiquidGlassView liquidView, int blurLevel, int opacity,
                                         int refractionHeight, int dispersion, float density) {
        liquidView.setCornerRadius(28 * density); // FAB normal 56dp，半径 28dp，强制圆形
        liquidView.setBlurRadius(blurLevel * 2.5f);
        liquidView.setRefractionHeight(refractionHeight * density);
        liquidView.setRefractionOffset(0f); // FAB 尺寸小，不偏移折射避免渲染溢出
        liquidView.setDispersion(dispersion / 100f);
        liquidView.setTintAlpha((opacity / 100f) * 0.3f);
        liquidView.setDraggableEnabled(false);
        liquidView.setElasticEnabled(false);
        liquidView.setTouchEffectEnabled(false);
    }

    /**
     * 为 BlurView 应用圆形裁剪（使用匿名内部类 outline.setOval()，不使用 OVAL 常量）。
     * 符合项目硬约束：ViewOutlineProvider.OVAL 不可用。
     */
    private void applyOvalOutline(View view) {
        // 延迟到布局完成后执行，避免 onCreateView 阶段 getWidth()/getHeight() 为 0
        view.post(() -> {
            view.setOutlineProvider(new ViewOutlineProvider() {
                @Override
                public void getOutline(View v, android.graphics.Outline outline) {
                    outline.setOval(0, 0, v.getWidth(), v.getHeight());
                }
            });
            view.setClipToOutline(true);
        });
    }

    /**
     * 创建圆形半透明背景 Drawable（BlurView 模式下作为覆盖色）。
     */
    private android.graphics.drawable.Drawable createOvalBackground(int alphaInt) {
        android.graphics.drawable.GradientDrawable drawable = new android.graphics.drawable.GradientDrawable();
        drawable.setShape(android.graphics.drawable.GradientDrawable.OVAL);
        drawable.setColor((alphaInt << 24) | 0x00FFFFFF);
        return drawable;
    }

    /**
     * 缓存上次应用的 FAB 材质参数，避免 onResume 等场景重复创建 Drawable。
     */
    private static class FabSettingsSnapshot {
        int effectMode = -1;
        int blurLevel = -1;
        int opacity = -1;
        int refractionHeight = -1;
        int dispersion = -1;
        int shadowSize = -1;
        int shadowOpacity = -1;

        boolean equals(int effectMode, int blurLevel, int opacity, int refractionHeight, int dispersion,
                       int shadowSize, int shadowOpacity) {
            return this.effectMode == effectMode && this.blurLevel == blurLevel
                    && this.opacity == opacity && this.refractionHeight == refractionHeight
                    && this.dispersion == dispersion && this.shadowSize == shadowSize
                    && this.shadowOpacity == shadowOpacity;
        }

        void update(int effectMode, int blurLevel, int opacity, int refractionHeight, int dispersion,
                    int shadowSize, int shadowOpacity) {
            this.effectMode = effectMode;
            this.blurLevel = blurLevel;
            this.opacity = opacity;
            this.refractionHeight = refractionHeight;
            this.dispersion = dispersion;
            this.shadowSize = shadowSize;
            this.shadowOpacity = shadowOpacity;
        }

        void reset() {
            effectMode = -1;
            blurLevel = -1;
            opacity = -1;
            refractionHeight = -1;
            dispersion = -1;
            shadowSize = -1;
            shadowOpacity = -1;
        }
    }

    // 显示首次打开聚光灯引导
    private void showSpotlightGuideIfNeeded() {
        SharedPreferences prefs = requireContext().getSharedPreferences("app_prefs", Context.MODE_PRIVATE);
        boolean hasShownGuide = prefs.getBoolean("has_shown_spotlight_guide", false);
        if (hasShownGuide) return;
        if (getView() == null || isDetached()) return;

        View quickRecord = getView().findViewById(R.id.btn_quick_record);
        View batchRecord = getView().findViewById(R.id.btn_batch_record);
        if (quickRecord == null || batchRecord == null) return;

        runSpotlightStage(quickRecord, getString(R.string.spotlight_quick_record_hint), () ->
                runSpotlightStage(batchRecord, getString(R.string.spotlight_batch_record_hint), () ->
                        prefs.edit().putBoolean("has_shown_spotlight_guide", true).apply()));
    }

    private void runSpotlightStage(View target, String hint, Runnable onDismiss) {
        if (getView() == null || isDetached()) return;

        int[] location = new int[2];
        target.getLocationOnScreen(location);
        android.graphics.Rect targetRect = new android.graphics.Rect(
                location[0], location[1],
                location[0] + target.getWidth(), location[1] + target.getHeight());

        SpotlightGuideView guide = new SpotlightGuideView(requireContext());
        this.currentSpotlightGuide = guide;
        guide.setTarget(targetRect, hint);
        guide.setAlpha(0f);

        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT);
        ((ViewGroup) getView()).addView(guide, params);

        guide.animate()
                .alpha(1f)
                .setDuration(250)
                .start();

        guide.setOnClickListener(v -> guide.animate()
                .alpha(0f)
                .setDuration(200)
                .withEndAction(() -> {
                    currentSpotlightGuide = null;
                    if (getView() != null) {
                        ((ViewGroup) getView()).removeView(guide);
                    }
                    if (isAdded() && !requireActivity().isFinishing() && !requireActivity().isDestroyed()) {
                        onDismiss.run();
                    }
                })
                .start());
    }

    // 【新增方法】：动态控制界面透明度，不破坏 XML 默认结构
    // 【修改】FAB 背景色管理统一由 applyFabEffectSettings 负责，此方法只管理顶部栏/周栏透明度
    private void updateFragmentTransparency(boolean isCustomBg) {
        View view = getView();
        if (view == null) return;

        View topBar = view.findViewById(R.id.layout_top_bar);
        View weekHeader = view.findViewById(R.id.layout_week_header);

        if (isCustomBg) {
            // 自定义主题：顶部基础框架全透明，让底层的图片完全透出来
            view.setBackgroundColor(android.graphics.Color.TRANSPARENT);
            if (topBar != null) topBar.setBackgroundColor(android.graphics.Color.TRANSPARENT);
            if (weekHeader != null) weekHeader.setBackgroundColor(android.graphics.Color.TRANSPARENT);
        } else {
            // 日间/夜间模式：使用资源文件中定义的背景色
            view.setBackgroundResource(R.color.bar_background);
            if (topBar != null) topBar.setBackgroundResource(R.color.bar_background);
            if (weekHeader != null) weekHeader.setBackgroundResource(R.color.bar_background);
        }
    }

    private void showCustomDatePicker() {
        if (getContext() == null) return;

        LocalDate baseDate = selectedDate != null ? selectedDate : LocalDate.now();

        DatePickerHelper.showDatePicker(getContext(), baseDate, (year, month, day) -> {
            currentMonth = YearMonth.of(year, month);
            selectedDate = LocalDate.of(year, month, day);
            updateCalendar(0);
            adapter.setSelectedDate(selectedDate);
        });
    }

    private void initGestureDetector() {
        gestureDetector = new GestureDetector(getContext(), new GestureDetector.SimpleOnGestureListener() {
            private static final int SWIPE_THRESHOLD = 100;
            private static final int SWIPE_VELOCITY_THRESHOLD = 100;

            @Override
            public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
                if (e1 == null || e2 == null) return false;

                float diffX = e2.getX() - e1.getX();
                float diffY = e2.getY() - e1.getY();

                if (Math.abs(diffX) > Math.abs(diffY) &&
                        Math.abs(diffX) > SWIPE_THRESHOLD &&
                        Math.abs(velocityX) > SWIPE_VELOCITY_THRESHOLD) {

                    if (diffX > 0) {
                        currentMonth = currentMonth.minusMonths(1);
                        updateCalendar(-1); // 从左侧滑入
                    } else {
                        currentMonth = currentMonth.plusMonths(1);
                        updateCalendar(1);  // 从右侧滑入
                    }
                    return true;
                }
                return false;
            }
        });
    }

    private void switchFilterMode(int mode) {
        currentFilterMode = mode;
        adapter.setFilterMode(mode);
    }

    // 更新日历，带有方向参数：-1 左滑入，1 右滑入，0 不执行动画
    private void updateCalendar(int direction) {
        tvMonthTitle.setText(currentMonth.format(DateTimeFormatter.ofPattern("yyyy年MM月")));

        // 🌟 核心优化：每次日历刷新前，触发按需加载本月数据
        fetchDataForCurrentMonth();

        List<LocalDate> days = new ArrayList<>();
        LocalDate firstDay = currentMonth.atDay(1);
        int dayOfWeek = firstDay.getDayOfWeek().getValue();
        int offset = dayOfWeek - 1;

        LocalDate startOfGrid = firstDay.minusDays(offset);
        for (int i = 0; i < offset; i++) {
            days.add(startOfGrid.plusDays(i));
        }

        int length = currentMonth.lengthOfMonth();
        for (int i = 1; i <= length; i++) {
            days.add(currentMonth.atDay(i));
        }

        // 🌟 读取刚刚请求到的本月数据 (取代全量获取)
        List<Transaction> allList = viewModel.getRangeTransactions().getValue();
        List<Transaction> currentList = allList != null ? allList : new ArrayList<>();

        adapter.setCurrentMonth(currentMonth);
        adapter.updateData(days, currentList);

        if (selectedDate != null && YearMonth.from(selectedDate).equals(currentMonth)) {
            adapter.setSelectedDate(selectedDate);
        }

        // 刷新下方的当日记账记录
        if (selectedDate != null && layoutDailyTransactions != null && layoutDailyTransactions.getVisibility() == View.VISIBLE) {
            showDailyTransactions(selectedDate);
        }

        if (getContext() != null && calendarRecycler != null) {
            if (direction == 1) {
                Animation anim = AnimationUtils.loadAnimation(getContext(), R.anim.slide_in_right);
                calendarRecycler.startAnimation(anim);
            } else if (direction == -1) {
                Animation anim = AnimationUtils.loadAnimation(getContext(), R.anim.slide_in_left);
                calendarRecycler.startAnimation(anim);
            }
        }
    }

    private void showDailyTransactions(LocalDate date) {
        if (layoutDailyTransactions == null || dailyTransactionsAdapter == null) return;

        // 显示当日记账记录区域
        layoutDailyTransactions.setVisibility(View.VISIBLE);
        tvDailyDateTitle.setText(date.format(DateTimeFormatter.ofPattern("yyyy年MM月dd日", Locale.CHINA)));

        // 获取当日的记账记录
        List<Transaction> all = viewModel.getRangeTransactions().getValue();
        List<Transaction> dayList = new ArrayList<>();
        if (all != null) {
            long start = date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
            long end = date.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
            dayList = all.stream()
                    .filter(t -> t.date >= start && t.date < end)
                    .collect(Collectors.toList());
        }

        // 更新统计数据
        double dayIncome = 0;
        double dayExpense = 0;

        for (Transaction t : dayList) {
            if (t.type == 1) {
                dayIncome += t.amount;
            } else if (t.type == 0) {
                dayExpense += t.amount;
            }
        }

        if (dayIncome == 0 && dayExpense == 0) {
            tvDailySummary.setVisibility(View.GONE);
        } else {
            tvDailySummary.setVisibility(View.VISIBLE);
            tvDailySummary.setText(buildIncomeExpenseSpan(requireContext(), dayIncome, dayExpense));
        }

        // 更新列表或显示空状态引导
        if (dayList.isEmpty()) {
            dailyTransactionsRecycler.setVisibility(View.GONE);
            if (layoutEmptyState != null) {
                layoutEmptyState.setVisibility(View.VISIBLE);
                AnimUtils.fadeIn(layoutEmptyState, 250);
            }
            dailyTransactionsAdapter.setTransactions(new ArrayList<>());
        } else {
            dailyTransactionsRecycler.setVisibility(View.VISIBLE);
            if (layoutEmptyState != null) {
                layoutEmptyState.setVisibility(View.GONE);
            }
            dailyTransactionsAdapter.setTransactions(dayList);
        }
    }

    private void showBatchDialog() {
        if (getContext() == null) return;

        BatchTransactionDialogHelper.showBatchDialog(getContext(), new BatchTransactionDialogHelper.OnBatchSavedListener() {
            @Override
            public void onBatchSaved(List<Transaction> transactions) {
                viewModel.addBatchTransactions(transactions, count -> {
                    if (isAdded() && getActivity() != null && !getActivity().isFinishing() && !getActivity().isDestroyed()) {
                        requireActivity().runOnUiThread(() -> {
                            if (isAdded()) {
                                viewModel.setDateRange(currentStartMillis, currentEndMillis);
                                WidgetUtils.updateAllWidgets(getContext());
                            }
                        });
                    }
                });
            }
        });
    }

    private void showAddOrEditDialog(Transaction existingTransaction, LocalDate date) {
        if (getContext() == null) return;

        TransactionDialogHelper.showAddOrEditDialog(getContext(), existingTransaction, date, new TransactionDialogHelper.OnTransactionSavedListener() {
            @Override
            public void onTransactionSaved(Transaction transaction, boolean isEdit) {
                boolean willBackup = willTriggerAutoBackup();
                if (isEdit) {
                    viewModel.updateTransaction(transaction);
                    Toast.makeText(getContext(), getBackupMessage("已修改记录", willBackup), Toast.LENGTH_SHORT).show();
                } else {
                    viewModel.addTransaction(transaction);
                    viewModel.setDateRange(currentStartMillis, currentEndMillis);
                    Toast.makeText(getContext(), getBackupMessage("已添加记录", willBackup), Toast.LENGTH_SHORT).show();
                    WidgetUtils.updateAllWidgets(getContext());
                }
            }

            @Override
            public void onTransactionDeleted(Transaction transaction) {
                boolean willBackup = willTriggerAutoBackup();
                viewModel.deleteTransaction(transaction);
                Toast.makeText(getContext(), getBackupMessage("已删除记录", willBackup), Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onPhotoDeleted(int transactionId) {
                viewModel.clearPhotoPath(transactionId);
            }

            @Override
            public void onSplitRequested(Transaction transaction) {
                SplitTransactionDialogHelper.showSplitDialog(getContext(), transaction, new SplitTransactionDialogHelper.OnSplitSavedListener() {
                    @Override
                    public void onSplitSaved(Transaction originalTransaction, List<Transaction> splitTransactions) {
                        viewModel.splitTransaction(originalTransaction, splitTransactions, count -> {
                            requireActivity().runOnUiThread(() -> {
                                viewModel.setDateRange(currentStartMillis, currentEndMillis);
                                WidgetUtils.updateAllWidgets(getContext());
                            });
                        });
                    }
                });
            }
        });
    }

    private boolean willTriggerAutoBackup() {
        SharedPreferences prefs = requireContext().getSharedPreferences("app_prefs", Context.MODE_PRIVATE);
        boolean enabled = prefs.getBoolean("enable_auto_backup", false);
        if (!enabled) return false;
        if (prefs.getString("auto_backup_uri", "").isEmpty()) return false;
        int freq = prefs.getInt("auto_backup_freq", 1);
        int count = prefs.getInt("auto_backup_change_count", 0);
        return (count + 1) >= freq;
    }

    private String getBackupMessage(String baseMessage, boolean backupJustTriggered) {
        return backupJustTriggered ? baseMessage + ",已备份" : baseMessage;
    }

    private android.text.SpannableStringBuilder buildIncomeExpenseSpan(Context context, double dayIncome, double dayExpense) {
        double dayBalance = dayIncome - dayExpense;

        int colorExpense = ContextCompat.getColor(context, R.color.expense_green);
        int colorIncome = ContextCompat.getColor(context, R.color.income_red);
        int colorBalance = ContextCompat.getColor(context, R.color.app_accent);

        android.text.SpannableStringBuilder ssb = new android.text.SpannableStringBuilder();

        if (dayExpense > 0) {
            String expStr = String.format(Locale.CHINA, "支出: %.2f", dayExpense);
            int start = ssb.length();
            ssb.append(expStr);
            ssb.setSpan(new android.text.style.ForegroundColorSpan(colorExpense), start, ssb.length(), android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            ssb.append("    ");
        }
        if (dayIncome > 0) {
            String incStr = String.format(Locale.CHINA, "收入: %.2f", dayIncome);
            int start = ssb.length();
            ssb.append(incStr);
            ssb.setSpan(new android.text.style.ForegroundColorSpan(colorIncome), start, ssb.length(), android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            ssb.append("    ");
        }
        String balStr = String.format(Locale.CHINA, "结余: %.2f", dayBalance);
        int startBal = ssb.length();
        ssb.append(balStr);
        ssb.setSpan(new android.text.style.ForegroundColorSpan(colorBalance), startBal, ssb.length(), android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

        return ssb;
    }

}