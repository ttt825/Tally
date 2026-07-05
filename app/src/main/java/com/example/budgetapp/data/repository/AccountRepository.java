package com.example.budgetapp.data.repository;

import androidx.lifecycle.LiveData;

import com.example.budgetapp.database.Account;
import com.example.budgetapp.database.AccountDao;
import com.example.budgetapp.database.AppDatabase;

import java.util.List;
import java.util.concurrent.ExecutorService;

/**
 * 账户数据仓库层
 * 负责所有账户相关的数据操作
 */
public class AccountRepository {

    private final AppDatabase database;
    private final AccountDao accountDao;
    private final ExecutorService executor;

    public AccountRepository(AppDatabase database) {
        this.database = database;
        this.accountDao = database.accountDao();
        this.executor = AppDatabase.databaseWriteExecutor;
    }

    /**
     * 插入账户
     */
    public void insert(Account account, RepositoryCallback<Long> callback) {
        executor.execute(() -> {
            long id = accountDao.insert(account);
            account.id = (int) id;
            if (callback != null) {
                callback.onComplete(id);
            }
        });
    }

    /**
     * 更新账户
     */
    public void update(Account account, RepositoryCallback<Void> callback) {
        executor.execute(() -> {
            accountDao.update(account);
            if (callback != null) {
                callback.onComplete(null);
            }
        });
    }

    /**
     * 删除账户
     */
    public void delete(Account account, RepositoryCallback<Void> callback) {
        executor.execute(() -> {
            accountDao.delete(account);
            if (callback != null) {
                callback.onComplete(null);
            }
        });
    }

    /**
     * 删除账户并清空历史交易的 accountId
     */
    public void deleteAndClearReferences(Account account, RepositoryCallback<Void> callback) {
        executor.execute(() -> {
            database.runInTransaction(() -> {
                accountDao.delete(account);
                database.transactionDao().clearAccountId(account.id);
            });
            if (callback != null) {
                callback.onComplete(null);
            }
        });
    }

    /**
     * 获取所有账户（LiveData）
     */
    public LiveData<List<Account>> getAllAccounts() {
        return accountDao.getAllAccounts();
    }

    /**
     * 获取启用的账户（LiveData）
     */
    public LiveData<List<Account>> getEnabledAccounts() {
        return accountDao.getEnabledAccounts();
    }

    /**
     * 同步获取所有账户
     */
    public List<Account> getAllAccountsSync() {
        return accountDao.getAllAccountsSync();
    }

    /**
     * 同步按ID获取账户
     */
    public Account getAccountByIdSync(int id) {
        return accountDao.getAccountByIdSync(id);
    }

    public interface RepositoryCallback<T> {
        void onComplete(T result);
    }
}
