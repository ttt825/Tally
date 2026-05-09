package com.example.budgetapp.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.text.Layout;
import android.text.Spannable;
import android.text.style.BackgroundColorSpan;
import android.text.style.ForegroundColorSpan;
import android.util.AttributeSet;
import androidx.appcompat.widget.AppCompatEditText;

/**
 * 自定义 EditText，支持自定义选中文本的背景色和文字颜色
 */
public class CustomHighlightEditText extends AppCompatEditText {
    
    private int highlightBackgroundColor;
    private int highlightTextColor;
    
    public CustomHighlightEditText(Context context) {
        super(context);
        init(context);
    }
    
    public CustomHighlightEditText(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }
    
    public CustomHighlightEditText(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }
    
    private void init(Context context) {
        // 默认使用主题色作为背景，白色作为文字
        highlightBackgroundColor = context.getResources().getColor(com.example.budgetapp.R.color.app_blue, null);
        highlightTextColor = context.getResources().getColor(android.R.color.white, null);
    }
    
    public void setHighlightColors(int backgroundColor, int textColor) {
        this.highlightBackgroundColor = backgroundColor;
        this.highlightTextColor = textColor;
        invalidate();
    }
    
    @Override
    protected void onDraw(Canvas canvas) {
        // 获取选中的文本范围
        int selStart = getSelectionStart();
        int selEnd = getSelectionEnd();
        
        if (selStart >= 0 && selEnd > selStart && getText() != null) {
            Spannable text = getText();
            
            // 移除之前的高亮 span
            BackgroundColorSpan[] bgSpans = text.getSpans(0, text.length(), BackgroundColorSpan.class);
            for (BackgroundColorSpan span : bgSpans) {
                text.removeSpan(span);
            }
            ForegroundColorSpan[] fgSpans = text.getSpans(0, text.length(), ForegroundColorSpan.class);
            for (ForegroundColorSpan span : fgSpans) {
                text.removeSpan(span);
            }
            
            // 添加新的高亮 span
            text.setSpan(new BackgroundColorSpan(highlightBackgroundColor), 
                        selStart, selEnd, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            text.setSpan(new ForegroundColorSpan(highlightTextColor), 
                        selStart, selEnd, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        
        super.onDraw(canvas);
    }
    
    @Override
    protected void onSelectionChanged(int selStart, int selEnd) {
        super.onSelectionChanged(selStart, selEnd);
        // 选择改变时重绘
        invalidate();
    }
}
