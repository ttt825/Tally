package com.example.budgetapp.ui;

import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.AbsoluteSizeSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewStub;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.NumberPicker;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.TextView;
import android.text.InputFilter;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.budgetapp.R;
import com.example.budgetapp.database.Transaction;
import com.example.budgetapp.model.TransactionType;
import com.example.budgetapp.utils.CategoryManager;
import com.example.budgetapp.viewmodel.TransactionViewModel;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.charts.PieChart;
import com.github.mikephil.charting.components.Legend;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.data.PieData;
import com.github.mikephil.charting.data.PieDataSet;
import com.github.mikephil.charting.data.PieEntry;
import com.github.mikephil.charting.formatter.ValueFormatter;
import com.github.mikephil.charting.highlight.Highlight;
import com.github.mikephil.charting.listener.OnChartValueSelectedListener;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;

import java.text.SimpleDateFormat;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.time.temporal.WeekFields;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class StatsFragment extends Fragment {

    // ... (成员变量部分保持不变) ...
    private TransactionViewModel viewModel;
    private LineChart lineChart;
    private PieChart expensePieChart;
    private PieChart incomePieChart;
    private TextView tvIncomeTitle;
    private ViewStub stubIncomeChart;
    private boolean incomeChartInflated = false;
    private LinearLayout layoutTrend;
    private LinearLayout layoutExpense;
    private LinearLayout layoutSummary;
    private View layoutStatsEmpty;
    private RadioGroup rgTimeScope;
    private TextView tvDateRange;
    private TextView tvSummaryTitle;
    private TextView tvSummaryContent;
    private ScrollView scrollView;
    private int currentMode = 2; // 0=Year, 1=Month, 2=Week
    private LocalDate selectedDate = LocalDate.now();
    private List<Transaction> allTransactions = new ArrayList<>();
    private CustomMarkerView markerView;

    // 新增状态变量，用于记录当前打开的弹窗
    private AlertDialog currentCategoryDetailDialog;
    private TransactionListAdapter currentCategoryDetailAdapter;
    private String currentDetailCategory;
    private int currentDetailType;
    private ChipGroup currentDetailChipGroup;
    private View currentDetailHsvSubCategories;
    private List<Transaction> currentDetailBaseList;

    // --- 新增：用于显示分类/二级分类的汇总金额 ---
    private TextView currentDetailSummaryTextView;
    private View dividerIncome;
    private TextView tvIncomeSummaryTitle;
    private TextView tvIncomeSummaryContent;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_stats, container, false);

        // 【新增】在创建视图时立即设置背景色，避免切换时闪烁
        SharedPreferences prefs = requireContext().getSharedPreferences("app_prefs", Context.MODE_PRIVATE);
        int themeMode = prefs.getInt("theme_mode", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
        
        // 需要设置背景的所有View
        View rootLayout = view.findViewById(R.id.root_layout_stats);
        View topTitle = view.findViewById(R.id.tv_top_title);
        
        if (themeMode == 3) {
            // 自定义主题：所有区域都设置透明背景，显示用户设置的背景图片
            if (rootLayout != null) {
                rootLayout.setBackgroundColor(android.graphics.Color.TRANSPARENT);
            }
            if (topTitle != null) {
                topTitle.setBackgroundColor(android.graphics.Color.TRANSPARENT);
            }
        } else {
            // 日间/夜间模式：使用资源文件中定义的背景色（会自动适配主题）
            int bgColor = getResources().getColor(R.color.bar_background, null);
            if (rootLayout != null) {
                rootLayout.setBackgroundColor(bgColor);
            }
            if (topTitle != null) {
                topTitle.setBackgroundColor(bgColor);
            }
        }

        initViews(view);
        setupLineChart();
        setupPieCharts();
        viewModel = new ViewModelProvider(requireActivity()).get(TransactionViewModel.class);
        viewModel.getRangeTransactions().observe(getViewLifecycleOwner(), list -> {
            this.allTransactions = (list != null) ? list : new ArrayList<>();
            refreshData();
            if (currentCategoryDetailDialog != null && currentCategoryDetailDialog.isShowing()) {
                updateCategoryDetailDialogData();
            }
        });
        loadYearData();
        setupListeners(view);
        updateDateRangeDisplay();
        return view;
    }

    private void initViews(View view) {
        layoutTrend = view.findViewById(R.id.layout_trend_section);
        layoutExpense = view.findViewById(R.id.layout_expense_section);
        layoutSummary = view.findViewById(R.id.layout_summary_section);
        layoutStatsEmpty = view.findViewById(R.id.layout_stats_empty);
        lineChart = view.findViewById(R.id.chart_line);
        expensePieChart = view.findViewById(R.id.chart_pie);
        stubIncomeChart = view.findViewById(R.id.stub_income_chart);
        // incomePieChart and tvIncomeTitle are resolved lazily via ensureIncomeChartInflated()
        rgTimeScope = view.findViewById(R.id.rg_time_scope);
        tvDateRange = view.findViewById(R.id.tv_current_date_range);
        tvSummaryTitle = view.findViewById(R.id.tv_summary_title);
        tvSummaryContent = view.findViewById(R.id.tv_summary_content);

        scrollView = view.findViewById(R.id.scroll_view_stats);

        dividerIncome = view.findViewById(R.id.divider_income);
        tvIncomeSummaryTitle = view.findViewById(R.id.tv_income_summary_title);
        tvIncomeSummaryContent = view.findViewById(R.id.tv_income_summary_content);

    }

    /**
     * Lazily inflate the income chart ViewStub.
     * Returns true if the chart is available (either already inflated or just inflated).
     */
    private boolean ensureIncomeChartInflated() {
        if (incomeChartInflated) return incomePieChart != null;
        if (stubIncomeChart == null) return false;
        View inflated = stubIncomeChart.inflate();
        incomeChartInflated = true;
        incomePieChart = inflated.findViewById(R.id.chart_pie_income);
        tvIncomeTitle = inflated.findViewById(R.id.tv_income_title);
        if (incomePieChart != null) {
            initPieChartStyle(incomePieChart, 1);
        }
        return incomePieChart != null;
    }

    // ... (中间的 setupGestures, setupListeners, changeDate, updateDateRangeDisplay 等未修改方法省略，保持原样) ...
    // 为了篇幅，这里假设 setupGestures, setupListeners, changeDate, updateDateRangeDisplay, showCustomDatePicker, updatePreviewText, refreshData, processYearlyData, processMonthlyData, processWeeklyData, aggregateData, updateCharts, updateSinglePieChart, updateSummarySection, generateThemeColors, createLineDataSet, setupLineChart, setupPieCharts, initPieChartStyle 均保持原样。
    // 请确保您的代码中包含这些方法。

    // 【修改】showCategoryDetailDialog 方法
    private void showCategoryDetailDialog(String category, int type) {
        if (allTransactions == null) return;

        // 记录状态以便后续即时刷新
        currentDetailCategory = category;
        currentDetailType = type;

        String dateRangeStr = "";
        ZoneId zone = ZoneId.systemDefault();
        if (currentMode == 0) {
            dateRangeStr = selectedDate.format(DateTimeFormatter.ofPattern("yyyy年"));
        } else if (currentMode == 1) {
            dateRangeStr = selectedDate.format(DateTimeFormatter.ofPattern("yyyy年MM月"));
        } else {
            LocalDate startOfWeek = selectedDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            LocalDate endOfWeek = selectedDate.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY));
            DateTimeFormatter fmt = DateTimeFormatter.ofPattern("M.d");
            dateRangeStr = startOfWeek.format(fmt) + " - " + endOfWeek.format(fmt);
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
        View dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_transaction_list, null);
        builder.setView(dialogView);
        AlertDialog dialog = builder.create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            dialog.getWindow().setWindowAnimations(R.style.Animation_Dialog);
        }

        currentCategoryDetailDialog = dialog;

        // --- 新增：绑定汇总 TextView ---
        currentDetailSummaryTextView = dialogView.findViewById(R.id.tv_dialog_summary);

        TextView tvTitle = dialogView.findViewById(R.id.tv_dialog_title);
        String typeStr = (type == 1) ? "收入" : "消费";
        tvTitle.setText(dateRangeStr + " " + category + " - " + typeStr + "清单");

        RecyclerView rv = dialogView.findViewById(R.id.rv_detail_list);
        rv.setLayoutManager(new LinearLayoutManager(requireContext()));
        FadeInItemAnimator dialogAnimator = new FadeInItemAnimator();
        dialogAnimator.setReduceMotion(AnimUtils.shouldReduceAnimations(requireContext()));
        rv.setItemAnimator(dialogAnimator);

        TransactionListAdapter adapter = new TransactionListAdapter(t -> {
            LocalDate date = Instant.ofEpochMilli(t.date).atZone(ZoneId.systemDefault()).toLocalDate();
            showAddOrEditDialog(t, date);
            // 核心修复点：这里移除 dialog.dismiss()，修改完毕后不再关闭当前弹窗
        });

        currentCategoryDetailAdapter = adapter;
        rv.setAdapter(adapter);

        currentDetailHsvSubCategories = dialogView.findViewById(R.id.hsv_sub_categories);
        currentDetailChipGroup = dialogView.findViewById(R.id.cg_dialog_sub_categories);

        // 初始化加载数据
        updateCategoryDetailDialogData();

        Button btnClose = dialogView.findViewById(R.id.btn_close_dialog);
        if (btnClose != null) btnClose.setOnClickListener(v -> dialog.dismiss());

        dialog.setOnDismissListener(d -> {
            currentCategoryDetailDialog = null;
            currentCategoryDetailAdapter = null;
            currentDetailChipGroup = null;
            currentDetailHsvSubCategories = null;
            currentDetailBaseList = null;

            // --- 新增：清空引用 ---
            currentDetailSummaryTextView = null;
        });

        dialog.show();
    }

    // 新增：提取的实时刷新数据和二级分类胶囊方法
    private void updateCategoryDetailDialogData() {
        if (currentCategoryDetailAdapter == null || allTransactions == null) return;
        long startMillis;
        long endMillis;
        ZoneId zone = ZoneId.systemDefault();

        if (currentMode == 0) {
            LocalDate start = LocalDate.of(selectedDate.getYear(), 1, 1);
            startMillis = start.atStartOfDay(zone).toInstant().toEpochMilli();
            endMillis = start.plusYears(1).atStartOfDay(zone).toInstant().toEpochMilli();
        } else if (currentMode == 1) {
            LocalDate start = LocalDate.of(selectedDate.getYear(), selectedDate.getMonthValue(), 1);
            startMillis = start.atStartOfDay(zone).toInstant().toEpochMilli();
            endMillis = start.plusMonths(1).atStartOfDay(zone).toInstant().toEpochMilli();
        } else {
            LocalDate startOfWeek = selectedDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            LocalDate endOfWeek = selectedDate.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY));
            startMillis = startOfWeek.atStartOfDay(zone).toInstant().toEpochMilli();
            endMillis = endOfWeek.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli();
        }

        Map<String, Double> categorySums = new HashMap<>();
        double totalScopeAmount = 0;
        for (Transaction t : allTransactions) {
            if (t.date >= startMillis && t.date < endMillis && t.type == currentDetailType) {
                categorySums.put(t.category, categorySums.getOrDefault(t.category, 0.0) + t.amount);
                totalScopeAmount += t.amount;
            }
        }
        double threshold = totalScopeAmount * 0.03;

        List<Transaction> baseList = new ArrayList<>();
        for (Transaction t : allTransactions) {
            if (t.date >= startMillis && t.date < endMillis && t.type == currentDetailType) {
                boolean isMatch = false;
                if ("其他".equals(currentDetailCategory)) {
                    Double catSum = categorySums.get(t.category);
                    if (catSum != null && catSum < threshold) {
                        isMatch = true;
                    } else if ("其他".equals(t.category)) {
                        isMatch = true;
                    }
                } else {
                    if (t.category.equals(currentDetailCategory)) {
                        isMatch = true;
                    }
                }

                if (isMatch) {
                    baseList.add(t);
                }
            }
        }

        currentDetailBaseList = baseList;

        // 保存刷新前选中的二级分类，防止刷新时跳回"全部"
        String selectedSubCat = null;
        if (currentDetailChipGroup != null) {
            int checkedId = currentDetailChipGroup.getCheckedChipId();
            if (checkedId != View.NO_ID) {
                Chip checkedChip = currentDetailChipGroup.findViewById(checkedId);
                if (checkedChip != null) {
                    selectedSubCat = checkedChip.getText().toString();
                }
            }
        }

        if (CategoryManager.isSubCategoryEnabled(requireContext()) && currentDetailChipGroup != null) {
            Set<String> subCats = new HashSet<>();
            for (Transaction t : baseList) {
                if (t.subCategory != null && !t.subCategory.isEmpty()) {
                    subCats.add(t.subCategory);
                }
            }

            if (!subCats.isEmpty()) {
                currentDetailHsvSubCategories.setVisibility(View.VISIBLE);
                currentDetailChipGroup.removeAllViews();

                int bgDefault = ContextCompat.getColor(requireContext(), R.color.cat_unselected_bg);
                int bgChecked = ContextCompat.getColor(requireContext(), R.color.app_accent);
                int textDefault = ContextCompat.getColor(requireContext(), R.color.text_primary);
                int textChecked = ContextCompat.getColor(requireContext(), R.color.cat_selected_text);
                int[][] states = new int[][]{new int[]{android.R.attr.state_checked}, new int[]{}};
                ColorStateList bgStateList = new ColorStateList(states, new int[]{bgChecked, bgDefault});
                ColorStateList textStateList = new ColorStateList(states, new int[]{textChecked, textDefault});

                for (String subCat : subCats) {
                    Chip chip = new Chip(requireContext());
                    chip.setText(subCat);
                    chip.setCheckable(true);
                    chip.setClickable(true);
                    chip.setChipBackgroundColor(bgStateList);
                    chip.setTextColor(textStateList);
                    chip.setChipStrokeWidth(0);
                    chip.setCheckedIconVisible(false);

                    if (subCat.equals(selectedSubCat)) {
                        chip.setChecked(true);
                    }

                    chip.setOnClickListener(v -> applySubCategoryFilter());
                    currentDetailChipGroup.addView(chip);
                }
            } else {
                currentDetailHsvSubCategories.setVisibility(View.GONE);
            }
        }

        applySubCategoryFilter();
    }

    private void applySubCategoryFilter() {
        if (currentCategoryDetailAdapter == null || currentDetailBaseList == null) return;
        List<Transaction> filteredList = new ArrayList<>();

        if (currentDetailChipGroup != null && CategoryManager.isSubCategoryEnabled(requireContext())) {
            int checkedId = currentDetailChipGroup.getCheckedChipId();
            if (checkedId == View.NO_ID) {
                filteredList.addAll(currentDetailBaseList);
            } else {
                Chip checkedChip = currentDetailChipGroup.findViewById(checkedId);
                if (checkedChip != null) {
                    String subCat = checkedChip.getText().toString();
                    for (Transaction t : currentDetailBaseList) {
                        if (subCat.equals(t.subCategory)) {
                            filteredList.add(t);
                        }
                    }
                } else {
                    filteredList.addAll(currentDetailBaseList);
                }
            }
        } else {
            filteredList.addAll(currentDetailBaseList);
        }

        currentCategoryDetailAdapter.setTransactions(filteredList);

        // ================= 新增：动态计算并更新汇总金额 =================
        if (currentDetailSummaryTextView != null) {
            double totalAmount = 0;
            for (Transaction t : filteredList) {
                totalAmount += t.amount;
            }

            if (filteredList.isEmpty() && totalAmount == 0) {
                currentDetailSummaryTextView.setVisibility(View.GONE);
            } else {
                currentDetailSummaryTextView.setVisibility(View.VISIBLE);
                SpannableStringBuilder ssb = new SpannableStringBuilder();

                if (currentDetailType == 0) { // 支出
                    int colorExpense = ContextCompat.getColor(requireContext(), R.color.expense_green);
                    String expStr = String.format(Locale.CHINA, "支出: %.2f", totalAmount);
                    int start = ssb.length();
                    ssb.append(expStr);
                    ssb.setSpan(new ForegroundColorSpan(colorExpense), start, ssb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                } else { // 收入
                    int colorIncome = ContextCompat.getColor(requireContext(), R.color.income_red);
                    String incStr = String.format(Locale.CHINA, "收入: %.2f", totalAmount);
                    int start = ssb.length();
                    ssb.append(incStr);
                    ssb.setSpan(new ForegroundColorSpan(colorIncome), start, ssb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                }

                currentDetailSummaryTextView.setText(ssb);
            }
        }
        // ==========================================================

    }
    private void showAddOrEditDialog(Transaction existingTransaction, LocalDate date) {
        if (getContext() == null) return;

        TransactionDialogHelper.showAddOrEditDialog(getContext(), existingTransaction, date, new TransactionDialogHelper.OnTransactionSavedListener() {
            @Override
            public void onTransactionSaved(Transaction transaction, boolean isEdit) {
                if (isEdit) {
                    viewModel.updateTransaction(transaction);
                } else {
                    viewModel.addTransaction(transaction);
                }
            }

            @Override
            public void onTransactionDeleted(Transaction transaction) {
                viewModel.deleteTransaction(transaction);
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
                        viewModel.splitTransaction(originalTransaction, splitTransactions, null);
                    }
                });
            }
        });
    }

    private void loadYearData() {
        int year = selectedDate.getYear();
        ZoneId zone = ZoneId.systemDefault();
        long startMillis = LocalDate.of(year, 1, 1).atStartOfDay(zone).toInstant().toEpochMilli();
        long endMillis = LocalDate.of(year + 1, 1, 1).atStartOfDay(zone).toInstant().toEpochMilli();
        viewModel.setDateRange(startMillis, endMillis);
    }

    private void setupListeners(View view) {
        ImageButton btnPrev = view.findViewById(R.id.btn_prev);
        ImageButton btnNext = view.findViewById(R.id.btn_next);

        rgTimeScope.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == R.id.rb_year) currentMode = 0;
            else if (checkedId == R.id.rb_month) currentMode = 1;
            else if (checkedId == R.id.rb_week) currentMode = 2;

            updateDateRangeDisplay();

            // 切换时间范围时，内容区淡出再淡入
            if (!AnimUtils.shouldReduceAnimations(requireContext())) {
                View[] contentViews = {layoutTrend, layoutExpense, layoutSummary};
                for (View v : contentViews) {
                    if (v != null && v.getVisibility() == View.VISIBLE) {
                        v.animate().alpha(0f).setDuration(120).start();
                    }
                }
                loadYearData();
                for (View v : contentViews) {
                    if (v != null && v.getVisibility() == View.VISIBLE) {
                        v.setAlpha(0f);
                        v.animate().alpha(1f).setDuration(200).start();
                    }
                }
            } else {
                loadYearData();
            }
        });

        btnPrev.setOnClickListener(v -> {
            v.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK); // 增加震动反馈
            changeDate(-1);
        });

        btnNext.setOnClickListener(v -> {
            v.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK); // 增加震动反馈
            changeDate(1);
        });
        tvDateRange.setOnClickListener(v -> {
            // 添加 CLOCK_TICK 清脆振动反馈
            v.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK);
            showCustomDatePicker();
        });
    }

    private void changeDate(int offset) {
        // 1. 定义需要执行动画的三个数据区块
        View[] animateViews = {layoutTrend, layoutExpense, layoutSummary};

        float screenWidth = scrollView.getWidth();
        if (screenWidth == 0) screenWidth = 1080;

        // 计算滑出目标位移：点击“下一周期”则向左滑出 (-screenWidth)，反之向右
        float targetX = (offset > 0) ? -screenWidth : screenWidth;

        // 2. 第一阶段：旧数据滑出并淡出 (时长 150ms)
        int visibleCount = 0;
        for (View v : animateViews) {
            if (v != null && v.getVisibility() == View.VISIBLE) {
                visibleCount++;
                v.animate()
                        .translationX(targetX)
                        .alpha(0f)
                        .setDuration(150)
                        .start();
            }
        }

        // 3. 第二阶段：在数据滑出后的回调中更新内容
        // 使用主布局的延时或其中一个 View 的 endAction 来确保同步
        if (layoutTrend != null) {
            layoutTrend.postDelayed(() -> {
                // --- 核心：在这里更新日期逻辑，顶部的 tvDateRange 会立即变化，但它是静止的 ---
                if (currentMode == 0) selectedDate = selectedDate.plusYears(offset);
                else if (currentMode == 1) selectedDate = selectedDate.plusMonths(offset);
                else selectedDate = selectedDate.plusWeeks(offset);

                updateDateRangeDisplay();
                loadYearData();

                // 4. 第三阶段：将新数据布局瞬移到反方向，然后减速滑入 (时长 300ms)
                for (View v : animateViews) {
                    if (v != null && v.getVisibility() == View.VISIBLE) {
                        v.setTranslationX(-targetX * 0.5f); // 预位移
                        v.animate()
                                .translationX(0f)
                                .alpha(1f)
                                .setDuration(300)
                                .setInterpolator(new android.view.animation.DecelerateInterpolator())
                                .start();
                    }
                }
            }, 150);
        }
    }

    /**
     * 提取的纯数据逻辑更新方法
     */
    private void performDateUpdate(int offset) {
        if (currentMode == 0) selectedDate = selectedDate.plusYears(offset);
        else if (currentMode == 1) selectedDate = selectedDate.plusMonths(offset);
        else selectedDate = selectedDate.plusWeeks(offset);

        updateDateRangeDisplay();
        loadYearData();
    }

    private void updateDateRangeDisplay() {
        if (currentMode == 0) {
            tvDateRange.setText(selectedDate.format(DateTimeFormatter.ofPattern("yyyy年")));
        } else if (currentMode == 1) {
            tvDateRange.setText(selectedDate.format(DateTimeFormatter.ofPattern("yyyy年MM月")));
        } else {
            WeekFields weekFields = WeekFields.of(Locale.CHINA);
            String yearMonthStr = selectedDate.format(DateTimeFormatter.ofPattern("yyyy年M月"));
            int weekOfMonth = selectedDate.get(weekFields.weekOfMonth());

            LocalDate startOfWeek = selectedDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            LocalDate endOfWeek = selectedDate.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY));

            DateTimeFormatter rangeFormatter = DateTimeFormatter.ofPattern("M月d日");
            String startStr = startOfWeek.format(rangeFormatter);
            String endStr = endOfWeek.format(rangeFormatter);

            String title = String.format(Locale.CHINA, "%s 第%d周", yearMonthStr, weekOfMonth);
            String subtitle = String.format(Locale.CHINA, "%s - %s", startStr, endStr);

            SpannableStringBuilder ssb = new SpannableStringBuilder();
            ssb.append(title);
            ssb.append("\n");

            int startSubtitle = ssb.length();
            ssb.append(subtitle);

            if (getContext() != null) {
                int secondaryColor = ContextCompat.getColor(requireContext(), R.color.text_secondary);
                ssb.setSpan(new ForegroundColorSpan(secondaryColor), startSubtitle, ssb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                ssb.setSpan(new AbsoluteSizeSpan(13, true), startSubtitle, ssb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }

            tvDateRange.setText(ssb);
        }
    }

    private void showCustomDatePicker() {
        if (getContext() == null) return;
        DatePickerHelper.showDatePicker(getContext(), selectedDate, (year, month, day) -> {
            selectedDate = LocalDate.of(year, month, day);
            updateDateRangeDisplay();
            loadYearData();
        });
    }

    private void refreshData() {
        if (allTransactions == null) return;
        if (currentMode == 0) processYearlyData();
        else if (currentMode == 1) processMonthlyData();
        else processWeeklyData();
    }

    private void processYearlyData() {
        int year = selectedDate.getYear();
        aggregateData(t -> {
            LocalDate date = Instant.ofEpochMilli(t.date).atZone(ZoneId.systemDefault()).toLocalDate();
            return date.getYear() == year ? date.getMonthValue() : -1;
        }, 12, "月", null);
    }

    private void processMonthlyData() {
        int year = selectedDate.getYear();
        int month = selectedDate.getMonthValue();
        int daysInMonth = selectedDate.lengthOfMonth();
        aggregateData(t -> {
            LocalDate date = Instant.ofEpochMilli(t.date).atZone(ZoneId.systemDefault()).toLocalDate();
            return (date.getYear() == year && date.getMonthValue() == month) ? date.getDayOfMonth() : -1;
        }, daysInMonth, "日", null);
    }

    private void processWeeklyData() {
        LocalDate startOfWeek = selectedDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate endOfWeek = selectedDate.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY));
        aggregateData(t -> {
            LocalDate date = Instant.ofEpochMilli(t.date).atZone(ZoneId.systemDefault()).toLocalDate();
            if (!date.isBefore(startOfWeek) && !date.isAfter(endOfWeek)) {
                return date.getDayOfWeek().getValue();
            }
            return -1;
        }, 7, "", new String[]{"", "周一", "周二", "周三", "周四", "周五", "周六", "周日"});
    }

    interface IndexExtractor { int getIndex(Transaction t); }

    private void aggregateData(IndexExtractor extractor, int maxX, String suffix, String[] customLabels) {
        Map<Integer, Double> incomeMap = new HashMap<>();
        Map<Integer, Double> expenseMap = new HashMap<>();
        Map<String, Double> expensePieCats = new HashMap<>();
        Map<String, Double> incomePieCats = new HashMap<>();

        for (Transaction t : allTransactions) {
            int index = extractor.getIndex(t);
            if (index != -1) {
                if (t.type == TransactionType.INCOME.getValue()) {
                    incomeMap.put(index, incomeMap.getOrDefault(index, 0.0) + t.amount);
                    incomePieCats.put(t.category, incomePieCats.getOrDefault(t.category, 0.0) + t.amount);
                } else if (t.type == TransactionType.EXPENSE.getValue()) {
                    expenseMap.put(index, expenseMap.getOrDefault(index, 0.0) + t.amount);
                    expensePieCats.put(t.category, expensePieCats.getOrDefault(t.category, 0.0) + t.amount);
                }
            }
        }
        updateCharts(incomeMap, expenseMap, expensePieCats, incomePieCats, maxX, suffix, customLabels);
    }

    private void updateCharts(Map<Integer, Double> incomeMap, Map<Integer, Double> expenseMap,
                              Map<String, Double> expensePieMap, Map<String, Double> incomePieMap,
                              int maxX, String suffix, String[] customLabels) {

        boolean hasTrendData = !incomeMap.isEmpty() || !expenseMap.isEmpty();
        boolean hasExpenseData = !expensePieMap.isEmpty();
        boolean hasAnyData = hasTrendData || hasExpenseData;

        // 显示/隐藏空状态引导
        if (layoutStatsEmpty != null) {
            if (hasAnyData) {
                layoutStatsEmpty.setVisibility(View.GONE);
            } else {
                layoutStatsEmpty.setVisibility(View.VISIBLE);
                AnimUtils.fadeIn(layoutStatsEmpty, 250);
            }
        }

        if (layoutTrend != null) {
            layoutTrend.setVisibility(hasTrendData ? View.VISIBLE : View.GONE);
        }

        if (hasTrendData) {
            List<Entry> inEntries = new ArrayList<>();
            List<Entry> outEntries = new ArrayList<>();
            List<Entry> netEntries = new ArrayList<>();

            for (int i = 1; i <= maxX; i++) {
                double in = incomeMap.getOrDefault(i, 0.0);
                double out = expenseMap.getOrDefault(i, 0.0);

                if (incomeMap.containsKey(i)) inEntries.add(new Entry(i, (float) in));
                if (expenseMap.containsKey(i)) outEntries.add(new Entry(i, (float) out));
                if (incomeMap.containsKey(i) || expenseMap.containsKey(i)) {
                    netEntries.add(new Entry(i, (float) (in - out)));
                }
            }

            LineDataSet setIn = createLineDataSet(inEntries, "收入", R.color.income_red);
            LineDataSet setOut = createLineDataSet(outEntries, "支出", R.color.expense_green);
            LineDataSet setNet = createLineDataSet(netEntries, "净收支", R.color.app_accent);
            setNet.enableDashedLine(10f, 5f, 0f);

            LineData lineData = new LineData(setIn, setOut, setNet);
            lineChart.setData(lineData);

            XAxis xAxis = lineChart.getXAxis();
            xAxis.setAxisMinimum(1f);
            xAxis.setAxisMaximum((float) maxX);

            if (maxX <= 12) xAxis.setLabelCount(maxX);
            else xAxis.setLabelCount(6);

            xAxis.setValueFormatter(new ValueFormatter() {
                @Override
                public String getFormattedValue(float value) {
                    int index = (int) value;
                    if (index < 1 || index > maxX) return "";
                    if (customLabels != null) {
                        if (index >= 1 && index < customLabels.length) return customLabels[index];
                        return "";
                    }
                    return index + suffix;
                }
            });

            if (markerView != null) {
                markerView.setSourceData(incomeMap, expenseMap, customLabels != null ? "" : suffix, customLabels);
            }

            lineChart.animateX(600);
            lineChart.invalidate();
        }

        if (layoutExpense != null) {
            layoutExpense.setVisibility(hasExpenseData ? View.VISIBLE : View.GONE);
        }
        if (hasExpenseData) {
            updateSinglePieChart(expensePieChart, expensePieMap);
        }

        double totalIncome = 0;
        for (Double val : incomePieMap.values()) totalIncome += val;

        if (totalIncome > 0) {
            ensureIncomeChartInflated();
            if (tvIncomeTitle != null) tvIncomeTitle.setVisibility(View.VISIBLE);
            if (incomePieChart != null) {
                incomePieChart.setVisibility(View.VISIBLE);
                updateSinglePieChart(incomePieChart, incomePieMap);
            }
        } else {
            if (tvIncomeTitle != null) tvIncomeTitle.setVisibility(View.GONE);
            if (incomePieChart != null) incomePieChart.setVisibility(View.GONE);
        }

        double totalExpense = 0;
        for (Double val : expensePieMap.values()) totalExpense += val;
        updateSummarySection(expensePieMap, totalExpense, incomePieMap, totalIncome); // 新调用，把收入数据也传进去
    }

    private void updateSinglePieChart(PieChart chart, Map<String, Double> pieMap) {
        List<PieEntry> finalEntries = new ArrayList<>();
        List<Integer> finalColors = new ArrayList<>();

        double totalAmount = 0;
        for (Double val : pieMap.values()) totalAmount += val;

        double threshold = totalAmount * 0.03;
        double otherAmount = 0;

        List<Map.Entry<String, Double>> largeEntries = new ArrayList<>();
        for (Map.Entry<String, Double> entry : pieMap.entrySet()) {
            if (entry.getValue() >= threshold) {
                largeEntries.add(entry);
            } else {
                otherAmount += entry.getValue();
            }
        }

        Collections.sort(largeEntries, (a, b) -> Double.compare(b.getValue(), a.getValue()));

        List<Integer> generatedColors = generateThemeColors(largeEntries.size());

        for (int i = 0; i < largeEntries.size(); i++) {
            Map.Entry<String, Double> entry = largeEntries.get(i);
            finalEntries.add(new PieEntry(entry.getValue().floatValue(), entry.getKey()));
            finalColors.add(generatedColors.get(i));
        }

        if (otherAmount > 0) {
            finalEntries.add(new PieEntry((float) otherAmount, "其他"));
            finalColors.add(Color.LTGRAY);
        }

        PieDataSet pieSet = new PieDataSet(finalEntries, "");
        pieSet.setColors(finalColors);

        int primaryTextColor = ContextCompat.getColor(requireContext(), R.color.text_primary);
        int secondaryLineColor = ContextCompat.getColor(requireContext(), R.color.text_secondary);

        pieSet.setYValuePosition(PieDataSet.ValuePosition.OUTSIDE_SLICE);
        pieSet.setXValuePosition(PieDataSet.ValuePosition.OUTSIDE_SLICE);
        pieSet.setValueLineColor(secondaryLineColor);
        pieSet.setValueLineWidth(1.0f);
        pieSet.setSliceSpace(2f);
        pieSet.setValueLinePart1OffsetPercentage(80.f);
        pieSet.setValueLinePart1Length(0.3f);
        pieSet.setValueLinePart2Length(0.8f);

        pieSet.setValueTextSize(11f);
        pieSet.setValueTextColor(primaryTextColor);

        pieSet.setValueFormatter(new ValueFormatter() {
            @Override
            public String getFormattedValue(float value) {
                return String.format("%.1f%%", value);
            }
        });

        PieData pieData = new PieData(pieSet);
        pieData.setValueTextSize(11f);
        pieData.setValueTextColor(primaryTextColor);

        chart.setData(pieData);
        chart.setUsePercentValues(true);
        chart.setExtraOffsets(25f, 10f, 25f, 10f);
        chart.setCenterTextColor(primaryTextColor);
        chart.getLegend().setTextColor(primaryTextColor);
        chart.setRotationAngle(0f);

        chart.animateY(800);
        chart.invalidate();
    }

    private void updateSummarySection(Map<String, Double> pieMap, double totalAmount, Map<String, Double> incomePieMap, double totalIncomeAmount) {
        StatsSummaryHelper.updateSummarySection(
                requireContext(),
                pieMap, totalAmount,
                incomePieMap, totalIncomeAmount,
                currentMode, selectedDate,
                allTransactions,
                tvSummaryTitle, tvSummaryContent,
                layoutSummary,
                dividerIncome, tvIncomeSummaryTitle, tvIncomeSummaryContent);
    }

    private List<Integer> generateThemeColors(int count) {
        List<Integer> colors = new ArrayList<>();
        int uiMode = getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK;
        boolean isNightMode = uiMode == android.content.res.Configuration.UI_MODE_NIGHT_YES;
        float goldenRatio = 0.618033988749895f;
        float currentHue = (float) (Math.random() * 360f);
        int generated = 0;
        int iter = 0;
        while (generated < count && iter < 1000) {
            float h = (currentHue + (iter * goldenRatio * 360f)) % 360f;
            iter++;
            boolean isGreen = (h >= 80f && h < 160f);
            boolean isCyan = (h >= 160f && h < 200f);
            boolean isPurple = (h >= 250f && h < 320f);
            if (isGreen || isCyan || isPurple) { continue; }
            float s, v;
            if (isNightMode) { s = 0.40f + (generated % 3) * 0.1f; v = 0.70f + (generated % 2) * 0.15f; } else { s = 0.35f + (generated % 3) * 0.1f; v = 0.80f + (generated % 2) * 0.15f; }
            colors.add(Color.HSVToColor(new float[]{h, s, v}));
            generated++;
        }
        while (colors.size() < count) { colors.add(Color.LTGRAY); }
        return colors;
    }

    private LineDataSet createLineDataSet(List<Entry> entries, String label, int colorResId) {
        LineDataSet set = new LineDataSet(entries, label);
        int color = ContextCompat.getColor(requireContext(), colorResId);
        set.setColor(color);
        set.setCircleColor(color);
        set.setLineWidth(2f);
        set.setDrawValues(false);
        set.setMode(LineDataSet.Mode.LINEAR);
        return set;
    }

    private void setupLineChart() {
        int textColor = ContextCompat.getColor(requireContext(), R.color.text_primary);
        lineChart.getDescription().setEnabled(false);
        lineChart.getAxisRight().setEnabled(false);
        lineChart.getAxisLeft().setTextColor(textColor);
        XAxis xAxis = lineChart.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setGranularity(1f);
        xAxis.setTextColor(textColor);
        lineChart.getLegend().setTextColor(textColor);
        lineChart.setExtraBottomOffset(10f);
        lineChart.setDragEnabled(true);
        lineChart.setScaleEnabled(false);
        lineChart.setHighlightPerDragEnabled(false);
        if (getContext() != null) {
            markerView = new CustomMarkerView(getContext(), R.layout.view_chart_marker);
            markerView.setChartView(lineChart);
            lineChart.setMarker(markerView);
        }
    }

    private void setupPieCharts() {
        initPieChartStyle(expensePieChart, 0);
        // incomePieChart is initialized lazily in ensureIncomeChartInflated()
    }

    private void initPieChartStyle(PieChart chart, int type) {
        if (chart == null) return;
        int textColor = ContextCompat.getColor(requireContext(), R.color.text_primary);
        int holeColor = ContextCompat.getColor(requireContext(), R.color.bar_background);
        chart.getDescription().setEnabled(false);
        chart.setHoleRadius(40f);
        chart.setTransparentCircleRadius(0);
        // 【修改这里】：将中心圆洞完全变透明，让底层你新加的半透明卡片色透出来
        chart.setHoleColor(Color.TRANSPARENT);
        chart.setEntryLabelColor(textColor);
        chart.setEntryLabelTextSize(10f);
        chart.setRotationEnabled(false);
        Legend l = chart.getLegend();
        l.setTextColor(textColor);
        l.setVerticalAlignment(Legend.LegendVerticalAlignment.BOTTOM);
        l.setHorizontalAlignment(Legend.LegendHorizontalAlignment.CENTER);
        l.setOrientation(Legend.LegendOrientation.HORIZONTAL);
        l.setDrawInside(false);
        chart.setOnChartValueSelectedListener(new OnChartValueSelectedListener() {
            @Override
            public void onValueSelected(Entry e, Highlight h) {
                String category = ((PieEntry) e).getLabel();
                showCategoryDetailDialog(category, type);
            }
            @Override
            public void onNothingSelected() { }
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        SharedPreferences prefs = requireContext().getSharedPreferences("app_prefs", Context.MODE_PRIVATE);
        boolean isCustomBg = prefs.getInt("theme_mode", -1) == 3;
        updateFragmentTransparency(isCustomBg);
    }

    private void updateFragmentTransparency(boolean isCustomBg) {
        View view = getView();
        if (view == null) return;

        TextView tvTopTitle = view.findViewById(R.id.tv_top_title);
        RadioGroup rgTimeMode = view.findViewById(R.id.rg_time_scope); // 注意你xml里叫 rg_time_scope

        // 【修改】获取三个图表和总结卡片
        View chartLine = view.findViewById(R.id.chart_line);
        View chartPie = view.findViewById(R.id.chart_pie);
        // chart_pie_income may be inside a ViewStub; use the field if already inflated
        View chartPieIncome = incomePieChart;
        View cardSummary = view.findViewById(R.id.card_summary);

        if (isCustomBg) {
            // 1. 基础大背景全透明，完全透出底层自定义图片
            view.setBackgroundColor(android.graphics.Color.TRANSPARENT);
            if (tvTopTitle != null) tvTopTitle.setBackgroundColor(android.graphics.Color.TRANSPARENT);

            // 2. 年月日切换栏：外框 90% 透明度，选中块 80% 透明度
            if (rgTimeMode != null) {
                if (rgTimeMode.getBackground() != null) {
                    rgTimeMode.getBackground().mutate().setAlpha(230);
                }
                for (int i = 0; i < rgTimeMode.getChildCount(); i++) {
                    View child = rgTimeMode.getChildAt(i);
                    if (child.getBackground() != null) {
                        child.getBackground().mutate().setAlpha(242);
                    }
                }
            }
        } else {
            // ================= 恢复系统默认模式 =================
            view.setBackgroundResource(R.color.bar_background);
            if (tvTopTitle != null) tvTopTitle.setBackgroundResource(R.color.bar_background);

            if (rgTimeMode != null) {
                if (rgTimeMode.getBackground() != null) rgTimeMode.getBackground().mutate().setAlpha(255);
                for (int i = 0; i < rgTimeMode.getChildCount(); i++) {
                    View child = rgTimeMode.getChildAt(i);
                    if (child.getBackground() != null) child.getBackground().mutate().setAlpha(255);
                }
            }
        }

        // 3. 将这 4 个区块转化为 90% 透明度的毛玻璃卡片（或恢复默认）
        // 传入 true，表示在普通模式下不需要背景
        applyGlassOrSolidBackground(chartLine, isCustomBg, true);
        applyGlassOrSolidBackground(chartPie, isCustomBg, true);
        applyGlassOrSolidBackground(chartPieIncome, isCustomBg, true);

        // 传入 false，表示这是"总结"，在普通模式下需要恢复原有的 assets_field 背景
        applyGlassOrSolidBackground(cardSummary, isCustomBg, false);
    }

    // 【修改】动态控制卡片背景，并增加内边距 (Padding) 的处理
    private void applyGlassOrSolidBackground(View targetView, boolean isCustomBg, boolean isChart) {
        if (targetView == null) return;

        // 将 16dp 转换为像素 (px)
        int padding16 = (int) android.util.TypedValue.applyDimension(
                android.util.TypedValue.COMPLEX_UNIT_DIP, 16, getResources().getDisplayMetrics());

        if (isCustomBg) {
            // 生成 16dp 圆角、90%透明度的浅灰背景
            android.graphics.drawable.GradientDrawable shape = new android.graphics.drawable.GradientDrawable();
            shape.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
            float radius = android.util.TypedValue.applyDimension(
                    android.util.TypedValue.COMPLEX_UNIT_DIP, 16, getResources().getDisplayMetrics());
            shape.setCornerRadius(radius);

            int lightGray = androidx.core.content.ContextCompat.getColor(requireContext(), R.color.white);
            shape.setColor(androidx.core.graphics.ColorUtils.setAlphaComponent(lightGray, 230));

            targetView.setBackground(shape);

            // 🌟 【新增】：开启背景时，给图表和总结卡片都加上 16dp 的内边距
            targetView.setPadding(padding16, padding16, padding16, padding16);

        } else {
            // ================= 恢复普通模式 =================
            if (isChart) {
                // 如果是图表，普通模式下清除背景（保持透明）
                targetView.setBackground(null);

                // 🌟 【新增】：清除背景的同时移除内边距，让图表恢复原本撑满的尺寸
                targetView.setPadding(0, 0, 0, 0);
            } else {
                // 如果是总结卡片，普通模式下恢复默认的背景
                targetView.setBackgroundResource(R.drawable.bg_input_field);

                // 🌟 【新增】：总结卡片在普通模式下原本就需要 16dp 的内边距
                targetView.setPadding(padding16, padding16, padding16, padding16);
            }
        }
    }
}