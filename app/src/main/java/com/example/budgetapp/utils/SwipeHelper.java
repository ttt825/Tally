package com.example.budgetapp.utils;

import android.view.MotionEvent;
import android.view.ViewConfiguration;
import android.view.animation.DecelerateInterpolator;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

/**
 * 水平跟手滑动引擎
 * 统一处理 RecyclerView 的左右跟手滑动、阻尼、回弹和翻页动画
 */
public class SwipeHelper {

    /**
     * 滑动方向回调
     * @param direction -1 表示右滑（上一页），1 表示左滑（下一页）
     */
    public interface OnSwipeListener {
        void onSwipe(int direction);
    }

    /**
     * 为 RecyclerView 设置跟手滑动引擎
     * @param recyclerView 目标 RecyclerView
     * @param listener 滑动方向回调
     */
    public static void setup(RecyclerView recyclerView, OnSwipeListener listener) {
        int touchSlop = ViewConfiguration.get(recyclerView.getContext()).getScaledTouchSlop();

        recyclerView.addOnItemTouchListener(new RecyclerView.OnItemTouchListener() {
            private float initialX = 0f;
            private float initialY = 0f;
            private boolean isHorizontalSwipe = false;

            @Override
            public boolean onInterceptTouchEvent(@NonNull RecyclerView rv, @NonNull MotionEvent e) {
                switch (e.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        initialX = e.getRawX();
                        initialY = e.getRawY();
                        isHorizontalSwipe = false;
                        break;
                    case MotionEvent.ACTION_MOVE:
                        float dx = e.getRawX() - initialX;
                        float dy = e.getRawY() - initialY;
                        if (!isHorizontalSwipe && Math.abs(dx) > touchSlop && Math.abs(dx) > Math.abs(dy)) {
                            isHorizontalSwipe = true;
                            if (rv.getParent() != null) {
                                rv.getParent().requestDisallowInterceptTouchEvent(true);
                            }
                            return true;
                        }
                        break;
                }
                return false;
            }

            @Override
            public void onTouchEvent(@NonNull RecyclerView rv, @NonNull MotionEvent e) {
                if (!isHorizontalSwipe) return;

                float dx = e.getRawX() - initialX;
                float screenWidth = rv.getWidth();
                if (screenWidth == 0) screenWidth = 1080;

                switch (e.getAction()) {
                    case MotionEvent.ACTION_MOVE:
                        rv.setTranslationX(dx * 0.6f);
                        rv.setAlpha(1f - (Math.abs(dx) / screenWidth) * 0.5f);
                        break;

                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        if (dx > screenWidth * 0.2f) {
                            finishSwipeAnimation(rv, screenWidth, -1, listener);
                        } else if (dx < -screenWidth * 0.2f) {
                            finishSwipeAnimation(rv, -screenWidth, 1, listener);
                        } else {
                            rv.animate().translationX(0f).alpha(1f).setDuration(250)
                                    .setInterpolator(new DecelerateInterpolator()).start();
                        }
                        isHorizontalSwipe = false;
                        break;
                }
            }

            @Override
            public void onRequestDisallowInterceptTouchEvent(boolean disallowIntercept) {}
        });
    }

    /**
     * 结算动画：滑出 -> 回调切换数据 -> 从另一侧滑入
     * @param rv 目标 RecyclerView
     * @param targetTranslationX 滑出目标位置
     * @param direction 滑动方向 (-1=上一页, 1=下一页)
     * @param listener 滑动回调
     */
    public static void finishSwipeAnimation(RecyclerView rv, float targetTranslationX, int direction, OnSwipeListener listener) {
        rv.animate()
                .translationX(targetTranslationX)
                .alpha(0f)
                .setDuration(150)
                .withEndAction(() -> {
                    if (listener != null) {
                        listener.onSwipe(direction);
                    }

                    rv.setTranslationX(-targetTranslationX * 0.5f);

                    rv.animate()
                            .translationX(0f)
                            .alpha(1f)
                            .setDuration(300)
                            .setInterpolator(new DecelerateInterpolator())
                            .start();
                })
                .start();
    }
}
