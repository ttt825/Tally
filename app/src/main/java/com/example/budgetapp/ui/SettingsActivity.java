package com.example.budgetapp.ui;

import android.util.Log;

import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Map;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.ViewModelProvider;

import com.example.budgetapp.BackupData;
import com.example.budgetapp.BackupManager;
import com.example.budgetapp.R;
import com.example.budgetapp.database.Transaction;
import com.example.budgetapp.database.TransactionForDuplicate;
import com.example.budgetapp.utils.CategoryManager;
import com.example.budgetapp.viewmodel.TransactionViewModel;
import com.example.budgetapp.utils.ThreadPoolManager;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public class SettingsActivity extends AppCompatActivity {

    private TransactionViewModel transactionViewModel;

    // --- 查重辅助方法 开始 ---
    private final ActivityResultLauncher<String[]> pickCustomBgLauncher = registerForActivityResult(
            new ActivityResultContracts.OpenDocument(),
            uri -> {
                if (uri != null) {
                    try {
                        // 获取持久化读取权限，保证应用重启后依然可以读取该图片
                        getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);

                        SharedPreferences prefs = getSharedPreferences("app_prefs", MODE_PRIVATE);
                        prefs.edit()
                                .putInt("theme_mode", 3) // 使用 3 代表自定义背景模式
                                .putString("custom_bg_uri", uri.toString())
                                .apply();

                        Toast.makeText(this, "自定义背景已保存，请返回主页查看", Toast.LENGTH_SHORT).show();
                    } catch (SecurityException e) {
                        Log.e("Tally", "Error", e);
                        Toast.makeText(this, "获取图片权限失败", Toast.LENGTH_SHORT).show();
                    }
                }
            }
    );



    private boolean isDuplicateTransaction(Transaction newTx, List<TransactionForDuplicate> existingList) {
        if (existingList == null || existingList.isEmpty()) return false;
        for (TransactionForDuplicate ext : existingList) {
            if (ext.date == newTx.date &&
                    ext.type == newTx.type &&
                    Math.abs(ext.amount - newTx.amount) < 0.01 &&
                    Objects.equals(ext.category, newTx.category) &&
                    Objects.equals(ext.subCategory, newTx.subCategory) &&
                    Objects.equals(ext.note, newTx.note) &&
                    Objects.equals(ext.remark, newTx.remark)) {
                return true;
            }
        }
        return false;
    }
    // --- 查重辅助方法 结束 ---

    private final ActivityResultLauncher<String> exportLauncher = registerForActivityResult(
            new ActivityResultContracts.CreateDocument("application/json"),
            uri -> {
                if (uri != null) {
                    ThreadPoolManager.getInstance().executeBackground(() -> {
                        try {
                            List<Transaction> transactions = transactionViewModel.getAllTransactionsSync();
                            BackupManager.exportToJson(this, uri, transactions);
                            runOnUiThread(() -> Toast.makeText(this, "导出成功", Toast.LENGTH_SHORT).show());
                        } catch (Exception e) {
                            Log.e("Tally", "Error", e);
                            runOnUiThread(() -> Toast.makeText(this, "导出失败: " + e.getMessage(), Toast.LENGTH_LONG).show());
                        }
                    });
                }
            }
    );



    private final ActivityResultLauncher<String> exportExcelLauncher = registerForActivityResult(
            new ActivityResultContracts.CreateDocument("text/csv"),
            uri -> {
                if (uri != null) {
                    ThreadPoolManager.getInstance().executeBackground(() -> {
                        try {
                            List<Transaction> transactions = transactionViewModel.getAllTransactionsSync();
                            BackupManager.exportTransactionsOnly(this, uri, transactions);
                            runOnUiThread(() -> Toast.makeText(this, "账单导出成功", Toast.LENGTH_SHORT).show());
                        } catch (Exception e) {
                            Log.e("Tally", "Error", e);
                            runOnUiThread(() -> Toast.makeText(this, "导出失败: " + e.getMessage(), Toast.LENGTH_LONG).show());
                        }
                    });
                }
            }
    );

    private final ActivityResultLauncher<String[]> importLauncher = registerForActivityResult(
            new ActivityResultContracts.OpenDocument(),
            uri -> {
                if (uri != null) {
                    ThreadPoolManager.getInstance().executeBackground(() -> {
                        try {
                            BackupData data = BackupManager.importFromJson(this, uri);
                            handleImportData(data, "导入");
                        } catch (Exception e) {
                            Log.e("Tally", "Error", e);
                            runOnUiThread(() -> Toast.makeText(this, "导入失败: " + e.getMessage(), Toast.LENGTH_LONG).show());
                        }
                    });
                }
            }
    );

    private final ActivityResultLauncher<String[]> importExcelLauncher = registerForActivityResult(
            new ActivityResultContracts.OpenDocument(),
            uri -> {
                if (uri != null) {
                    ThreadPoolManager.getInstance().executeBackground(() -> {
                        try {
                            BackupData data = BackupManager.importTransactionsCsv(this, uri);
                            handleImportTransactions(data, "CSV导入");
                        } catch (Exception e) {
                            Log.e("Tally", "Error", e);
                            runOnUiThread(() -> Toast.makeText(this, "CSV导入失败: " + e.getMessage(), Toast.LENGTH_LONG).show());
                        }
                    });
                }
            }
    );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        setContentView(R.layout.activity_settings);

        View rootView = findViewById(R.id.settings_root);
        final int originalPaddingLeft = rootView.getPaddingLeft();
        final int originalPaddingTop = rootView.getPaddingTop();
        final int originalPaddingRight = rootView.getPaddingRight();
        final int originalPaddingBottom = rootView.getPaddingBottom();
        ViewCompat.setOnApplyWindowInsetsListener(rootView, (v, windowInsets) -> {
            androidx.core.graphics.Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(originalPaddingLeft + insets.left, originalPaddingTop + insets.top, originalPaddingRight + insets.right, originalPaddingBottom + insets.bottom);
            return WindowInsetsCompat.CONSUMED;
        });

        transactionViewModel = new ViewModelProvider(this).get(TransactionViewModel.class);

        findViewById(R.id.btn_category_setting).setOnClickListener(v -> startActivity(new Intent(this, CategorySettingsActivity.class)));

        findViewById(R.id.btn_backup_restore).setOnClickListener(v -> showBackupOptions());
        findViewById(R.id.btn_toggle_night_mode).setOnClickListener(v -> startActivity(new Intent(this, ThemeSettingsActivity.class)));

        View btnPhotoBackup = findViewById(R.id.btn_photo_backup_setting);
        if (btnPhotoBackup != null) {
            btnPhotoBackup.setOnClickListener(v -> startActivity(new Intent(this, PhotoBackupSettingsActivity.class)));
        }

        View btnAutoBackup = findViewById(R.id.btn_auto_backup_setting);
        if (btnAutoBackup != null) {
            btnAutoBackup.setOnClickListener(v -> startActivity(new Intent(this, AutoBackupSettingsActivity.class)));
        }

        findViewById(R.id.btn_currency_setting).setOnClickListener(v -> {
            startActivity(new Intent(this, CurrencySettingsActivity.class));
        });

        findViewById(R.id.btn_amount_display_setting).setOnClickListener(v -> showAmountDisplayDialog());

        // 新增：点击进入密码与生物识别页面
        findViewById(R.id.btn_security_settings).setOnClickListener(v -> {
            startActivity(new Intent(this, SecuritySettingsActivity.class));
        });

        findViewById(R.id.btn_tab_background_setting).setOnClickListener(v -> showTabBackgroundDialog());

        // 新增：点击进入关于页面
        findViewById(R.id.btn_about).setOnClickListener(v -> {
            startActivity(new Intent(this, AboutActivity.class));
        });

        // 默认激活高级设置，不再显示激活弹窗
        SharedPreferences prefs = getSharedPreferences("app_prefs", MODE_PRIVATE);
        prefs.edit().putBoolean("is_premium_activated", true).apply();
    }

    private void showSaveQrConfirmDialog(int resId, String fileName) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_save_qr, null);
        builder.setView(view);

        AlertDialog dialog = builder.create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        View btnCancel = view.findViewById(R.id.btn_dialog_cancel);
        if (btnCancel == null) btnCancel = view.findViewById(R.id.btn_cancel);

        View btnConfirm = view.findViewById(R.id.btn_dialog_confirm);
        if (btnConfirm == null) btnConfirm = view.findViewById(R.id.btn_confirm);

        if (btnCancel != null) {
            btnCancel.setOnClickListener(v -> dialog.dismiss());
        }

        if (btnConfirm != null) {
            btnConfirm.setOnClickListener(v -> {
                saveImageToGallery(resId, fileName);
                dialog.dismiss();
            });
        }

        dialog.show();
    }

    private final ActivityResultLauncher<String[]> importWeChatLauncher = registerForActivityResult(
            new ActivityResultContracts.OpenDocument(),
            uri -> {
                if (uri != null) {
                    ThreadPoolManager.getInstance().executeBackground(() -> {
                        try {
                            BackupData data = BackupManager.importFromWeChat(this, uri);
                            handleImportData(data, "微信导入");
                        } catch (Exception e) {
                            Log.e("Tally", "Error", e);
                            runOnUiThread(() -> Toast.makeText(this, "微信导入失败: " + e.getMessage(), Toast.LENGTH_LONG).show());
                        }
                    });
                }
            }
    );

    private final ActivityResultLauncher<String[]> importAlipayLauncher = registerForActivityResult(
            new ActivityResultContracts.OpenDocument(),
            uri -> {
                if (uri != null) {
                    ThreadPoolManager.getInstance().executeBackground(() -> {
                        try {
                            BackupData data = BackupManager.importFromAlipay(this, uri);
                            handleImportData(data, "支付宝导入");
                        } catch (Exception e) {
                            Log.e("Tally", "Error", e);
                            runOnUiThread(() -> Toast.makeText(this, "支付宝导入失败: " + e.getMessage(), Toast.LENGTH_LONG).show());
                        }
                    });
                }
            }
    );

    private void handleImportData(BackupData data, String sourceName) {
        int recordCount = 0;

        if (data.expenseCategories != null && !data.expenseCategories.isEmpty()) {
            CategoryManager.saveExpenseCategories(this, data.expenseCategories);
        }
        if (data.incomeCategories != null && !data.incomeCategories.isEmpty()) {
            CategoryManager.saveIncomeCategories(this, data.incomeCategories);
        }

        List<Transaction> newTransactions = new ArrayList<>();
        List<TransactionForDuplicate> currentTransactions = transactionViewModel.getTransactionsForDuplicateSync();
        if (data.records != null && !data.records.isEmpty()) {
            for (Transaction t : data.records) {
                if (!isDuplicateTransaction(t, currentTransactions)) {
                    t.id = 0;
                    newTransactions.add(t);
                    recordCount++;
                }
            }
        }

        if (!newTransactions.isEmpty()) {
            transactionViewModel.insertTransactions(newTransactions, count -> {
                transactionViewModel.triggerBackupAndWidgetUpdate();
            });
        }

        if (recordCount > 0) {
            String msg = String.format("%s成功: %d条账单 (已过滤重复)", sourceName, recordCount);
            runOnUiThread(() -> Toast.makeText(this, msg, Toast.LENGTH_LONG).show());
        } else {
            runOnUiThread(() -> Toast.makeText(this, "所有账单均已存在，或未找到有效数据", Toast.LENGTH_LONG).show());
        }
    }

    private void handleImportTransactions(BackupData data, String sourceName) {
        int recordCount = 0;
        List<Transaction> newTransactions = new ArrayList<>();
        List<TransactionForDuplicate> currentTransactions = transactionViewModel.getTransactionsForDuplicateSync();
        if (data.records != null && !data.records.isEmpty()) {
            for (Transaction t : data.records) {
                if (!isDuplicateTransaction(t, currentTransactions)) {
                    t.id = 0;
                    newTransactions.add(t);
                    recordCount++;
                }
            }
        }
        if (!newTransactions.isEmpty()) {
            transactionViewModel.insertTransactions(newTransactions, count -> {
                transactionViewModel.triggerBackupAndWidgetUpdate();
            });
        }
        if (recordCount > 0) {
            String msg = String.format("%s成功: %d条账单 (已过滤重复)", sourceName, recordCount);
            runOnUiThread(() -> Toast.makeText(this, msg, Toast.LENGTH_LONG).show());
        } else {
            runOnUiThread(() -> Toast.makeText(this, "所有账单均已存在，或未找到有效数据", Toast.LENGTH_LONG).show());
        }
    }

    @SuppressWarnings("deprecation")
    private void saveImageToGallery(int resId, String fileName) {
        try {
            android.graphics.Bitmap bitmap = android.graphics.BitmapFactory.decodeResource(getResources(), resId);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                android.content.ContentValues values = new android.content.ContentValues();
                values.put(android.provider.MediaStore.Images.Media.DISPLAY_NAME, fileName);
                values.put(android.provider.MediaStore.Images.Media.MIME_TYPE, "image/png");
                values.put(android.provider.MediaStore.Images.Media.RELATIVE_PATH, android.os.Environment.DIRECTORY_PICTURES);
                android.net.Uri uri = getContentResolver().insert(
                        android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
                if (uri != null) {
                    java.io.OutputStream os = getContentResolver().openOutputStream(uri);
                    if (os != null) {
                        bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, os);
                        os.close();
                    }
                    Toast.makeText(this, "二维码已保存到相册", Toast.LENGTH_SHORT).show();
                }
            } else {
                String savedUri = android.provider.MediaStore.Images.Media.insertImage(
                        getContentResolver(), bitmap, fileName, "Scan to pay");
                if (savedUri != null) {
                    Toast.makeText(this, "二维码已保存到相册", Toast.LENGTH_SHORT).show();
                }
            }
        } catch (Exception e) {
            Toast.makeText(this, "保存失败", Toast.LENGTH_SHORT).show();
        }
    }

    private void showBackupOptions() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_backup_options, null);
        builder.setView(view);
        AlertDialog dialog = builder.create();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        view.findViewById(R.id.tv_export).setOnClickListener(v -> {
            String timeStr = com.example.budgetapp.utils.DateUtils.formatBackupTimestamp(System.currentTimeMillis());
            String fileName = "Tally " + timeStr + ".json";
            exportLauncher.launch(fileName);
            dialog.dismiss();
        });

        view.findViewById(R.id.tv_export_excel).setOnClickListener(v -> {
            String timeStr = com.example.budgetapp.utils.DateUtils.formatExportTimestamp(System.currentTimeMillis());
            String fileName = "Tally_账单_" + timeStr + ".csv";
            exportExcelLauncher.launch(fileName);
            dialog.dismiss();
        });

        view.findViewById(R.id.tv_import).setOnClickListener(v -> {
            importLauncher.launch(new String[]{"application/json", "*/*"});
            dialog.dismiss();
        });

        view.findViewById(R.id.tv_import_excel).setOnClickListener(v -> {
            importExcelLauncher.launch(new String[]{
                    "text/csv",
                    "text/plain",
                    "application/vnd.ms-excel",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    "*/*"
            });
            dialog.dismiss();
        });

        view.findViewById(R.id.tv_import_wechat).setOnClickListener(v -> {
            importWeChatLauncher.launch(new String[]{
                    "text/csv",
                    "text/plain",
                    "application/vnd.ms-excel",
                    "*/*"
            });
            dialog.dismiss();
        });

        view.findViewById(R.id.tv_import_alipay).setOnClickListener(v -> {
            importAlipayLauncher.launch(new String[]{
                    "text/csv",
                    "text/plain",
                    "application/vnd.ms-excel",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    "*/*"
            });
            dialog.dismiss();
        });

        view.findViewById(R.id.btn_cancel_backup).setOnClickListener(v -> dialog.dismiss());

        dialog.show();
    }

    private void showAmountDisplayDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_default_display_settings, null);
        builder.setView(view);
        AlertDialog dialog = builder.create();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        SharedPreferences prefs = getSharedPreferences("app_prefs", MODE_PRIVATE);
        int currentMode = prefs.getInt("default_record_mode", 0);

        android.widget.RadioGroup rgDisplay = view.findViewById(R.id.rg_default_display);
        android.widget.RadioButton rbBalance = view.findViewById(R.id.rb_display_balance);
        android.widget.RadioButton rbIncome = view.findViewById(R.id.rb_display_income);
        android.widget.RadioButton rbExpense = view.findViewById(R.id.rb_display_expense);

        switch (currentMode) {
            case 0: rbBalance.setChecked(true); break;
            case 1: rbIncome.setChecked(true); break;
            case 2: rbExpense.setChecked(true); break;
        }

        rgDisplay.setOnCheckedChangeListener((group, checkedId) -> {
            int mode;
            if (checkedId == R.id.rb_display_balance) {
                mode = 0;
            } else if (checkedId == R.id.rb_display_income) {
                mode = 1;
            } else {
                mode = 2;
            }

            prefs.edit().putInt("default_record_mode", mode).apply();
            Toast.makeText(this, "金额显示设置已保存", Toast.LENGTH_SHORT).show();
            dialog.dismiss();
        });

        view.findViewById(R.id.btn_cancel_display).setOnClickListener(v -> dialog.dismiss());

        dialog.show();
    }

    private void showTabBackgroundDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_tab_background_settings, null);
        builder.setView(view);
        AlertDialog dialog = builder.create();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        SharedPreferences prefs = getSharedPreferences("app_prefs", MODE_PRIVATE);

        int defaultBlur = prefs.getInt("tab_blur_level", 5);
        int defaultCorner = prefs.getInt("tab_corner_radius", 50);
        int defaultOpacity = prefs.getInt("tab_opacity", 80);
        int defaultShadowSize = prefs.getInt("tab_shadow_size", 1);
        int defaultShadowOpacity = prefs.getInt("tab_shadow_opacity", 25);

        SeekBar seekBlur = view.findViewById(R.id.seek_blur);
        TextView tvBlurValue = view.findViewById(R.id.tv_blur_value);
        SeekBar seekCorner = view.findViewById(R.id.seek_corner);
        TextView tvCornerValue = view.findViewById(R.id.tv_corner_value);
        SeekBar seekOpacity = view.findViewById(R.id.seek_opacity);
        TextView tvOpacityValue = view.findViewById(R.id.tv_opacity_value);
        SeekBar seekShadowSize = view.findViewById(R.id.seek_shadow_size);
        TextView tvShadowSizeValue = view.findViewById(R.id.tv_shadow_size_value);
        SeekBar seekShadowOpacity = view.findViewById(R.id.seek_shadow_opacity);
        TextView tvShadowOpacityValue = view.findViewById(R.id.tv_shadow_opacity_value);

        seekBlur.setProgress(defaultBlur - 1);
        tvBlurValue.setText(String.valueOf(defaultBlur));
        seekCorner.setProgress(defaultCorner);
        tvCornerValue.setText(defaultCorner + "dp");
        seekOpacity.setProgress(defaultOpacity);
        tvOpacityValue.setText(defaultOpacity + "%");
        seekShadowSize.setProgress(defaultShadowSize);
        tvShadowSizeValue.setText(String.format("%.1f", defaultShadowSize * 0.5f) + "dp");
        seekShadowOpacity.setProgress(defaultShadowOpacity);
        tvShadowOpacityValue.setText(defaultShadowOpacity + "%");

        seekBlur.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                tvBlurValue.setText(String.valueOf(progress + 1));
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        seekCorner.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                tvCornerValue.setText(progress + "dp");
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        seekOpacity.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                tvOpacityValue.setText(progress + "%");
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        seekShadowSize.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                tvShadowSizeValue.setText(String.format("%.1f", progress * 0.5f) + "dp");
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        seekShadowOpacity.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                tvShadowOpacityValue.setText(progress + "%");
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        view.findViewById(R.id.btn_confirm).setOnClickListener(v -> {
            int blurLevel = seekBlur.getProgress() + 1;
            int cornerRadius = seekCorner.getProgress();
            int opacity = seekOpacity.getProgress();
            int shadowSize = seekShadowSize.getProgress();
            int shadowOpacity = seekShadowOpacity.getProgress();

            prefs.edit()
                    .putInt("tab_blur_level", blurLevel)
                    .putInt("tab_corner_radius", cornerRadius)
                    .putInt("tab_opacity", opacity)
                    .putInt("tab_shadow_size", shadowSize)
                    .putInt("tab_shadow_opacity", shadowOpacity)
                    .apply();

            Toast.makeText(this, "Tab背景设置已保存", Toast.LENGTH_SHORT).show();
            dialog.dismiss();
        });

        view.findViewById(R.id.btn_cancel).setOnClickListener(v -> dialog.dismiss());

        dialog.show();
    }

}