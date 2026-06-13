package com.example.budgetapp.ui;

import android.animation.AnimatorListenerAdapter;
import android.view.View;

import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.RecyclerView;

/**
 * 轻量级列表项入场动画：新增项淡入 + 轻微上移。
 * 退出和移动动画沿用 DefaultItemAnimator 的默认行为。
 * 时长 180ms，符合产品注册的"冷静、高效"调性。
 * 当用户开启"移除动画"无障碍设置时，动画自动降级为瞬时完成。
 */
public class FadeInItemAnimator extends DefaultItemAnimator {

    private static final long DURATION = 180L;
    private boolean reduceMotion = false;

    public FadeInItemAnimator() {
        setAddDuration(DURATION);
    }

    public void setReduceMotion(boolean reduce) {
        this.reduceMotion = reduce;
    }

    @Override
    public boolean animateAdd(RecyclerView.ViewHolder holder) {
        if (reduceMotion) {
            holder.itemView.setAlpha(1f);
            holder.itemView.setTranslationY(0f);
            dispatchAddFinished(holder);
            return false;
        }
        View view = holder.itemView;
        view.setAlpha(0f);
        view.setTranslationY(12f);
        view.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(DURATION)
                .setInterpolator(new android.view.animation.DecelerateInterpolator())
                .setListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(android.animation.Animator animation) {
                        view.setAlpha(1f);
                        view.setTranslationY(0f);
                        dispatchAddFinished(holder);
                    }
                })
                .start();
        return true;
    }

    @Override
    public void endAnimation(RecyclerView.ViewHolder holder) {
        holder.itemView.animate().cancel();
        holder.itemView.setAlpha(1f);
        holder.itemView.setTranslationY(0f);
        super.endAnimation(holder);
    }
}
