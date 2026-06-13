package com.example.budgetapp;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.text.TextUtils;
import android.util.Log;

import com.example.budgetapp.database.Transaction;
import com.example.budgetapp.model.TransactionType;
import com.example.budgetapp.utils.CategoryManager;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import com.example.budgetapp.utils.DateUtils;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class BackupManager {

    private static final String JSON_FILE_NAME = "backup_data.json";
    private static final int MAX_IMPORT_LINES = 50000;
    private static final int MAX_JSON_SIZE_BYTES = 10 * 1024 * 1024;

    public static class BackupResult {
        public final boolean success;
        public final String errorMessage;

        public BackupResult(boolean success, String errorMessage) {
            this.success = success;
            this.errorMessage = errorMessage;
        }
    }

    private static final Set<String> EXCLUDED_PREF_KEYS = new HashSet<>();
    static {
        EXCLUDED_PREF_KEYS.add("custom_bg_day_uri");
        EXCLUDED_PREF_KEYS.add("custom_bg_night_uri");
        EXCLUDED_PREF_KEYS.add("enable_photo_backup");
        EXCLUDED_PREF_KEYS.add("photo_backup_uri");
        EXCLUDED_PREF_KEYS.add("enable_auto_backup");
        EXCLUDED_PREF_KEYS.add("auto_backup_uri");
        EXCLUDED_PREF_KEYS.add("auto_backup_freq");
        EXCLUDED_PREF_KEYS.add("auto_backup_change_count");
    }

    // ============================================================================================
    // JSON 导出/导入 (JSON格式)
    // ============================================================================================
    public static void exportToJson(Context context, Uri uri, List<Transaction> transactions) throws Exception {
        if (transactions == null) transactions = new ArrayList<>();
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        BackupData data = new BackupData(transactions);
        
        List<String> expenseCats = CategoryManager.getExpenseCategories(context);
        List<String> incomeCats = CategoryManager.getIncomeCategories(context);
        data.expenseCategories = expenseCats;
        data.incomeCategories = incomeCats;

        Map<String, List<String>> subMap = new HashMap<>();
        List<String> allCats = new ArrayList<>(expenseCats);
        allCats.addAll(incomeCats);
        for (String parent : allCats) {
            List<String> subs = CategoryManager.getSubCategories(context, parent);
            if (subs != null && !subs.isEmpty()) {
                subMap.put(parent, subs);
            }
        }
        data.subCategoryMap = subMap;
        data.subCategoryEnabled = CategoryManager.isSubCategoryEnabled(context);
        data.detailedCategoryEnabled = CategoryManager.isDetailedCategoryEnabled(context);

        SharedPreferences prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE);
        Map<String, BackupData.PrefItem> prefsMap = new HashMap<>();
        for (Map.Entry<String, ?> entry : prefs.getAll().entrySet()) {
            if (entry.getValue() != null) {
                String key = entry.getKey();
                if (EXCLUDED_PREF_KEYS.contains(key)) continue;
                String type = entry.getValue().getClass().getSimpleName();
                String value = String.valueOf(entry.getValue());
                if ("theme_mode".equals(key)) {
                    try {
                        int mode = Integer.parseInt(value);
                        if (mode == 3) continue;
                    } catch (NumberFormatException ignored) {}
                }
                prefsMap.put(key, new BackupData.PrefItem(type, value));
            }
        }
        data.appPreferences = prefsMap;

        String jsonString = gson.toJson(data);
        try (OutputStream outputStream = context.getContentResolver().openOutputStream(uri)) {
            outputStream.write(jsonString.getBytes(StandardCharsets.UTF_8));
        }

    }

    // ============================================================================================
    // 自动备份功能
    // ============================================================================================
    /**
     * 执行自动备份到指定文件夹（导出全部数据为JSON）
     */
    public static BackupResult performAutoBackup(Context context, List<Transaction> transactions) {
        SharedPreferences prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE);
        boolean isEnabled = prefs.getBoolean("enable_auto_backup", false);
        String uriStr = prefs.getString("auto_backup_uri", "");

        if (!isEnabled || uriStr.isEmpty()) {
            return new BackupResult(false, null);
        }

        try {
            Uri treeUri = Uri.parse(uriStr);
            androidx.documentfile.provider.DocumentFile tree = androidx.documentfile.provider.DocumentFile.fromTreeUri(context, treeUri);
            
            if (tree == null || !tree.exists()) {
                return new BackupResult(false, "备份目录不存在或权限已丢失，请重新设置备份路径");
            }

            String backupFileName = "Tally_自动备份_" + DateUtils.formatBackupTimestamp(new Date().getTime()) + ".json";
            androidx.documentfile.provider.DocumentFile backupFile = tree.createFile("application/json", backupFileName);

            if (backupFile == null || !backupFile.exists()) {
                return new BackupResult(false, "无法创建备份文件，存储空间可能不足");
            }

            exportToJson(context, backupFile.getUri(), transactions);
            return new BackupResult(true, null);

        } catch (Exception e) {
            Log.e("Tally", "Error", e);
            return new BackupResult(false, "自动备份失败：" + e.getMessage());
        }
    }

    /**
     * 增加账单变动计数，并在达到设定频次时触发自动备份
     */
    public static BackupResult incrementChangeCountAndBackup(Context context, List<Transaction> transactions) {
        SharedPreferences prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE);
        boolean isEnabled = prefs.getBoolean("enable_auto_backup", false);
        String uriStr = prefs.getString("auto_backup_uri", "");
        
        if (!isEnabled || uriStr.isEmpty()) {
            return new BackupResult(false, null);
        }

        int backupFreq = prefs.getInt("auto_backup_freq", 1);
        int currentCount = prefs.getInt("auto_backup_change_count", 0);
        
        currentCount++;
        prefs.edit().putInt("auto_backup_change_count", currentCount).apply();
        
        if (currentCount >= backupFreq) {
            BackupResult result = performAutoBackup(context, transactions);
            if (result.success) {
                prefs.edit().putInt("auto_backup_change_count", 0).apply();
                Log.d("Tally", "自动备份成功，变动计数已重置");
                return result;
            } else {
                prefs.edit().putInt("auto_backup_change_count", 0).apply();
                Log.d("Tally", "自动备份失败，变动计数已重置，将在下次达到频次时重试");
                return result;
            }
        } else {
            Log.d("Tally", "当前变动计数: " + currentCount + "，还需变动 " + (backupFreq - currentCount) + " 次触发备份");
            return new BackupResult(false, null);
        }
    }

    public static BackupData importFromJson(Context context, Uri uri) throws Exception {
        try (InputStream inputStream = context.getContentResolver().openInputStream(uri);
             BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
                if (sb.length() > MAX_JSON_SIZE_BYTES) {
                    throw new Exception("导入文件过大，超过10MB限制");
                }
            }
            Gson gson = new Gson();
            BackupData data = gson.fromJson(sb.toString(), BackupData.class);

            if (data.subCategoryMap != null) {
                for (Map.Entry<String, List<String>> entryMap : data.subCategoryMap.entrySet()) {
                    CategoryManager.saveSubCategories(context, entryMap.getKey(), entryMap.getValue());
                }
            }

            CategoryManager.setSubCategoryEnabled(context, data.subCategoryEnabled);
            CategoryManager.setDetailedCategoryEnabled(context, data.detailedCategoryEnabled);

            if (data.appPreferences != null) {
                SharedPreferences prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE);

                boolean wasCustomTheme = prefs.getInt("theme_mode", -1) == 3;
                boolean wasPhotoBackup = prefs.getBoolean("enable_photo_backup", false);
                boolean wasAutoBackup = prefs.getBoolean("enable_auto_backup", false);

                SharedPreferences.Editor editor = prefs.edit();
                for (Map.Entry<String, BackupData.PrefItem> entryMap : data.appPreferences.entrySet()) {
                    String key = entryMap.getKey();
                    BackupData.PrefItem item = entryMap.getValue();
                    if (item == null || item.value == null) continue;

                    if (EXCLUDED_PREF_KEYS.contains(key)) continue;

                    if ("theme_mode".equals(key)) {
                        try {
                            int mode = Integer.parseInt(item.value);
                            if (mode == 3 && !wasCustomTheme) continue;
                        } catch (NumberFormatException ignored) {}
                    }

                    try {
                        switch (item.type) {
                            case "Boolean": editor.putBoolean(key, Boolean.parseBoolean(item.value)); break;
                            case "Integer": editor.putInt(key, Integer.parseInt(item.value)); break;
                            case "Float": editor.putFloat(key, Float.parseFloat(item.value)); break;
                            case "Long": editor.putLong(key, Long.parseLong(item.value)); break;
                            default: editor.putString(key, item.value); break;
                        }
                    } catch (Exception e) {
                        Log.e("Tally", "恢复配置异常", e);
                    }
                }
                editor.apply();

                if (!wasCustomTheme) {
                    prefs.edit()
                            .remove("custom_bg_day_uri")
                            .remove("custom_bg_night_uri")
                            .apply();
                }

                if (!wasPhotoBackup) {
                    prefs.edit()
                            .putBoolean("enable_photo_backup", false)
                            .remove("photo_backup_uri")
                            .apply();
                }

                if (!wasAutoBackup) {
                    prefs.edit()
                            .putBoolean("enable_auto_backup", false)
                            .remove("auto_backup_uri")
                            .putInt("auto_backup_freq", 1)
                            .putInt("auto_backup_change_count", 0)
                            .apply();
                }
            }

            return data;
        }
    }

    // ============================================================================================
    // CSV 导出/导入（仅交易记录）
    // ============================================================================================

    /**
     * 只导出交易记录（不包含配置信息）
     * 统一入口：设置导出账单、自动备份、明细页手动导出 均调用此方法
     */
    public static void exportTransactionsOnly(Context context, Uri uri, List<Transaction> transactions) throws Exception {
        if (transactions == null) transactions = new ArrayList<>();

        StringBuilder csvBuilder = new StringBuilder();
        csvBuilder.append('\ufeff');

        csvBuilder.append("交易ID,时间,类型,分类,金额,记录标识,备注,二级分类,币种,对象\n");

        for (Transaction t : transactions) {
            csvBuilder.append(t.id).append(",");
            csvBuilder.append(DateUtils.formatDate(t.date)).append(",");
            csvBuilder.append(TransactionType.fromValue(t.type).getLabel()).append(",");
            csvBuilder.append(escapeCsv(t.category)).append(",");
            csvBuilder.append(String.format("%.2f", t.amount)).append(",");
            csvBuilder.append(escapeCsv(t.note)).append(",");
            csvBuilder.append(escapeCsv(t.remark)).append(",");
            csvBuilder.append(escapeCsv(t.subCategory)).append(",");
            String currency = (t.currencySymbol == null) ? "¥" : t.currencySymbol;
            csvBuilder.append(escapeCsv(currency)).append(",");
            csvBuilder.append(escapeCsv(t.targetObject != null ? t.targetObject : "")).append("\n");
        }

        try (OutputStream outputStream = context.getContentResolver().openOutputStream(uri)) {
            outputStream.write(csvBuilder.toString().getBytes(StandardCharsets.UTF_8));
        }
    }

    public static BackupData importTransactionsCsv(Context context, Uri uri) throws Exception {
        List<String> lines = new ArrayList<>();
        try (InputStream inputStream = context.getContentResolver().openInputStream(uri);
             BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            String line;
            int lineCount = 0;
            while ((line = reader.readLine()) != null) {
                if (++lineCount > MAX_IMPORT_LINES) {
                    throw new Exception("导入文件过大，超过" + MAX_IMPORT_LINES + "行限制");
                }
                lines.add(line);
            }
        }
        if (lines.isEmpty()) throw new Exception("文件内容为空");
        if (lines.get(0).startsWith("\ufeff")) lines.set(0, lines.get(0).substring(1));

        List<Transaction> transactions = new ArrayList<>();

        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i).trim();
            if (TextUtils.isEmpty(line)) continue;
            if (line.startsWith("交易ID,")) continue;

            List<String> tokens = parseCsvLine(line);
            if (tokens.size() < 5) continue;

            try {
                Transaction t = new Transaction();
                try { t.id = Integer.parseInt(tokens.get(0)); } catch (Exception e) { t.id = 0; }

                String timeStr = tokens.get(1).trim();
                try { t.date = DateUtils.parseDate(timeStr); } catch (Exception e) { continue; }

                String typeStr = tokens.get(2).trim();
                t.type = TransactionType.fromLabel(typeStr).getValue();

                t.category = (tokens.size() > 3) ? tokens.get(3).trim() : "";
                t.amount = (tokens.size() > 4) ? parseDoubleSafe(tokens.get(4).trim()) : 0.0;
                t.note = (tokens.size() > 5) ? tokens.get(5).trim() : "";
                t.remark = (tokens.size() > 6) ? tokens.get(6).trim() : "";
                t.subCategory = (tokens.size() > 7) ? tokens.get(7).trim() : "";
                if (tokens.size() > 8) {
                    String currencySymbol = tokens.get(8).trim();
                    t.currencySymbol = TextUtils.isEmpty(currencySymbol) ? "¥" : currencySymbol;
                } else {
                    t.currencySymbol = "¥";
                }
                t.targetObject = (tokens.size() > 9) ? tokens.get(9).trim() : "";

                transactions.add(t);
            } catch (Exception e) {
                Log.e("Tally", "解析交易行失败: " + line, e);
            }
        }

        if (transactions.isEmpty()) throw new Exception("未找到有效的交易记录");
        return new BackupData(transactions);
    }

    // ============================================================================================
    // 微信账单导入 (支持 .xlsx 和 .csv)
    // ============================================================================================

    private static String[] matchOrCreateCategory(String rawCategory, int type, List<String> expCats, List<String> incCats, Map<String, List<String>> subCatMap) {
        if (TextUtils.isEmpty(rawCategory) || "/".equals(rawCategory)) {
            rawCategory = "其它";
        }

        List<String> targetList = (type == 1) ? incCats : expCats;

        for (String cat : targetList) {
            if (rawCategory.contains(cat) || cat.contains(rawCategory)) {
                return new String[]{cat, ""};
            }
        }

        for (String parentCat : targetList) {
            List<String> subs = subCatMap.get(parentCat);
            if (subs != null) {
                for (String sub : subs) {
                    if (rawCategory.contains(sub) || sub.contains(rawCategory)) {
                        return new String[]{parentCat, sub};
                    }
                }
            }
        }

        if (!targetList.contains(rawCategory)) {
            targetList.add(rawCategory);
        }
        return new String[]{rawCategory, ""};
    }

    public static BackupData importFromWeChat(Context context, Uri uri) throws Exception {
        return importWeChatAsCsv(context, uri);
    }

    private static BackupData importWeChatAsCsv(Context context, Uri uri) throws Exception {
        List<Transaction> transactions = new ArrayList<>();

        List<String> expCats = new ArrayList<>(CategoryManager.getExpenseCategories(context));
        List<String> incCats = new ArrayList<>(CategoryManager.getIncomeCategories(context));
        Map<String, List<String>> subCatMap = new HashMap<>();
        for (String cat : expCats) {
            List<String> subs = CategoryManager.getSubCategories(context, cat);
            if (subs != null && !subs.isEmpty()) subCatMap.put(cat, subs);
        }
        for (String cat : incCats) {
            List<String> subs = CategoryManager.getSubCategories(context, cat);
            if (subs != null && !subs.isEmpty()) subCatMap.put(cat, subs);
        }

        try (InputStream inputStream = context.getContentResolver().openInputStream(uri);
             BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {

            String line;
            boolean isDataSection = false;
            int lineCount = 0;

            while ((line = reader.readLine()) != null) {
                if (++lineCount > MAX_IMPORT_LINES) {
                    throw new Exception("导入文件过大，超过" + MAX_IMPORT_LINES + "行限制");
                }
                if (line.startsWith("\ufeff")) line = line.substring(1);
                if (TextUtils.isEmpty(line.trim())) continue;

                if (line.contains("交易时间") && line.contains("收/支") && line.contains("金额(元)")) {
                    isDataSection = true;
                    continue;
                }

                if (!isDataSection) continue;

                List<String> tokens = parseCsvLine(line);
                if (tokens.size() < 7) continue;

                Transaction t = new Transaction();
                String timeStr = tokens.get(0);
                try {
                    t.date = DateUtils.parseDateTime(timeStr);
                } catch (Exception e) {
                    continue;
                }

                t.note = timeStr;
                t.remark = tokens.get(2);

                String typeStr = tokens.get(4);
                if ("收入".equals(typeStr)) t.type = 1;
                else t.type = 0;

                String amountStr = tokens.get(5).replace("¥", "").replace(",", "").trim();
                t.amount = parseDoubleSafe(amountStr);

                String rawCategory = tokens.size() > 1 ? tokens.get(1).trim() : "其它";
                String[] matchedCat = matchOrCreateCategory(rawCategory, t.type, expCats, incCats, subCatMap);
                t.category = matchedCat[0];
                t.subCategory = matchedCat[1];

                transactions.add(t);
            }
        }
        BackupData data = new BackupData(transactions);
        data.expenseCategories = expCats;
        data.incomeCategories = incCats;
        return data;
    }

    // ============================================================================================
    // 支付宝账单导入 (支持 CSV, 动态列索引与双重编码尝试)
    // ============================================================================================
    public static BackupData importFromAlipay(Context context, Uri uri) throws Exception {
        BackupData data = parseAlipayCsvWithEncoding(context, uri, "GBK");
        if (data.records.isEmpty()) {
            data = parseAlipayCsvWithEncoding(context, uri, "UTF-8");
        }
        return data;
    }

    private static BackupData parseAlipayCsvWithEncoding(Context context, Uri uri, String charsetName) throws Exception {
        List<Transaction> transactions = new ArrayList<>();

        List<String> expCats = new ArrayList<>(CategoryManager.getExpenseCategories(context));
        List<String> incCats = new ArrayList<>(CategoryManager.getIncomeCategories(context));
        Map<String, List<String>> subCatMap = new HashMap<>();
        for (String cat : expCats) {
            List<String> subs = CategoryManager.getSubCategories(context, cat);
            if (subs != null && !subs.isEmpty()) subCatMap.put(cat, subs);
        }
        for (String cat : incCats) {
            List<String> subs = CategoryManager.getSubCategories(context, cat);
            if (subs != null && !subs.isEmpty()) subCatMap.put(cat, subs);
        }

        try (InputStream inputStream = context.getContentResolver().openInputStream(uri);
             BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, charsetName))) {

            String line;
            boolean isDataSection = false;

            int timeIdx = -1, typeIdx = -1, amountIdx = -1, descIdx = -1, paymentIdx = -1, tradeCatIdx = -1;

            while ((line = reader.readLine()) != null) {
                if (line.startsWith("\ufeff")) line = line.substring(1);
                if (TextUtils.isEmpty(line.trim())) continue;

                if (isDataSection && line.startsWith("---")) break;

                if (!isDataSection && line.contains("交易时间") && line.contains("收/支") && line.contains("金额")) {
                    isDataSection = true;
                    List<String> headers = parseCsvLine(line);
                    for (int i = 0; i < headers.size(); i++) {
                        String h = headers.get(i).trim();
                        if (h.contains("交易时间")) timeIdx = i;
                        else if (h.contains("收/支")) typeIdx = i;
                        else if (h.contains("金额")) amountIdx = i;
                        else if (h.contains("商品说明") || h.contains("商品名称")) descIdx = i;
                        else if (h.contains("收/付款方式")) paymentIdx = i;
                        else if (h.contains("交易分类")) tradeCatIdx = i;
                    }
                    continue;
                }

                if (!isDataSection) continue;

                List<String> tokens = parseCsvLine(line);
                int maxRequiredIdx = Math.max(Math.max(timeIdx, typeIdx), Math.max(amountIdx, descIdx));
                maxRequiredIdx = Math.max(maxRequiredIdx, paymentIdx);
                maxRequiredIdx = Math.max(maxRequiredIdx, tradeCatIdx);

                if (timeIdx == -1 || typeIdx == -1 || amountIdx == -1 || tokens.size() <= maxRequiredIdx) {
                    continue;
                }

                Transaction t = new Transaction();
                String timeStr = tokens.get(timeIdx).trim();
                try {
                    t.date = DateUtils.parseDateTime(timeStr);
                } catch (Exception e) {
                    continue;
                }

                t.note = timeStr;
                t.remark = (descIdx != -1) ? tokens.get(descIdx).trim() : "";

                String typeStr = tokens.get(typeIdx).trim();
                if ("收入".equals(typeStr)) {
                    t.type = 1;
                } else if ("支出".equals(typeStr)) {
                    t.type = 0;
                } else {
                    t.type = 0;
                }

                String amountStr = tokens.get(amountIdx).replace("¥", "").replace(",", "").trim();
                t.amount = parseDoubleSafe(amountStr);

                String rawCategory = (tradeCatIdx != -1) ? tokens.get(tradeCatIdx).trim() : "其它";
                String[] matchedCat = matchOrCreateCategory(rawCategory, t.type, expCats, incCats, subCatMap);
                t.category = matchedCat[0];
                t.subCategory = matchedCat[1];

                transactions.add(t);
            }
        }
        BackupData data = new BackupData(transactions);
        data.expenseCategories = expCats;
        data.incomeCategories = incCats;
        return data;
    }

    private static String joinSet(Set<String> set) {
        if (set == null || set.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (String s : set) {
            if (sb.length() > 0) sb.append("|");
            sb.append(s);
        }
        return sb.toString();
    }

    private static Set<String> splitSet(String val) {
        Set<String> set = new HashSet<>();
        if (TextUtils.isEmpty(val)) return set;
        String[] parts = val.split("\\|");
        for (String p : parts) {
            if (!TextUtils.isEmpty(p)) set.add(p);
        }
        return set;
    }

    private static List<String> parseCsvLine(String line) {
        List<String> tokens = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        boolean inQuotes = false;

        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '\"') {
                if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == '\"') {
                    sb.append('\"'); i++; 
                } else {
                    inQuotes = !inQuotes;
                }
            } else if (c == ',' && !inQuotes) {
                tokens.add(sb.toString()); sb.setLength(0);
            } else {
                sb.append(c);
            }
        }
        tokens.add(sb.toString());
        return tokens;
    }

    private static double parseDoubleSafe(String val) {
        try { return Double.parseDouble(val); } catch (Exception e) { return 0.0; }
    }
    
    private static String escapeCsv(String value) {
        if (value == null) return "";
        String result = value.replace("\"", "\"\""); 
        if (result.contains(",") || result.contains("\n") || result.contains("\"")) {
            return "\"" + result + "\"";
        }
        return result;
    }
}
