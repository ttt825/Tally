package com.example.budgetapp.ui;

import android.util.Log;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.Display;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.widget.SwitchCompat;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.content.FileProvider;

import com.example.budgetapp.R;

import java.io.File;

public class ThemeSettingsActivity extends AppCompatActivity {

    private SharedPreferences prefs;
    private View cardCustomBg;
    private View cardMaskSettings;
    private Button btnPickDayBg, btnClearDayBg;
    private Button btnPickNightBg, btnClearNightBg;
    private androidx.appcompat.widget.SwitchCompat switchMaskEnabled;
    private Button btnPickMaskColor;
    private View viewMaskColorPreview;
    private SeekBar seekMaskAlpha;
    private TextView tvMaskAlphaValue;
    private View layoutMaskColor, layoutMaskAlpha;

    private int pickingMode = 0;

    private final ActivityResultLauncher<String[]> pickCustomBgLauncher = registerForActivityResult(
            new ActivityResultContracts.OpenDocument(),
            uri -> {
                if (uri != null) {
                    try {
                        getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                        startCrop(uri);
                    } catch (SecurityException e) {
                        Log.e("Tally", "Error", e);
                        Toast.makeText(this, "获取图片权限失败", Toast.LENGTH_SHORT).show();
                    }
                }
            }
    );

