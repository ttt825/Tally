package com.example.budgetapp;

import com.example.budgetapp.database.Transaction;
import com.google.gson.annotations.SerializedName;

import java.util.List;
import java.util.Map;
import java.util.Set;

public class BackupData {
    @SerializedName("version")
    public int version = 5;

    @SerializedName("createTime")
    public long createTime;

    @SerializedName("expenseCategories")
    public List<String> expenseCategories;

    @SerializedName("incomeCategories")
    public List<String> incomeCategories;

    @SerializedName("subCategoryMap")
    public Map<String, List<String>> subCategoryMap;

    @SerializedName("subCategoryEnabled")
    public boolean subCategoryEnabled;

    @SerializedName("detailedCategoryEnabled")
    public boolean detailedCategoryEnabled;

    @SerializedName("assistantConfig")
    public AssistantConfigData assistantConfig;

    @SerializedName("appPreferences")
    public Map<String, PrefItem> appPreferences;

    @SerializedName("records")
    public List<Transaction> records;

    public BackupData() {}

    public BackupData(List<Transaction> records) {
        this.createTime = System.currentTimeMillis();
        this.records = records;
    }

    public static class AssistantConfigData {
        @SerializedName("enableAutoTrack")
        public boolean enableAutoTrack;

        @SerializedName("enableRefund")
        public boolean enableRefund;

        @SerializedName("expenseKeywords")
        public Set<String> expenseKeywords;

        @SerializedName("incomeKeywords")
        public Set<String> incomeKeywords;

        @SerializedName("weekdayRate")
        public float weekdayRate;

        @SerializedName("holidayRate")
        public float holidayRate;

        @SerializedName("monthlyBaseSalary")
        public float monthlyBaseSalary;
    }

    public static class PrefItem {
        @SerializedName("type")
        public String type;

        @SerializedName("value")
        public String value;

        public PrefItem() {}

        public PrefItem(String type, String value) {
            this.type = type;
            this.value = value;
        }
    }
}
