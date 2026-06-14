package com.example.budgetapp.ui;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.text.Editable;
import android.text.InputFilter;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.example.budgetapp.R;
import com.example.budgetapp.database.Transaction;
import com.example.budgetapp.model.TransactionType;
import com.example.budgetapp.utils.CategoryManager;

import com.example.budgetapp.utils.DateUtils;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

public class SplitTransactionDialogHelper {

    public interface OnSplitSavedListener {
        void onSplitSaved(Transaction originalTransaction, List<Transaction> splitTransactions);
    }

    public static AlertDialog showSplitDialog(
            Context context,
            Transaction originalTransaction,
            OnSplitSavedListener listener) {

        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        View dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_split_transaction, null);
        builder.setView(dialogView);
        AlertDialog dialog = builder.create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            dialog.getWindow().setWindowAnimations(R.style.Animation_Dialog);
        }

        TextView tvOriginalAmount = dialogView.findViewById(R.id.tv_split_original_amount);
        LinearLayout layoutRows = dialogView.findViewById(R.id.layout_split_rows);
        ImageButton btnAddRow = dialogView.findViewById(R.id.btn_split_add_row);
        EditText etRecordId = dialogView.findViewById(R.id.et_split_record_id);
        TextView btnSave = dialogView.findViewById(R.id.btn_split_save);
        TextView btnCancel = dialogView.findViewById(R.id.btn_split_cancel);
        TextView tvCol2Label = dialogView.findViewById(R.id.tv_split_col2_label);

        double originalAmount = originalTransaction.amount;
        tvOriginalAmount.setText(String.format(Locale.CHINA, "原始金额: %.2f", originalAmount));

        // 初始化记录标识
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(originalTransaction.date);
        etRecordId.setText(originalTransaction.note != null ? originalTransaction.note : DateUtils.formatNoteTime(calendar.getTimeInMillis()));

        // 类型保持与原始账单一致，不允许修改
        int currentType = originalTransaction.type;

        boolean hasTargetObject = currentType == TransactionType.LEND.getValue()
                || currentType == TransactionType.LIABILITY.getValue();
        if (hasTargetObject) {
            tvCol2Label.setText("对象");
        } else {
            tvCol2Label.setText("类型");
        }

        List<String> categories = new ArrayList<>(currentType == TransactionType.INCOME.getValue()
                ? CategoryManager.getIncomeCategories(context)
                : CategoryManager.getExpenseCategories(context));
        categories.remove("自定义");

        List<SplitRowHolder> rowHolders = new ArrayList<>();

        // 第一排：原始账单数据，金额不可编辑
        addRow(context, layoutRows, rowHolders, categories, currentType, true, originalTransaction.amount,
                originalTransaction.category, originalTransaction.remark, originalTransaction.targetObject, originalAmount);

        // 第二排：空行，金额可编辑
        addRow(context, layoutRows, rowHolders, categories, currentType, false, 0,
                null, "", "", originalAmount);

        // 添加行
        btnAddRow.setOnClickListener(v -> {
            v.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK);
            addRow(context, layoutRows, rowHolders, categories, currentType, false, 0,
                    null, "", "", originalAmount);
            updateDeleteButtons(rowHolders);
        });

        // 取消按钮：直接退出
        btnCancel.setOnClickListener(v -> {
            v.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK);
            dialog.dismiss();
        });

        // 保存按钮
        btnSave.setOnClickListener(v -> {
            v.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK);

            if (rowHolders.size() < 2) {
                Toast.makeText(context, "至少需要2笔才能完成拆单", Toast.LENGTH_SHORT).show();
                return;
            }

            List<Transaction> transactions = new ArrayList<>();
            String batchRecordId = etRecordId.getText().toString().trim();
            double totalAmount = 0;

            for (int i = 0; i < rowHolders.size(); i++) {
                SplitRowHolder holder = rowHolders.get(i);

                String amountStr = holder.etAmount.getText().toString().trim();
                if (amountStr.isEmpty()) {
                    Toast.makeText(context, "第" + (i + 1) + "行：请输入金额", Toast.LENGTH_SHORT).show();
                    return;
                }

                double amount;
                try {
                    amount = Double.parseDouble(amountStr);
                } catch (NumberFormatException e) {
                    Toast.makeText(context, "第" + (i + 1) + "行：金额格式不正确", Toast.LENGTH_SHORT).show();
                    return;
                }

                if (i > 0 && amount < 0) {
                    Toast.makeText(context, "第" + (i + 1) + "行：金额不能为负数", Toast.LENGTH_SHORT).show();
                    return;
                }

                if (amount <= 0 && i == 0) {
                    Toast.makeText(context, "第一行金额不能为零或负数，请检查其他行金额", Toast.LENGTH_SHORT).show();
                    return;
                }

                String category;
                String targetObject = "";

                if (hasTargetObject) {
                    targetObject = holder.etTarget.getText().toString().trim();
                    if (targetObject.isEmpty()) {
                        Toast.makeText(context, "第" + (i + 1) + "行：请输入对象信息", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    category = currentType == TransactionType.LEND.getValue() ? "借出" : "借入";
                } else {
                    if (holder.spinnerCategory.getSelectedItem() != null) {
                        category = holder.spinnerCategory.getSelectedItem().toString();
                    } else {
                        category = categories.isEmpty() ? "其他" : categories.get(0);
                    }
                }

                String remark = holder.etRemark.getText().toString().trim();

                Transaction transaction = new Transaction();
                transaction.date = calendar.getTimeInMillis();
                transaction.type = currentType;
                transaction.category = category;
                transaction.amount = amount;
                transaction.remark = remark;
                transaction.note = batchRecordId;
                transaction.currencySymbol = originalTransaction.currencySymbol != null ? originalTransaction.currencySymbol : "¥";
                transaction.subCategory = "";
                transaction.photoPath = "";
                transaction.targetObject = targetObject;

                transactions.add(transaction);
                totalAmount += amount;
            }

            // 验证总金额（转换为分比较，避免浮点精度问题）
            long totalCents = Math.round(totalAmount * 100);
            long originalCents = Math.round(originalAmount * 100);
            if (totalCents != originalCents) {
                Toast.makeText(context,
                        String.format(Locale.CHINA, "金额总和不等于原始金额\n原始: %.2f  当前: %.2f", originalAmount, totalAmount),
                        Toast.LENGTH_LONG).show();
                return;
            }

            listener.onSplitSaved(originalTransaction, transactions);
            dialog.dismiss();
            Toast.makeText(context, "已拆分为" + transactions.size() + "笔记录", Toast.LENGTH_SHORT).show();
        });

        updateDeleteButtons(rowHolders);

        dialog.show();
        return dialog;
    }

    private static void addRow(Context context, LinearLayout layoutRows,
                               List<SplitRowHolder> rowHolders,
                               List<String> categories, int currentType,
                               boolean isFirstRow, double amount,
                               String category, String remark,
                               String targetObject, double originalAmount) {
        View rowView = LayoutInflater.from(context).inflate(R.layout.item_batch_row, layoutRows, false);
        SplitRowHolder holder = new SplitRowHolder();
        holder.etAmount = rowView.findViewById(R.id.et_row_amount);
        holder.spinnerCategory = rowView.findViewById(R.id.spinner_category);
        holder.etTarget = rowView.findViewById(R.id.et_row_target);
        holder.etRemark = rowView.findViewById(R.id.et_row_remark);
        holder.btnDelete = rowView.findViewById(R.id.btn_row_delete);
        holder.rowView = rowView;
        holder.isFirstRow = isFirstRow;
        holder.originalAmount = originalAmount;

        holder.etAmount.setFilters(new InputFilter[]{new TransactionDialogHelper.DecimalDigitsInputFilter(2)});

        if (isFirstRow) {
            holder.etAmount.setText(String.format(Locale.CHINA, "%.2f", amount));
            holder.etAmount.setFocusable(false);
            holder.etAmount.setFocusableInTouchMode(false);
            holder.etAmount.setClickable(false);
            holder.etAmount.setTextColor(androidx.core.content.ContextCompat.getColor(context, android.R.color.darker_gray));
        } else {
            holder.etAmount.setText(amount > 0 ? String.format(Locale.CHINA, "%.2f", amount) : "");
        }

        ArrayAdapter<String> spinnerAdapter = new ArrayAdapter<>(context,
                android.R.layout.simple_spinner_item, categories);
        spinnerAdapter.setDropDownViewResource(R.layout.item_spinner_dropdown);
        holder.spinnerCategory.setAdapter(spinnerAdapter);

        if (remark != null && !remark.isEmpty()) {
            holder.etRemark.setText(remark);
        }

        if (targetObject != null && !targetObject.isEmpty()) {
            holder.etTarget.setText(targetObject);
        }

        if (isFirstRow && category != null) {
            boolean hasTargetObject = currentType == TransactionType.LEND.getValue()
                    || currentType == TransactionType.LIABILITY.getValue();
            if (!hasTargetObject) {
                for (int i = 0; i < categories.size(); i++) {
                    if (categories.get(i).equals(category)) {
                        holder.spinnerCategory.setSelection(i);
                        break;
                    }
                }
            }
        }

        updateRowForType(holder, currentType, categories, context);

        // 非第一排的金额输入监听：实时校验并更新第一排金额
        if (!isFirstRow) {
            holder.etAmount.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {}

                @Override
                public void afterTextChanged(Editable s) {
                    // 计算其他排金额总和
                    double otherTotal = 0;
                    for (int i = 1; i < rowHolders.size(); i++) {
                        String amtStr = rowHolders.get(i).etAmount.getText().toString().trim();
                        if (!amtStr.isEmpty()) {
                            try {
                                otherTotal += Double.parseDouble(amtStr);
                            } catch (NumberFormatException ignored) {}
                        }
                    }

                    if (otherTotal > originalAmount) {
                        // 清空当前输入并提示
                        holder.etAmount.removeTextChangedListener(this);
                        holder.etAmount.setText("");
                        holder.etAmount.addTextChangedListener(this);
                        Toast.makeText(context, "拆分金额总和不能超过原始金额", Toast.LENGTH_SHORT).show();
                        updateFirstRowAmount(rowHolders);
                        return;
                    }

                    updateFirstRowAmount(rowHolders);
                }
            });
        }

        holder.btnDelete.setOnClickListener(v -> {
            v.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK);
            rowHolders.remove(holder);
            layoutRows.removeView(rowView);
            updateDeleteButtons(rowHolders);
            updateFirstRowAmount(rowHolders);
        });

        rowHolders.add(holder);
        layoutRows.addView(rowView);
        updateDeleteButtons(rowHolders);
    }

    private static void updateFirstRowAmount(List<SplitRowHolder> rowHolders) {
        if (rowHolders.isEmpty()) return;

        SplitRowHolder firstRow = rowHolders.get(0);
        double otherTotal = 0;

        for (int i = 1; i < rowHolders.size(); i++) {
            String amountStr = rowHolders.get(i).etAmount.getText().toString().trim();
            if (!amountStr.isEmpty()) {
                try {
                    double amt = Double.parseDouble(amountStr);
                    if (amt > 0) {
                        otherTotal += amt;
                    }
                } catch (NumberFormatException ignored) {}
            }
        }

        double firstAmount = firstRow.originalAmount - otherTotal;
        firstRow.etAmount.setText(String.format(Locale.CHINA, "%.2f", firstAmount));
    }

    private static void updateRowForType(SplitRowHolder holder, int currentType,
                                          List<String> categories, Context context) {
        boolean hasTargetObject = currentType == TransactionType.LEND.getValue()
                || currentType == TransactionType.LIABILITY.getValue();

        if (hasTargetObject) {
            holder.spinnerCategory.setVisibility(View.GONE);
            holder.etTarget.setVisibility(View.VISIBLE);
            if (currentType == TransactionType.LIABILITY.getValue()) {
                holder.etTarget.setHint("借入对象");
            } else {
                holder.etTarget.setHint("借出对象");
            }
        } else {
            holder.spinnerCategory.setVisibility(View.VISIBLE);
            holder.etTarget.setVisibility(View.GONE);
            ArrayAdapter<String> spinnerAdapter = new ArrayAdapter<>(context,
                    android.R.layout.simple_spinner_item, categories);
            spinnerAdapter.setDropDownViewResource(R.layout.item_spinner_dropdown);
            holder.spinnerCategory.setAdapter(spinnerAdapter);
        }
    }

    private static void updateDeleteButtons(List<SplitRowHolder> rowHolders) {
        boolean canDelete = rowHolders.size() > 2;
        for (int i = 0; i < rowHolders.size(); i++) {
            SplitRowHolder holder = rowHolders.get(i);
            if (holder.isFirstRow) {
                holder.btnDelete.setVisibility(View.GONE);
            } else {
                holder.btnDelete.setVisibility(canDelete ? View.VISIBLE : View.GONE);
            }
        }
    }

    static class SplitRowHolder {
        View rowView;
        EditText etAmount;
        Spinner spinnerCategory;
        EditText etTarget;
        EditText etRemark;
        ImageButton btnDelete;
        boolean isFirstRow;
        double originalAmount;
    }
}
