package com.example.budgetapp.ui;

import android.app.AlertDialog;
import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.budgetapp.R;
import com.example.budgetapp.database.AppDatabase;
import com.example.budgetapp.util.AssetCategoryManager;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;

import java.util.ArrayList;
import java.util.List;

public class AssetCategorySettingsActivity extends AppCompatActivity {

    private SwitchCompat switchAssetCategory;
    private ChipGroup chipGroupAssetCategory;
    private TextView tvHint;
    private List<String> assetCategories = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        getWindow().setStatusBarColor(Color.TRANSPARENT);
        getWindow().setNavigationBarColor(Color.TRANSPARENT);
        setContentView(R.layout.activity_asset_category_settings);

        View rootView = findViewById(R.id.root_view);
        ViewCompat.setOnApplyWindowInsetsListener(rootView, (v, windowInsets) -> {
            Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(insets.left, insets.top, insets.right, insets.bottom);
            return WindowInsetsCompat.CONSUMED;
        });

        initViews();
        loadData();
    }

    private void initViews() {
        switchAssetCategory = findViewById(R.id.switch_asset_category);
        chipGroupAssetCategory = findViewById(R.id.chip_group_asset_category);
        tvHint = findViewById(R.id.tv_asset_category_hint);

        switchAssetCategory.setChecked(AssetCategoryManager.isEnabled(this));
        switchAssetCategory.setOnCheckedChangeListener((buttonView, isChecked) -> {
            AssetCategoryManager.setEnabled(this, isChecked);
            updateEnabledState(isChecked);
        });

        findViewById(R.id.btn_add_asset_category).setOnClickListener(v -> showAddDialog());
    }

    private void loadData() {
        assetCategories = AssetCategoryManager.getCategories(this);
        refreshChips();
        updateEnabledState(AssetCategoryManager.isEnabled(this));
    }

    private void updateEnabledState(boolean enabled) {
        chipGroupAssetCategory.setAlpha(enabled ? 1f : 0.45f);
        findViewById(R.id.btn_add_asset_category).setEnabled(enabled);
        tvHint.setText(enabled ? "点击分类可编辑，长按可删除" : "开启后可添加、编辑资产分类");
    }

    private void refreshChips() {
        chipGroupAssetCategory.removeAllViews();
        for (String category : assetCategories) {
            Chip chip = new Chip(this);
            chip.setText(category);
            chip.setCheckable(false);
            chip.setClickable(true);
            chip.setChipBackgroundColorResource(R.color.cat_unselected_bg);
            chip.setTextColor(ContextCompat.getColor(this, R.color.text_primary));
            chip.setChipStrokeWidth(0);
            chip.setCheckedIconVisible(false);
            chip.setOnClickListener(v -> {
                if (AssetCategoryManager.isEnabled(this)) {
                    showEditDialog(category);
                }
            });
            chip.setOnLongClickListener(v -> {
                if (AssetCategoryManager.isEnabled(this)) {
                    showDeleteDialog(category);
                    return true;
                }
                return false;
            });
            chipGroupAssetCategory.addView(chip);
        }
    }

    private void showAddDialog() {
        if (!AssetCategoryManager.isEnabled(this)) {
            Toast.makeText(this, "请先开启资产分类功能", Toast.LENGTH_SHORT).show();
            return;
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_add_category, null);
        builder.setView(view);
        AlertDialog dialog = builder.create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        TextView tvTitle = view.findViewById(R.id.tv_dialog_title);
        EditText etInput = view.findViewById(R.id.et_category_name);
        Button btnCancel = view.findViewById(R.id.btn_cancel);
        Button btnConfirm = view.findViewById(R.id.btn_confirm);

        tvTitle.setText("添加资产分类");
        btnCancel.setOnClickListener(v -> dialog.dismiss());
        btnConfirm.setOnClickListener(v -> {
            String name = etInput.getText().toString().trim();
            if (name.isEmpty()) {
                Toast.makeText(this, "请输入资产分类名称", Toast.LENGTH_SHORT).show();
                return;
            }
            if (assetCategories.contains(name)) {
                Toast.makeText(this, "该资产分类已存在", Toast.LENGTH_SHORT).show();
                return;
            }
            assetCategories.add(name);
            AssetCategoryManager.saveCategories(this, assetCategories);
            refreshChips();
            dialog.dismiss();
        });

        dialog.show();
    }

    private void showEditDialog(String oldCategory) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_edit_category, null);
        builder.setView(view);
        AlertDialog dialog = builder.create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        TextView tvTitle = view.findViewById(R.id.tv_dialog_title);
        EditText etInput = view.findViewById(R.id.et_category_name);
        Button btnDelete = view.findViewById(R.id.btn_delete);
        Button btnConfirm = view.findViewById(R.id.btn_confirm);

        tvTitle.setText("编辑资产分类");
        etInput.setText(oldCategory);
        etInput.setSelection(oldCategory.length());

        btnDelete.setOnClickListener(v -> {
            dialog.dismiss();
            showDeleteDialog(oldCategory);
        });

        btnConfirm.setOnClickListener(v -> {
            String newCategory = etInput.getText().toString().trim();
            if (newCategory.isEmpty()) {
                Toast.makeText(this, "请输入资产分类名称", Toast.LENGTH_SHORT).show();
                return;
            }
            if (newCategory.equals(oldCategory)) {
                dialog.dismiss();
                return;
            }
            if (assetCategories.contains(newCategory)) {
                Toast.makeText(this, "该资产分类已存在", Toast.LENGTH_SHORT).show();
                return;
            }

            int index = assetCategories.indexOf(oldCategory);
            if (index >= 0) {
                assetCategories.set(index, newCategory);
                AssetCategoryManager.saveCategories(this, assetCategories);
                AppDatabase.databaseWriteExecutor.execute(() ->
                        AppDatabase.getDatabase(getApplicationContext()).assetAccountDao()
                                .updateAssetCategoryName(oldCategory, newCategory));
                refreshChips();
                dialog.dismiss();
            }
        });

        dialog.show();
    }

    private void showDeleteDialog(String category) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_confirm_delete, null);
        builder.setView(view);
        AlertDialog dialog = builder.create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        TextView tvTitle = view.findViewById(R.id.tv_dialog_title);
        TextView tvMsg = view.findViewById(R.id.tv_dialog_message);
        tvTitle.setText("删除资产分类");
        tvMsg.setText("确定要删除 \"" + category + "\" 吗？已使用该分类的资产会清空分类。");

        view.findViewById(R.id.btn_dialog_cancel).setOnClickListener(v -> dialog.dismiss());
        view.findViewById(R.id.btn_dialog_confirm).setOnClickListener(v -> {
            assetCategories.remove(category);
            AssetCategoryManager.saveCategories(this, assetCategories);
            AppDatabase.databaseWriteExecutor.execute(() ->
                    AppDatabase.getDatabase(getApplicationContext()).assetAccountDao()
                            .clearAssetCategory(category));
            refreshChips();
            dialog.dismiss();
        });

        dialog.show();
    }
}
