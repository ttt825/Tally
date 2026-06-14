package com.example.budgetapp.ui;

import android.content.Context;
import android.graphics.Color;
import androidx.core.content.ContextCompat;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.DiffUtil;
import com.example.budgetapp.R;
import com.example.budgetapp.database.Transaction;
import com.example.budgetapp.model.TransactionType;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class TransactionListAdapter extends RecyclerView.Adapter<TransactionListAdapter.ViewHolder> {
    private static final DiffUtil.ItemCallback<Transaction> DIFF_CALLBACK = new DiffUtil.ItemCallback<Transaction>() {
        @Override
        public boolean areItemsTheSame(@NonNull Transaction oldItem, @NonNull Transaction newItem) {
            return oldItem.id == newItem.id;
        }

        @Override
        public boolean areContentsTheSame(@NonNull Transaction oldItem, @NonNull Transaction newItem) {
            return oldItem.amount == newItem.amount
                    && Objects.equals(oldItem.category, newItem.category)
                    && Objects.equals(oldItem.note, newItem.note)
                    && oldItem.type == newItem.type
                    && oldItem.date == newItem.date;
        }
    };

    private List<Transaction> list = new ArrayList<>();
    private OnItemClickListener listener;
    private boolean showCurrency;

    public interface OnItemClickListener {
        void onItemClick(Transaction transaction);
    }

    public TransactionListAdapter(OnItemClickListener listener) {
        this.listener = listener;
    }

    public void setTransactions(List<Transaction> newList) {
        List<Transaction> oldList = this.list;
        this.list = newList != null ? newList : new ArrayList<>();
        DiffUtil.DiffResult result = DiffUtil.calculateDiff(new DiffUtil.Callback() {
            @Override
            public int getOldListSize() {
                return oldList.size();
            }

            @Override
            public int getNewListSize() {
                return list.size();
            }

            @Override
            public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {
                return DIFF_CALLBACK.areItemsTheSame(oldList.get(oldItemPosition), list.get(newItemPosition));
            }

            @Override
            public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
                return DIFF_CALLBACK.areContentsTheSame(oldList.get(oldItemPosition), list.get(newItemPosition));
            }
        });
        result.dispatchUpdatesTo(this);
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_transaction_detail, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Transaction t = list.get(position);
        Context context = holder.itemView.getContext();

        // 基础配置：货币单位与符号（缓存 showCurrency，避免每次 bind 读取 SharedPreferences）
        if (position == 0) {
            showCurrency = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
                    .getBoolean("enable_currency", false);
        }

        String symbol = (t.currencySymbol != null && !t.currencySymbol.isEmpty()) ? t.currencySymbol : "¥";
        String amountStr = String.format("%.2f", t.amount);
        String displayAmount = showCurrency ? (symbol + " " + amountStr) : amountStr;

        // 清除背景，防止复用错乱
        holder.itemView.setBackgroundResource(0);

        // 正常入库记录的颜色逻辑
        holder.tvNote.setAlpha(1.0f);
        if (t.type == TransactionType.INCOME.getValue()) {
            holder.tvAmount.setTextColor(ContextCompat.getColor(context, R.color.income_red));
            holder.tvAmount.setText("+" + displayAmount);
        } else if (t.type == TransactionType.LIABILITY.getValue()) {
            holder.tvAmount.setTextColor(ContextCompat.getColor(context, R.color.liability_orange));
            holder.tvAmount.setText("+" + displayAmount);
        } else if (t.type == TransactionType.LEND.getValue()) {
            holder.tvAmount.setTextColor(context.getColor(R.color.lend_purple));
            holder.tvAmount.setText("-" + displayAmount);
        } else {
            holder.tvAmount.setTextColor(context.getColor(R.color.expense_green));
            holder.tvAmount.setText("-" + displayAmount);
        }

        // 分类与二级分类显示
        holder.tvDate.setText(t.category);
        if (t.subCategory != null && !t.subCategory.isEmpty()) {
            holder.tvSubCategory.setText(t.subCategory);
            holder.tvSubCategory.setVisibility(View.VISIBLE);
        } else {
            holder.tvSubCategory.setVisibility(View.GONE);
        }

        // 创建时间显示（t.note字段包含创建时间）
        if (t.note != null && !t.note.isEmpty()) {
            holder.tvTime.setVisibility(View.VISIBLE);
            holder.tvTime.setText(t.note);
        } else {
            holder.tvTime.setVisibility(View.GONE);
        }

        // 备注显示
        if (t.remark != null && !t.remark.isEmpty()) {
            holder.tvNote.setVisibility(View.VISIBLE);
            holder.tvNote.setText(t.remark);
        } else {
            holder.tvNote.setVisibility(View.GONE);
        }

        holder.itemView.setOnClickListener(v -> {
            // 按压反馈
            AnimUtils.pressFeedback(v, 0.97f, 60);
            if (listener != null) listener.onItemClick(t);
        });
    }

    @Override
    public int getItemCount() {
        return list == null ? 0 : list.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvDate, tvTime, tvAmount, tvNote;
        TextView tvSubCategory;

        ViewHolder(View v) {
            super(v);
            tvDate = v.findViewById(R.id.tv_detail_date);
            tvSubCategory = v.findViewById(R.id.tv_detail_sub_category);
            tvTime = v.findViewById(R.id.tv_detail_time);
            tvAmount = v.findViewById(R.id.tv_detail_amount);
            tvNote = v.findViewById(R.id.tv_detail_note);
        }
    }
}