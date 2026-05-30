package com.example.budgetapp.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

public class CropImageView extends View {
    private Bitmap bitmap;
    private final Matrix matrix = new Matrix();
    private final float[] matrixValues = new float[9];
    private RectF cropRect;
    private int aspectX = 9;
    private int aspectY = 20;
    private float minScale = 1f;

    private float lastX, lastY;
    private float lastSpan;
    private boolean isZooming;

    private final Paint overlayPaint;
    private final Paint borderPaint;
    private final Paint cornerPaint;

    public CropImageView(Context context) {
        this(context, null);
    }

    public CropImageView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public CropImageView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        overlayPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        overlayPaint.setColor(0x99000000);
        overlayPaint.setStyle(Paint.Style.FILL);

        borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        borderPaint.setColor(Color.WHITE);
        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setStrokeWidth(dpToPx(1.5f));

        cornerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        cornerPaint.setColor(Color.WHITE);
        cornerPaint.setStyle(Paint.Style.STROKE);
        cornerPaint.setStrokeWidth(dpToPx(3));
    }

    private float dpToPx(float dp) {
        return dp * getResources().getDisplayMetrics().density;
    }

    public void setImageBitmap(Bitmap bm) {
        this.bitmap = bm;
        if (getWidth() > 0 && getHeight() > 0) {
            calculateCropRect();
            calculateInitialMatrix();
        }
        invalidate();
    }

    public void setAspectRatio(int x, int y) {
        this.aspectX = x;
        this.aspectY = y;
    }

    private void calculateCropRect() {
        int viewWidth = getWidth();
        int viewHeight = getHeight();
        if (viewWidth <= 0 || viewHeight <= 0) return;

        float cropAspect = (float) aspectX / aspectY;
        float viewAspect = (float) viewWidth / viewHeight;

        float cropWidth, cropHeight;
        if (viewAspect > cropAspect) {
            cropHeight = viewHeight * 0.8f;
            cropWidth = cropHeight * cropAspect;
        } else {
            cropWidth = viewWidth * 0.8f;
            cropHeight = cropWidth / cropAspect;
        }

        float left = (viewWidth - cropWidth) / 2;
        float top = (viewHeight - cropHeight) / 2;
        cropRect = new RectF(left, top, left + cropWidth, top + cropHeight);
    }

    private void calculateInitialMatrix() {
        if (bitmap == null || cropRect == null) return;

        float scaleX = cropRect.width() / bitmap.getWidth();
        float scaleY = cropRect.height() / bitmap.getHeight();
        float scale = Math.max(scaleX, scaleY);
        minScale = scale;

        matrix.reset();
        matrix.postScale(scale, scale);

        float scaledWidth = bitmap.getWidth() * scale;
        float scaledHeight = bitmap.getHeight() * scale;
        float dx = cropRect.left + (cropRect.width() - scaledWidth) / 2;
        float dy = cropRect.top + (cropRect.height() - scaledHeight) / 2;
        matrix.postTranslate(dx, dy);

        invalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        calculateCropRect();
        if (bitmap != null) {
            calculateInitialMatrix();
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if (bitmap != null) {
            canvas.drawBitmap(bitmap, matrix, null);
        }

        if (cropRect != null) {
            canvas.drawRect(0, 0, getWidth(), cropRect.top, overlayPaint);
            canvas.drawRect(0, cropRect.bottom, getWidth(), getHeight(), overlayPaint);
            canvas.drawRect(0, cropRect.top, cropRect.left, cropRect.bottom, overlayPaint);
            canvas.drawRect(cropRect.right, cropRect.top, getWidth(), cropRect.bottom, overlayPaint);

            canvas.drawRect(cropRect, borderPaint);

            float cornerLen = dpToPx(24);
            float cornerOff = dpToPx(1);
            float l = cropRect.left - cornerOff;
            float t = cropRect.top - cornerOff;
            float r = cropRect.right + cornerOff;
            float b = cropRect.bottom + cornerOff;

            canvas.drawLine(l, t, l + cornerLen, t, cornerPaint);
            canvas.drawLine(l, t, l, t + cornerLen, cornerPaint);

            canvas.drawLine(r, t, r - cornerLen, t, cornerPaint);
            canvas.drawLine(r, t, r, t + cornerLen, cornerPaint);

            canvas.drawLine(l, b, l + cornerLen, b, cornerPaint);
            canvas.drawLine(l, b, l, b - cornerLen, cornerPaint);

            canvas.drawLine(r, b, r - cornerLen, b, cornerPaint);
            canvas.drawLine(r, b, r, b - cornerLen, cornerPaint);

            Paint gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            gridPaint.setColor(0x33FFFFFF);
            gridPaint.setStyle(Paint.Style.STROKE);
            gridPaint.setStrokeWidth(dpToPx(0.5f));

            float thirdW = cropRect.width() / 3;
            float thirdH = cropRect.height() / 3;
            for (int i = 1; i < 3; i++) {
                float x = cropRect.left + thirdW * i;
                canvas.drawLine(x, cropRect.top, x, cropRect.bottom, gridPaint);
                float y = cropRect.top + thirdH * i;
                canvas.drawLine(cropRect.left, y, cropRect.right, y, gridPaint);
            }
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (bitmap == null || cropRect == null) return false;

        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                lastX = event.getX();
                lastY = event.getY();
                isZooming = false;
                break;

            case MotionEvent.ACTION_POINTER_DOWN:
                lastSpan = getSpan(event);
                isZooming = true;
                break;

            case MotionEvent.ACTION_MOVE:
                if (isZooming && event.getPointerCount() >= 2) {
                    float span = getSpan(event);
                    if (lastSpan > 0) {
                        float scaleFactor = span / lastSpan;
                        matrix.getValues(matrixValues);
                        float currentScale = matrixValues[Matrix.MSCALE_X];
                        float newScale = currentScale * scaleFactor;
                        if (newScale >= minScale) {
                            matrix.postScale(scaleFactor, scaleFactor,
                                    (event.getX(0) + event.getX(1)) / 2,
                                    (event.getY(0) + event.getY(1)) / 2);
                        }
                    }
                    lastSpan = span;
                } else if (!isZooming && event.getPointerCount() == 1) {
                    float dx = event.getX() - lastX;
                    float dy = event.getY() - lastY;
                    matrix.postTranslate(dx, dy);
                    lastX = event.getX();
                    lastY = event.getY();
                }
                invalidate();
                break;

            case MotionEvent.ACTION_UP:
                isZooming = false;
                constrainMatrix();
                invalidate();
                break;

            case MotionEvent.ACTION_POINTER_UP:
                isZooming = false;
                constrainMatrix();
                invalidate();
                break;
        }
        return true;
    }

    private void constrainMatrix() {
        matrix.getValues(matrixValues);
        float scale = matrixValues[Matrix.MSCALE_X];
        float transX = matrixValues[Matrix.MTRANS_X];
        float transY = matrixValues[Matrix.MTRANS_Y];

        if (scale < minScale) {
            calculateInitialMatrix();
            return;
        }

        float scaledWidth = bitmap.getWidth() * scale;
        float scaledHeight = bitmap.getHeight() * scale;

        float minTransX, maxTransX;
        if (scaledWidth > cropRect.width()) {
            minTransX = cropRect.right - scaledWidth;
            maxTransX = cropRect.left;
        } else {
            float center = cropRect.left + (cropRect.width() - scaledWidth) / 2;
            minTransX = center;
            maxTransX = center;
        }

        float minTransY, maxTransY;
        if (scaledHeight > cropRect.height()) {
            minTransY = cropRect.bottom - scaledHeight;
            maxTransY = cropRect.top;
        } else {
            float center = cropRect.top + (cropRect.height() - scaledHeight) / 2;
            minTransY = center;
            maxTransY = center;
        }

        matrixValues[Matrix.MTRANS_X] = Math.max(minTransX, Math.min(maxTransX, transX));
        matrixValues[Matrix.MTRANS_Y] = Math.max(minTransY, Math.min(maxTransY, transY));
        matrix.setValues(matrixValues);
    }

    private float getSpan(MotionEvent event) {
        if (event.getPointerCount() < 2) return 0;
        float dx = event.getX(0) - event.getX(1);
        float dy = event.getY(0) - event.getY(1);
        return (float) Math.sqrt(dx * dx + dy * dy);
    }

    public Bitmap getCroppedBitmap() {
        if (bitmap == null || cropRect == null) return null;

        matrix.getValues(matrixValues);
        float scale = matrixValues[Matrix.MSCALE_X];
        float transX = matrixValues[Matrix.MTRANS_X];
        float transY = matrixValues[Matrix.MTRANS_Y];

        int bmpX = Math.max(0, Math.round((cropRect.left - transX) / scale));
        int bmpY = Math.max(0, Math.round((cropRect.top - transY) / scale));
        int bmpWidth = Math.min(bitmap.getWidth() - bmpX, Math.round(cropRect.width() / scale));
        int bmpHeight = Math.min(bitmap.getHeight() - bmpY, Math.round(cropRect.height() / scale));

        if (bmpWidth <= 0 || bmpHeight <= 0) return null;

        return Bitmap.createBitmap(bitmap, bmpX, bmpY, bmpWidth, bmpHeight);
    }
}
