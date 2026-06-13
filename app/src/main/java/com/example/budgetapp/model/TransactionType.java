package com.example.budgetapp.model;

import androidx.annotation.ColorRes;
import androidx.annotation.StringRes;

import com.example.budgetapp.R;

public enum TransactionType {
    EXPENSE(0, "支出", R.color.expense_green, R.string.type_expense),
    INCOME(1, "收入", R.color.income_red, R.string.type_income),
    LIABILITY(3, "借入", R.color.liability_orange, R.string.type_liability),
    LEND(4, "借出", R.color.lend_purple, R.string.type_lend);

    private final int value;
    private final String label;
    private final int colorRes;
    private final int stringRes;

    TransactionType(int value, String label, @ColorRes int colorRes, @StringRes int stringRes) {
        this.value = value;
        this.label = label;
        this.colorRes = colorRes;
        this.stringRes = stringRes;
    }

    public int getValue() {
        return value;
    }

    public String getLabel() {
        return label;
    }

    @ColorRes
    public int getColorRes() {
        return colorRes;
    }

    @StringRes
    public int getStringRes() {
        return stringRes;
    }

    public static TransactionType fromValue(int value) {
        for (TransactionType type : values()) {
            if (type.value == value) {
                return type;
            }
        }
        return EXPENSE;
    }

    public static TransactionType fromLabel(String label) {
        for (TransactionType type : values()) {
            if (type.label.equals(label)) {
                return type;
            }
        }
        return EXPENSE;
    }

    public boolean isExpense() {
        return this == EXPENSE;
    }

    public boolean isIncome() {
        return this == INCOME;
    }

    public boolean isLiability() {
        return this == LIABILITY;
    }

    public boolean isLend() {
        return this == LEND;
    }

    public boolean hasTargetObject() {
        return this == LIABILITY || this == LEND;
    }

    public String getAmountPrefix() {
        switch (this) {
            case EXPENSE:
                return "-";
            case INCOME:
            case LIABILITY:
                return "+";
            case LEND:
            default:
                return "";
        }
    }
}
