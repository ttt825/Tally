package com.example.budgetapp.util;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.net.Uri;
import android.util.Log;

import androidx.documentfile.provider.DocumentFile;

import java.io.OutputStream;
import java.util.Locale;
import java.util.Random;

/**
 * 截图自动保存管理器
 * 负责管理AI记账截图自动保存功能的核心逻辑
 */
public class ScreenshotAutoSaveManager {
    private static final String TAG = "ScreenshotAutoSave";
    private static final String PREFS_NAME = "app_prefs";
    private static final String KEY_ENABLED = "ai_screenshot_auto_save";
    private static final String KEY_PHOTO_BACKUP_ENABLED = "enable_photo_backup";
    private static final String KEY_PHOTO_BACKUP_URI = "photo_backup_uri";
    
    private final Context context;
    private final SharedPreferences prefs;
    
    public ScreenshotAutoSaveManager(Context context) {
        this.context = context.getApplicationContext();
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }
    
    /**
     * 检查功能是否启用
     * @return true if enabled, false otherwise
     */
    public boolean isEnabled() {
        return prefs.getBoolean(KEY_ENABLED, false);
    }
    
    /**
     * 设置功能开关状态
     * @param enabled 是否启用
     */
    public void setEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_ENABLED, enabled).apply();
    }
    
    /**
     * 检查照片备份前置条件是否满足
     * @return ValidationResult 包含是否通过和错误消息
     */
    public ValidationResult validatePrerequisites() {
        boolean photoBackupEnabled = prefs.getBoolean(KEY_PHOTO_BACKUP_ENABLED, false);
        if (!photoBackupEnabled) {
            return ValidationResult.error("需要先开启照片备注");
        }
        
        String photoBackupUri = prefs.getString(KEY_PHOTO_BACKUP_URI, "");
        if (photoBackupUri.isEmpty()) {
            return ValidationResult.error("需要先设置照片存储路径");
        }
        
        return ValidationResult.success();
    }
    
    /**
     * 保存截图到照片备份目录
     * @param bitmap 截图Bitmap对象
     * @return 保存成功返回文件路径，失败返回null
     */
    public String saveScreenshot(Bitmap bitmap) {
        if (!isEnabled()) {
            return null;
        }
        
        String uriString = prefs.getString(KEY_PHOTO_BACKUP_URI, "");
        if (uriString.isEmpty()) {
            Log.w(TAG, "Photo backup URI not configured");
            return null;
        }
        
        try {
            Uri treeUri = Uri.parse(uriString);
            DocumentFile directory = DocumentFile.fromTreeUri(context, treeUri);
            
            if (directory == null || !directory.exists() || !directory.canWrite()) {
                Log.e(TAG, "Photo backup directory not accessible");
                return null;
            }
            
            // 生成唯一文件名: screenshot_{timestamp}_{random}.jpg
            String fileName = generateUniqueFileName();
            DocumentFile imageFile = directory.createFile("image/jpeg", fileName);
            
            if (imageFile == null) {
                Log.e(TAG, "Failed to create image file");
                return null;
            }
            
            // 保存Bitmap到文件 (JPEG, 85% quality)
            try (OutputStream outputStream = context.getContentResolver().openOutputStream(imageFile.getUri())) {
                if (outputStream == null) {
                    Log.e(TAG, "Failed to open output stream");
                    return null;
                }
                bitmap.compress(Bitmap.CompressFormat.JPEG, 85, outputStream);
                outputStream.flush();
            }
            
            return imageFile.getUri().toString();
            
        } catch (Exception e) {
            Log.e(TAG, "Failed to save screenshot: " + e.getMessage(), e);
            return null;
        }
    }
    
    /**
     * 生成唯一文件名
     * @return 格式: screenshot_{timestamp}_{random}.jpg
     */
    private String generateUniqueFileName() {
        long timestamp = System.currentTimeMillis();
        int random = new Random().nextInt(10000);
        return String.format(Locale.US, "screenshot_%d_%04d.jpg", timestamp, random);
    }
    
    /**
     * 验证结果类
     */
    public static class ValidationResult {
        public final boolean isValid;
        public final String errorMessage;
        
        private ValidationResult(boolean isValid, String errorMessage) {
            this.isValid = isValid;
            this.errorMessage = errorMessage;
        }
        
        public static ValidationResult success() {
            return new ValidationResult(true, null);
        }
        
        public static ValidationResult error(String message) {
            return new ValidationResult(false, message);
        }
    }
}
