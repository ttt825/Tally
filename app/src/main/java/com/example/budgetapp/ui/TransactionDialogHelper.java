package com.example.budgetapp.ui;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.ResultReceiver;
import android.text.InputFilter;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.NumberPicker;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.documentfile.provider.DocumentFile;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.budgetapp.R;
import com.example.budgetapp.database.Transaction;
import com.example.budgetapp.model.TransactionType;
import com.example.budgetapp.util.CategoryManager;
import com.example.budgetapp.util.CurrencyUtils;
import com.example.budgetapp.widget.WidgetUtils;
import com.google.android.flexbox.FlexDirection;
import com.google.android.flexbox.FlexWrap;
import com.google.android.flexbox.FlexboxLayoutManager;
import com.google.android.flexbox.JustifyContent;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;

import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public class TransactionDialogHelper {

    public interface OnTransactionSavedListener {
        void onTransactionSaved(Transaction transaction, boolean isEdit);
        void onTransactionDeleted(Transaction transaction);
        void onPhotoDeleted(int transactionId);
        default void onSplitRequested(Transaction transaction) {}
    }

    public static AlertDialog showAddOrEditDialog(
            Context context,
            @Nullable Transaction existingTransaction,
            LocalDate date,
            OnTransactionSavedListener listener) {

        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        View dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_add_transaction, null);
        builder.setView(dialogView);
        AlertDialog dialog = builder.create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }

        TextView tvDate = dialogView.findViewById(R.id.tv_dialog_date);
        RadioGroup rgType = dialogView.findViewById(R.id.rg_type);
        RecyclerView rvCategory = dialogView.findViewById(R.id.rv_category);
        EditText etAmount = dialogView.findViewById(R.id.et_amount);
        Button btnCurrency = dialogView.findViewById(R.id.btn_currency);
        EditText etCustomCategory = dialogView.findViewById(R.id.et_custom_category);
        EditText etRemark = dialogView.findViewById(R.id.et_remark);
        EditText etNote = dialogView.findViewById(R.id.et_note);
        EditText etTargetObject = dialogView.findViewById(R.id.et_target_object);
        Button btnSave = dialogView.findViewById(R.id.btn_save);
        Button btnDelete = dialogView.findViewById(R.id.btn_delete);
        MaterialButton btnTakePhoto = dialogView.findViewById(R.id.btn_take_photo);
        MaterialButton btnViewPhoto = dialogView.findViewById(R.id.btn_view_photo);

        // 根据是否编辑模式设置标题
        TextView tvDialogTitle = dialogView.findViewById(R.id.tv_dialog_title);
        TextView btnSplit = dialogView.findViewById(R.id.btn_split);
        if (existingTransaction != null) {
            tvDialogTitle.setText("编辑账单");
            btnSplit.setVisibility(View.VISIBLE);
        }

        etAmount.setFilters(new InputFilter[]{new DecimalDigitsInputFilter(2)});

        SharedPreferences prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE);
        boolean isCurrencyEnabled = prefs.getBoolean("enable_currency", false);
        boolean isPhotoBackupEnabled = prefs.getBoolean("enable_photo_backup", false);

        if (isCurrencyEnabled) {
            btnCurrency.setVisibility(View.VISIBLE);
            if (existingTransaction != null && existingTransaction.currencySymbol != null && !existingTransaction.currencySymbol.isEmpty()) {
                btnCurrency.setText(existingTransaction.currencySymbol);
            } else {
                String defaultSymbol = prefs.getString("default_currency_symbol", "¥");
                btnCurrency.setText(defaultSymbol);
            }
            btnCurrency.setOnClickListener(v -> CurrencyUtils.showCurrencyDialog(context, btnCurrency, false));
        } else {
            btnCurrency.setVisibility(View.GONE);
        }

        final String[] currentPhotoPath = {existingTransaction != null ? existingTransaction.photoPath : ""};

        Runnable updatePhotoButtons = () -> {
            if (currentPhotoPath[0] != null && !currentPhotoPath[0].isEmpty()) {
                btnViewPhoto.setVisibility(View.VISIBLE);
            } else {
                btnViewPhoto.setVisibility(View.GONE);
            }
        };

        if (isPhotoBackupEnabled) {
            btnTakePhoto.setVisibility(View.VISIBLE);
            updatePhotoButtons.run();

            btnTakePhoto.setOnClickListener(v -> {
                Intent intent = new Intent(context, PhotoActionActivity.class);
                intent.putExtra(PhotoActionActivity.EXTRA_RECEIVER, new ResultReceiver(new Handler(Looper.getMainLooper())) {
                    @Override
                    protected void onReceiveResult(int resultCode, Bundle resultData) {
                        if (resultCode == 1 && resultData != null) {
                            String uri = resultData.getString(PhotoActionActivity.KEY_RESULT_URI);
                            currentPhotoPath[0] = uri;
                            updatePhotoButtons.run();

                            if (existingTransaction != null) {
                                existingTransaction.photoPath = uri;
                                listener.onTransactionSaved(existingTransaction, true);
                                Toast.makeText(context, "照片已添加并保存", Toast.LENGTH_SHORT).show();
                            }
                        }
                    }
                });
                context.startActivity(intent);
            });

            btnViewPhoto.setOnClickListener(v -> {
                if (currentPhotoPath[0] != null && !currentPhotoPath[0].isEmpty()) {
                    showPhotoDialog(context, currentPhotoPath[0]);
                }
            });

            btnViewPhoto.setOnLongClickListener(v -> {
                AlertDialog.Builder deleteBuilder = new AlertDialog.Builder(context);
                View deleteView = LayoutInflater.from(context).inflate(R.layout.dialog_delete_photo, null);
                deleteBuilder.setView(deleteView);
                AlertDialog deleteDialog = deleteBuilder.create();
                if (deleteDialog.getWindow() != null) {
                    deleteDialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
                }

                deleteView.findViewById(R.id.btn_cancel_delete).setOnClickListener(view -> deleteDialog.dismiss());
                deleteView.findViewById(R.id.btn_confirm_delete).setOnClickListener(view -> {
                    if (currentPhotoPath[0] != null && !currentPhotoPath[0].isEmpty()) {
                        try {
                            Uri uri = Uri.parse(currentPhotoPath[0]);
                            DocumentFile file = DocumentFile.fromSingleUri(context, uri);
                            if (file != null && file.exists()) {
                                file.delete();
                            }
                        } catch (Exception ignored) {}
                    }
                    currentPhotoPath[0] = "";
                    updatePhotoButtons.run();
                    if (existingTransaction != null) {
                        existingTransaction.photoPath = "";
                        listener.onPhotoDeleted(existingTransaction.id);
                    }
                    deleteDialog.dismiss();
                });
                deleteDialog.show();
                return true;
            });
        } else {
            btnTakePhoto.setVisibility(View.GONE);
            btnViewPhoto.setVisibility(View.GONE);
        }

        List<String> expenseCategories = CategoryManager.getExpenseCategories(context);
        List<String> incomeCategories = CategoryManager.getIncomeCategories(context);

        boolean isDetailed = CategoryManager.isDetailedCategoryEnabled(context);
        if (isDetailed) {
            FlexboxLayoutManager flexboxLayoutManager = new FlexboxLayoutManager(context);
            flexboxLayoutManager.setFlexWrap(FlexWrap.WRAP);
            flexboxLayoutManager.setFlexDirection(FlexDirection.ROW);
            flexboxLayoutManager.setJustifyContent(JustifyContent.FLEX_START);
            rvCategory.setLayoutManager(flexboxLayoutManager);
        } else {
            rvCategory.setLayoutManager(new GridLayoutManager(context, 5));
        }

        final boolean[] isExpense = {true};
        final String[] selectedCategory = {expenseCategories.isEmpty() ? "自定义" : expenseCategories.get(0)};
        final String[] selectedSubCategory = {""};

        CategoryAdapter categoryAdapter = new CategoryAdapter(context, expenseCategories, selectedCategory[0], category -> {
            selectedCategory[0] = category;
            selectedSubCategory[0] = "";
            if ("自定义".equals(category)) {
                etCustomCategory.setVisibility(View.VISIBLE);
                etCustomCategory.requestFocus();
            } else {
                etCustomCategory.setVisibility(View.GONE);
            }
        });

        categoryAdapter.setOnCategoryLongClickListener(category -> {
            if (CategoryManager.isSubCategoryEnabled(context) && !"自定义".equals(category)) {
                if (!category.equals(selectedCategory[0])) {
                    categoryAdapter.setSelectedCategory(category);
                    selectedCategory[0] = category;
                    selectedSubCategory[0] = "";
                    etCustomCategory.setVisibility(View.GONE);
                }

                AlertDialog subCatDialog = createSubCategoryDialog(context, category,
                        selectedSubCategory[0], false,
                        subCat -> {
                            if (subCat == null) {
                                selectedSubCategory[0] = null;
                                Toast.makeText(context, "已取消细分", Toast.LENGTH_SHORT).show();
                            } else {
                                selectedSubCategory[0] = subCat;
                                Toast.makeText(context, "已选择: " + subCat, Toast.LENGTH_SHORT).show();
                            }
                            categoryAdapter.setSelectedCategory(category);
                            selectedCategory[0] = category;
                            etCustomCategory.setVisibility(View.GONE);
                        });
                subCatDialog.show();
                return true;
            }
            return false;
        });

        rvCategory.setAdapter(categoryAdapter);

        if (existingTransaction != null && existingTransaction.subCategory != null) {
            selectedSubCategory[0] = existingTransaction.subCategory;
        }

        final Calendar calendar = Calendar.getInstance();
        if (existingTransaction != null) {
            calendar.setTimeInMillis(existingTransaction.date);
        } else if (date != null) {
            calendar.set(Calendar.YEAR, date.getYear());
            calendar.set(Calendar.MONTH, date.getMonthValue() - 1);
            calendar.set(Calendar.DAY_OF_MONTH, date.getDayOfMonth());
        }

        Runnable updateDateDisplay = () -> {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy年MM月dd日 HH:mm", Locale.CHINA);
            tvDate.setText(sdf.format(calendar.getTime()));

            if (existingTransaction == null) {
                SimpleDateFormat noteSdf = new SimpleDateFormat("MM-dd HH:mm", Locale.CHINA);
                etNote.setText(noteSdf.format(calendar.getTime()));
            } else {
                String currentNote = etNote.getText().toString().trim();
                if (currentNote.matches("\\d{2}-\\d{2}\\s+\\d{2}:\\d{2}.*")) {
                    SimpleDateFormat noteSdf = new SimpleDateFormat("MM-dd HH:mm", Locale.CHINA);
                    etNote.setText(noteSdf.format(calendar.getTime()));
                }
            }
        };
        updateDateDisplay.run();

        tvDate.setClickable(true);
        tvDate.setFocusable(true);
        tvDate.setOnClickListener(v -> {
            v.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK);
            showDateTimePickerDialog(context, calendar, updateDateDisplay);
        });

        rgType.setOnCheckedChangeListener((g, id) -> {
            g.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK);

            if (id == R.id.rb_liability || id == R.id.rb_lend) {
                etTargetObject.setVisibility(View.VISIBLE);
                rvCategory.setVisibility(View.GONE);
                etCustomCategory.setVisibility(View.GONE);

                if (id == R.id.rb_liability) {
                    etTargetObject.setHint("借入对象");
                    selectedCategory[0] = "借入";
                } else {
                    etTargetObject.setHint("借出对象");
                    selectedCategory[0] = "借出";
                }
            } else {
                etTargetObject.setVisibility(View.GONE);
                etTargetObject.setText("");
                rvCategory.setVisibility(View.VISIBLE);

                boolean switchToExpense = (id == R.id.rb_expense);
                isExpense[0] = switchToExpense;
                List<String> targetCategories = switchToExpense ? expenseCategories : incomeCategories;
                String defaultCat = targetCategories.isEmpty() ? "自定义" : targetCategories.get(0);
                categoryAdapter.updateData(targetCategories);
                categoryAdapter.setSelectedCategory(defaultCat);
                selectedCategory[0] = defaultCat;
                if ("自定义".equals(defaultCat)) {
                    etCustomCategory.setVisibility(View.VISIBLE);
                } else {
                    etCustomCategory.setVisibility(View.GONE);
                }
            }
        });

        if (existingTransaction != null) {
            btnSave.setText("保存修改");
            etAmount.setText(String.valueOf(existingTransaction.amount));
            if (existingTransaction.remark != null) etRemark.setText(existingTransaction.remark);
            if (existingTransaction.note != null) etNote.setText(existingTransaction.note);

            if (existingTransaction.type == TransactionType.INCOME.getValue()) {
                rgType.check(R.id.rb_income);
                isExpense[0] = false;
                categoryAdapter.updateData(incomeCategories);
                String currentCat = existingTransaction.category;
                if (incomeCategories.contains(currentCat)) {
                    categoryAdapter.setSelectedCategory(currentCat);
                    selectedCategory[0] = currentCat;
                    etCustomCategory.setVisibility(View.GONE);
                } else {
                    categoryAdapter.setSelectedCategory("自定义");
                    selectedCategory[0] = "自定义";
                    etCustomCategory.setVisibility(View.VISIBLE);
                    etCustomCategory.setText(currentCat);
                }
            } else if (existingTransaction.type == TransactionType.LIABILITY.getValue()) {
                rgType.check(R.id.rb_liability);
                if (existingTransaction.targetObject != null) {
                    etTargetObject.setText(existingTransaction.targetObject);
                }
            } else if (existingTransaction.type == TransactionType.LEND.getValue()) {
                rgType.check(R.id.rb_lend);
                if (existingTransaction.targetObject != null) {
                    etTargetObject.setText(existingTransaction.targetObject);
                }
            } else {
                rgType.check(R.id.rb_expense);
                isExpense[0] = true;
                categoryAdapter.updateData(expenseCategories);
                String currentCat = existingTransaction.category;
                if (expenseCategories.contains(currentCat)) {
                    categoryAdapter.setSelectedCategory(currentCat);
                    selectedCategory[0] = currentCat;
                    etCustomCategory.setVisibility(View.GONE);
                } else {
                    categoryAdapter.setSelectedCategory("自定义");
                    selectedCategory[0] = "自定义";
                    etCustomCategory.setVisibility(View.VISIBLE);
                    etCustomCategory.setText(currentCat);
                }
            }

            btnDelete.setVisibility(View.VISIBLE);
            btnDelete.setOnClickListener(v -> {
                AlertDialog.Builder delBuilder = new AlertDialog.Builder(context);
                View delView = LayoutInflater.from(context).inflate(R.layout.dialog_confirm_delete, null);
                delBuilder.setView(delView);
                AlertDialog delDialog = delBuilder.create();
                if (delDialog.getWindow() != null) {
                    delDialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
                }

                TextView tvMsg = delView.findViewById(R.id.tv_dialog_message);
                if (tvMsg != null) {
                    tvMsg.setText("确定要删除这条记录吗？\n删除后将无法恢复。");
                }

                delView.findViewById(R.id.btn_dialog_cancel).setOnClickListener(dv -> delDialog.dismiss());
                delView.findViewById(R.id.btn_dialog_confirm).setOnClickListener(dv -> {
                    listener.onTransactionDeleted(existingTransaction);
                    delDialog.dismiss();
                    dialog.dismiss();
                });
                delDialog.show();
            });
        } else {
            btnSave.setText("保 存");
            btnDelete.setVisibility(View.GONE);
            SimpleDateFormat noteSdf = new SimpleDateFormat("MM-dd HH:mm", Locale.CHINA);
            etNote.setText(noteSdf.format(calendar.getTime()));
        }

        btnSave.setOnClickListener(v -> {
            v.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK);

            String amountStr = etAmount.getText().toString();
            if (amountStr.isEmpty()) {
                Toast.makeText(context, "请输入金额", Toast.LENGTH_SHORT).show();
                return;
            }

            double amount;
            try {
                amount = Double.parseDouble(amountStr);
            } catch (NumberFormatException e) {
                Toast.makeText(context, "金额格式不正确", Toast.LENGTH_SHORT).show();
                return;
            }

            if (amount <= 0 || amount > 99999999.99) {
                Toast.makeText(context, "金额范围：0.01 ~ 99,999,999.99", Toast.LENGTH_SHORT).show();
                return;
            }

            int type;
            int checkedId = rgType.getCheckedRadioButtonId();
            if (checkedId == R.id.rb_income) type = TransactionType.INCOME.getValue();
            else if (checkedId == R.id.rb_liability) type = TransactionType.LIABILITY.getValue();
            else if (checkedId == R.id.rb_lend) type = TransactionType.LEND.getValue();
            else type = TransactionType.EXPENSE.getValue();

            String category = selectedCategory[0];
            if (type == TransactionType.EXPENSE.getValue() || type == TransactionType.INCOME.getValue()) {
                if ("自定义".equals(category)) {
                    category = etCustomCategory.getText().toString().trim();
                    if (category.isEmpty()) {
                        Toast.makeText(context, "请输入自定义分类", Toast.LENGTH_SHORT).show();
                        return;
                    }
                }
            }

            String targetObj = "";
            if (type == TransactionType.LIABILITY.getValue() || type == TransactionType.LEND.getValue()) {
                targetObj = etTargetObject.getText().toString().trim();
                if (targetObj.isEmpty()) {
                    Toast.makeText(context, "请输入借入/借出对象", Toast.LENGTH_SHORT).show();
                    return;
                }
            }

            String userRemark = etRemark.getText().toString().trim();
            String noteContent = etNote.getText().toString().trim();
            long ts = calendar.getTimeInMillis();
            String currencySymbol = isCurrencyEnabled ? btnCurrency.getText().toString() : "¥";

            boolean isEdit = existingTransaction != null;
            if (isEdit) {
                boolean unchanged = existingTransaction.date == ts
                        && existingTransaction.type == type
                        && Math.abs(existingTransaction.amount - amount) < 0.01
                        && Objects.equals(existingTransaction.category, category)
                        && Objects.equals(existingTransaction.subCategory, selectedSubCategory[0])
                        && Objects.equals(existingTransaction.note, noteContent)
                        && Objects.equals(existingTransaction.remark, userRemark)
                        && Objects.equals(existingTransaction.currencySymbol, currencySymbol)
                        && Objects.equals(existingTransaction.photoPath, currentPhotoPath[0])
                        && Objects.equals(existingTransaction.targetObject, targetObj);
                if (unchanged) {
                    dialog.dismiss();
                    Toast.makeText(context, "未做任何修改", Toast.LENGTH_SHORT).show();
                    return;
                }
            }

            Transaction transaction = isEdit ? new Transaction() : new Transaction();
            if (isEdit) {
                transaction.id = existingTransaction.id;
            }
            transaction.date = ts;
            transaction.type = type;
            transaction.category = category;
            transaction.amount = amount;
            transaction.note = noteContent;
            transaction.remark = userRemark;
            transaction.currencySymbol = currencySymbol;
            transaction.subCategory = selectedSubCategory[0];
            transaction.photoPath = currentPhotoPath[0];
            transaction.targetObject = targetObj;

            listener.onTransactionSaved(transaction, isEdit);
            dialog.dismiss();

            WidgetUtils.updateAllWidgets(context);
        });

        // 拆单按钮逻辑
        btnSplit.setOnClickListener(v -> {
            v.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK);
            if (existingTransaction == null) return;

            // 构建当前编辑状态的Transaction用于拆单
            Transaction currentTransaction = new Transaction();
            currentTransaction.id = existingTransaction.id;
            currentTransaction.date = calendar.getTimeInMillis();
            currentTransaction.type = currentTypeFromRadioGroup(rgType);
            currentTransaction.category = selectedCategory[0];
            currentTransaction.amount = parseAmountSafe(etAmount.getText().toString().trim());
            currentTransaction.note = etNote.getText().toString().trim();
            currentTransaction.remark = etRemark.getText().toString().trim();
            currentTransaction.currencySymbol = isCurrencyEnabled ? btnCurrency.getText().toString() : "¥";
            currentTransaction.subCategory = selectedSubCategory[0];
            currentTransaction.photoPath = currentPhotoPath[0];
            currentTransaction.targetObject = etTargetObject.getText().toString().trim();

            // 检测是否有未保存的修改
            boolean hasChanges = existingTransaction.date != currentTransaction.date
                    || existingTransaction.type != currentTransaction.type
                    || Math.abs(existingTransaction.amount - currentTransaction.amount) >= 0.01
                    || !Objects.equals(existingTransaction.category, currentTransaction.category)
                    || !Objects.equals(existingTransaction.subCategory, currentTransaction.subCategory)
                    || !Objects.equals(existingTransaction.note, currentTransaction.note)
                    || !Objects.equals(existingTransaction.remark, currentTransaction.remark)
                    || !Objects.equals(existingTransaction.currencySymbol, currentTransaction.currencySymbol)
                    || !Objects.equals(existingTransaction.photoPath, currentTransaction.photoPath)
                    || !Objects.equals(existingTransaction.targetObject, currentTransaction.targetObject);

            if (hasChanges) {
                // 自动保存修改后再进入拆单
                listener.onTransactionSaved(currentTransaction, true);
                WidgetUtils.updateAllWidgets(context);
            }

            dialog.dismiss();
            listener.onSplitRequested(currentTransaction);
        });

        dialog.setOnDismissListener(d -> {
            android.view.inputmethod.InputMethodManager imm = (android.view.inputmethod.InputMethodManager)
                    context.getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null && dialogView != null && dialogView.getWindowToken() != null) {
                imm.hideSoftInputFromWindow(dialogView.getWindowToken(), 0);
            }
        });

        dialog.show();
        return dialog;
    }

    private static int currentTypeFromRadioGroup(RadioGroup rgType) {
        int checkedId = rgType.getCheckedRadioButtonId();
        if (checkedId == R.id.rb_income) return TransactionType.INCOME.getValue();
        else if (checkedId == R.id.rb_liability) return TransactionType.LIABILITY.getValue();
        else if (checkedId == R.id.rb_lend) return TransactionType.LEND.getValue();
        else return TransactionType.EXPENSE.getValue();
    }

    private static double parseAmountSafe(String amountStr) {
        try {
            return Double.parseDouble(amountStr);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static void showDateTimePickerDialog(Context context, Calendar calendar, Runnable updateDateDisplay) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        View dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_date_time_picker, null);
        builder.setView(dialogView);
        AlertDialog dialog = builder.create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }

        NumberPicker npYear = dialogView.findViewById(R.id.np_year);
        NumberPicker npMonth = dialogView.findViewById(R.id.np_month);
        NumberPicker npDay = dialogView.findViewById(R.id.np_day);
        NumberPicker npHour = dialogView.findViewById(R.id.np_hour);
        NumberPicker npMinute = dialogView.findViewById(R.id.np_minute);
        Button btnCancel = dialogView.findViewById(R.id.btn_cancel);
        Button btnConfirm = dialogView.findViewById(R.id.btn_confirm);

        if (npYear == null || npMonth == null || npDay == null || npHour == null || npMinute == null) return;

        int curYear = calendar.get(Calendar.YEAR);
        int curMonth = calendar.get(Calendar.MONTH) + 1;
        int curDay = calendar.get(Calendar.DAY_OF_MONTH);
        int curHour = calendar.get(Calendar.HOUR_OF_DAY);
        int curMinute = calendar.get(Calendar.MINUTE);

        npYear.setMinValue(2000);
        npYear.setMaxValue(2100);
        npYear.setValue(curYear);

        npMonth.setMinValue(1);
        npMonth.setMaxValue(12);
        npMonth.setValue(curMonth);

        npDay.setMinValue(1);
        npDay.setMaxValue(31);
        npDay.setValue(curDay);

        npHour.setMinValue(0);
        npHour.setMaxValue(23);
        npHour.setValue(curHour);

        npMinute.setMinValue(0);
        npMinute.setMaxValue(59);
        npMinute.setValue(curMinute);

        Runnable adjustDayRange = () -> {
            int year = npYear.getValue();
            int month = npMonth.getValue();
            int maxDay = 31;
            if (month == 2) {
                maxDay = (year % 4 == 0 && (year % 100 != 0 || year % 400 == 0)) ? 29 : 28;
            } else if (month == 4 || month == 6 || month == 9 || month == 11) {
                maxDay = 30;
            }
            npDay.setMaxValue(maxDay);
            if (npDay.getValue() > maxDay) {
                npDay.setValue(maxDay);
            }
        };

        npYear.setOnValueChangedListener((picker, oldVal, newVal) -> adjustDayRange.run());
        npMonth.setOnValueChangedListener((picker, oldVal, newVal) -> adjustDayRange.run());

        btnCancel.setOnClickListener(v -> dialog.dismiss());
        btnConfirm.setOnClickListener(v -> {
            calendar.set(npYear.getValue(), npMonth.getValue() - 1, npDay.getValue(), npHour.getValue(), npMinute.getValue());
            updateDateDisplay.run();
            dialog.dismiss();
        });

        dialog.show();
    }

    private static void showPhotoDialog(Context context, String uriStr) {
        if (uriStr == null) return;
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        android.widget.ImageView iv = new android.widget.ImageView(context);
        try {
            iv.setImageURI(Uri.parse(uriStr));
            iv.setAdjustViewBounds(true);
            iv.setScaleType(android.widget.ImageView.ScaleType.FIT_CENTER);
            builder.setView(iv);
            builder.show();
        } catch (Exception e) {
            Toast.makeText(context, "无法加载图片", Toast.LENGTH_SHORT).show();
        }
    }

    private static String formatDate(long timestamp) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        return sdf.format(new java.util.Date(timestamp));
    }

    private static String formatDate(LocalDate date) {
        return date.toString();
    }

    public static class DecimalDigitsInputFilter implements InputFilter {
        private final int decimalDigits;

        public DecimalDigitsInputFilter(int decimalDigits) {
            this.decimalDigits = decimalDigits;
        }

        @Override
        public CharSequence filter(CharSequence source, int start, int end,
                                    android.text.Spanned dest, int dstart, int dend) {
            int dotPos = -1;
            int len = dest.length();
            for (int i = 0; i < len; i++) {
                char c = dest.charAt(i);
                if (c == '.' || c == '。') {
                    dotPos = i;
                    break;
                }
            }

            if (dotPos >= 0) {
                if (source.equals(".") || source.equals("。")) {
                    return "";
                }
                if (len - dotPos > decimalDigits) {
                    return "";
                }
            }
            return null;
        }
    }

    public interface OnSubCategorySelectedListener {
        void onSubCategorySelected(String subCategory);
    }

    public static AlertDialog createSubCategoryDialog(Context context, String parentCategory,
                                                       @Nullable String currentSelectedSub,
                                                       boolean isOverlay,
                                                       OnSubCategorySelectedListener listener) {
        List<String> subCats = CategoryManager.getSubCategories(context, parentCategory);

        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        View subCatView = LayoutInflater.from(context).inflate(R.layout.dialog_select_sub_category, null);
        builder.setView(subCatView);
        AlertDialog subCatDialog = builder.create();

        if (isOverlay && subCatDialog.getWindow() != null) {
            subCatDialog.getWindow().setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY);
        }

        if (subCatDialog.getWindow() != null) {
            subCatDialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }

        TextView tvTitle = subCatView.findViewById(R.id.tv_title);
        tvTitle.setText(parentCategory + " - 选择细分");

        ChipGroup cgSubCategories = subCatView.findViewById(R.id.cg_sub_categories);
        TextView tvEmpty = subCatView.findViewById(R.id.tv_empty);
        View nsvContainer = subCatView.findViewById(R.id.nsv_container);
        Button btnCancel = subCatView.findViewById(R.id.btn_cancel);

        if (subCats.isEmpty()) {
            cgSubCategories.setVisibility(View.GONE);
            tvEmpty.setVisibility(View.VISIBLE);
            nsvContainer.setMinimumHeight(150);
        } else {
            cgSubCategories.setVisibility(View.VISIBLE);
            tvEmpty.setVisibility(View.GONE);

            int bgDefault = ContextCompat.getColor(context, R.color.cat_unselected_bg);
            int bgChecked = ContextCompat.getColor(context, R.color.app_yellow);
            int textDefault = ContextCompat.getColor(context, R.color.text_primary);
            int textChecked = ContextCompat.getColor(context, R.color.cat_selected_text);

            int[][] states = new int[][]{new int[]{android.R.attr.state_checked}, new int[]{}};
            ColorStateList bgStateList = new ColorStateList(states, new int[]{bgChecked, bgDefault});
            ColorStateList textStateList = new ColorStateList(states, new int[]{textChecked, textDefault});

            for (String subCatName : subCats) {
                Chip chip = new Chip(context);
                chip.setText(subCatName);
                chip.setCheckable(true);
                chip.setClickable(true);
                chip.setChipBackgroundColor(bgStateList);
                chip.setTextColor(textStateList);
                chip.setChipStrokeWidth(0);
                chip.setCheckedIconVisible(false);

                if (subCatName.equals(currentSelectedSub)) {
                    chip.setChecked(true);
                }

                chip.setOnClickListener(v -> {
                    if (listener != null) {
                        listener.onSubCategorySelected(subCatName.equals(currentSelectedSub) ? null : subCatName);
                    }
                    subCatDialog.dismiss();
                });
                cgSubCategories.addView(chip);
            }
        }
        btnCancel.setOnClickListener(v -> subCatDialog.dismiss());
        return subCatDialog;
    }
}