package com.example.budgetapp.ui;

import android.util.Log;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputFilter;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.AbsoluteSizeSpan;
import android.text.style.ForegroundColorSpan;
import android.view.GestureDetector;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.content.ContextCompat;
import androidx.documentfile.provider.DocumentFile;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.budgetapp.R;
import com.example.budgetapp.BackupManager;
import com.example.budgetapp.database.Transaction;
import com.example.budgetapp.model.TransactionType;
import com.example.budgetapp.util.CategoryManager;
import com.example.budgetapp.viewmodel.TransactionViewModel;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;

import java.text.SimpleDateFormat;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.time.temporal.WeekFields;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import android.widget.NumberPicker;
import com.google.android.material.bottomsheet.BottomSheetDialog;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;

public class DetailsFragment extends Fragment {

    private TransactionViewModel viewModel;
    private RecyclerView recyclerView;
    private DetailsAdapter adapter;
    private List<Transaction> allTransactions = new ArrayList<>();
    private TextView tvDateRange;

    // 🌟 修改点：使用常规的 List 替代 PagingData
    private androidx.lifecycle.LiveData<List<Transaction>> currentFilteredDataLive;

    private GestureDetector gestureDetector;
    private LocalDate selectedDate = LocalDate.now();
    private int currentMode = 1; // 0:年, 1:月, 2:周

    private static final String PREFS_NAME = "details_prefs";
    private static final String KEY_TIME_MODE = "time_mode";

    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
    private final SimpleDateFormat displayDateFormat = new SimpleDateFormat("MM月dd日 EEEE", Locale.getDefault());

    private int touchSlop;

