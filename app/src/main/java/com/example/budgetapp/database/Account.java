package com.example.budgetapp.database;

import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(tableName = "accounts",
        indices = {@Index(value = "name", unique = true)})
public class Account {

    @PrimaryKey(autoGenerate = true)
    public int id;

    public String name;
    public double balance;
    public boolean enabled;
    public String remark;
    public long createTime;
    public long sortOrder;

    public Account() {
    }

    @androidx.room.Ignore
    public Account(String name, double balance, boolean enabled, String remark) {
        this.name = name;
        this.balance = balance;
        this.enabled = enabled;
        this.remark = remark;
        this.createTime = System.currentTimeMillis();
        this.sortOrder = System.currentTimeMillis();
    }
}
