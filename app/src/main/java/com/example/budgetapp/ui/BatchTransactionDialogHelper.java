package com.example.budgetapp.ui;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.text.InputFilter;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
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

public class BatchTransactionDialogHelper {

    public interface OnBatchSavedListener {
        void onBatchSaved(List<Transaction> transactions);
    }

    public static AlertDialog showBatchDialog(
            Context context,
            OnBatchSavedListener listener) {

        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        View dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_batch_transaction, null);
        builder.setView(dialogView);
        AlertDialog dialog = builder.create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            dialog.getWindow().setWindowAnimations(R.style.Animation_Dialog);
        }

        TextView tvDate = dialogView.findViewById(R.id.tv_batch_date);
        RadioGroup rgType = dialogView.findViewById(R.id.rg_batch_type);
        LinearLayout layoutRows = dialogView.findViewById(R.id.layout_batch_rows);
        ImageButton btnAddRow = dialogView.findViewById(R.id.btn_add_row);
        EditText etRecordId = dialogView.findViewById(R.id.et_batch_record_id);
        TextView btnSave = dialogView.findViewById(R.id.btn_batch_save);
        TextView tvCol2Label = dialogView.findViewById(R.id.tv_batch_col2_label);

        Calendar calendar = Calendar.getInstance();

        tvDate.setText(DateUtils.formatDialogDate(calendar.getTimeInMillis()));

        etRecordId.setText(DateUtils.formatNoteTime(calendar.getTimeInMillis()));

        final List<String> expenseCategories = new ArrayList<>(CategoryManager.getExpenseCategories(context));
        final List<String> incomeCategories = new ArrayList<>(CategoryManager.getIncomeCategories(context));

        // 多笔记账场景下移除"自定义"选项
        expenseCategories.remove("自定义");
        incomeCategories.remove("自定义");

        int[] currentType = {TransactionType.EXPENSE.getValue()};

        List<BatchRowHolder> rowHolders = new ArrayList<>();

        addRow(context, layoutRows, rowHolders, expenseCategories, currentType[0]);

        addRow(context, layoutRows, rowHolders, expenseCategories, currentType[0]);

        rgType.setOnCheckedChangeListener((group, checkedId) -> {
            group.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK);

            if (checkedId == R.id.rb_batch_expense) {
                currentType[0] = TransactionType.EXPENSE.getValue();
            } else if (checkedId == R.id.rb_batch_income) {
                currentType[0] = TransactionType.INCOME.getValue();
            } else if (checkedId == R.id.rb_batch_lend) {
                currentType[0] = TransactionType.LEND.getValue();
            } else if (checkedId == R.id.rb_batch_liability) {
                currentType[0] = TransactionType.LIABILITY.getValue();
            }

            boolean hasTargetObject = currentType[0] == TransactionType.LEND.getValue()
                    || currentType[0] == TransactionType.LIABILITY.getValue();

            if (hasTargetObject) {
                tvCol2Label.setText("对象");
            } else {
                tvCol2Label.setText("类型");
            }

            List<String> categories = currentType[0] == TransactionType.INCOME.getValue()
                    ? incomeCategories : expenseCategories;

            for (BatchRowHolder holder : rowHolders) {
                updateRowForType(holder, currentType[0], categories, context);
            }
        });

        btnAddRow.setOnClickListener(v -> {
            v.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK);
            List<String> categories = currentType[0] == TransactionType.INCOME.getValue()
                    ? incomeCategories : expenseCategories;
            addRow(context, layoutRows, rowHolders, categories, currentType[0]);
            updateDeleteButtons(rowHolders);
        });

        btnSave.setOnClickListener(v -> {
            v.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK);

            if (rowHolders.size() < 2) {
                Toast.makeText(context, "至少需要输入2笔账单", Toast.LENGTH_SHORT).show();
                return;
            }

            List<Transaction> transactions = new ArrayList<>();
            String batchRecordId = etRecordId.getText().toString().trim();

            for (int i = 0; i < rowHolders.size(); i++) {
                BatchRowHolder holder = rowHolders.get(i);

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

                if (amount <= 0 || amount > 99999999.99) {
                    Toast.makeText(context, "第" + (i + 1) + "行：金额范围：0.01 ~ 99,999,999.99", Toast.LENGTH_SHORT).show();
                    return;
                }

                String category;
                String targetObject = "";

                boolean hasTargetObject = currentType[0] == TransactionType.LEND.getValue()
                        || currentType[0] == TransactionType.LIABILITY.getValue();

                if (hasTargetObject) {
                    targetObject = holder.etTarget.getText().toString().trim();
                    if (targetObject.isEmpty()) {
                        Toast.makeText(context, "第" + (i + 1) + "行：请输入对象信息", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    category = currentType[0] == TransactionType.LEND.getValue() ? "借出" : "借入";
                } else {
                    if (holder.spinnerCategory.getSelectedItem() != null) {
                        category = holder.spinnerCategory.getSelectedItem().toString();
                    } else {
                        List<String> fallbackCategories = currentType[0] == TransactionType.INCOME.getValue()
                                ? incomeCategories : expenseCategories;
                        category = fallbackCategories.isEmpty() ? "其他" : fallbackCategories.get(0);
                    }
                }

                String remark = holder.etRemark.getText().toString().trim();

                Transaction transaction = new Transaction();
                transaction.date = calendar.getTimeInMillis();
                transaction.type = currentType[0];
                transaction.category = category;
                transaction.amount = amount;
                transaction.remark = remark;
                transaction.note = batchRecordId;
                transaction.currencySymbol = "¥";
                transaction.subCategory = "";
                transaction.photoPath = "";
                transaction.targetObject = targetObject;

                transactions.add(transaction);
            }

            if (transactions.size() < 2) {
                Toast.makeText(context, "至少需要输入2笔账单", Toast.LENGTH_SHORT).show();
                return;
            }

            listener.onBatchSaved(transactions);
            dialog.dismiss();
            Toast.makeText(context, "已保存" + transactions.size() + "笔记录", Toast.LENGTH_SHORT).show();
        });

        updateDeleteButtons(rowHolders);

        dialog.show();
        return dialog;
    }

    private static void addRow(Context context, LinearLayout layoutRows,
                               List<BatchRowHolder> rowHolders,
                               List<String> categories, int currentType) {
        View rowView = LayoutInflater.from(context).inflate(R.layout.item_batch_row, layoutRows, false);
        BatchRowHolder holder = new BatchRowHolder();
        holder.etAmount = rowView.findViewById(R.id.et_row_amount);
        holder.spinnerCategory = rowView.findViewById(R.id.spinner_category);
        holder.etTarget = rowView.findViewById(R.id.et_row_target);
        holder.etRemark = rowView.findViewById(R.id.et_row_remark);
        holder.btnDelete = rowView.findViewById(R.id.btn_row_delete);
        holder.layoutCol2 = rowView.findViewById(R.id.layout_col2);
        holder.rowView = rowView;

        holder.etAmount.setFilters(new InputFilter[]{new TransactionDialogHelper.DecimalDigitsInputFilter(2)});

        ArrayAdapter<String> spinnerAdapter = new ArrayAdapter<>(context,
                android.R.layout.simple_spinner_item, categories);
        spinnerAdapter.setDropDownViewResource(R.layout.item_spinner_dropdown);
        holder.spinnerCategory.setAdapter(spinnerAdapter);

        updateRowForType(holder, currentType, categories, context);

        holder.btnDelete.setOnClickListener(v -> {
            v.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK);
            rowHolders.remove(holder);
            layoutRows.removeView(rowView);
            updateDeleteButtons(rowHolders);
        });

        rowHolders.add(holder);
        layoutRows.addView(rowView);
        updateDeleteButtons(rowHolders);
    }

    private static void updateRowForType(BatchRowHolder holder, int currentType,
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

    private static void updateDeleteButtons(List<BatchRowHolder> rowHolders) {
        boolean canDelete = rowHolders.size() > 2;
        for (BatchRowHolder holder : rowHolders) {
            holder.btnDelete.setVisibility(canDelete ? View.VISIBLE : View.GONE);
        }
    }

    static class BatchRowHolder {
        View rowView;
        EditText etAmount;
        Spinner spinnerCategory;
        EditText etTarget;
        EditText etRemark;
        ImageButton btnDelete;
        View layoutCol2;
    }
}
