package com.example.budgetapp.database;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

@Dao
public interface AccountDao {

    @Insert
    long insert(Account account);

    @Update
    void update(Account account);

    @Delete
    void delete(Account account);

    @Query("SELECT * FROM accounts ORDER BY enabled DESC, sortOrder ASC, createTime ASC")
    LiveData<List<Account>> getAllAccounts();

    @Query("SELECT * FROM accounts WHERE enabled = 1 ORDER BY sortOrder ASC, createTime ASC")
    LiveData<List<Account>> getEnabledAccounts();

    @Query("SELECT * FROM accounts WHERE enabled = 1 ORDER BY sortOrder ASC, createTime ASC")
    List<Account> getEnabledAccountsSync();

    @Query("SELECT * FROM accounts ORDER BY enabled DESC, sortOrder ASC, createTime ASC")
    List<Account> getAllAccountsSync();

    @Query("SELECT * FROM accounts WHERE id = :id LIMIT 1")
    Account getAccountByIdSync(int id);

    @Query("DELETE FROM accounts")
    void deleteAll();
}
