package com.example.budgetapp.ui;

import android.app.AlertDialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.budgetapp.R;
import com.example.budgetapp.database.Account;
import com.example.budgetapp.viewmodel.AccountViewModel;

import java.util.ArrayList;
import java.util.List;

public class AccountSettingsActivity extends AppCompatActivity {

    private AccountViewModel viewModel;
    private RecyclerView recyclerAccounts;
    private View emptyView;
    private AccountAdapter adapter;

    @Override
    @SuppressWarnings("deprecation")
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        getWindow().setStatusBarColor(Color.TRANSPARENT);
        getWindow().setNavigationBarColor(Color.TRANSPARENT);
        setContentView(R.layout.activity_account_settings);

        View rootView = findViewById(R.id.root_view);
        ViewCompat.setOnApplyWindowInsetsListener(rootView, (v, windowInsets) -> {
            Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(insets.left, insets.top, insets.right, insets.bottom);
            return WindowInsetsCompat.CONSUMED;
        });

        viewModel = new ViewModelProvider(this).get(AccountViewModel.class);

        recyclerAccounts = findViewById(R.id.recycler_accounts);
        emptyView = findViewById(R.id.empty_view);

        recyclerAccounts.setLayoutManager(new LinearLayoutManager(this));
        adapter = new AccountAdapter(new ArrayList<>(),
                new AccountAdapter.AccountActionListener() {
                    @Override
                    public void onEdit(Account account) {
                        showAccountFormDialog(account);
                    }

                    @Override
                    public void onDelete(Account account) {
                        confirmDelete(account);
                    }

                    @Override
                    public void onToggleEnabled(Account account, boolean enabled) {
                        account.enabled = enabled;
                        viewModel.update(account, null);
                    }
                });
        recyclerAccounts.setAdapter(adapter);

        findViewById(R.id.btn_add_account).setOnClickListener(v -> showAccountFormDialog(null));

        viewModel.getAllAccounts().observe(this, accounts -> {
            adapter.updateAccounts(accounts);
            boolean isEmpty = accounts == null || accounts.isEmpty();
            emptyView.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
            recyclerAccounts.setVisibility(isEmpty ? View.GONE : View.VISIBLE);
        });
    }

    private void showAccountFormDialog(Account account) {
        boolean isEdit = account != null;
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_account_form, null);
        TextView tvTitle = dialogView.findViewById(R.id.tv_title);
        EditText etName = dialogView.findViewById(R.id.et_account_name);
        EditText etBalance = dialogView.findViewById(R.id.et_account_balance);
        EditText etRemark = dialogView.findViewById(R.id.et_account_remark);
        androidx.appcompat.widget.SwitchCompat switchEnabled = dialogView.findViewById(R.id.switch_enabled);
        Button btnCancel = dialogView.findViewById(R.id.btn_cancel);
        Button btnConfirm = dialogView.findViewById(R.id.btn_confirm);

        tvTitle.setText(isEdit ? R.string.account_edit_title : R.string.account_add_title);
        if (isEdit) {
            etName.setText(account.name);
            etBalance.setText(String.format(java.util.Locale.getDefault(), "%.2f", account.balance));
            etRemark.setText(account.remark);
            switchEnabled.setChecked(account.enabled);
        }

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(dialogView)
                .create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            dialog.getWindow().setWindowAnimations(R.style.Animation_Dialog);
        }

        btnCancel.setOnClickListener(v -> dialog.dismiss());
        btnConfirm.setOnClickListener(v -> {
            String name = etName.getText() != null ? etName.getText().toString().trim() : "";
            if (TextUtils.isEmpty(name)) {
                Toast.makeText(this, R.string.account_name_required, Toast.LENGTH_SHORT).show();
                return;
            }

            double balance;
            try {
                balance = Double.parseDouble(etBalance.getText() != null
                        ? etBalance.getText().toString().trim() : "0");
            } catch (NumberFormatException e) {
                balance = 0.0;
            }

            String remark = etRemark.getText() != null ? etRemark.getText().toString().trim() : "";
            boolean enabled = switchEnabled.isChecked();

            if (isEdit) {
                account.name = name;
                account.balance = balance;
                account.remark = remark;
                account.enabled = enabled;
                viewModel.update(account, result -> runOnUiThread(() -> {
                    Toast.makeText(this, R.string.account_edit_title, Toast.LENGTH_SHORT).show();
                    dialog.dismiss();
                }));
            } else {
                Account newAccount = new Account(name, balance, enabled, remark);
                viewModel.insert(newAccount, id -> runOnUiThread(() -> {
                    Toast.makeText(this, R.string.account_add_title, Toast.LENGTH_SHORT).show();
                    dialog.dismiss();
                }));
            }
        });

        dialog.show();
    }

    private void confirmDelete(Account account) {
        AlertDialog.Builder delBuilder = new AlertDialog.Builder(this);
        View delView = LayoutInflater.from(this).inflate(R.layout.dialog_confirm_delete, null);
        delBuilder.setView(delView);
        AlertDialog delDialog = delBuilder.create();
        if (delDialog.getWindow() != null) {
            delDialog.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));
            delDialog.getWindow().setWindowAnimations(R.style.Animation_Dialog);
        }

        TextView tvTitle = delView.findViewById(R.id.tv_dialog_title);
        TextView tvMsg = delView.findViewById(R.id.tv_dialog_message);
        if (tvTitle != null) {
            tvTitle.setText(R.string.account_delete_title);
        }
        if (tvMsg != null) {
            tvMsg.setText(R.string.account_delete_message);
        }

        delView.findViewById(R.id.btn_dialog_cancel).setOnClickListener(v -> delDialog.dismiss());
        delView.findViewById(R.id.btn_dialog_confirm).setOnClickListener(v -> {
            viewModel.deleteAndClearReferences(account, result -> runOnUiThread(() ->
                    Toast.makeText(this, R.string.account_delete_title, Toast.LENGTH_SHORT).show()));
            delDialog.dismiss();
        });

        delDialog.show();
    }
}
