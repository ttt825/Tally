package com.example.budgetapp.drawable;

import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;

/**
 * 真实外发散阴影 Drawable：使用 BlurMaskFilter 在 Tab 栏边缘外侧绘制高斯模糊阴影。
 */
public class TabShadowDrawable extends Drawable {
    private final Paint paint;
    private final float cornerRadius;
    private final float shadowSize;

    public TabShadowDrawable(float cornerRadius, float shadowSize, int shadowAlpha) {
        this.cornerRadius = cornerRadius;
        this.shadowSize = shadowSize;
        this.paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        this.paint.setColor(Color.BLACK);
        this.paint.setAlpha(shadowAlpha);
        this.paint.setMaskFilter(new BlurMaskFilter(shadowSize, BlurMaskFilter.Blur.NORMAL));
    }

    @Override
    public void draw(Canvas canvas) {
        Rect bounds = getBounds();
        // 内部矩形内缩 shadowSize，使模糊主要向外发散到容器 padding 区域
        RectF rect = new RectF(
                bounds.left + shadowSize,
                bounds.top + shadowSize,
                bounds.right - shadowSize,
                bounds.bottom - shadowSize
        );
        canvas.drawRoundRect(rect, cornerRadius, cornerRadius, paint);
    }

    @Override
    public void setAlpha(int alpha) {}

    @Override
    public void setColorFilter(ColorFilter colorFilter) {}

    @SuppressWarnings("deprecation")
    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}