    private final ActivityResultLauncher<Intent> cropLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    String croppedPath = result.getData().getStringExtra(CropActivity.EXTRA_CROPPED_PATH);
                    if (croppedPath != null) {
                        File croppedFile = new File(croppedPath);
                        Uri contentUri = FileProvider.getUriForFile(
                                this,
                                "com.example.budgetapp.fileprovider",
                                croppedFile
                        );

                        if (pickingMode == 1) {
                            prefs.edit().putString("custom_bg_day_uri", contentUri.toString()).apply();
                            Toast.makeText(this, "日间背景已保存", Toast.LENGTH_SHORT).show();
                        } else if (pickingMode == 2) {
                            prefs.edit().putString("custom_bg_night_uri", contentUri.toString()).apply();
                            Toast.makeText(this, "夜间背景已保存", Toast.LENGTH_SHORT).show();
                        }
                        updateCustomBgButtons();
                        applyThemeInstantly();
                    }
                }
            }
    );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 🌟 【新增 1】：允许内容延伸到系统状态栏和导航栏（小白条）区域
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(getWindow(), false);

        setContentView(R.layout.activity_theme_settings);

        // 🌟 【新增 2】：处理沉浸式带来的遮挡，动态增加 Padding
        View rootView = findViewById(R.id.theme_settings_root);
        final int originalPaddingLeft = rootView.getPaddingLeft();
        final int originalPaddingTop = rootView.getPaddingTop();
        final int originalPaddingRight = rootView.getPaddingRight();
        final int originalPaddingBottom = rootView.getPaddingBottom();

        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(rootView, (v, windowInsets) -> {
            androidx.core.graphics.Insets insets = windowInsets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars());
            v.setPadding(
                    originalPaddingLeft + insets.left,
                    originalPaddingTop + insets.top,
                    originalPaddingRight + insets.right,
                    originalPaddingBottom + insets.bottom
            );
            return androidx.core.view.WindowInsetsCompat.CONSUMED;
        });

        // ================= 下面是你原本的逻辑 =================
        prefs = getSharedPreferences("app_prefs", MODE_PRIVATE);

        cardCustomBg = findViewById(R.id.card_custom_bg);
        cardMaskSettings = findViewById(R.id.card_mask_settings);
        btnPickDayBg = findViewById(R.id.btn_pick_day_bg);
        btnClearDayBg = findViewById(R.id.btn_clear_day_bg);
        btnPickNightBg = findViewById(R.id.btn_pick_night_bg);
        btnClearNightBg = findViewById(R.id.btn_clear_night_bg);

        switchMaskEnabled = findViewById(R.id.switch_mask_enabled);
        btnPickMaskColor = findViewById(R.id.btn_pick_mask_color);
        viewMaskColorPreview = findViewById(R.id.view_mask_color_preview);
        seekMaskAlpha = findViewById(R.id.seek_mask_alpha);
        tvMaskAlphaValue = findViewById(R.id.tv_mask_alpha_value);
        layoutMaskColor = findViewById(R.id.layout_mask_color);
        layoutMaskAlpha = findViewById(R.id.layout_mask_alpha);

        Spinner spThemeMode = findViewById(R.id.sp_theme_mode);
        String[] themeOptions = {"跟随系统", "日间模式", "夜间模式", "自定义主题"};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, R.layout.item_spinner_dropdown, themeOptions);
        spThemeMode.setAdapter(adapter);

        // 初始化当前选中的模式
        int currentMode = prefs.getInt("theme_mode", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
        int selectionIndex = 0;
        if (currentMode == AppCompatDelegate.MODE_NIGHT_NO) selectionIndex = 1;
        else if (currentMode == AppCompatDelegate.MODE_NIGHT_YES) selectionIndex = 2;
        else if (currentMode == 3) selectionIndex = 3;

        spThemeMode.setSelection(selectionIndex, false);

        spThemeMode.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                int selectedMode;
                if (position == 1) selectedMode = AppCompatDelegate.MODE_NIGHT_NO;
                else if (position == 2) selectedMode = AppCompatDelegate.MODE_NIGHT_YES;
                else if (position == 3) selectedMode = 3;
                else selectedMode = AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;

                prefs.edit().putInt("theme_mode", selectedMode).apply();
                updateCustomBgVisibility(selectedMode);
                applyThemeInstantly();
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        updateCustomBgVisibility(currentMode);
        updateCustomBgButtons();
        updateMaskSettings();

        // 按钮事件
        btnPickDayBg.setOnClickListener(v -> { pickingMode = 1; pickCustomBgLauncher.launch(new String[]{"image/*"}); });
        btnPickNightBg.setOnClickListener(v -> { pickingMode = 2; pickCustomBgLauncher.launch(new String[]{"image/*"}); });

        btnClearDayBg.setOnClickListener(v -> {
            prefs.edit().remove("custom_bg_day_uri").apply();
            updateCustomBgButtons();
            applyThemeInstantly();
        });

        btnClearNightBg.setOnClickListener(v -> {
            prefs.edit().remove("custom_bg_night_uri").apply();
            updateCustomBgButtons();
            applyThemeInstantly();
        });

        // 遮罩设置事件
        switchMaskEnabled.setOnCheckedChangeListener((buttonView, isChecked) -> {
            prefs.edit().putBoolean("mask_enabled", isChecked).apply();
            updateMaskSettings();
            applyThemeInstantly();
        });

        btnPickMaskColor.setOnClickListener(v -> {
            // 显示预设颜色选择对话框
            showMaskColorPicker();
        });

        seekMaskAlpha.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int alpha = progress;
                prefs.edit().putInt("mask_alpha", alpha).apply();
                updateMaskAlphaText(alpha);
                if (fromUser) {
                    applyThemeInstantly();
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });
    }

    private void updateCustomBgVisibility(int mode) {
        cardCustomBg.setVisibility(mode == 3 ? View.VISIBLE : View.GONE);
        cardMaskSettings.setVisibility(mode == 3 ? View.VISIBLE : View.GONE);
    }

    private void updateCustomBgButtons() {
        boolean hasDayBg = prefs.contains("custom_bg_day_uri");
        boolean hasNightBg = prefs.contains("custom_bg_night_uri");

        btnPickDayBg.setText(hasDayBg ? "重新选择日间背景" : "选择日间背景");
        btnClearDayBg.setVisibility(hasDayBg ? View.VISIBLE : View.GONE);

        btnPickNightBg.setText(hasNightBg ? "重新选择夜间背景" : "选择夜间背景");
        btnClearNightBg.setVisibility(hasNightBg ? View.VISIBLE : View.GONE);
    }

    private void updateMaskSettings() {
        boolean maskEnabled = prefs.getBoolean("mask_enabled", false);
        switchMaskEnabled.setChecked(maskEnabled);
        
        layoutMaskColor.setEnabled(maskEnabled);
        layoutMaskAlpha.setEnabled(maskEnabled);
        
        String maskColor = prefs.getString("mask_color", "#000000");
        viewMaskColorPreview.setBackgroundColor(Color.parseColor(maskColor));
        
        int maskAlpha = prefs.getInt("mask_alpha", 128);
        seekMaskAlpha.setProgress(maskAlpha);
        updateMaskAlphaText(maskAlpha);
    }

    private void showMaskColorPicker() {
        // 预设颜色列表
        final String[] colors = {
            "#000000", // 黑色
            "#FFFFFF", // 白色
            "#FF0000", // 红色
            "#00FF00", // 绿色
            "#0000FF", // 蓝色
            "#FFFF00", // 黄色
            "#FF00FF", // 紫色
            "#00FFFF", // 青色
            "#808080", // 灰色
            "#800000", // 深红
            "#008000", // 深绿
            "#000080", // 深蓝
            "#808000", // 橄榄色
            "#800080", // 紫色
            "#008080", // 深青色
            "#C0C0C0"  // 银色
        };

        // 创建颜色选择对话框
        androidx.appcompat.app.AlertDialog.Builder builder = new androidx.appcompat.app.AlertDialog.Builder(this);
        builder.setTitle("选择遮罩颜色");

        // 创建颜色选择布局（使用LinearLayout替代GridLayout）
        LinearLayout colorLayout = new LinearLayout(this);
        colorLayout.setOrientation(LinearLayout.VERTICAL);
        colorLayout.setPadding(16, 16, 16, 16);

        // 每行4个颜色
        for (int row = 0; row < 4; row++) {
            LinearLayout rowLayout = new LinearLayout(this);
            rowLayout.setOrientation(LinearLayout.HORIZONTAL);
            rowLayout.setGravity(android.view.Gravity.CENTER);
            LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            );
            rowParams.setMargins(0, 8, 0, 8);
            rowLayout.setLayoutParams(rowParams);

            for (int col = 0; col < 4; col++) {
                int index = row * 4 + col;
                if (index < colors.length) {
                    final String color = colors[index];
                    View colorView = new View(this);
                    int size = 80;
                    LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(size, size);
                    params.setMargins(8, 0, 8, 0);
                    colorView.setLayoutParams(params);
                    colorView.setBackgroundColor(Color.parseColor(color));
                    colorView.setOnClickListener(v -> {
                        prefs.edit().putString("mask_color", color).apply();
                        viewMaskColorPreview.setBackgroundColor(Color.parseColor(color));
                        applyThemeInstantly();
                        builder.create().dismiss();
                    });
                    rowLayout.addView(colorView);
                }
            }
            colorLayout.addView(rowLayout);
        }

        builder.setView(colorLayout);
        builder.setNegativeButton("取消", null);
        builder.show();
    }

    private void updateMaskAlphaText(int alpha) {
        int percentage = Math.round((alpha / 255.0f) * 100);
        tvMaskAlphaValue.setText(percentage + "%");
    }

    private void applyThemeInstantly() {
        int themeMode = prefs.getInt("theme_mode", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
        int delegateMode = themeMode;

        if (themeMode == 3) {
            String dayUri = prefs.getString("custom_bg_day_uri", null);
            String nightUri = prefs.getString("custom_bg_night_uri", null);
            
            if (dayUri != null && nightUri == null) {
                delegateMode = AppCompatDelegate.MODE_NIGHT_NO; // 只有日间，强行锁死日间模式
            } else if (nightUri != null && dayUri == null) {
                delegateMode = AppCompatDelegate.MODE_NIGHT_YES; // 只有夜间，强行锁死夜间模式
            } else {
                delegateMode = AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM; // 都设置了或都没设置，跟随系统
            }
        }
        AppCompatDelegate.setDefaultNightMode(delegateMode);
    }

    @SuppressWarnings("deprecation")
    private void startCrop(Uri sourceUri) {
        int screenWidth, screenHeight;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            android.view.WindowMetrics metrics = getWindowManager().getCurrentWindowMetrics();
            screenWidth = metrics.getBounds().width();
            screenHeight = metrics.getBounds().height();
        } else {
            Display display = getWindowManager().getDefaultDisplay();
            android.graphics.Point size = new android.graphics.Point();
            display.getSize(size);
            screenWidth = size.x;
            screenHeight = size.y;
        }
        int ratioX = screenWidth;
        int ratioY = screenHeight;
        int gcd = gcd(ratioX, ratioY);
        ratioX /= gcd;
        ratioY /= gcd;

        String suffix = pickingMode == 1 ? "day" : "night";

        Intent intent = new Intent(this, CropActivity.class);
        intent.putExtra(CropActivity.EXTRA_SOURCE_URI, sourceUri.toString());
        intent.putExtra(CropActivity.EXTRA_ASPECT_X, ratioX);
        intent.putExtra(CropActivity.EXTRA_ASPECT_Y, ratioY);
        intent.putExtra(CropActivity.EXTRA_CROP_SUFFIX, suffix);
        cropLauncher.launch(intent);
    }

    private static int gcd(int a, int b) {
        while (b != 0) {
            int temp = b;
            b = a % b;
            a = temp;
        }
        return a;
    }
}