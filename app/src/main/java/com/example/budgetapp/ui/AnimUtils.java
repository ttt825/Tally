package com.example.budgetapp.ui;

import android.animation.AnimatorListenerAdapter;
import android.content.Context;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

/**
 * 动画工具类：统一管理动画时长和减弱动画偏好。
 * 当系统开启了"移除动画"无障碍设置时，所有动画降级为瞬时完成。
 */
public final class AnimUtils {

    private AnimUtils() {}

    /**
     * 检查用户是否偏好减弱动画。
     * Android 11+ 可通过 ANIMATOR_DURATION_SCALE 判断；
     * 低版本退回 AccessibilityManager 的开关状态。
     */
    public static boolean shouldReduceAnimations(Context context) {
        if (context == null) return false;
        try {
            // 检查系统动画时长缩放：0 表示关闭动画
            float scale = android.provider.Settings.Global.getFloat(
                    context.getContentResolver(),
                    android.provider.Settings.Global.ANIMATOR_DURATION_SCALE,
                    1f);
            if (scale <= 0f) return true;

            // 检查无障碍服务是否启用了触摸探索（通常意味着用户需要减弱动画）
            android.view.accessibility.AccessibilityManager am =
                    (android.view.accessibility.AccessibilityManager)
                            context.getSystemService(Context.ACCESSIBILITY_SERVICE);
            return am != null && am.isEnabled() && am.isTouchExplorationEnabled();
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 安全的缩放按压反馈动画。
     * 减弱动画模式下直接跳过。
     */
    public static void pressFeedback(View view, float scale, long duration) {
        if (view == null) return;
        if (shouldReduceAnimations(view.getContext())) return;
        view.animate().scaleX(scale).scaleY(scale).setDuration(duration)
                .withEndAction(() -> view.animate().scaleX(1f).scaleY(1f).setDuration(
                        (long) (duration * 1.2f)).start())
                .start();
    }

    /**
     * 安全的淡入动画。
     * 减弱动画模式下直接设置可见且全透明度为1。
     */
    public static void fadeIn(View view, long duration) {
        if (view == null) return;
        if (shouldReduceAnimations(view.getContext())) {
            view.setAlpha(1f);
            return;
        }
        view.setAlpha(0f);
        view.animate().alpha(1f).setDuration(duration)
                .setInterpolator(new DecelerateInterpolator())
                .start();
    }
}
