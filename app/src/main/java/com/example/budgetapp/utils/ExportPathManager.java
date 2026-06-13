package com.example.budgetapp.utils;

import android.util.Log;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;

import androidx.documentfile.provider.DocumentFile;

import java.util.Date;
import java.util.Locale;

/**
 * 导出路径管理器
 * 管理导出文件的默认路径和文件名生成
 */
public class ExportPathManager {

    private static final String PREFS_NAME = "app_prefs";
    private static final String KEY_AUTO_BACKUP_URI = "auto_backup_uri";
    private static final String KEY_PHOTO_BACKUP_URI = "photo_backup_uri";

    /**
     * 获取自动备份的文件夹 URI
     * @return 用户设置的自动备份路径，如果未设置返回 null
     */
    public static String getAutoBackupUri(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getString(KEY_AUTO_BACKUP_URI, null);
    }

    /**
     * 检查是否已设置自动备份路径
     */
    public static boolean hasAutoBackupPath(Context context) {
        return getAutoBackupUri(context) != null;
    }

    /**
     * 生成 JSON 导出文件名
     * 格式: Tally_yyyy-MM-dd_HH-mm-ss.json
     */
    public static String generateZipFileName() {
        String timeStr = DateUtils.formatExportTimestamp(new Date().getTime());
        return "Tally_" + timeStr + ".json";
    }

    /**
     * 生成 Excel/CSV 导出文件名
     * 格式: 账单导出_yyyy-MM-dd_HH-mm-ss.csv
     */
    public static String generateExcelFileName() {
        String timeStr = DateUtils.formatBackupTimestamp(new Date().getTime());
        return "账单导出_" + timeStr + ".csv";
    }

    /**
     * 在用户设置的自动备份目录中创建导出文件
     * @param context 上下文
     * @param fileName 文件名
     * @param mimeType MIME类型
     * @return 创建的文件 URI，如果失败返回 null
     */
    public static Uri createExportFileInAutoBackupPath(Context context, String fileName, String mimeType) {
        String uriStr = getAutoBackupUri(context);
        if (uriStr == null || uriStr.isEmpty()) {
            return null;
        }

        try {
            Uri treeUri = Uri.parse(uriStr);
            DocumentFile tree = DocumentFile.fromTreeUri(context, treeUri);

            if (tree == null || !tree.exists()) {
                return null;
            }

            // 查找是否已存在同名文件
            DocumentFile existingFile = tree.findFile(fileName);
            if (existingFile != null) {
                // 删除已存在的文件
                existingFile.delete();
            }

            // 创建新文件
            DocumentFile newFile = tree.createFile(mimeType, fileName);
            if (newFile != null && newFile.exists()) {
                return newFile.getUri();
            }
        } catch (Exception e) {
            Log.e("Tally", "Error", e);
        }

        return null;
    }

    /**
     * 获取导出文件的默认显示名称（用于系统文件选择器的默认文件名）
     */
    public static String getDefaultExportFileName(boolean isZip) {
        if (isZip) {
            return generateZipFileName();
        } else {
            return generateExcelFileName();
        }
    }
}
