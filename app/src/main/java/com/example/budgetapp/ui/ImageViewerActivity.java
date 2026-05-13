package com.example.budgetapp.ui;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageButton;
import android.widget.ImageView;

import androidx.appcompat.app.AppCompatActivity;

import com.example.budgetapp.R;

/**
 * 全屏查看图片的 Activity。
 * 通过静态 Bitmap 引用传递图片（避免 Intent 传输大图的限制）。
 */
public class ImageViewerActivity extends AppCompatActivity {

    /** 静态引用，用于传递要显示的 Bitmap */
    private static Bitmap pendingBitmap;

    public static void setPendingBitmap(Bitmap bitmap) {
        pendingBitmap = bitmap;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 全屏沉浸式
        getWindow().setFlags(
                WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN
        );

        setContentView(R.layout.activity_image_viewer);

        ImageView ivImage = findViewById(R.id.iv_fullscreen_image);
        ImageButton btnClose = findViewById(R.id.btn_close);

        if (pendingBitmap != null) {
            ivImage.setImageBitmap(pendingBitmap);
        }

        // 点击图片关闭
        ivImage.setOnClickListener(v -> finish());
        btnClose.setOnClickListener(v -> finish());
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // 不要在这里回收 bitmap，因为聊天列表还在引用它
        pendingBitmap = null;
    }
}
