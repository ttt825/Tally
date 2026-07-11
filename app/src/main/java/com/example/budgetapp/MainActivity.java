package com.example.budgetapp;

import android.util.Log;

import android.appwidget.AppWidgetManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.view.MotionEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.FrameLayout;
import android.widget.PopupMenu;
import android.widget.Toast;

import com.example.budgetapp.utils.DateUtils;
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
import com.example.budgetapp.ui.SpotlightGuideView;
import com.example.budgetapp.ui.TabEffectMode;
import com.example.budgetapp.ui.TabPreferenceKeys;
import com.example.budgetapp.drawable.TabShadowDrawable;
import com.example.budgetapp.viewmodel.TransactionViewModel;
import com.example.budgetapp.utils.ThreadPoolManager;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import me.ibrahimsn.lib.SmoothBottomBar;
import eightbitlab.com.blurview.BlurView;
import eightbitlab.com.blurview.RenderScriptBlur;
import com.qmdeve.liquidglass.widget.LiquidGlassView;

import java.util.ArrayList;
import java.util.List;

import androidx.activity.OnBackPressedCallback;

public class MainActivity extends AppCompatActivity {

    private TransactionViewModel transactionViewModel;
    private eightbitlab.com.blurview.BlurView blurTabBar;
    private LiquidGlassView liquidGlassTabBar;
    private ViewGroup contentLayout;
    private View rootLayout;
    private boolean liquidGlassBound;
    private final TabSettingsSnapshot lastTabSettings = new TabSettingsSnapshot();
    // 【修复】移除全量数据缓存，改为导出时按需读取，避免内存占用过大

    // 双击返回退出功能
    private long backPressedTime = 0;
    private static final int TIME_INTERVAL = 2000; // 2秒间隔

    // 用于跟踪当前被按下的Tab，避免与选中动画冲突

    private final Handler spotlightHandler = new Handler(Looper.getMainLooper());
    private SpotlightGuideView currentSpotlightGuide;
    private final android.util.LruCache<String, android.graphics.drawable.Drawable> backgroundDrawableCache =
            new android.util.LruCache<>(2);

    // 导出功能保留在此处作为备份逻辑
    private final ActivityResultLauncher<String> exportLauncher = registerForActivityResult(
            new ActivityResultContracts.CreateDocument("application/json"),
            uri -> {
                if (uri != null) {
                    ThreadPoolManager.getInstance().executeBackground(() -> {
                        try {
                            List<Transaction> transactions = transactionViewModel.getAllTransactionsSync();
                            BackupManager.exportToJson(MainActivity.this, uri, transactions);
                            runOnUiThreadSafe(() -> Toast.makeText(MainActivity.this, "导出成功", Toast.LENGTH_SHORT).show());
                        } catch (Exception e) {
                            Log.e("Tally", "Error", e);
                            runOnUiThreadSafe(() -> Toast.makeText(MainActivity.this, "导出失败: " + e.getMessage(), Toast.LENGTH_LONG).show());
                        }
                    });
                }
            }
    );

    // 导入功能保留在此处作为备份逻辑
    private final ActivityResultLauncher<String[]> importLauncher = registerForActivityResult(
            new ActivityResultContracts.OpenDocument(),
            uri -> {
                if (uri != null) {
                    // 【修复】导入使用批量插入，避免逐条触发备份计数
                    ThreadPoolManager.getInstance().executeBackground(() -> {
                        try {
                            BackupData data = BackupManager.importFromJson(MainActivity.this, uri);

                            if (data.records != null && !data.records.isEmpty()) {
                                for (Transaction t : data.records) {
                                    t.id = 0;
                                }
                                transactionViewModel.insertTransactionsSync(data.records, count -> {
                                    runOnUiThreadSafe(() -> {
                                        if (count > 0) {
                                            Toast.makeText(MainActivity.this,
                                                String.format("成功导入: %d条账单", count),
                                                Toast.LENGTH_LONG).show();
                                        } else {
                                            Toast.makeText(MainActivity.this, "备份文件中未发现数据", Toast.LENGTH_SHORT).show();
                                        }
                                    });
                                });
                            } else {
                                runOnUiThreadSafe(() -> Toast.makeText(MainActivity.this, "备份文件中未发现数据", Toast.LENGTH_SHORT).show());
                            }
                        } catch (Exception e) {
                            Log.e("Tally", "Error", e);
                            runOnUiThreadSafe(() -> Toast.makeText(MainActivity.this, "导入失败: " + e.getMessage(), Toast.LENGTH_LONG).show());
                        }
                    });
                }
            }
    );

