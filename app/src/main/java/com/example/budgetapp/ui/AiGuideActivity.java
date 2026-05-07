package com.example.budgetapp.ui;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.budgetapp.R;

public class AiGuideActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 开启全局沉浸式
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        setContentView(R.layout.activity_ai_guide);

        // 处理系统栏 Insets
        View rootView = findViewById(R.id.ai_guide_root);
        if (rootView != null) {
            final int originalPaddingLeft = rootView.getPaddingLeft();
            final int originalPaddingTop = rootView.getPaddingTop();
            final int originalPaddingRight = rootView.getPaddingRight();
            final int originalPaddingBottom = rootView.getPaddingBottom();

            ViewCompat.setOnApplyWindowInsetsListener(rootView, (v, windowInsets) -> {
                Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
                v.setPadding(
                        originalPaddingLeft + insets.left,
                        originalPaddingTop + insets.top,
                        originalPaddingRight + insets.right,
                        originalPaddingBottom + insets.bottom
                );
                return WindowInsetsCompat.CONSUMED;
            });
        }

        // 应用自定义背景（如果启用）
        applyCustomBackground();
        
        // 设置可点击的链接
        setupClickableLinks();
    }

    private void applyCustomBackground() {
        SharedPreferences prefs = getSharedPreferences("app_prefs", MODE_PRIVATE);
        int themeMode = prefs.getInt("theme_mode", -1);

        View rootView = findViewById(R.id.ai_guide_root);
        if (rootView != null && themeMode == 3) {
            // 自定义背景模式
            rootView.setBackgroundColor(Color.TRANSPARENT);
        }
    }
    
    private void setupClickableLinks() {
        // 步骤 1 中的链接
        setupClickableUrl(R.id.tv_step1_url, "https://longcat.chat/platform/usage");
        setupClickableUrl(R.id.tv_step1_example, "https://api.longcat.chat/openai");
        
        // 步骤 2 中的链接
        setupClickableUrl(R.id.tv_step2_url, "https://longcat.chat/platform/api_keys");
        
        // 设置示例文本可复制（长按复制）
        // 步骤 1：接口地址示例
        setupCopyableText(R.id.tv_step1_example, "https://api.longcat.chat/openai");
        // 步骤 3：模型名称
        setupCopyableText(R.id.tv_step3_model, "LongCat-Flash-Chat-2602-Exps");
    }
    
    private void setupCopyableText(int textViewId, String textToCopy) {
        TextView textView = findViewById(textViewId);
        if (textView == null) return;
        
        textView.setTextIsSelectable(true);
        textView.setOnLongClickListener(v -> {
            ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            ClipData clip = ClipData.newPlainText("示例文本", textToCopy);
            clipboard.setPrimaryClip(clip);
            Toast.makeText(this, "已复制：" + textToCopy, Toast.LENGTH_SHORT).show();
            return true;
        });
    }
    
    private void setupClickableUrl(int textViewId, String url) {
        TextView textView = findViewById(textViewId);
        if (textView == null) return;
        
        String text = textView.getText().toString();
        SpannableString spannableString = new SpannableString(text);
        
        // 查找 URL 在文本中的位置
        int startIndex = text.indexOf(url);
        if (startIndex != -1) {
            int endIndex = startIndex + url.length();
            
            ClickableSpan clickableSpan = new ClickableSpan() {
                @Override
                public void onClick(@NonNull View widget) {
                    openUrl(url);
                }
                
                @Override
                public void updateDrawState(@NonNull android.text.TextPaint ds) {
                    super.updateDrawState(ds);
                    ds.setUnderlineText(true); // 添加下划线
                }
            };
            
            spannableString.setSpan(clickableSpan, startIndex, endIndex, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            textView.setText(spannableString);
            textView.setMovementMethod(LinkMovementMethod.getInstance());
        }
    }
    
    private void openUrl(String url) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            startActivity(intent);
        } catch (Exception e) {
            // 如果无法打开浏览器，复制到剪贴板
            ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            ClipData clip = ClipData.newPlainText("URL", url);
            clipboard.setPrimaryClip(clip);
            Toast.makeText(this, "已复制链接到剪贴板", Toast.LENGTH_SHORT).show();
        }
    }
}
