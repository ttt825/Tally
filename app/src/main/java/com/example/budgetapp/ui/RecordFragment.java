package com.example.budgetapp.ui;

import android.util.Log;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Bundle;
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

import com.example.budgetapp.database.Transaction;
import com.example.budgetapp.util.CategoryManager;
import com.example.budgetapp.viewmodel.TransactionViewModel;
import com.example.budgetapp.widget.WidgetUtils;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import eightbitlab.com.blurview.BlurView;
import eightbitlab.com.blurview.RenderScriptBlur;

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
    private TextView tvNoRecords;

    // 提取全局 RecyclerView 以便执行动画
    private RecyclerView calendarRecycler;
    private RecyclerView dailyTransactionsRecycler;
    private TransactionListAdapter dailyTransactionsAdapter;

    private TransactionListAdapter currentDetailAdapter;

    // 手势检测器
    private GestureDetector gestureDetector;

    // Activity Result Launcher
    private ActivityResultLauncher<Intent> yearCalendarLauncher;

    // 新增的成员变量
    private AlertDialog currentDetailDialog;
    private TextView currentDetailSummaryTextView;

    // 新增：记录当前的过滤模式
    private int currentFilterMode = 0;

    // 新增：用于记录当前请求的时间范围，防止无限循环查询
    private long currentStartMillis = 0;
    private long currentEndMillis = 0;

    private int touchSlop;

    /**
     * 核心跟手引擎：接管日历的水平方向滑动
     */
    private void setupFollowHandSwipe(RecyclerView recyclerView) {
        touchSlop = android.view.ViewConfiguration.get(requireContext()).getScaledTouchSlop();

        recyclerView.addOnItemTouchListener(new RecyclerView.OnItemTouchListener() {
            private float initialX = 0f;
            private float initialY = 0f;
            private boolean isHorizontalSwipe = false;

            @Override
            public boolean onInterceptTouchEvent(@NonNull RecyclerView rv, @NonNull MotionEvent e) {
                switch (e.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        // 🌟 核心修复 1：必须使用屏幕绝对坐标 getRawX()，否则视图移动会导致坐标疯狂抵消
                        initialX = e.getRawX();
                        initialY = e.getRawY();
                        isHorizontalSwipe = false;
                        break;
                    case MotionEvent.ACTION_MOVE:
                        float dx = e.getRawX() - initialX;
                        float dy = e.getRawY() - initialY;
                        if (!isHorizontalSwipe && Math.abs(dx) > touchSlop && Math.abs(dx) > Math.abs(dy)) {
                            isHorizontalSwipe = true;
                            // 🌟 核心修复 2：强制剥夺父容器（如外层 ScrollView）的滑动权，保证不被外层打断
                            if (rv.getParent() != null) {
                                rv.getParent().requestDisallowInterceptTouchEvent(true);
                            }
                            return true;
                        }
                        break;
                }
                return false;
            }

            @Override
            public void onTouchEvent(@NonNull RecyclerView rv, @NonNull MotionEvent e) {
                if (!isHorizontalSwipe) return;

                float dx = e.getRawX() - initialX;
                float screenWidth = rv.getWidth();
                if (screenWidth == 0) screenWidth = 1080;

                switch (e.getAction()) {
                    case MotionEvent.ACTION_MOVE:
                        // 跟手阻尼移动
                        rv.setTranslationX(dx * 0.6f);
                        rv.setAlpha(1f - (Math.abs(dx) / screenWidth) * 0.5f);
                        break;

                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        if (dx > screenWidth * 0.2f) {
                            finishSwipeAnimation(rv, screenWidth, 0, -1); // 拉出上个月
                        } else if (dx < -screenWidth * 0.2f) {
                            finishSwipeAnimation(rv, -screenWidth, 0, 1); // 拉出下个月
                        } else {
                            // 距离不够，平滑恢复原位（删除了多余的 Overshoot 回弹效果）
                            rv.animate().translationX(0f).alpha(1f).setDuration(250)
                                    .setInterpolator(new android.view.animation.DecelerateInterpolator()).start();
                        }
                        isHorizontalSwipe = false;
                        break;
                }
            }

            @Override
            public void onRequestDisallowInterceptTouchEvent(boolean disallowIntercept) {}
        });
    }

    /**
     * 供按钮点击触发的自动滑动封装
     */
    private void changeCalendarPage(int yearOffset, int monthOffset) {
        if (calendarRecycler != null) {
            float screenWidth = calendarRecycler.getWidth();
            if (screenWidth == 0) screenWidth = 1080;
            // 右滑拉出过去，左滑拉出未来
            float targetX = (yearOffset > 0 || monthOffset > 0) ? -screenWidth : screenWidth;
            finishSwipeAnimation(calendarRecycler, targetX, yearOffset, monthOffset);
        }
    }

    /**
     * 结算动画：滑出 -> 切换底层数据 -> 从另一侧滑入
     */
    private void finishSwipeAnimation(RecyclerView rv, float targetTranslationX, int yearOffset, int monthOffset) {
        rv.animate()
                .translationX(targetTranslationX)
                .alpha(0f)
                .setDuration(150)
                .withEndAction(() -> {
                    // 1. 在屏幕外悄悄修改时间
                    if (yearOffset != 0) currentMonth = currentMonth.plusYears(yearOffset);
                    if (monthOffset != 0) currentMonth = currentMonth.plusMonths(monthOffset);

                    // 2. 将列表瞬移到屏幕的另一侧，准备入场
                    rv.setTranslationX(-targetTranslationX * 0.5f);

                    // 3. 触发数据库查询与日历数据重绘（传 0 禁用原有的旧动画）
                    updateCalendar(0);

                    // 4. 减速滑入并恢复全不透明
                    rv.animate()
                            .translationX(0f)
                            .alpha(1f)
                            .setDuration(300)
                            .setInterpolator(new android.view.animation.DecelerateInterpolator())
                            .start();
                })
                .start();
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
        tvNoRecords = view.findViewById(R.id.tv_no_records);
        dailyTransactionsRecycler = view.findViewById(R.id.rv_daily_transactions);

        if (dailyTransactionsRecycler != null) {
            dailyTransactionsRecycler.setLayoutManager(new LinearLayoutManager(getContext()));
            dailyTransactionsAdapter = new TransactionListAdapter(transaction -> {
                LocalDate transDate = Instant.ofEpochMilli(transaction.date).atZone(ZoneId.systemDefault()).toLocalDate();
                showAddOrEditDialog(transaction, transDate);
            });
            dailyTransactionsRecycler.setAdapter(dailyTransactionsAdapter);
        }

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

            // 添加触摸反馈动画
            btnQuickRecord.setOnTouchListener(new View.OnTouchListener() {
                @Override
                public boolean onTouch(View v, MotionEvent event) {
                    switch (event.getAction()) {
                        case MotionEvent.ACTION_DOWN:
                            // 按下时缩小动画
                            v.animate()
                                    .scaleX(0.9f)
                                    .scaleY(0.9f)
                                    .setDuration(100)
                                    .start();
                            break;
                        case MotionEvent.ACTION_UP:
                        case MotionEvent.ACTION_CANCEL:
                            // 抬起或取消时恢复原状
                            v.animate()
                                    .scaleX(1.0f)
                                    .scaleY(1.0f)
                                    .setDuration(100)
                                    .start();
                            break;
                    }
                    return false; // 返回false以确保点击事件仍会被处理
                }
            });
        }

        FloatingActionButton btnBatchRecord = view.findViewById(R.id.btn_batch_record);
        if (btnBatchRecord != null) {
            btnBatchRecord.setOnClickListener(v -> {
                v.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK);
                showBatchDialog();
            });

            btnBatchRecord.setOnTouchListener(new View.OnTouchListener() {
                @Override
                public boolean onTouch(View v, MotionEvent event) {
                    switch (event.getAction()) {
                        case MotionEvent.ACTION_DOWN:
                            v.animate().scaleX(0.9f).scaleY(0.9f).setDuration(100).start();
                            break;
                        case MotionEvent.ACTION_UP:
                        case MotionEvent.ACTION_CANCEL:
                            v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(100).start();
                            break;
                    }
                    return false;
                }
            });
        }

        BlurView blurFabBatch = view.findViewById(R.id.blur_fab_batch);
        if (blurFabBatch != null) {
            View rootView = view.findViewById(R.id.root_layout_record);
            SharedPreferences tabPrefs = requireContext().getSharedPreferences("app_prefs", Context.MODE_PRIVATE);
            int blurLevel = tabPrefs.getInt("tab_blur_level", 5);
            @SuppressWarnings("deprecation")
            var ignoredBatch = blurFabBatch.setupWith((ViewGroup) rootView, new RenderScriptBlur(requireContext()))
                    .setBlurRadius(blurLevel);
            blurFabBatch.setOutlineProvider(new ViewOutlineProvider() {
                @Override
                public void getOutline(View view, android.graphics.Outline outline) {
                    outline.setOval(0, 0, view.getWidth(), view.getHeight());
                }
            });
            blurFabBatch.setClipToOutline(true);
        }

        BlurView blurFab = view.findViewById(R.id.blur_fab);
        if (blurFab != null) {
            View rootView = view.findViewById(R.id.root_layout_record);
            SharedPreferences tabPrefs = requireContext().getSharedPreferences("app_prefs", Context.MODE_PRIVATE);
            int blurLevel = tabPrefs.getInt("tab_blur_level", 5);
            @SuppressWarnings("deprecation")
            var ignored = blurFab.setupWith((ViewGroup) rootView, new RenderScriptBlur(requireContext()))
                    .setBlurRadius(blurLevel);
            blurFab.setOutlineProvider(new ViewOutlineProvider() {
                @Override
                public void getOutline(View view, android.graphics.Outline outline) {
                    outline.setOval(0, 0, view.getWidth(), view.getHeight());
                }
            });
            blurFab.setClipToOutline(true);
        }

        calendarRecycler = view.findViewById(R.id.calendar_recycler);
        GridLayoutManager layoutManager = new GridLayoutManager(getContext(), 7) {
            @Override
            public boolean canScrollVertically() { return false; }
        };
        calendarRecycler.setLayoutManager(layoutManager);
        calendarRecycler.setOverScrollMode(View.OVER_SCROLL_NEVER);

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

        // 新增：如果当前默认模式是加班，则读取用户上次记忆的是工资(3)还是工时(4)
        if (defaultMode == 3 || defaultMode == 4) {
            defaultMode = prefs.getInt("overtime_display_mode", 3);
        }
        switchFilterMode(defaultMode);

