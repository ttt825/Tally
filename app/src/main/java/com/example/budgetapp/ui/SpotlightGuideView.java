package com.example.budgetapp.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.RectF;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.example.budgetapp.R;

/**
 * 聚光灯引导视图
 * 以半透明遮罩 + 透明圆形挖孔的方式高亮指定视图，并在其旁边显示提示文字。
 */
public class SpotlightGuideView extends View {

    private final Paint overlayPaint;
    private final Paint clearPaint;
    private final Paint borderPaint;
    private final TextPaint textPaint;
    private final RectF targetRect = new RectF();
    private String hintText;
    private final int highlightPadding;
    private final int textGap;
    private final int horizontalMargin;
    private final int verticalMargin;

    public SpotlightGuideView(Context context) {
        this(context, null);
    }

    public SpotlightGuideView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        setLayerType(LAYER_TYPE_SOFTWARE, null);

        overlayPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        overlayPaint.setColor(ContextCompat.getColor(context, R.color.overlay_mask));
        overlayPaint.setAlpha((int) (0.75f * 255));

        clearPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        clearPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));

        borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        borderPaint.setColor(Color.WHITE);
        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setStrokeWidth(dpToPx(context, 2));

        textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setColor(Color.WHITE);
        textPaint.setTextSize(dpToPx(context, 15));
        textPaint.setTextAlign(Paint.Align.LEFT);

        highlightPadding = dpToPx(context, 12);
        textGap = dpToPx(context, 16);
        horizontalMargin = dpToPx(context, 24);
        verticalMargin = dpToPx(context, 24);
    }

    public void setTarget(Rect target, String hint) {
        targetRect.set(target);
        hintText = hint;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int width = getWidth();
        int height = getHeight();
        if (width == 0 || height == 0) return;

        // 1. 绘制半透明遮罩
        canvas.drawRect(0, 0, width, height, overlayPaint);

        // 2. 挖空高亮区域
        float centerX = targetRect.centerX();
        float centerY = targetRect.centerY();
        float radius = getHighlightRadius();
        canvas.drawCircle(centerX, centerY, radius, clearPaint);

        // 3. 绘制高亮边框
        canvas.drawCircle(centerX, centerY, radius, borderPaint);

        // 4. 绘制提示文字
        drawHint(canvas, centerX, centerY, radius);
    }

    private void drawHint(Canvas canvas, float centerX, float centerY, float radius) {
        if (hintText == null || hintText.isEmpty()) return;

        int maxTextWidth = Math.max(0, getWidth() - horizontalMargin * 2);
        float desiredWidth = textPaint.measureText(hintText);
        int textWidth = (int) Math.ceil(Math.min(desiredWidth, maxTextWidth));
        if (textWidth <= 0) return;

        StaticLayout layout = StaticLayout.Builder.obtain(
                        hintText, 0, hintText.length(), textPaint, textWidth)
                .setAlignment(Layout.Alignment.ALIGN_CENTER)
                .setLineSpacing(0, 1f)
                .setIncludePad(false)
                .build();

        float textX = Math.max(horizontalMargin,
                Math.min(getWidth() - horizontalMargin - layout.getWidth(),
                        centerX - layout.getWidth() / 2f));

        // 默认放在目标上方，空间不足则放到下方
        float textY = centerY - radius - textGap - layout.getHeight();
        if (textY < verticalMargin) {
            textY = centerY + radius + textGap;
        }

        canvas.save();
        canvas.translate(textX, textY);
        layout.draw(canvas);
        canvas.restore();
    }

    private float getHighlightRadius() {
        return (float) (Math.max(targetRect.width(), targetRect.height()) / 2.0 * Math.sqrt(2)) + highlightPadding;
    }

    private static int dpToPx(Context context, float dp) {
        return (int) (dp * context.getResources().getDisplayMetrics().density + 0.5f);
    }
}