    private void runOnUiThreadSafe(Runnable action) {
        if (isFinishing() || isDestroyed()) return;
        runOnUiThread(action);
    }

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

        this.rootLayout = findViewById(R.id.root_layout);
        this.contentLayout = findViewById(R.id.content_layout);
        SmoothBottomBar bottomBar = findViewById(R.id.bottomBar);

        BlurView blurTabBar = findViewById(R.id.blur_tab_bar);
        this.blurTabBar = blurTabBar;
        this.liquidGlassTabBar = findViewById(R.id.liquid_glass_tab_bar);
        @SuppressWarnings("deprecation")
        var ignored = blurTabBar.setupWith((ViewGroup) this.rootLayout, new RenderScriptBlur(this));
        blurTabBar.setOutlineProvider(ViewOutlineProvider.BACKGROUND);
        blurTabBar.setClipToOutline(true);

        applyTabBackgroundSettings();

        // 初始化时应用背景
        applyCustomBackground();

        // 显示首次打开引导提示
        showSpotlightGuideIfNeeded();

        // 【修改】将WindowInsets监听器设置在content_layout上，避免影响遮罩层的覆盖范围
        ViewCompat.setOnApplyWindowInsetsListener(this.contentLayout, (v, windowInsets) -> {
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

    @Override
    protected void onDestroy() {
        super.onDestroy();
        spotlightHandler.removeCallbacksAndMessages(null);
        if (currentSpotlightGuide != null && currentSpotlightGuide.getParent() != null) {
            ((ViewGroup) currentSpotlightGuide.getParent()).removeView(currentSpotlightGuide);
            currentSpotlightGuide = null;
        }
    }

    private void applyTabBackgroundSettings() {
        SharedPreferences prefs = getSharedPreferences("app_prefs", MODE_PRIVATE);
        int effectMode = prefs.getInt(TabPreferenceKeys.TAB_EFFECT_MODE, TabEffectMode.NONE);
        // 低版本系统不支持液态玻璃，自动回退为无效果
        if (effectMode == TabEffectMode.LIQUID_GLASS && android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.TIRAMISU) {
            effectMode = TabEffectMode.NONE;
        }
        int blurLevel = prefs.getInt(TabPreferenceKeys.TAB_BLUR_LEVEL, 5);
        int cornerRadius = prefs.getInt(TabPreferenceKeys.TAB_CORNER_RADIUS, 50);
        int opacity = prefs.getInt(TabPreferenceKeys.TAB_OPACITY, 80);
        int shadowSize = prefs.getInt(TabPreferenceKeys.TAB_SHADOW_SIZE, 1);
        int shadowOpacity = prefs.getInt(TabPreferenceKeys.TAB_SHADOW_OPACITY, 25);
        int refractionHeight = prefs.getInt(TabPreferenceKeys.TAB_LIQUID_REFRACTION_HEIGHT, 15);
        int dispersion = prefs.getInt(TabPreferenceKeys.TAB_LIQUID_DISPERSION, 50);

        // 参数未变化时跳过，避免 onResume 重复创建 Drawable
        if (lastTabSettings.equals(effectMode, blurLevel, cornerRadius, opacity,
                shadowSize, shadowOpacity, refractionHeight, dispersion)) {
            return;
        }

        float density = getResources().getDisplayMetrics().density;

        if (effectMode == TabEffectMode.LIQUID_GLASS) {
            // 液态玻璃效果：使用 LiquidGlassView，隐藏 BlurView
            blurTabBar.setVisibility(View.GONE);
            liquidGlassTabBar.setVisibility(View.VISIBLE);
            // 绑定内容布局（不包含 Tab 栏自身），避免自采样导致渲染异常
            if (!liquidGlassBound && contentLayout != null) {
                try {
                    liquidGlassTabBar.bind(contentLayout);
                    liquidGlassBound = true;
                } catch (Exception e) {
                    Log.e("MainActivity", "Failed to bind LiquidGlassView", e);
                }
            }
            liquidGlassTabBar.setCornerRadius(cornerRadius * density);
            liquidGlassTabBar.setBlurRadius(blurLevel * 2.5f);
            liquidGlassTabBar.setRefractionHeight(refractionHeight * density);
            liquidGlassTabBar.setRefractionOffset(70f * density);
            liquidGlassTabBar.setDispersion(dispersion / 100f);
            liquidGlassTabBar.setTintAlpha((opacity / 100f) * 0.3f);
            liquidGlassTabBar.setDraggableEnabled(false);
            liquidGlassTabBar.setElasticEnabled(false);
            liquidGlassTabBar.setTouchEffectEnabled(false);
        } else {
            // 无效果 / 模糊效果：使用 BlurView
            blurTabBar.setVisibility(View.VISIBLE);
            liquidGlassTabBar.setVisibility(View.GONE);

            blurTabBar.setBlurEnabled(effectMode == TabEffectMode.BLUR);
            if (effectMode == TabEffectMode.BLUR) {
                blurTabBar.setBlurRadius(blurLevel);
            }

            int alphaInt = (int) (opacity / 100f * 255);
            android.graphics.drawable.GradientDrawable roundedBg = new android.graphics.drawable.GradientDrawable();
            roundedBg.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
            roundedBg.setCornerRadius(cornerRadius);
            roundedBg.setColor((alphaInt << 24) | 0x00FFFFFF);
            blurTabBar.setBackground(roundedBg);
        }

        View container = findViewById(R.id.tab_bar_container);
        if (container != null) {
            float shadowSizeDp = shadowSize * 0.5f;
            // shadowSize 为 0 时彻底隐藏阴影图层；透明度仅控制阴影浓淡
            if (shadowSize > 0) {
                int shadowPx = (int) (shadowSizeDp * density);
                int shadowAlpha = (int) (shadowOpacity / 100f * 255);
                container.setBackground(new TabShadowDrawable(cornerRadius, shadowPx, shadowAlpha));
                container.setPadding(shadowPx, shadowPx, shadowPx, shadowPx);
            } else {
                container.setBackground(null);
                container.setPadding(0, 0, 0, 0);
            }
        }

        lastTabSettings.update(effectMode, blurLevel, cornerRadius, opacity,
                shadowSize, shadowOpacity, refractionHeight, dispersion);
    }

    /**
     * 缓存上次应用的 Tab 栏设置参数，避免 onResume 等场景重复创建 Drawable。
     */
    private static class TabSettingsSnapshot {
        int effectMode;
        int blurLevel;
        int cornerRadius;
        int opacity;
        int shadowSize;
        int shadowOpacity;
        int refractionHeight;
        int dispersion;

        boolean equals(int effectMode, int blurLevel, int cornerRadius, int opacity,
                       int shadowSize, int shadowOpacity, int refractionHeight, int dispersion) {
            return this.effectMode == effectMode && this.blurLevel == blurLevel
                    && this.cornerRadius == cornerRadius && this.opacity == opacity
                    && this.shadowSize == shadowSize && this.shadowOpacity == shadowOpacity
                    && this.refractionHeight == refractionHeight && this.dispersion == dispersion;
        }

        void update(int effectMode, int blurLevel, int cornerRadius, int opacity,
                    int shadowSize, int shadowOpacity, int refractionHeight, int dispersion) {
            this.effectMode = effectMode;
            this.blurLevel = blurLevel;
            this.cornerRadius = cornerRadius;
            this.opacity = opacity;
            this.shadowSize = shadowSize;
            this.shadowOpacity = shadowOpacity;
            this.refractionHeight = refractionHeight;
            this.dispersion = dispersion;
        }
    }

    // 【修改】应用自定义背景（完美保留原有透明度适配逻辑，仅新增日/夜双图片判断）
    private void applyCustomBackground() {
        SharedPreferences prefs = getSharedPreferences("app_prefs", MODE_PRIVATE);
        int themeMode = getSafeInt(prefs, "theme_mode", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
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
                final String cacheKey = targetUriStr;
                android.graphics.drawable.Drawable cached = backgroundDrawableCache.get(cacheKey);
                if (cached != null) {
                    applyCustomBackgroundDrawable(rootLayout, navHostFragment, bottomBar, cached);
                } else {
                    final View rootLayoutRef = rootLayout;
                    final View navHostFragmentRef = navHostFragment;
                    final View bottomBarRef = bottomBar;
                    ThreadPoolManager.getInstance().executeBackground(() -> {
                        try {
                            android.net.Uri uri = android.net.Uri.parse(cacheKey);
                            try (java.io.InputStream inputStream = getContentResolver().openInputStream(uri)) {
                                android.graphics.drawable.Drawable drawable =
                                        android.graphics.drawable.Drawable.createFromStream(inputStream, uri.toString());
                                if (drawable != null) {
                                    backgroundDrawableCache.put(cacheKey, drawable);
                                }
                                final android.graphics.drawable.Drawable result = drawable;
                                runOnUiThreadSafe(() -> applyCustomBackgroundDrawable(
                                        rootLayoutRef, navHostFragmentRef, bottomBarRef, result));
                            }
                        } catch (Exception e) {
                            Log.e("Tally", "Error", e);
                            runOnUiThreadSafe(() -> applyCustomBackgroundFallback(
                                    rootLayoutRef, navHostFragmentRef, bottomBarRef));
                        }
                    });
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

    private void applyCustomBackgroundDrawable(View rootLayout, View navHostFragment, View bottomBar,
                                                android.graphics.drawable.Drawable drawable) {
        if (drawable != null) {
            rootLayout.setBackground(drawable);
        } else {
            rootLayout.setBackgroundResource(R.color.bar_background);
        }
        if (navHostFragment != null) {
            navHostFragment.setBackgroundColor(android.graphics.Color.TRANSPARENT);
        }
        if (bottomBar != null && bottomBar.getBackground() != null) {
            bottomBar.getBackground().mutate().setAlpha(230);
        }
    }

    private void applyCustomBackgroundFallback(View rootLayout, View navHostFragment, View bottomBar) {
        rootLayout.setBackgroundResource(R.color.bar_background);
        if (navHostFragment != null) {
            navHostFragment.setBackgroundColor(android.graphics.Color.TRANSPARENT);
        }
        if (bottomBar != null && bottomBar.getBackground() != null) {
            bottomBar.getBackground().mutate().setAlpha(255);
        }
    }

    private void showBackupOptions() {
        String[] options = {"导出数据", "导入数据"};
        new AlertDialog.Builder(this)
                .setTitle("数据备份与恢复")
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        String timeStr = DateUtils.formatDateTime(System.currentTimeMillis()).replace(":", "-");
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
        applyTabBackgroundSettings();
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

    // 显示首次打开聚光灯引导
    private void showSpotlightGuideIfNeeded() {
        SharedPreferences prefs = getSharedPreferences("app_prefs", MODE_PRIVATE);
        boolean hasShownGuide = prefs.getBoolean("has_shown_spotlight_guide", false);
        if (hasShownGuide) return;

        // 延迟显示，确保 Fragment 与按钮已完成布局
        spotlightHandler.postDelayed(() -> {
            if (isFinishing() || isDestroyed()) return;
            View quickRecord = findViewById(R.id.btn_quick_record);
            View batchRecord = findViewById(R.id.btn_batch_record);
            if (quickRecord == null || batchRecord == null) return;

            runSpotlightStage(quickRecord, getString(R.string.spotlight_quick_record_hint), () ->
                    runSpotlightStage(batchRecord, getString(R.string.spotlight_batch_record_hint), () ->
                            prefs.edit().putBoolean("has_shown_spotlight_guide", true).apply()));
        }, 800);
    }

    private void runSpotlightStage(View target, String hint, Runnable onDismiss) {
        if (rootLayout == null || isFinishing() || isDestroyed()) return;

        int[] location = new int[2];
        target.getLocationOnScreen(location);
        android.graphics.Rect targetRect = new android.graphics.Rect(
                location[0], location[1],
                location[0] + target.getWidth(), location[1] + target.getHeight());

        SpotlightGuideView guide = new SpotlightGuideView(this);
        this.currentSpotlightGuide = guide;
        guide.setTarget(targetRect, hint);
        guide.setAlpha(0f);

        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT);
        ((ViewGroup) rootLayout).addView(guide, params);

        guide.animate()
                .alpha(1f)
                .setDuration(250)
                .start();

        guide.setOnClickListener(v -> guide.animate()
                .alpha(0f)
                .setDuration(200)
                .withEndAction(() -> {
                    currentSpotlightGuide = null;
                    if (rootLayout != null) {
                        ((ViewGroup) rootLayout).removeView(guide);
                    }
                    if (!isFinishing() && !isDestroyed()) {
                        onDismiss.run();
                    }
                })
                .start());
    }
    // ==============================================================================



}