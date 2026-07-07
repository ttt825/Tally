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
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
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
import java.util.Collections;
import java.util.List;

public class AccountSettingsActivity extends AppCompatActivity {

    private AccountViewModel viewModel;
    private RecyclerView recyclerAccounts;
    private View emptyView;
    private AccountAdapter adapter;
    private List<Account> currentAccounts = new ArrayList<>();

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
        findViewById(R.id.btn_sort_accounts).setOnClickListener(v -> showSortDialog());

        viewModel.getAllAccounts().observe(this, accounts -> {
            currentAccounts = accounts != null ? new ArrayList<>(accounts) : new ArrayList<>();
            adapter.updateAccounts(currentAccounts);
            boolean isEmpty = currentAccounts.isEmpty();
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

    private void showSortDialog() {
        List<Account> enabledAccounts = new ArrayList<>();
        for (Account account : currentAccounts) {
            if (account.enabled) {
                enabledAccounts.add(account);
            }
        }
        if (enabledAccounts.isEmpty()) {
            Toast.makeText(this, R.string.account_sort_empty, Toast.LENGTH_SHORT).show();
            return;
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_sort_categories, null);
        builder.setView(view);
        AlertDialog dialog = builder.create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            dialog.getWindow().setWindowAnimations(R.style.Animation_Dialog);
        }

        TextView tvTitle = view.findViewById(R.id.tv_dialog_title);
        if (tvTitle != null) {
            tvTitle.setText(R.string.account_sort_title);
        }

        LinearLayout container = view.findViewById(R.id.ll_sort_container);
        buildSortList(container, enabledAccounts);

        Button btnConfirm = view.findViewById(R.id.btn_confirm);
        btnConfirm.setOnClickListener(v -> dialog.dismiss());

        dialog.show();
    }

    private void buildSortList(LinearLayout container, List<Account> accounts) {
        container.removeAllViews();

        int marginV = (int) (6 * getResources().getDisplayMetrics().density);
        int paddingContent = (int) (14 * getResources().getDisplayMetrics().density);

        for (int i = 0; i < accounts.size(); i++) {
            final int index = i;
            Account account = accounts.get(i);

            LinearLayout itemLayout = new LinearLayout(this);
            LinearLayout.LayoutParams itemParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            itemParams.setMargins(0, marginV, 0, marginV);
            itemLayout.setLayoutParams(itemParams);
            itemLayout.setOrientation(LinearLayout.HORIZONTAL);
            itemLayout.setGravity(android.view.Gravity.CENTER_VERTICAL);
            itemLayout.setPadding(paddingContent, paddingContent, paddingContent, paddingContent);
            itemLayout.setBackgroundResource(R.drawable.bg_input_field);

            TextView tvName = new TextView(this);
            tvName.setText(account.name);
            tvName.setTextSize(15);
            tvName.setTextColor(ContextCompat.getColor(this, R.color.text_primary));
            LinearLayout.LayoutParams tvParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            itemLayout.addView(tvName, tvParams);

            TextView btnUp = new TextView(this);
            btnUp.setText("上移");
            btnUp.setTextSize(13);
            btnUp.setPadding(20, 10, 20, 10);
            if (index > 0) {
                btnUp.setTextColor(ContextCompat.getColor(this, R.color.app_accent));
                btnUp.setOnClickListener(v -> {
                    Collections.swap(accounts, index, index - 1);
                    saveAccountSortOrder(accounts);
                    buildSortList(container, accounts);
                });
            } else {
                btnUp.setTextColor(ContextCompat.getColor(this, R.color.button_disabled_text));
            }
            itemLayout.addView(btnUp);

            TextView btnDown = new TextView(this);
            btnDown.setText("下移");
            btnDown.setTextSize(13);
            btnDown.setPadding(20, 10, 10, 10);
            if (index < accounts.size() - 1) {
                btnDown.setTextColor(ContextCompat.getColor(this, R.color.app_accent));
                btnDown.setOnClickListener(v -> {
                    Collections.swap(accounts, index, index + 1);
                    saveAccountSortOrder(accounts);
                    buildSortList(container, accounts);
                });
            } else {
                btnDown.setTextColor(ContextCompat.getColor(this, R.color.button_disabled_text));
            }
            itemLayout.addView(btnDown);

            container.addView(itemLayout);
        }
    }

    private void saveAccountSortOrder(List<Account> accounts) {
        for (int i = 0; i < accounts.size(); i++) {
            accounts.get(i).sortOrder = i;
        }
        viewModel.updateSortOrders(accounts, null);
    }
}
