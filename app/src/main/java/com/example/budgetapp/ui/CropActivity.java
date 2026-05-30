package com.example.budgetapp.ui;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.budgetapp.R;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

public class CropActivity extends AppCompatActivity {

    public static final String EXTRA_SOURCE_URI = "source_uri";
    public static final String EXTRA_ASPECT_X = "aspect_x";
    public static final String EXTRA_ASPECT_Y = "aspect_y";
    public static final String EXTRA_CROP_SUFFIX = "crop_suffix";
    public static final String EXTRA_CROPPED_PATH = "cropped_path";

    private CropImageView cropImageView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_crop);

        cropImageView = findViewById(R.id.crop_image_view);

        int aspectX = getIntent().getIntExtra(EXTRA_ASPECT_X, 9);
        int aspectY = getIntent().getIntExtra(EXTRA_ASPECT_Y, 20);
        cropImageView.setAspectRatio(aspectX, aspectY);

        String uriStr = getIntent().getStringExtra(EXTRA_SOURCE_URI);
        if (uriStr != null) {
            loadImage(Uri.parse(uriStr));
        } else {
            Toast.makeText(this, "无法加载图片", Toast.LENGTH_SHORT).show();
            setResult(RESULT_CANCELED);
            finish();
        }

        findViewById(R.id.btn_cancel_crop).setOnClickListener(v -> {
            setResult(RESULT_CANCELED);
            finish();
        });

        findViewById(R.id.btn_confirm_crop).setOnClickListener(v -> {
            Bitmap cropped = cropImageView.getCroppedBitmap();
            if (cropped != null) {
                try {
                    String suffix = getIntent().getStringExtra(EXTRA_CROP_SUFFIX);
                    if (suffix == null) suffix = "day";

                    File dir = new File(getFilesDir(), "Pictures");
                    if (!dir.exists()) dir.mkdirs();

                    File destFile = new File(dir, "bg_" + suffix + ".png");
                    if (destFile.exists()) destFile.delete();

                    FileOutputStream fos = new FileOutputStream(destFile);
                    cropped.compress(Bitmap.CompressFormat.PNG, 100, fos);
                    fos.flush();
                    fos.close();
                    cropped.recycle();

                    Intent result = new Intent();
                    result.putExtra(EXTRA_CROPPED_PATH, destFile.getAbsolutePath());
                    setResult(RESULT_OK, result);
                    finish();
                } catch (Exception e) {
                    Toast.makeText(this, "保存裁剪图片失败", Toast.LENGTH_SHORT).show();
                }
            } else {
                Toast.makeText(this, "裁剪失败", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void loadImage(Uri uri) {
        try {
            InputStream is = getContentResolver().openInputStream(uri);
            if (is == null) {
                Toast.makeText(this, "无法加载图片", Toast.LENGTH_SHORT).show();
                setResult(RESULT_CANCELED);
                finish();
                return;
            }

            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            BitmapFactory.decodeStream(is, null, options);
            is.close();

            int maxDim = Math.max(options.outWidth, options.outHeight);
            int sampleSize = 1;
            while (maxDim / sampleSize > 2048) {
                sampleSize *= 2;
            }

            is = getContentResolver().openInputStream(uri);
            options.inJustDecodeBounds = false;
            options.inSampleSize = sampleSize;
            Bitmap bitmap = BitmapFactory.decodeStream(is, null, options);
            is.close();

            if (bitmap != null) {
                cropImageView.setImageBitmap(bitmap);
            } else {
                Toast.makeText(this, "无法加载图片", Toast.LENGTH_SHORT).show();
                setResult(RESULT_CANCELED);
                finish();
            }
        } catch (Exception e) {
            Toast.makeText(this, "无法加载图片", Toast.LENGTH_SHORT).show();
            setResult(RESULT_CANCELED);
            finish();
        }
    }
}
