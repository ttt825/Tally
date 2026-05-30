package com.example.budgetapp.database;

import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.Index;
import androidx.room.PrimaryKey;

// 【优化】添加复合索引，极大提升查询和过滤速度
@Entity(tableName = "transactions",
        indices = {
                @Index("date"),
                @Index("type"),
                @Index("category"),
                // 【新增】复合索引，优化常见查询场景
                @Index(value = {"date", "type"}),
                @Index(value = {"category", "date"}),
                @Index(value = {"type", "category"})
        })
public class Transaction {
    @PrimaryKey(autoGenerate = true)
    public int id;
    public long date;
    public int type; // 0支出, 1收入, 3借入, 4借出
    public String category;
    public double amount;
    public String note;
    public String remark;
    public String currencySymbol;
    public String photoPath;
    // 【新增】二级分类
    public String subCategory;
    // 借入/借出对象
    public String targetObject;


    public Transaction() {
    }

    @Ignore
    public Transaction(long date, int type, String category, double amount) {
        this(date, type, category, amount, "", "");
    }

    @Ignore
    public Transaction(long date, int type, String category, double amount, String note) {
        this(date, type, category, amount, note, "");
    }

    @Ignore
    public Transaction(long date, int type, String category, double amount, String note, String remark) {
        this.date = date;
        this.type = type;
        this.category = category;
        this.amount = amount;
        this.note = note;
        this.remark = remark;
        this.currencySymbol = "¥";
        this.subCategory = ""; // 默认为空
        this.photoPath = "";
    }
}