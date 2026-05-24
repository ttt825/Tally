package com.example.budgetapp.util;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

import com.example.budgetapp.BackupManager;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class AssetCategoryManager {
    private static final String PREF_NAME = "asset_category_prefs";
    private static final String KEY_ENABLE_ASSET_CATEGORY = "enable_asset_category";
    private static final String KEY_ASSET_CATEGORIES = "asset_categories";

    public static boolean isEnabled(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        return prefs.getBoolean(KEY_ENABLE_ASSET_CATEGORY, false);
    }

    public static void setEnabled(Context context, boolean enabled) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        prefs.edit().putBoolean(KEY_ENABLE_ASSET_CATEGORY, enabled).apply();
        BackupManager.triggerAutoUploadIfEnabled(context);
    }

    public static List<String> getCategories(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        String savedString = prefs.getString(KEY_ASSET_CATEGORIES, "");
        if (savedString == null || savedString.isEmpty()) {
            return new ArrayList<>();
        }
        return new ArrayList<>(Arrays.asList(savedString.split(",")));
    }

    public static void saveCategories(Context context, List<String> categories) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        String joinedString = TextUtils.join(",", categories);
        prefs.edit().putString(KEY_ASSET_CATEGORIES, joinedString).apply();
        BackupManager.triggerAutoUploadIfEnabled(context);
    }
}
