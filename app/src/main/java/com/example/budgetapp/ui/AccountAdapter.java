package com.example.budgetapp.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.SwitchCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.example.budgetapp.R;
import com.example.budgetapp.database.Account;
import com.google.android.material.button.MaterialButton;

import java.util.List;
import java.util.Locale;

public class AccountAdapter extends RecyclerView.Adapter<AccountAdapter.ViewHolder> {

    private List<Account> accounts;
    private final AccountActionListener listener;

    public interface AccountActionListener {
        void onEdit(Account account);
        void onDelete(Account account);
        void onToggleEnabled(Account account, boolean enabled);
    }

    public AccountAdapter(List<Account> accounts, AccountActionListener listener) {
        this.accounts = accounts;
        this.listener = listener;
    }

    public void updateAccounts(List<Account> accounts) {
        this.accounts = accounts;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_account, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Account account = accounts.get(position);
        holder.tvName.setText(account.name);
        int balanceFormat = account.enabled
                ? R.string.account_card_balance_format
                : R.string.account_card_balance_disabled_format;
        holder.tvBalance.setText(String.format(Locale.getDefault(),
                holder.itemView.getContext().getString(balanceFormat), account.balance));
        if (account.remark != null && !account.remark.isEmpty()) {
            holder.tvRemark.setText(account.remark);
            holder.tvRemark.setVisibility(View.VISIBLE);
        } else {
            holder.tvRemark.setVisibility(View.GONE);
        }
        holder.switchEnabled.setOnCheckedChangeListener(null);
        holder.switchEnabled.setChecked(account.enabled);

        holder.switchEnabled.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (listener != null) {
                listener.onToggleEnabled(account, isChecked);
            }
        });

        holder.btnEdit.setOnClickListener(v -> {
            if (listener != null) {
                listener.onEdit(account);
            }
        });

        holder.btnDelete.setOnClickListener(v -> {
            if (listener != null) {
                listener.onDelete(account);
            }
        });
    }

    @Override
    public int getItemCount() {
        return accounts == null ? 0 : accounts.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvName;
        TextView tvBalance;
        TextView tvRemark;
        SwitchCompat switchEnabled;
        MaterialButton btnEdit;
        MaterialButton btnDelete;

        ViewHolder(View itemView) {
            super(itemView);
            tvName = itemView.findViewById(R.id.tv_account_name);
            tvBalance = itemView.findViewById(R.id.tv_account_balance);
            tvRemark = itemView.findViewById(R.id.tv_account_remark);
            switchEnabled = itemView.findViewById(R.id.switch_enabled);
            btnEdit = itemView.findViewById(R.id.btn_edit);
            btnDelete = itemView.findViewById(R.id.btn_delete);
        }
    }
}
