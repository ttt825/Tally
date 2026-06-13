package com.example.budgetapp.service;

import android.util.Log;

import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.ResultReceiver;
import android.provider.Settings;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;
import android.view.ContextThemeWrapper;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.budgetapp.R;
import com.example.budgetapp.database.AppDatabase;
import com.example.budgetapp.database.Transaction;
import com.example.budgetapp.ui.CategoryAdapter;
import com.example.budgetapp.ui.PhotoActionActivity;
import com.example.budgetapp.ui.TransactionDialogHelper;
import com.example.budgetapp.utils.CategoryManager;

import com.example.budgetapp.utils.DateUtils;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class QuickAddTileService extends TileService {

    private boolean isWindowShowing = false;
    private View windowRootView; // 【新增】持有悬浮窗根视图引用
    private String selectedSubCategory = null;

    @Override
    public void onStartListening() {
        super.onStartListening();
        Tile tile = getQsTile();
        if (tile != null) {
            tile.setState(Tile.STATE_INACTIVE);
            tile.updateTile();
        }
    }

    @Override
    public void onClick() {
        super.onClick();
        Tile tile = getQsTile();
        if (tile != null) {
            tile.setState(Tile.STATE_ACTIVE);
            tile.updateTile();
        }

        if (isLocked()) {
            unlockAndRun(this::checkPermissionAndShowWindow);
        } else {
            checkPermissionAndShowWindow();
        }
    }

    @SuppressWarnings("deprecation")
    private void checkPermissionAndShowWindow() {
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "需要悬浮窗权限才能使用快捷记账", Toast.LENGTH_LONG).show();
            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION);
            intent.setData(Uri.parse("package:" + getPackageName()));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            if (Build.VERSION.SDK_INT >= 34) {
                PendingIntent pendingIntent = PendingIntent.getActivity(
                        this,
                        0,
                        intent,
                        PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT
                );
                startActivityAndCollapse(pendingIntent);
            } else {
                startActivityAndCollapse(intent);
            }
            return;
        }
        showConfirmWindow();
    }

    private void showConfirmWindow() {
        if (isWindowShowing) return;
        selectedSubCategory = null;

        try {
            WindowManager windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
            WindowManager.LayoutParams params = new WindowManager.LayoutParams();

            params.type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
            params.format = PixelFormat.TRANSLUCENT;
            params.width = WindowManager.LayoutParams.MATCH_PARENT;
            params.height = WindowManager.LayoutParams.MATCH_PARENT;
            params.flags = WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | WindowManager.LayoutParams.FLAG_DIM_BEHIND;
            params.dimAmount = 0.5f;
            params.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN;
            params.gravity = Gravity.CENTER;
            params.y = -350;

            ContextThemeWrapper themeContext = new ContextThemeWrapper(this, R.style.Theme_BudgetApp);
            LayoutInflater inflater = LayoutInflater.from(themeContext);
            View floatView = inflater.inflate(R.layout.window_confirm_transaction, null);

            this.windowRootView = floatView;
            android.widget.FrameLayout windowContentRoot = floatView.findViewById(R.id.window_root);

            View rootView = floatView.findViewById(R.id.window_root);
            if (rootView != null) {
                rootView.setOnClickListener(v -> closeWindow(windowManager, floatView));
            }
            View cardContent = floatView.findViewById(R.id.window_card_content);
            if (cardContent != null) {
                cardContent.setOnClickListener(v -> { /* 拦截 */ });
            }

            isWindowShowing = true;

            EditText etAmount = floatView.findViewById(R.id.et_window_amount);
            RadioGroup rgType = floatView.findViewById(R.id.rg_window_type);
            RecyclerView rvCategory = floatView.findViewById(R.id.rv_window_category);
            EditText etCategory = floatView.findViewById(R.id.et_window_category);
            EditText etNote = floatView.findViewById(R.id.et_window_note);
            EditText etRemark = floatView.findViewById(R.id.et_window_remark);
            Button btnCurrency = floatView.findViewById(R.id.btn_window_currency);

            Button btnSave = floatView.findViewById(R.id.btn_window_save);
            Button btnCancel = floatView.findViewById(R.id.btn_window_cancel);

            Button btnTakePhoto = floatView.findViewById(R.id.btn_window_take_photo);
            Button btnViewPhoto = floatView.findViewById(R.id.btn_window_view_photo);

            EditText etTarget = floatView.findViewById(R.id.et_window_target);

            etAmount.setText("");
            etAmount.requestFocus();
            rgType.check(R.id.rb_window_expense);

            SharedPreferences prefs = getSharedPreferences("app_prefs", MODE_PRIVATE);
            boolean isCurrencyEnabled = prefs.getBoolean("enable_currency", false);
            boolean isPhotoBackupEnabled = prefs.getBoolean("enable_photo_backup", false);
            final String[] currentPhotoPath = {null};

            if (isCurrencyEnabled) {
                btnCurrency.setVisibility(View.VISIBLE);
                String defaultSymbol = prefs.getString("default_currency_symbol", "¥");
                btnCurrency.setText(defaultSymbol);
                btnCurrency.setOnClickListener(v -> {
                    com.example.budgetapp.utils.CurrencyUtils.showCurrencyDialog(themeContext, btnCurrency, true);
                });
            } else {
                btnCurrency.setVisibility(View.GONE);
            }

            if (isPhotoBackupEnabled) {
                btnTakePhoto.setVisibility(View.VISIBLE);
                btnTakePhoto.setOnClickListener(v -> {
                    showLocalPhotoDialog(themeContext, windowContentRoot, actionType -> {
                        hideWindowAndStartPhotoActivity(actionType, null, currentPhotoPath);
                    });
                });

                btnViewPhoto.setOnClickListener(v -> {
                    if (currentPhotoPath[0] != null) {
                        hideWindowAndStartPhotoActivity(PhotoActionActivity.ACTION_VIEW, currentPhotoPath[0], currentPhotoPath);
                    }
                });
            }

            List<String> expenseCategories = CategoryManager.getExpenseCategories(this);
            List<String> incomeCategories = CategoryManager.getIncomeCategories(this);

// 动态判断：如果是详细分类，则使用弹性流式布局；否则恢复 5 列网格布局
            boolean isDetailed = com.example.budgetapp.utils.CategoryManager.isDetailedCategoryEnabled(this);
            if (isDetailed) {
                com.google.android.flexbox.FlexboxLayoutManager flexboxLayoutManager = new com.google.android.flexbox.FlexboxLayoutManager(themeContext);
                flexboxLayoutManager.setFlexWrap(com.google.android.flexbox.FlexWrap.WRAP);
                flexboxLayoutManager.setFlexDirection(com.google.android.flexbox.FlexDirection.ROW);
                flexboxLayoutManager.setJustifyContent(com.google.android.flexbox.JustifyContent.FLEX_START);
                rvCategory.setLayoutManager(flexboxLayoutManager);
            } else {
                rvCategory.setLayoutManager(new GridLayoutManager(themeContext, 5));
            }

            final String[] selectedCategory = {expenseCategories.isEmpty() ? "自定义" : expenseCategories.get(0)};

            CategoryAdapter categoryAdapter = new CategoryAdapter(themeContext, expenseCategories, selectedCategory[0], cat -> {
                selectedCategory[0] = cat;
                selectedSubCategory = null;
                if ("自定义".equals(cat)) {
                    etCategory.setVisibility(View.VISIBLE);
                    etCategory.requestFocus();
                } else {
                    etCategory.setVisibility(View.GONE);
                }
            });

            categoryAdapter.setOnCategoryLongClickListener(cat -> {
                if (CategoryManager.isSubCategoryEnabled(this) && !"自定义".equals(cat)) {
                    if (!cat.equals(selectedCategory[0])) {
                        categoryAdapter.setSelectedCategory(cat);
                        selectedCategory[0] = cat;
                        selectedSubCategory = null;
                        etCategory.setVisibility(View.GONE);
                    }
                    showSubCategoryDialog(themeContext, cat, categoryAdapter);
                    return true;
                }
                return false;
            });

            rvCategory.setAdapter(categoryAdapter);

            rgType.setOnCheckedChangeListener((group, checkedId) -> {
                if (checkedId == R.id.rb_window_income) {
                    rvCategory.setVisibility(View.VISIBLE);
                    etTarget.setVisibility(View.GONE);
                    categoryAdapter.updateData(incomeCategories);
                    String first = incomeCategories.isEmpty() ? "自定义" : incomeCategories.get(0);
                    categoryAdapter.setSelectedCategory(first);
                    selectedCategory[0] = first;
                    selectedSubCategory = null;
                    etCategory.setVisibility("自定义".equals(first) ? View.VISIBLE : View.GONE);
                } else if (checkedId == R.id.rb_window_expense) {
                    rvCategory.setVisibility(View.VISIBLE);
                    etTarget.setVisibility(View.GONE);
                    categoryAdapter.updateData(expenseCategories);
                    String first = expenseCategories.isEmpty() ? "自定义" : expenseCategories.get(0);
                    categoryAdapter.setSelectedCategory(first);
                    selectedCategory[0] = first;
                    selectedSubCategory = null;
                    etCategory.setVisibility("自定义".equals(first) ? View.VISIBLE : View.GONE);
                } else if (checkedId == R.id.rb_window_liability) {
                    rvCategory.setVisibility(View.GONE);
                    etCategory.setVisibility(View.GONE);
                    etTarget.setVisibility(View.VISIBLE);
                    etTarget.setHint("借入对象");
                    selectedCategory[0] = "借入";
                } else if (checkedId == R.id.rb_window_loan) {
                    rvCategory.setVisibility(View.GONE);
                    etCategory.setVisibility(View.GONE);
                    etTarget.setVisibility(View.VISIBLE);
                    etTarget.setHint("借出对象");
                    selectedCategory[0] = "借出"; // 借出对应资金流出
                }
            });

            etNote.setText(DateUtils.formatNoteTime(System.currentTimeMillis()));

            btnSave.setOnClickListener(v -> {
                String amountStr = etAmount.getText().toString();
                if (amountStr.isEmpty()) {
                    Toast.makeText(this, "请输入金额", Toast.LENGTH_SHORT).show();
                    return;
                }
                try {
                    double finalAmount = Double.parseDouble(amountStr);
                    if (finalAmount <= 0 || finalAmount > 99999999.99) {
                        Toast.makeText(this, "金额范围：0.01 ~ 99,999,999.99", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    String finalNote = etNote.getText().toString();
                    String finalRemark = etRemark.getText().toString().trim();

                    int checkedId = rgType.getCheckedRadioButtonId();
                    int finalType = 0;
                    if (checkedId == R.id.rb_window_income) finalType = 1;
                    else if (checkedId == R.id.rb_window_liability) finalType = 3;
                    else if (checkedId == R.id.rb_window_loan) finalType = 4;

                    String finalCat = selectedCategory[0];
                    String targetObject = null;
                    int liabilityLoanType = -1;

                    if (checkedId == R.id.rb_window_liability) {
                        targetObject = etTarget.getText().toString().trim();
                        if (targetObject.isEmpty()) { Toast.makeText(this, "请输入借入对象", Toast.LENGTH_SHORT).show(); return; }
                        liabilityLoanType = 1;
                    } else if (checkedId == R.id.rb_window_loan) {
                        targetObject = etTarget.getText().toString().trim();
                        if (targetObject.isEmpty()) { Toast.makeText(this, "请输入借出对象", Toast.LENGTH_SHORT).show(); return; }
                        liabilityLoanType = 2;
                    } else if ("自定义".equals(finalCat)) {
                        String customInput = etCategory.getText().toString().trim();
                        if (!customInput.isEmpty()) {
                            finalCat = customInput;
                        } else {
                            Toast.makeText(this, "请输入自定义分类", Toast.LENGTH_SHORT).show();
                            return;
                        }
                    }

                    String symbol = isCurrencyEnabled ? btnCurrency.getText().toString() : "¥";

                    // 【修改】将 targetObject 和 liabilityLoanType 传给底层方法
                    saveToDatabase(finalAmount, finalType, finalCat, selectedSubCategory, finalNote, finalRemark, symbol, currentPhotoPath[0], targetObject, liabilityLoanType);
                    closeWindow(windowManager, floatView);
                    Toast.makeText(this, "记账成功", Toast.LENGTH_SHORT).show();
                } catch (Exception e) {
                    Toast.makeText(this, "金额格式错误", Toast.LENGTH_SHORT).show();
                }
            });

            btnCancel.setOnClickListener(v -> closeWindow(windowManager, floatView));
            windowManager.addView(floatView, params);

        } catch (Exception e) {
            Log.e("Tally", "Error", e);
        }
    }
    // --- 新增辅助方法 START ---

    interface PhotoActionResult {
        void onAction(int type);
    }

    private void showLocalPhotoDialog(Context context, android.widget.FrameLayout root, PhotoActionResult listener) {
        View dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_photo_action, root, false);
        
        // 背景遮罩
        View mask = new View(context);
        mask.setBackgroundColor(ContextCompat.getColor(context, R.color.scrim_dark));
        mask.setOnClickListener(v -> {
            root.removeView(mask);
            root.removeView(dialogView);
        });
        
        android.widget.FrameLayout.LayoutParams params = new android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT, 
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT);
        root.addView(mask, params);

        android.view.ViewGroup.LayoutParams lp = dialogView.getLayoutParams();
        android.widget.FrameLayout.LayoutParams dialogParams = new android.widget.FrameLayout.LayoutParams(
                lp.width,
                lp.height);
        dialogParams.gravity = Gravity.CENTER;
        root.addView(dialogView, dialogParams);

        // 绑定事件
        dialogView.findViewById(R.id.btn_action_camera).setOnClickListener(v -> {
            root.removeView(mask);
            root.removeView(dialogView);
            listener.onAction(PhotoActionActivity.ACTION_CAMERA);
        });

        dialogView.findViewById(R.id.btn_action_gallery).setOnClickListener(v -> {
            root.removeView(mask);
            root.removeView(dialogView);
            listener.onAction(PhotoActionActivity.ACTION_GALLERY);
        });

        dialogView.findViewById(R.id.btn_action_cancel).setOnClickListener(v -> {
            root.removeView(mask);
            root.removeView(dialogView);
        });
    }

    private void hideWindowAndStartPhotoActivity(int actionType, String uri, String[] currentPhotoPathRef) {
        // 1. 隐藏悬浮窗
        if (windowRootView != null) windowRootView.setVisibility(View.GONE);

        Intent intent = new Intent(this, PhotoActionActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.putExtra(PhotoActionActivity.EXTRA_ACTION_TYPE, actionType);
        if (uri != null) {
            intent.putExtra(PhotoActionActivity.EXTRA_IMAGE_URI, uri);
        }
        
        intent.putExtra(PhotoActionActivity.EXTRA_RECEIVER, new ResultReceiver(new Handler(Looper.getMainLooper())) {
            @Override
            protected void onReceiveResult(int resultCode, Bundle resultData) {
                // 2. 恢复悬浮窗
                if (windowRootView != null) windowRootView.setVisibility(View.VISIBLE);
                
                if (resultCode == 1 && resultData != null) {
                    String resultUri = resultData.getString(PhotoActionActivity.KEY_RESULT_URI);
                    if (currentPhotoPathRef != null) {
                        currentPhotoPathRef[0] = resultUri;
                    }
                    if (resultUri != null && windowRootView != null) {
                        Button btnView = windowRootView.findViewById(R.id.btn_window_view_photo);
                        if (btnView != null) btnView.setVisibility(View.VISIBLE);
                    }
                }
            }
        });
        startActivity(intent);
    }
    // --- 新增辅助方法 END ---

    private void showSubCategoryDialog(Context context, String parentCategory, CategoryAdapter adapter) {
        AlertDialog subCatDialog = TransactionDialogHelper.createSubCategoryDialog(
                this, parentCategory, selectedSubCategory, true,
                subCat -> {
                    if (subCat == null) {
                        selectedSubCategory = null;
                        Toast.makeText(this, "已取消细分", Toast.LENGTH_SHORT).show();
                    } else {
                        selectedSubCategory = subCat;
                        Toast.makeText(this, "已选择: " + subCat, Toast.LENGTH_SHORT).show();
                    }
                    if (adapter != null) {
                        adapter.setSelectedCategory(parentCategory);
                    }
                });
        subCatDialog.show();
    }

    private void closeWindow(WindowManager wm, View view) {
        try {
            if (view != null && wm != null) wm.removeView(view);
        } catch (Exception e) {
            Log.e("Tally", "Error", e);
        } finally {
            isWindowShowing = false;
            windowRootView = null; // 清理引用
        }
    }

    private void saveToDatabase(double amount, int type, String category, String subCategory, String note, String remark, String currencySymbol, String photoPath, String targetObject, int liabilityLoanType) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            AppDatabase db = AppDatabase.getDatabase(getApplicationContext());

            Transaction t = new Transaction();
            t.date = System.currentTimeMillis();
            t.type = type;
            t.category = category;
            t.subCategory = subCategory;
            t.amount = amount;
            t.note = note;
            t.remark = remark;
            t.currencySymbol = currencySymbol;
            t.photoPath = photoPath;
            t.targetObject = targetObject;
            db.transactionDao().insert(t);

            // 👇👇👇 一键刷新所有桌面小组件 👇👇👇
            com.example.budgetapp.widget.WidgetUtils.updateAllWidgets(getApplicationContext());

        }); // 这里是 execute 的结尾大括号
    }
}