//        calendarRecycler.addOnItemTouchListener(new RecyclerView.SimpleOnItemTouchListener() {
//            @Override
//            public boolean onInterceptTouchEvent(@NonNull RecyclerView rv, @NonNull MotionEvent e) {
//                return gestureDetector.onTouchEvent(e);
//            }
//        });

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
            if (currentDetailDialog != null && currentDetailDialog.isShowing() && selectedDate != null) {
                updateDetailDialogData(selectedDate);
            }
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
    public void onResume() {
        super.onResume();
        View view = getView();
        if (view != null) {
            BlurView blurFab = view.findViewById(R.id.blur_fab);
            if (blurFab != null) {
                SharedPreferences prefs = requireContext().getSharedPreferences("app_prefs", Context.MODE_PRIVATE);
                int blurLevel = prefs.getInt("tab_blur_level", 5);
                blurFab.setBlurRadius(blurLevel);
            }
            BlurView blurFabBatch = view.findViewById(R.id.blur_fab_batch);
            if (blurFabBatch != null) {
                SharedPreferences prefs = requireContext().getSharedPreferences("app_prefs", Context.MODE_PRIVATE);
                int blurLevel = prefs.getInt("tab_blur_level", 5);
                blurFabBatch.setBlurRadius(blurLevel);
            }
        }

        // 【新增】：根据模式动态调整本界面透明度
        SharedPreferences prefs = requireContext().getSharedPreferences("app_prefs", Context.MODE_PRIVATE);
        int themeMode = prefs.getInt("theme_mode", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
        updateFragmentTransparency(themeMode == 3);

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

    // 【新增方法】：动态控制界面透明度，不破坏 XML 默认结构
    // 【修改或替换】现有的 updateFragmentTransparency 方法
    private void updateFragmentTransparency(boolean isCustomBg) {
        View view = getView();
        if (view == null) return;

        View topBar = view.findViewById(R.id.layout_top_bar);
        View weekHeader = view.findViewById(R.id.layout_week_header);

        // 获取需要调整质感的按钮
        com.google.android.material.floatingactionbutton.FloatingActionButton btnQuickRecord = view.findViewById(R.id.btn_quick_record);
        com.google.android.material.floatingactionbutton.FloatingActionButton btnBatchRecord = view.findViewById(R.id.btn_batch_record);

        if (isCustomBg) {
            // 1. 顶部基础框架全透明，让底层的图片完全透出来
            view.setBackgroundColor(android.graphics.Color.TRANSPARENT);
            if (topBar != null) topBar.setBackgroundColor(android.graphics.Color.TRANSPARENT);
            if (weekHeader != null) weekHeader.setBackgroundColor(android.graphics.Color.TRANSPARENT);

            // 2. 快捷记账按钮：85%透明度 (216) + 去除阴影
            if (btnQuickRecord != null) {
                int fabColor = ContextCompat.getColor(requireContext(), R.color.app_yellow);
                int translucentFab = androidx.core.graphics.ColorUtils.setAlphaComponent(fabColor, 230);
                btnQuickRecord.setBackgroundTintList(ColorStateList.valueOf(translucentFab));
                btnQuickRecord.setCompatElevation(0f);
            }
            if (btnBatchRecord != null) {
                int fabColor = ContextCompat.getColor(requireContext(), R.color.app_yellow);
                int translucentFab = androidx.core.graphics.ColorUtils.setAlphaComponent(fabColor, 230);
                btnBatchRecord.setBackgroundTintList(ColorStateList.valueOf(translucentFab));
                btnBatchRecord.setCompatElevation(0f);
            }

        } else {
            // ================= 恢复普通系统/日间/夜间模式 =================
            view.setBackgroundResource(R.color.bar_background);
            if (topBar != null) topBar.setBackgroundResource(R.color.bar_background);
            if (weekHeader != null) weekHeader.setBackgroundResource(R.color.bar_background);

            if (btnQuickRecord != null) {
                int fabColor = ContextCompat.getColor(requireContext(), R.color.app_yellow);
                btnQuickRecord.setBackgroundTintList(ColorStateList.valueOf(fabColor));
                btnQuickRecord.setCompatElevation(0f);
            }
            if (btnBatchRecord != null) {
                int fabColor = ContextCompat.getColor(requireContext(), R.color.app_yellow);
                btnBatchRecord.setBackgroundTintList(ColorStateList.valueOf(fabColor));
                btnBatchRecord.setCompatElevation(0f);
            }
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

        // 更新列表或显示无记录提示
        if (dayList.isEmpty()) {
            dailyTransactionsRecycler.setVisibility(View.GONE);
            tvNoRecords.setVisibility(View.VISIBLE);
            dailyTransactionsAdapter.setTransactions(new ArrayList<>());
        } else {
            dailyTransactionsRecycler.setVisibility(View.VISIBLE);
            tvNoRecords.setVisibility(View.GONE);
            dailyTransactionsAdapter.setTransactions(dayList);
        }
    }

    private void showDateDetailDialog(LocalDate date) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        View dialogView = LayoutInflater.from(getContext()).inflate(R.layout.dialog_transaction_list, null);
        builder.setView(dialogView);
        AlertDialog dialog = builder.create();
        if (dialog.getWindow() != null) dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));

        currentDetailDialog = dialog;

        TextView tvTitle = dialogView.findViewById(R.id.tv_dialog_title);
        if (tvTitle != null) {
            tvTitle.setText(date.format(DateTimeFormatter.ofPattern("yyyy年MM月dd日", Locale.CHINA)));
        }

        currentDetailSummaryTextView = dialogView.findViewById(R.id.tv_dialog_summary);

        RecyclerView rvList = dialogView.findViewById(R.id.rv_detail_list);
        rvList.setLayoutManager(new LinearLayoutManager(getContext()));

        TransactionListAdapter listAdapter = new TransactionListAdapter(transaction -> {
            LocalDate transDate = Instant.ofEpochMilli(transaction.date).atZone(ZoneId.systemDefault()).toLocalDate();
                showAddOrEditDialog(transaction, transDate);
        });

        currentDetailAdapter = listAdapter;
        rvList.setAdapter(listAdapter);

        updateDetailDialogData(date);

        dialogView.findViewById(R.id.btn_add_transaction).setOnClickListener(v -> showAddOrEditDialog(null, date));
        dialogView.findViewById(R.id.btn_add_overtime).setOnClickListener(v -> { dialog.dismiss(); showOvertimeDialog(date); });
        dialogView.findViewById(R.id.btn_close_dialog).setOnClickListener(v -> dialog.dismiss());
        dialog.setOnDismissListener(d -> {
            currentDetailAdapter = null;
            currentDetailDialog = null;
            currentDetailSummaryTextView = null;
        });
        dialog.show();
    }

    

    private void updateDetailDialogData(LocalDate date) {
        if (currentDetailAdapter == null) return;
        List<Transaction> all = viewModel.getRangeTransactions().getValue();
        List<Transaction> dayList = new ArrayList<>();
        if (all != null) {
            long start = date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
            long end = date.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
            dayList = all.stream()
                    .filter(t -> t.date >= start && t.date < end)
                    .collect(Collectors.toList());
        }

        if (currentDetailSummaryTextView != null) {
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
                currentDetailSummaryTextView.setVisibility(View.GONE);
            } else {
                currentDetailSummaryTextView.setVisibility(View.VISIBLE);
                currentDetailSummaryTextView.setText(buildIncomeExpenseSpan(getContext(), dayIncome, dayExpense));
            }
        }
        currentDetailAdapter.setTransactions(dayList);
    }

    private void showOvertimeDialog(LocalDate date) {
        OvertimeDialogHelper.showOvertimeDialog(requireContext(), date, transaction -> {
            viewModel.addTransaction(transaction);
        });
    }

    private void showBatchDialog() {
        if (getContext() == null) return;

        BatchTransactionDialogHelper.showBatchDialog(getContext(), new BatchTransactionDialogHelper.OnBatchSavedListener() {
            @Override
            public void onBatchSaved(List<Transaction> transactions) {
                for (Transaction t : transactions) {
                    viewModel.addTransaction(t);
                }
                viewModel.setDateRange(currentStartMillis, currentEndMillis);
                WidgetUtils.updateAllWidgets(getContext());
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
        int colorBalance = ContextCompat.getColor(context, R.color.app_yellow);

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