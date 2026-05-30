package com.example.budgetapp;

import android.util.Log;

import android.appwidget.AppWidgetManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.view.MotionEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.PopupMenu;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;
import androidx.navigation.ui.NavigationUI;

import com.example.budgetapp.database.Transaction;
import com.example.budgetapp.widget.TodaySummaryWidget;
import com.example.budgetapp.ui.SettingsActivity;
import com.example.budgetapp.viewmodel.TransactionViewModel;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import me.ibrahimsn.lib.SmoothBottomBar;
import eightbitlab.com.blurview.BlurView;
import eightbitlab.com.blurview.RenderScriptBlur;

import java.util.ArrayList;
import java.util.List;

import androidx.activity.OnBackPressedCallback;

public class MainActivity extends AppCompatActivity {

    private TransactionViewModel transactionViewModel;
    private eightbitlab.com.blurview.BlurView blurTabBar;
    // 【修复】移除全量数据缓存，改为导出时按需读取，避免内存占用过大

    // 双击返回退出功能
    private long backPressedTime = 0;
    private static final int TIME_INTERVAL = 2000; // 2秒间隔

    // 用于跟踪当前被按下的Tab，避免与选中动画冲突



    // 导出功能保留在此处作为备份逻辑
    private final ActivityResultLauncher<String> exportLauncher = registerForActivityResult(
            new ActivityResultContracts.CreateDocument("application/json"),
            uri -> {
                if (uri != null) {
                    new Thread(() -> {
                        try {
                            List<Transaction> transactions = transactionViewModel.getAllTransactionsSync();
                            BackupManager.exportToJson(this, uri, transactions);
                            runOnUiThread(() -> Toast.makeText(this, "导出成功", Toast.LENGTH_SHORT).show());
                        } catch (Exception e) {
                            Log.e("Tally", "Error", e);
                            runOnUiThread(() -> Toast.makeText(this, "导出失败: " + e.getMessage(), Toast.LENGTH_LONG).show());
                        }
                    }).start();
                }
            }
    );

    // 导入功能保留在此处作为备份逻辑
    private final ActivityResultLauncher<String[]> importLauncher = registerForActivityResult(
            new ActivityResultContracts.OpenDocument(),
            uri -> {
                if (uri != null) {
                    // 【修复】导入使用批量插入，避免逐条触发备份计数
                    new Thread(() -> {
                        try {
                            BackupData data = BackupManager.importFromJson(this, uri);
                            
                            if (data.records != null && !data.records.isEmpty()) {
                                for (Transaction t : data.records) {
                                    t.id = 0;
                                }
                                transactionViewModel.insertTransactionsSync(data.records, count -> {
                                    runOnUiThread(() -> {
                                        if (count > 0) {
                                            Toast.makeText(this, 
                                                String.format("成功导入: %d条账单", count), 
                                                Toast.LENGTH_LONG).show();
                                        } else {
                                            Toast.makeText(this, "备份文件中未发现数据", Toast.LENGTH_SHORT).show();
                                        }
                                    });
                                });
                            } else {
                                runOnUiThread(() -> Toast.makeText(this, "备份文件中未发现数据", Toast.LENGTH_SHORT).show());
                            }
                        } catch (Exception e) {
                            Log.e("Tally", "Error", e);
                            runOnUiThread(() -> Toast.makeText(this, "导入失败: " + e.getMessage(), Toast.LENGTH_LONG).show());
                        }
                    }).start();
                }
            }
    );

