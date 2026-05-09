package com.example.budgetapp.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.text.Spannable;
import android.text.style.BackgroundColorSpan;
import android.text.style.ForegroundColorSpan;
import android.util.AttributeSet;
import androidx.appcompat.widget.AppCompatEditText;

/**
 * 自定义 EditText，支持自定义选中文本的背景色和文字颜色
 * 隐藏原生高亮样式，失去焦点时取消高亮
 */
public class CustomHighlightEditText extends AppCompatEditText {
    
    private int highlightBackgroundColor;
    private int highlightTextColor;
    private boolean hasFocus = false;
    
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
        
        // 确保文本可以被选择和复制
        setTextIsSelectable(true);
        setLongClickable(true);
    }
    
    public void setHighlightColors(int backgroundColor, int textColor) {
        this.highlightBackgroundColor = backgroundColor;
        this.highlightTextColor = textColor;
        invalidate();
    }
    
    @Override
    protected void onFocusChanged(boolean focused, int direction, android.graphics.Rect previouslyFocusedRect) {
        super.onFocusChanged(focused, direction, previouslyFocusedRect);
        hasFocus = focused;
        invalidate();
    }
    
    @Override
    protected void onDraw(Canvas canvas) {
        // 只有在有焦点且有选中文本时才显示自定义高亮
        if (hasFocus) {
            int selStart = getSelectionStart();
            int selEnd = getSelectionEnd();
            
            if (selStart >= 0 && selEnd > selStart && getText() != null) {
                Spannable text = getText();
                
                // 移除之前的高亮 span
                clearHighlight();
                
                // 添加新的高亮 span
                text.setSpan(new BackgroundColorSpan(highlightBackgroundColor), 
                            selStart, selEnd, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                text.setSpan(new ForegroundColorSpan(highlightTextColor), 
                            selStart, selEnd, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            } else {
                // 没有选中文本时，清除高亮
                clearHighlight();
            }
        }
        
        super.onDraw(canvas);
    }
    
    @Override
    protected void onSelectionChanged(int selStart, int selEnd) {
        super.onSelectionChanged(selStart, selEnd);
        
        // 当选择范围变为空（即取消选择）时，清除高亮
        if (selStart == selEnd) {
            clearHighlight();
        }
        
        // 选择改变时重绘
        invalidate();
    }
    
    /**
     * 清除所有高亮 span
     */
    private void clearHighlight() {
        if (getText() != null) {
            Spannable text = getText();
            
            // 移除背景色 span
            BackgroundColorSpan[] bgSpans = text.getSpans(0, text.length(), BackgroundColorSpan.class);
            for (BackgroundColorSpan span : bgSpans) {
                text.removeSpan(span);
            }
            
            // 移除前景色 span
            ForegroundColorSpan[] fgSpans = text.getSpans(0, text.length(), ForegroundColorSpan.class);
            for (ForegroundColorSpan span : fgSpans) {
                text.removeSpan(span);
            }
        }
    }
    
    @Override
    public int getHighlightColor() {
        // 返回透明色以隐藏原生高亮
        return Color.TRANSPARENT;
    }
}