    // CSV导出相关
    private final ActivityResultLauncher<String> exportCsvLauncher = registerForActivityResult(
            new ActivityResultContracts.CreateDocument("text/csv"),
            uri -> {
                if (uri != null) {
                    try {
                        // 获取当前筛选后的数据
                        List<Transaction> currentTransactions = adapter.getCurrentTransactions();
                        if (currentTransactions == null || currentTransactions.isEmpty()) {
                            Toast.makeText(getContext(), "当前没有数据可导出", Toast.LENGTH_SHORT).show();
                            return;
                        }
                        // 只导出交易记录，不包含配置信息
                        BackupManager.exportTransactionsOnly(requireContext(), uri, currentTransactions);
                        Toast.makeText(getContext(), "CSV导出成功", Toast.LENGTH_SHORT).show();
                    } catch (Exception e) {
                        Log.e("Tally", "Error", e);
                        Toast.makeText(getContext(), "导出失败: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    }
                }
            }
    );

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
                        initialX = e.getRawX();
                        initialY = e.getRawY();
                        isHorizontalSwipe = false;
                        break;
                    case MotionEvent.ACTION_MOVE:
                        float dx = e.getRawX() - initialX;
                        float dy = e.getRawY() - initialY;
                        if (!isHorizontalSwipe && Math.abs(dx) > touchSlop && Math.abs(dx) > Math.abs(dy)) {
                            isHorizontalSwipe = true;
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
                        rv.setTranslationX(dx * 0.6f);
                        rv.setAlpha(1f - (Math.abs(dx) / screenWidth) * 0.5f);
                        break;

                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        if (dx > screenWidth * 0.2f) {
                            finishSwipeAnimation(rv, screenWidth, -1);
                        } else if (dx < -screenWidth * 0.2f) {
                            finishSwipeAnimation(rv, -screenWidth, 1);
                        } else {
                            rv.animate().translationX(0f).alpha(1f).setDuration(250)
                                    .setInterpolator(new android.view.animation.OvershootInterpolator()).start();
                        }
                        isHorizontalSwipe = false;
                        break;
                }
            }

            @Override
            public void onRequestDisallowInterceptTouchEvent(boolean disallowIntercept) {}
        });
    }

    private void finishSwipeAnimation(RecyclerView rv, float targetTranslationX, int offset) {
        rv.animate()
                .translationX(targetTranslationX)
                .alpha(0f)
                .setDuration(150)
                .withEndAction(() -> {
                    if (currentMode == 0) selectedDate = selectedDate.plusYears(offset);
                    else if (currentMode == 1) selectedDate = selectedDate.plusMonths(offset);
                    else selectedDate = selectedDate.plusWeeks(offset);
                    updateDateRangeDisplay();

                    rv.setTranslationX(-targetTranslationX * 0.5f);

                    processAndDisplayData(0);

                    rv.animate()
                            .translationX(0f)
                            .alpha(1f)
                            .setDuration(300)
                            .setInterpolator(new android.view.animation.DecelerateInterpolator())
                            .start();
                })
                .start();
    }

    private static class FilterCriteria {
        Float minAmount, maxAmount;
        String category, assetName;
        Integer type;
        void clear() {
            minAmount = null;
            maxAmount = null;
            category = null;
            assetName = null;
            type = null;
        }
    }
    private final FilterCriteria currentFilter = new FilterCriteria();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_details, container, false);

        // 【新增】在创建视图时立即设置背景色，避免切换时闪烁
        SharedPreferences prefs = requireContext().getSharedPreferences("app_prefs", Context.MODE_PRIVATE);
        int themeMode = prefs.getInt("theme_mode", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
        
        // 需要设置背景的所有View
        View rootLayout = view.findViewById(R.id.root_layout_details);
        View topTitle = view.findViewById(R.id.tv_top_title);
        View recyclerDetails = view.findViewById(R.id.recycler_details);
        
        if (themeMode == 3) {
            // 自定义主题：所有区域都设置透明背景，显示用户设置的背景图片
            if (rootLayout != null) {
                rootLayout.setBackgroundColor(android.graphics.Color.TRANSPARENT);
            }
            if (topTitle != null) {
                topTitle.setBackgroundColor(android.graphics.Color.TRANSPARENT);
            }
            if (recyclerDetails != null) {
                recyclerDetails.setBackgroundColor(android.graphics.Color.TRANSPARENT);
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
            if (recyclerDetails != null) {
                recyclerDetails.setBackgroundColor(bgColor);
            }
        }

        SharedPreferences detailsPrefs = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);

        tvDateRange = view.findViewById(R.id.tv_current_date_range);

        tvDateRange.setOnClickListener(v -> {
            v.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK);
            showCustomDatePicker();
        });

        RadioGroup rgTimeMode = view.findViewById(R.id.rg_time_mode);
        ImageButton btnPrev = view.findViewById(R.id.btn_prev);
        ImageButton btnNext = view.findViewById(R.id.btn_next);
        ImageButton btnExport = view.findViewById(R.id.btn_export);
        ImageButton btnFilter = view.findViewById(R.id.btn_filter);

        currentMode = detailsPrefs.getInt(KEY_TIME_MODE, 1);
        if (currentMode == 0) rgTimeMode.check(R.id.rb_year);
        else if (currentMode == 2) rgTimeMode.check(R.id.rb_week);
        else rgTimeMode.check(R.id.rb_month);

        rgTimeMode.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == R.id.rb_year) currentMode = 0;
            else if (checkedId == R.id.rb_month) currentMode = 1;
            else if (checkedId == R.id.rb_week) currentMode = 2;
            detailsPrefs.edit().putInt(KEY_TIME_MODE, currentMode).apply();
            updateDateRangeDisplay();
            processAndDisplayData(0);
        });

        btnPrev.setOnClickListener(v -> changeDate(-1, -1));
        btnNext.setOnClickListener(v -> changeDate(1, 1));
        btnExport.setOnClickListener(v -> exportCurrentData());
        btnFilter.setOnClickListener(v -> showFilterDialog());

        recyclerView = view.findViewById(R.id.recycler_details);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        recyclerView.setItemAnimator(null);

        adapter = new DetailsAdapter();
        adapter.setOnTransactionClickListener(t -> {
            LocalDate date = java.time.Instant.ofEpochMilli(t.date).atZone(ZoneId.systemDefault()).toLocalDate();
            showAddOrEditDialog(t, date);
        });

        recyclerView.setAdapter(adapter);
        setupFollowHandSwipe(recyclerView);

        viewModel = new ViewModelProvider(requireActivity()).get(TransactionViewModel.class);
        processAndDisplayData(0);

        updateDateRangeDisplay();
        return view;
    }

    private void changeDate(int offset, int direction) {
        if (recyclerView != null) {
            float screenWidth = recyclerView.getWidth();
            if (screenWidth == 0) screenWidth = 1080;
            finishSwipeAnimation(recyclerView, direction == 1 ? -screenWidth : screenWidth, offset);
        }
    }

    private void updateDateRangeDisplay() {
        if (currentMode == 0) {
            tvDateRange.setText(selectedDate.format(DateTimeFormatter.ofPattern("yyyy年")));
        } else if (currentMode == 1) {
            tvDateRange.setText(selectedDate.format(DateTimeFormatter.ofPattern("yyyy年MM月")));
        } else {
            WeekFields weekFields = WeekFields.of(Locale.CHINA);
            String title = String.format(Locale.CHINA, "%s 第%d周", selectedDate.format(DateTimeFormatter.ofPattern("yyyy年M月")), selectedDate.get(weekFields.weekOfMonth()));
            LocalDate start = selectedDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            LocalDate end = selectedDate.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY));
            String subtitle = String.format(Locale.CHINA, "%s - %s", start.format(DateTimeFormatter.ofPattern("M月d日")), end.format(DateTimeFormatter.ofPattern("M月d日")));

            SpannableStringBuilder ssb = new SpannableStringBuilder(title + "\n" + subtitle);
            int startSub = title.length() + 1;
            if (getContext() != null) {
                ssb.setSpan(new ForegroundColorSpan(ContextCompat.getColor(requireContext(), R.color.text_secondary)), startSub, ssb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                ssb.setSpan(new AbsoluteSizeSpan(12, true), startSub, ssb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            tvDateRange.setText(ssb);
        }
    }

    private void showCustomDatePicker() {
        if (getContext() == null) return;

        final BottomSheetDialog dialog = new BottomSheetDialog(getContext());
        dialog.setContentView(R.layout.dialog_bottom_date_picker);

        dialog.setOnShowListener(dialogInterface -> {
            BottomSheetDialog bottomSheetDialog = (BottomSheetDialog) dialogInterface;
            View bottomSheet = bottomSheetDialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
            if (bottomSheet != null) {
                bottomSheet.setBackgroundResource(android.R.color.transparent);
            }
        });

        NumberPicker npYear = dialog.findViewById(R.id.np_year);
        NumberPicker npMonth = dialog.findViewById(R.id.np_month);
        NumberPicker npDay = dialog.findViewById(R.id.np_day);
        TextView tvPreview = dialog.findViewById(R.id.tv_date_preview);
        Button btnCancel = dialog.findViewById(R.id.btn_cancel);
        Button btnConfirm = dialog.findViewById(R.id.btn_confirm);

        if (npYear == null || npMonth == null || npDay == null || btnConfirm == null || btnCancel == null) return;

        int curYear = selectedDate.getYear();
        int curMonth = selectedDate.getMonthValue();
        int curDay = selectedDate.getDayOfMonth();

        npYear.setMinValue(2000);
        npYear.setMaxValue(2050);
        npYear.setValue(curYear);

        npMonth.setMinValue(1);
        npMonth.setMaxValue(12);
        npMonth.setValue(curMonth);

        npDay.setMinValue(1);
        int maxDays = java.time.YearMonth.of(curYear, curMonth).lengthOfMonth();
        npDay.setMaxValue(maxDays);
        npDay.setValue(curDay);

        NumberPicker.OnValueChangeListener dateChangeListener = (picker, oldVal, newVal) -> {
            picker.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK);
            int y = npYear.getValue();
            int m = npMonth.getValue();
            int newMaxDays = java.time.YearMonth.of(y, m).lengthOfMonth();
            if (npDay.getMaxValue() != newMaxDays) {
                npDay.setMaxValue(newMaxDays);
                if (npDay.getValue() > newMaxDays) npDay.setValue(newMaxDays);
            }
            updatePreviewText(tvPreview, y, m, npDay.getValue());
        };

        npYear.setOnValueChangedListener(dateChangeListener);
        npMonth.setOnValueChangedListener(dateChangeListener);
        npDay.setOnValueChangedListener(dateChangeListener);

        updatePreviewText(tvPreview, curYear, curMonth, curDay);

        btnCancel.setOnClickListener(v -> dialog.dismiss());

        btnConfirm.setOnClickListener(v -> {
            v.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK);
            int year = npYear.getValue();
            int month = npMonth.getValue();
            int day = npDay.getValue();

            selectedDate = LocalDate.of(year, month, day);
            updateDateRangeDisplay();
            processAndDisplayData(0);

            dialog.dismiss();
        });

        dialog.show();
    }

    private void exportCurrentData() {
        if (adapter == null) return;

        List<Transaction> currentTransactions = adapter.getCurrentTransactions();
        if (currentTransactions == null || currentTransactions.isEmpty()) {
            Toast.makeText(getContext(), "当前没有数据可导出", Toast.LENGTH_SHORT).show();
            return;
        }

        // 生成文件名：账单导出_年月.csv
        String fileName = "账单导出_";
        if (currentMode == 0) {
            fileName += selectedDate.format(DateTimeFormatter.ofPattern("yyyy年"));
        } else if (currentMode == 1) {
            fileName += selectedDate.format(DateTimeFormatter.ofPattern("yyyy年MM月"));
        } else {
            fileName += selectedDate.format(DateTimeFormatter.ofPattern("yyyy年MM月")) + "_第" +
                    selectedDate.get(WeekFields.of(Locale.CHINA).weekOfMonth()) + "周";
        }
        fileName += ".csv";

        exportCsvLauncher.launch(fileName);
    }

    private void updatePreviewText(TextView tv, int year, int month, int day) {
        if (tv == null) return;
        try {
            LocalDate date = LocalDate.of(year, month, day);
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy年M月d日 EEEE", Locale.CHINA);
            tv.setText(date.format(formatter));
        } catch (Exception e) {
            tv.setText(year + "年" + month + "月" + day + "日");
        }
    }

    private void processAndDisplayData(int direction) {
        long[] range = getTimeRange();

        if (currentFilteredDataLive != null) {
            currentFilteredDataLive.removeObservers(getViewLifecycleOwner());
        }

        String keyword = "";
        if (!TextUtils.isEmpty(currentFilter.category)) keyword += currentFilter.category + " ";
        if (!TextUtils.isEmpty(currentFilter.assetName)) keyword += currentFilter.assetName + " ";
        keyword = keyword.trim();
        if (keyword.isEmpty()) keyword = null;

        // 🌟 修改点：调用 ViewModel 获取常规的 List 流
        currentFilteredDataLive = viewModel.getFilteredTransactions(
                range[0], range[1],
                currentFilter.type,
                currentFilter.minAmount,
                currentFilter.maxAmount,
                keyword
        );

        currentFilteredDataLive.observe(getViewLifecycleOwner(), list -> {
            if (list != null) {
                // 🌟 修改点：将分页流替换为常规的 List 更新
                adapter.setTransactions(list);
            }

            if (getContext() != null && recyclerView != null && direction != 0) {
                Animation anim = AnimationUtils.loadAnimation(getContext(), direction == 1 ? R.anim.slide_in_right : R.anim.slide_in_left);
                recyclerView.startAnimation(anim);
            }
        });
    }

    private long[] getTimeRange() {
        ZoneId zone = ZoneId.systemDefault();
        if (currentMode == 0) return new long[]{selectedDate.with(TemporalAdjusters.firstDayOfYear()).atStartOfDay(zone).toInstant().toEpochMilli(), selectedDate.with(TemporalAdjusters.lastDayOfYear()).atTime(23,59,59).atZone(zone).toInstant().toEpochMilli()};
        if (currentMode == 2) return new long[]{selectedDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).atStartOfDay(zone).toInstant().toEpochMilli(), selectedDate.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY)).atTime(23,59,59).atZone(zone).toInstant().toEpochMilli()};
        return new long[]{selectedDate.with(TemporalAdjusters.firstDayOfMonth()).atStartOfDay(zone).toInstant().toEpochMilli(), selectedDate.with(TemporalAdjusters.lastDayOfMonth()).atTime(23,59,59).atZone(zone).toInstant().toEpochMilli()};
    }

    private void showAddOrEditDialog(Transaction existingTransaction, LocalDate date) {
        if (getContext() == null) return;

        TransactionDialogHelper.showAddOrEditDialog(getContext(), existingTransaction, date, new TransactionDialogHelper.OnTransactionSavedListener() {
            @Override
            public void onTransactionSaved(Transaction transaction, boolean isEdit) {
                List<Transaction> list = adapter.getCurrentTransactions();
                if (isEdit) {
                    viewModel.updateTransaction(transaction);
                    for (int i = 0; i < list.size(); i++) {
                        if (list.get(i).id == transaction.id) {
                            list.set(i, transaction);
                            break;
                        }
                    }
                    Toast.makeText(getContext(), "已修改记录", Toast.LENGTH_SHORT).show();
                } else {
                    viewModel.addTransaction(transaction);
                    list.add(0, transaction);
                    Toast.makeText(getContext(), "已添加记录", Toast.LENGTH_SHORT).show();
                }
                adapter.notifyDataSetChanged();
            }

            @Override
            public void onTransactionDeleted(Transaction transaction) {
                viewModel.deleteTransaction(transaction);
                List<Transaction> list = adapter.getCurrentTransactions();
                for (int i = 0; i < list.size(); i++) {
                    if (list.get(i).id == transaction.id) {
                        list.remove(i);
                        break;
                    }
                }
                adapter.notifyDataSetChanged();
                Toast.makeText(getContext(), "已删除记录", Toast.LENGTH_SHORT).show();
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
                            if (getActivity() != null) {
                                getActivity().runOnUiThread(() -> {
                                    adapter.notifyDataSetChanged();
                                });
                            }
                        });
                    }
                });
            }
        });
    }

        private void showFilterDialog() {
        View v = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_details_filter, null);
        AlertDialog dialog = new AlertDialog.Builder(getContext()).setView(v).create();
        if (dialog.getWindow() != null) dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);

        EditText etMin = v.findViewById(R.id.et_min_amount);
        EditText etMax = v.findViewById(R.id.et_max_amount);
        EditText etCategory = v.findViewById(R.id.et_category);
        EditText etAsset = v.findViewById(R.id.et_asset);

        Spinner spType = v.findViewById(R.id.sp_filter_type);
        String[] types = {"全部", "支出", "收入", "借入", "借出"};
        ArrayAdapter<String> typeAdapter = new ArrayAdapter<>(requireContext(), R.layout.item_spinner_dropdown, types);
        typeAdapter.setDropDownViewResource(R.layout.item_spinner_dropdown);
        spType.setAdapter(typeAdapter);

        if (currentFilter.minAmount != null) {
            etMin.setText(String.valueOf(currentFilter.minAmount).replaceAll("\\.0$", ""));
        }
        if (currentFilter.maxAmount != null) {
            etMax.setText(String.valueOf(currentFilter.maxAmount).replaceAll("\\.0$", ""));
        }
        if (!TextUtils.isEmpty(currentFilter.category)) {
            etCategory.setText(currentFilter.category);
            etCategory.setSelection(currentFilter.category.length());
        }
        if (!TextUtils.isEmpty(currentFilter.assetName)) {
            etAsset.setText(currentFilter.assetName);
        }

        if (currentFilter.type == null) {
            spType.setSelection(0);
        } else if (currentFilter.type == TransactionType.EXPENSE.getValue()) {
            spType.setSelection(1);
        } else if (currentFilter.type == TransactionType.INCOME.getValue()) {
            spType.setSelection(2);
        } else if (currentFilter.type == TransactionType.LIABILITY.getValue()) {
            spType.setSelection(3);
        } else if (currentFilter.type == TransactionType.LEND.getValue()) {
            spType.setSelection(4);
        }

        v.findViewById(R.id.btn_apply).setOnClickListener(view -> {
            String minStr = etMin.getText().toString();
            String maxStr = etMax.getText().toString();

            try {
                currentFilter.minAmount = minStr.isEmpty() ? null : Float.parseFloat(minStr);
                currentFilter.maxAmount = maxStr.isEmpty() ? null : Float.parseFloat(maxStr);
            } catch (NumberFormatException e) {
                Toast.makeText(getContext(), "请输入有效的金额", Toast.LENGTH_SHORT).show();
                return;
            }
            currentFilter.category = etCategory.getText().toString().trim();
            currentFilter.assetName = etAsset.getText().toString().trim();

            int selectedPos = spType.getSelectedItemPosition();
            if (selectedPos == 1) {
                currentFilter.type = TransactionType.EXPENSE.getValue();
            } else if (selectedPos == 2) {
                currentFilter.type = TransactionType.INCOME.getValue();
            } else if (selectedPos == 3) {
                currentFilter.type = TransactionType.LIABILITY.getValue();
            } else if (selectedPos == 4) {
                currentFilter.type = TransactionType.LEND.getValue();
            } else {
                currentFilter.type = null;
            }

            processAndDisplayData(0);
            dialog.dismiss();
        });

        v.findViewById(R.id.btn_reset).setOnClickListener(view -> {
            currentFilter.clear();
            processAndDisplayData(0);
            dialog.dismiss();
        });

        dialog.show();
    }

    class DecimalDigitsInputFilter implements InputFilter {
        private final Pattern mPattern;
        public DecimalDigitsInputFilter(int digitsAfterZero) {
            mPattern = Pattern.compile("[0-9]*+((\\.[0-9]{0," + (digitsAfterZero - 1) + "})?)||(\\.)?");
        }
        @Override
        public CharSequence filter(CharSequence source, int start, int end, Spanned dest, int dstart, int dend) {
            String replacement = source.subSequence(start, end).toString();
            String newVal = dest.subSequence(0, dstart).toString() + replacement + dest.subSequence(dend, dest.length()).toString();
            Matcher matcher = mPattern.matcher(newVal);
            if (!matcher.matches()) {
                if (newVal.contains(".")) {
                    int index = newVal.indexOf(".");
                    if (newVal.length() - index - 1 > 2) return "";
                }
            }
            return null;
        }
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

        View rootLayout = view.findViewById(R.id.root_layout_details);
        TextView tvTopTitle = view.findViewById(R.id.tv_top_title);
        RadioGroup rgTimeMode = view.findViewById(R.id.rg_time_mode);
        RecyclerView recyclerDetails = view.findViewById(R.id.recycler_details);

        if (isCustomBg) {
            if (rootLayout != null) rootLayout.setBackgroundColor(Color.TRANSPARENT);
            if (tvTopTitle != null) tvTopTitle.setBackgroundColor(Color.TRANSPARENT);
            if (recyclerDetails != null) recyclerDetails.setBackgroundColor(Color.TRANSPARENT);

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
            if (rootLayout != null) rootLayout.setBackgroundResource(R.color.bar_background);
            if (tvTopTitle != null) tvTopTitle.setBackgroundResource(R.color.bar_background);
            if (recyclerDetails != null) recyclerDetails.setBackgroundResource(R.color.bar_background);

            if (rgTimeMode != null) {
                if (rgTimeMode.getBackground() != null) {
                    rgTimeMode.getBackground().mutate().setAlpha(255);
                }
                for (int i = 0; i < rgTimeMode.getChildCount(); i++) {
                    View child = rgTimeMode.getChildAt(i);
                    if (child.getBackground() != null) {
                        child.getBackground().mutate().setAlpha(255);
                    }
                }
            }
        }

        if (adapter != null) adapter.notifyDataSetChanged();
    }
}