    @Override
    @SuppressWarnings("deprecation")
    protected void onCreate(Bundle savedInstanceState) {
        // 【修改】适配双背景组合逻辑
        SharedPreferences prefs = getSharedPreferences("app_prefs", MODE_PRIVATE);
        // 【修复】使用安全的读取方法，防止老版本导入导致 ClassCastException
        int themeMode = getSafeInt(prefs, "theme_mode", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
        int delegateMode = themeMode;
        if (themeMode == 3) {
            String dayUri = prefs.getString("custom_bg_day_uri", null);
            String nightUri = prefs.getString("custom_bg_night_uri", null);
            if (dayUri != null && nightUri == null) {
                delegateMode = AppCompatDelegate.MODE_NIGHT_NO; // 只有日间图片，全局锁死日间模式
            } else if (nightUri != null && dayUri == null) {
                delegateMode = AppCompatDelegate.MODE_NIGHT_YES; // 只有夜间图片，全局锁死夜间模式
            } else {
                delegateMode = AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM; // 都有，跟随系统自动切换
            }
        }
        AppCompatDelegate.setDefaultNightMode(delegateMode);

        super.onCreate(savedInstanceState);

        // 【关键新增】允许内容延伸到状态栏和导航栏区域
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);

        // 【关键新增】：强制状态栏颜色为透明，确保自定义图片能完美沉浸到状态栏区域
        getWindow().setStatusBarColor(android.graphics.Color.TRANSPARENT);

        setContentView(R.layout.activity_main);

        transactionViewModel = new ViewModelProvider(this).get(TransactionViewModel.class);
        
        // 【修复】移除全量数据观察，避免内存占用过大

        View rootLayout = findViewById(R.id.root_layout);
        View contentLayout = findViewById(R.id.content_layout);
        SmoothBottomBar bottomBar = findViewById(R.id.bottomBar);

        BlurView blurTabBar = findViewById(R.id.blur_tab_bar);
        this.blurTabBar = blurTabBar;
        @SuppressWarnings("deprecation")
        var ignored = blurTabBar.setupWith((ViewGroup) rootLayout, new RenderScriptBlur(this));
        blurTabBar.setOutlineProvider(ViewOutlineProvider.BACKGROUND);
        blurTabBar.setClipToOutline(true);

        applyTabBackgroundSettings(blurTabBar);

        // 初始化时应用背景
        applyCustomBackground();

        // 显示首次打开引导提示
        showLongPressHintIfNeeded();

        // 【修改】将WindowInsets监听器设置在content_layout上，避免影响遮罩层的覆盖范围
        ViewCompat.setOnApplyWindowInsetsListener(contentLayout, (v, windowInsets) -> {
            Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(0, insets.top, 0, 0);
            return windowInsets;
        });

        ViewCompat.setOnApplyWindowInsetsListener(bottomBar, (v, windowInsets) -> {
            Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(v.getPaddingLeft(), v.getPaddingTop(), v.getPaddingRight(), insets.bottom);
            return WindowInsetsCompat.CONSUMED;
        });

        NavHostFragment navHostFragment = (NavHostFragment) getSupportFragmentManager()
                .findFragmentById(R.id.nav_host_fragment);

        if (navHostFragment != null) {
            NavController navController = navHostFragment.getNavController();

            // 设置SmoothBottomBar与Navigation Component的集成
            PopupMenu popupMenu = new PopupMenu(this, null);
            popupMenu.getMenuInflater().inflate(R.menu.bottom_menu, popupMenu.getMenu());
            bottomBar.setupWithNavController(popupMenu.getMenu(), navController);

            // 获取菜单并根据配置隐藏明细页面（如果需要的话）
            Menu menu = popupMenu.getMenu();
            MenuItem detailsItem = menu.findItem(R.id.nav_details);
            
            if (detailsItem != null) {
                detailsItem.setVisible(true);
            }

            // 【新增】拦截返回手势逻辑
            getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
                @Override
                public void handleOnBackPressed() {
                    if (navController.getCurrentDestination() != null) {
                        int currentId = navController.getCurrentDestination().getId();
                        // 主页面，实现双击退出功能
                        if (currentId == R.id.nav_record || currentId == R.id.nav_stats ||
                                currentId == R.id.nav_details) {
                            // 主页面，实现双击退出功能
                            if (backPressedTime + TIME_INTERVAL > System.currentTimeMillis()) {
                                // 2秒内再次按下返回键，退出应用
                                finish();
                            } else {
                                // 第一次按下返回键，显示提示
                                backPressedTime = System.currentTimeMillis();
                                Toast.makeText(MainActivity.this, "再按一次退出应用", Toast.LENGTH_SHORT).show();
                            }
                        } else {
                            // 其他页面，正常返回
                            if (!navController.popBackStack()) {
                                finish();
                            }
                        }
                    } else {
                        finish();
                    }
                }
            });
        }

    }

    private void applyTabBackgroundSettings(eightbitlab.com.blurview.BlurView blurTabBar) {
        SharedPreferences prefs = getSharedPreferences("app_prefs", MODE_PRIVATE);
        int blurLevel = prefs.getInt("tab_blur_level", 5);
        int cornerRadius = prefs.getInt("tab_corner_radius", 50);
        int opacity = prefs.getInt("tab_opacity", 80);
        int shadowSize = prefs.getInt("tab_shadow_size", 1);
        int shadowOpacity = prefs.getInt("tab_shadow_opacity", 25);

        float actualRadius = blurLevel;
        blurTabBar.setBlurRadius(actualRadius);

        int alphaInt = (int) (opacity / 100f * 255);
        android.graphics.drawable.GradientDrawable roundedBg = new android.graphics.drawable.GradientDrawable();
        roundedBg.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
        roundedBg.setCornerRadius(cornerRadius);
        roundedBg.setColor((alphaInt << 24) | 0x00FFFFFF);
        blurTabBar.setBackground(roundedBg);

        View container = findViewById(R.id.tab_bar_container);
        if (container != null) {
            float density = getResources().getDisplayMetrics().density;
            float shadowSizeDp = shadowSize * 0.5f;
            if (shadowSize > 0 && shadowOpacity > 0) {
                int shadowPx = (int) (shadowSizeDp * density);
                int shadowAlpha = (int) (shadowOpacity / 100f * 255);
                android.graphics.drawable.GradientDrawable shadowDrawable = new android.graphics.drawable.GradientDrawable();
                shadowDrawable.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
                shadowDrawable.setCornerRadius(cornerRadius + shadowSizeDp);
                shadowDrawable.setColor((shadowAlpha << 24) | 0x000000);
                container.setBackground(shadowDrawable);
                container.setPadding(shadowPx, shadowPx / 2, shadowPx, shadowPx * 2);
            } else {
                container.setBackground(null);
                container.setPadding(0, 0, 0, 0);
            }
        }
    }

    // 【修改】应用自定义背景（完美保留原有透明度适配逻辑，仅新增日/夜双图片判断）
    private void applyCustomBackground() {
        SharedPreferences prefs = getSharedPreferences("app_prefs", MODE_PRIVATE);
        int themeMode = getSafeInt(prefs, "theme_mode", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
        View rootLayout = findViewById(R.id.root_layout);
        View navHostFragment = findViewById(R.id.nav_host_fragment); // 获取碎片容器
        View maskOverlay = findViewById(R.id.view_mask_overlay); // 获取遮罩图层

        // 🌟 获取底部导航栏
        View bottomBar = findViewById(R.id.bottomBar);

        if (rootLayout == null) return;

        // 🌟 【新增】应用遮罩设置
        if (maskOverlay != null) {
            boolean maskEnabled = prefs.getBoolean("mask_enabled", false);
            if (maskEnabled && themeMode == 3) {
                String maskColorStr = prefs.getString("mask_color", "#000000");
                int maskAlpha = prefs.getInt("mask_alpha", 128);
                
                try {
                    int maskColor = android.graphics.Color.parseColor(maskColorStr);
                    int maskColorWithAlpha = android.graphics.Color.argb(maskAlpha,
                        android.graphics.Color.red(maskColor),
                        android.graphics.Color.green(maskColor),
                        android.graphics.Color.blue(maskColor));
                    
                    maskOverlay.setBackgroundColor(maskColorWithAlpha);
                    maskOverlay.setVisibility(View.VISIBLE);
                } catch (Exception e) {
                    maskOverlay.setVisibility(View.GONE);
                }
            } else {
                maskOverlay.setVisibility(View.GONE);
            }
        }

        if (themeMode == 3) { // 3 代表开启了自定义背景
            // 🌟 【修改部分开始】：智能获取应该加载日间还是夜间的图片
            String dayUriStr = prefs.getString("custom_bg_day_uri", null);
            String nightUriStr = prefs.getString("custom_bg_night_uri", null);
            String targetUriStr = null;

            if (dayUriStr != null && nightUriStr == null) {
                targetUriStr = dayUriStr; // 只有日间
            } else if (nightUriStr != null && dayUriStr == null) {
                targetUriStr = nightUriStr; // 只有夜间
            } else if (dayUriStr != null && nightUriStr != null) {
                // 两个都有，判断当前系统是否为暗黑模式
                int currentNightMode = getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK;
                if (currentNightMode == android.content.res.Configuration.UI_MODE_NIGHT_YES) {
                    targetUriStr = nightUriStr;
                } else {
                    targetUriStr = dayUriStr;
                }
            }
            // 🌟 【修改部分结束】

            if (targetUriStr != null) {
                try {
                    android.net.Uri uri = android.net.Uri.parse(targetUriStr);
                    java.io.InputStream inputStream = getContentResolver().openInputStream(uri);
                    android.graphics.drawable.Drawable drawable =
                            android.graphics.drawable.Drawable.createFromStream(inputStream, uri.toString());

                    // 设置为根布局背景
                    rootLayout.setBackground(drawable);

                    // 【动态透明】：保留你原本的逻辑，把顶层容器的透明度调成 100% (完全透明)
                    if (navHostFragment != null) {
                        navHostFragment.setBackgroundColor(android.graphics.Color.TRANSPARENT);
                    }

                    // 🌟 【保留原有】：底栏设置透明度 (Alpha: 230)
                    if (bottomBar != null && bottomBar.getBackground() != null) {
                        bottomBar.getBackground().mutate().setAlpha(230);
                    }

                    if (inputStream != null) inputStream.close();
                } catch (Exception e) {
                    Log.e("Tally", "Error", e);
                    rootLayout.setBackgroundResource(R.color.bar_background);
                    if (navHostFragment != null) navHostFragment.setBackgroundColor(android.graphics.Color.TRANSPARENT);
                    // 异常时恢复底栏透明度
                    if (bottomBar != null && bottomBar.getBackground() != null) bottomBar.getBackground().mutate().setAlpha(255);
                }
            } else {
                // 如果开启了自定义背景，但用户把两张图都"清除"了，则恢复系统默认背景，但FragmentContainerView保持透明
                rootLayout.setBackgroundResource(R.color.bar_background);
                if (navHostFragment != null) navHostFragment.setBackgroundColor(android.graphics.Color.TRANSPARENT);
                if (bottomBar != null && bottomBar.getBackground() != null) bottomBar.getBackground().mutate().setAlpha(255);
            }
        } else {
            // 如果不是自定义背景模式，恢复系统默认颜色
            rootLayout.setBackgroundResource(R.color.bar_background);
            if (navHostFragment != null) {
                navHostFragment.setBackgroundResource(R.color.white);
            }

            // 🌟 【保留原有】：恢复底栏 100% 不透明度
            if (bottomBar != null && bottomBar.getBackground() != null) {
                bottomBar.getBackground().mutate().setAlpha(255);
            }
        }
    }

    // 已移除旧的 toggleNightMode 方法，因为现在由 SettingsActivity 统一管理

    private void showBackupOptions() {
        String[] options = {"导出数据", "导入数据"};
        new AlertDialog.Builder(this)
                .setTitle("数据备份与恢复")
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
                        String timeStr = sdf.format(new Date()).replace(":", "-"); 
                        String fileName = "Tally " + timeStr + ".json";
                        exportLauncher.launch(fileName);
                    }
                    else {
                        importLauncher.launch(new String[]{"application/json", "*/*"});
                    }
                })
                .show();
    }

    @Override
    protected void onResume() {
        super.onResume();

        applyCustomBackground();
        if (blurTabBar != null) {
            applyTabBackgroundSettings(blurTabBar);
        }
    }
	
	
	    // ================== 新增：安全读取 SharedPreferences 的兼容方法 ==================
	    private int getSafeInt(SharedPreferences prefs, String key, int defValue) {
        try {
            return prefs.getInt(key, defValue);
        } catch (ClassCastException e) {
            // 如果遇到了 String 类型的老数据，尝试强转并自动修复为 Int
            try {
                int val = Integer.parseInt(prefs.getString(key, String.valueOf(defValue)));
                prefs.edit().putInt(key, val).apply(); // 自动修复本地数据
                return val;
            } catch (Exception ex) {
                return defValue;
            }
        }
    }

    private boolean getSafeBoolean(SharedPreferences prefs, String key, boolean defValue) {
        try {
            return prefs.getBoolean(key, defValue);
        } catch (ClassCastException e) {
            // 如果遇到了 String 类型的老数据，尝试强转并自动修复为 Boolean
            try {
                boolean val = Boolean.parseBoolean(prefs.getString(key, String.valueOf(defValue)));
                prefs.edit().putBoolean(key, val).apply(); // 自动修复本地数据
                return val;
            } catch (Exception ex) {
                return defValue;
            }
        }
    }

    // 显示首次打开引导提示
    private void showLongPressHintIfNeeded() {
        SharedPreferences prefs = getSharedPreferences("app_prefs", MODE_PRIVATE);
        boolean hasShownHint = prefs.getBoolean("has_shown_long_press_hint", false);

        if (!hasShownHint) {
            // 延迟显示，确保界面已经完全加载
            new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                new AlertDialog.Builder(this)
                        .setTitle("使用提示")
                        .setMessage("短按➕添加账单，长按➕进入设置")
                        .setPositiveButton("知道了", (dialog, which) -> {
                            // 用户确认后，标记为已显示
                            prefs.edit().putBoolean("has_shown_long_press_hint", true).apply();
                            dialog.dismiss();
                        })
                        .setCancelable(false)
                        .show();
            }, 500); // 延迟500毫秒显示
        }
    }
    // ==============================================================================



}