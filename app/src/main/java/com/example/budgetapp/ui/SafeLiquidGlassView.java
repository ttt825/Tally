package com.example.budgetapp.ui;

import android.content.Context;
import android.os.Handler;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;

import com.qmdeve.liquidglass.widget.LiquidGlassView;

import java.lang.reflect.Field;

/**
 * LiquidGlassView 的安全包装子类。
 *
 * 第三方库 LiquidGlassView 内部在 updateConfig() 中执行：
 *     glass.post(() -> glass.updateParameters());
 * 该 lambda 在运行时读取实例字段 glass。当 View 被 detach 时，
 * removeGlass() 会立即将 glass 置为 null，但消息队列中可能仍有尚未执行的
 * updateParameters 任务，导致 NullPointerException。
 *
 * 本类在 onDetachedFromWindow() 中先于父类清理 glass 的待执行回调，
 * 并对所有会触发 updateConfig() 的公开方法做 detach 后拦截，
 * 避免上述竞态条件引发的闪退。
 */
public class SafeLiquidGlassView extends LiquidGlassView {

    private static final String TAG = "SafeLiquidGlassView";

    /**
     * 标记当前 View 是否已经执行过 onDetachedFromWindow。
     * 用于拦截延迟 post 到主线程的 setter/bind 调用。
     */
    private boolean detached = false;

    public SafeLiquidGlassView(Context context) {
        super(context);
    }

    public SafeLiquidGlassView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public SafeLiquidGlassView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    protected void onAttachedToWindow() {
        detached = false;
        super.onAttachedToWindow();
    }

    @Override
    protected void onDetachedFromWindow() {
        // 必须在 super 之前清理：super 会调用 removeGlass() 将 glass 置 null
        clearPendingGlassUpdates();
        detached = true;
        super.onDetachedFromWindow();
    }

    @Override
    public void bind(ViewGroup source) {
        if (detached) return;
        super.bind(source);
    }

    @Override
    public void setCornerRadius(float px) {
        if (detached) return;
        super.setCornerRadius(px);
    }

    @Override
    public void setBlurRadius(float radius) {
        if (detached) return;
        super.setBlurRadius(radius);
    }

    @Override
    public void setRefractionHeight(float px) {
        if (detached) return;
        super.setRefractionHeight(px);
    }

    @Override
    public void setRefractionOffset(float px) {
        if (detached) return;
        super.setRefractionOffset(px);
    }

    @Override
    public void setDispersion(float dispersion) {
        if (detached) return;
        super.setDispersion(dispersion);
    }

    @Override
    public void setTintAlpha(float alpha) {
        if (detached) return;
        super.setTintAlpha(alpha);
    }

    @Override
    public void setTintColorRed(float red) {
        if (detached) return;
        super.setTintColorRed(red);
    }

    @Override
    public void setTintColorGreen(float green) {
        if (detached) return;
        super.setTintColorGreen(green);
    }

    @Override
    public void setTintColorBlue(float blue) {
        if (detached) return;
        super.setTintColorBlue(blue);
    }

    @Override
    public void setDraggableEnabled(boolean enabled) {
        if (detached) return;
        super.setDraggableEnabled(enabled);
    }

    @Override
    public void setElasticEnabled(boolean enabled) {
        if (detached) return;
        super.setElasticEnabled(enabled);
    }

    @Override
    public void setTouchEffectEnabled(boolean enabled) {
        if (detached) return;
        super.setTouchEffectEnabled(enabled);
    }

    /**
     * 通过反射获取 LiquidGlassView 内部的 glass 对象，并移除其 Handler 上所有
     * 待执行的回调与消息，防止 detach 后执行 updateParameters() 触发 NPE。
     */
    private void clearPendingGlassUpdates() {
        try {
            Field glassField = LiquidGlassView.class.getDeclaredField("glass");
            glassField.setAccessible(true);
            Object glass = glassField.get(this);
            if (glass instanceof View) {
                Handler handler = ((View) glass).getHandler();
                if (handler != null) {
                    handler.removeCallbacksAndMessages(null);
                }
            }
        } catch (NoSuchFieldException e) {
            Log.w(TAG, "LiquidGlassView internal field 'glass' not found, skip cleanup", e);
        } catch (Exception e) {
            Log.e(TAG, "Failed to clear pending glass updates", e);
        }
    }
}
