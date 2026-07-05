package com.example.budgetapp.ui;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.DiffUtil;

import com.example.budgetapp.R;
import com.example.budgetapp.database.Transaction;
import com.example.budgetapp.model.TransactionType;
import com.example.budgetapp.utils.DateUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

public class DetailsAdapter extends RecyclerView.Adapter<DetailsAdapter.GroupViewHolder> {

    private List<Transaction> rawTransactions = new ArrayList<>();
    private List<TransactionGroup> groups = new ArrayList<>();
    private OnTransactionClickListener listener;

    public interface OnTransactionClickListener {
        void onTransactionClick(Transaction transaction);
    }

    public void setOnTransactionClickListener(OnTransactionClickListener listener) {
        this.listener = listener;
    }

    public static class TransactionGroup {
        String dateStr;      // yyyyMMdd
        String displayDate;  // MM月dd日 星期X
        List<Transaction> transactions;

        TransactionGroup(String dateStr, String displayDate, List<Transaction> transactions) {
            this.dateStr = dateStr;
            this.displayDate = displayDate;
            this.transactions = transactions;
        }
    }

    public void setTransactions(List<Transaction> newList) {
        List<TransactionGroup> oldGroups = this.groups;

        this.rawTransactions = newList == null ? new ArrayList<>() : new ArrayList<>(newList);
        this.groups = groupByDate(this.rawTransactions);

        DiffUtil.DiffResult result = DiffUtil.calculateDiff(new DiffUtil.Callback() {
            @Override
            public int getOldListSize() {
                return oldGroups.size();
            }

            @Override
            public int getNewListSize() {
                return groups.size();
            }

            @Override
            public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {
                return oldGroups.get(oldItemPosition).dateStr.equals(groups.get(newItemPosition).dateStr);
            }

            @Override
            public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
                TransactionGroup oldG = oldGroups.get(oldItemPosition);
                TransactionGroup newG = groups.get(newItemPosition);
                if (!oldG.dateStr.equals(newG.dateStr)) return false;
                if (oldG.transactions.size() != newG.transactions.size()) return false;
                for (int i = 0; i < oldG.transactions.size(); i++) {
                    Transaction t1 = oldG.transactions.get(i);
                    Transaction t2 = newG.transactions.get(i);
                    if (t1.id != t2.id
                            || t1.amount != t2.amount
                            || !Objects.equals(t1.category, t2.category)
                            || !Objects.equals(t1.note, t2.note)
                            || !Objects.equals(t1.remark, t2.remark)
                            || !Objects.equals(t1.subCategory, t2.subCategory)
                            || t1.type != t2.type
                            || t1.date != t2.date) {
                        return false;
                    }
                }
                return true;
            }
        });
        result.dispatchUpdatesTo(this);
    }

    private List<TransactionGroup> groupByDate(List<Transaction> list) {
        Map<String, List<Transaction>> map = new LinkedHashMap<>();
        for (Transaction t : list) {
            String key = DateUtils.formatCompareDate(t.date);
            map.computeIfAbsent(key, k -> new ArrayList<>()).add(t);
        }
        List<TransactionGroup> result = new ArrayList<>();
        for (Map.Entry<String, List<Transaction>> entry : map.entrySet()) {
            String display = DateUtils.formatDisplayDate(entry.getValue().get(0).date);
            result.add(new TransactionGroup(entry.getKey(), display, entry.getValue()));
        }
        return result;
    }

    public List<Transaction> getCurrentTransactions() {
        return rawTransactions;
    }

    private float cachedDensity = 0;

    private int dpToPx(Context context, int dp) {
        if (cachedDensity == 0) {
            cachedDensity = context.getResources().getDisplayMetrics().density;
        }
        return (int) (dp * cachedDensity);
    }

    @NonNull
    @Override
    public GroupViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_transaction_group, parent, false);
        return new GroupViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull GroupViewHolder holder, int position) {
        if (position < 0 || position >= groups.size()) return;

        TransactionGroup group = groups.get(position);
        Context context = holder.itemView.getContext();
        SharedPreferences prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE);
        boolean isCustomBg = prefs.getInt("theme_mode", -1) == 3;
        boolean showCurrency = prefs.getBoolean("enable_currency", false);

        // 1. 日期标题
        holder.tvGroupDate.setText(group.displayDate);

        // 2. 分组容器背景：每次绑定独立创建 GradientDrawable，彻底避免共享 drawable 导致的复用异常
        android.graphics.drawable.GradientDrawable groupBg = new android.graphics.drawable.GradientDrawable();
        groupBg.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
        float radius = dpToPx(context, 16);
        groupBg.setCornerRadii(new float[]{radius, radius, radius, radius, radius, radius, radius, radius});
        int surfaceColor = ContextCompat.getColor(context, R.color.white);
        if (isCustomBg) {
            surfaceColor = androidx.core.graphics.ColorUtils.setAlphaComponent(surfaceColor, 230);
        }
        groupBg.setColor(surfaceColor);
        holder.groupContainer.setBackground(groupBg);

        // 3. 清空并重建内部交易列表
        holder.llTransactions.removeAllViews();

        for (int i = 0; i < group.transactions.size(); i++) {
            Transaction current = group.transactions.get(i);
            View itemView = LayoutInflater.from(context)
                    .inflate(R.layout.item_transaction_detail, holder.llTransactions, false);
            bindTransactionItem(context, itemView, current, showCurrency);

            itemView.setOnClickListener(v -> {
                AnimUtils.pressFeedback(v, 0.97f, 60);
                if (listener != null) listener.onTransactionClick(current);
            });

            holder.llTransactions.addView(itemView);

            // 非最后一项添加底部分割线，使同一容器内的记录分隔清晰
            if (i < group.transactions.size() - 1) {
                View divider = new View(context);
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, dpToPx(context, 1));
                params.setMargins(dpToPx(context, 16), 0, dpToPx(context, 16), 0);
                divider.setLayoutParams(params);
                divider.setBackgroundColor(ContextCompat.getColor(context, R.color.divider));
                holder.llTransactions.addView(divider);
            }
        }
    }

    private void bindTransactionItem(Context context, View itemView, Transaction current, boolean showCurrency) {
        TextView tvDate = itemView.findViewById(R.id.tv_detail_date);
        TextView tvSubCategory = itemView.findViewById(R.id.tv_detail_sub_category);
        TextView tvTime = itemView.findViewById(R.id.tv_detail_time);
        TextView tvAmount = itemView.findViewById(R.id.tv_detail_amount);
        TextView tvNote = itemView.findViewById(R.id.tv_detail_note);

        String symbol = (current.currencySymbol != null && !current.currencySymbol.isEmpty()) ? current.currencySymbol : "¥";
        String amountStr = String.format(Locale.CHINA, "%.2f", current.amount);
        String displayAmount = showCurrency ? (symbol + " " + amountStr) : amountStr;

        if (current.type == TransactionType.INCOME.getValue()) {
            tvAmount.setTextColor(context.getColor(R.color.income_red));
            tvAmount.setText("+" + displayAmount);
        } else if (current.type == TransactionType.LIABILITY.getValue()) {
            tvAmount.setTextColor(context.getColor(R.color.liability_orange));
            tvAmount.setText("+" + displayAmount);
        } else if (current.type == TransactionType.LEND.getValue()) {
            tvAmount.setTextColor(context.getColor(R.color.lend_purple));
            tvAmount.setText("-" + displayAmount);
        } else {
            tvAmount.setTextColor(ContextCompat.getColor(context, R.color.expense_green));
            tvAmount.setText("-" + displayAmount);
        }

        tvDate.setText(current.category);

        if (!TextUtils.isEmpty(current.subCategory)) {
            tvSubCategory.setText(current.subCategory);
            tvSubCategory.setVisibility(View.VISIBLE);
        } else {
            tvSubCategory.setVisibility(View.GONE);
        }

        if (!TextUtils.isEmpty(current.note)) {
            tvTime.setVisibility(View.VISIBLE);
            tvTime.setText(current.note);
        } else {
            tvTime.setVisibility(View.GONE);
        }

        if (!TextUtils.isEmpty(current.remark)) {
            tvNote.setVisibility(View.VISIBLE);
            tvNote.setText(current.remark);
        } else {
            tvNote.setVisibility(View.GONE);
        }
    }

    @Override
    public int getItemCount() {
        return groups.size();
    }

    static class GroupViewHolder extends RecyclerView.ViewHolder {
        TextView tvGroupDate;
        LinearLayout groupContainer;
        LinearLayout llTransactions;

        GroupViewHolder(View itemView) {
            super(itemView);
            tvGroupDate = itemView.findViewById(R.id.tv_group_date);
            groupContainer = itemView.findViewById(R.id.group_container);
            llTransactions = itemView.findViewById(R.id.ll_transactions);
        }
    }
}
