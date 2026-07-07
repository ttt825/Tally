package com.example.budgetapp.data.repository;

import androidx.lifecycle.LiveData;

import com.example.budgetapp.database.Account;
import com.example.budgetapp.database.AppDatabase;
import com.example.budgetapp.database.Transaction;
import com.example.budgetapp.database.TransactionDao;
import com.example.budgetapp.database.TransactionForDuplicate;
import com.example.budgetapp.database.TransactionStats;
import com.example.budgetapp.model.TransactionType;
import com.example.budgetapp.utils.AccountBalanceHelper;

import java.util.List;
import java.util.concurrent.ExecutorService;

/**
 * 交易数据仓库层
 * 负责所有交易相关的数据操作，提供统一的API接口
 */
public class TransactionRepository {

    private final AppDatabase database;
    private final TransactionDao transactionDao;
    private final ExecutorService executor;

    public TransactionRepository(AppDatabase database) {
        this.database = database;
        this.transactionDao = database.transactionDao();
        this.executor = AppDatabase.databaseWriteExecutor;
    }

    // ================= 基础 CRUD 操作 =================

    /**
     * 插入单条交易记录，插入后自动设置生成的ID
     */
    public void insert(Transaction transaction, RepositoryCallback<Void> callback) {
        executor.execute(() -> {
            long id = transactionDao.insert(transaction);
            transaction.id = (int) id;
            if (callback != null) {
                callback.onComplete(null);
            }
        });
    }

    /**
     * 批量插入交易记录，插入后自动设置每条记录生成的ID
     */
    public void insertAll(List<Transaction> transactions, RepositoryCallback<Integer> callback) {
        executor.execute(() -> {
            List<Long> ids = transactionDao.insertAll(transactions);
            for (int i = 0; i < transactions.size() && i < ids.size(); i++) {
                transactions.get(i).id = ids.get(i).intValue();
            }
            if (callback != null) {
                callback.onComplete(transactions.size());
            }
        });
    }

    /**
     * 更新交易记录
     */
    public void update(Transaction transaction, RepositoryCallback<Integer> callback) {
        executor.execute(() -> {
            transactionDao.update(transaction);
            if (callback != null) {
                callback.onComplete(1);
            }
        });
    }

    /**
     * 删除交易记录
     */
    public void delete(Transaction transaction, RepositoryCallback<Integer> callback) {
        executor.execute(() -> {
            transactionDao.delete(transaction);
            if (callback != null) {
                callback.onComplete(1);
            }
        });
    }

    /**
     * 拆单操作：删除原始账单并批量插入拆分账单（原子操作），插入后自动设置ID
     */
    public void deleteAndInsertAll(Transaction original, List<Transaction> splitList, RepositoryCallback<Integer> callback) {
        executor.execute(() -> {
            database.runInTransaction(() -> {
                transactionDao.delete(original);
                List<Long> ids = transactionDao.insertAll(splitList);
                for (int i = 0; i < splitList.size() && i < ids.size(); i++) {
                    splitList.get(i).id = ids.get(i).intValue();
                }
            });
            if (callback != null) {
                callback.onComplete(splitList.size());
            }
        });
    }

    // ================= 账户余额联动操作 =================

    /**
     * 插入交易并同步调整账户余额（原子操作）
     */
    public void insertWithAccountBalance(Transaction transaction, RepositoryCallback<Void> callback) {
        executor.execute(() -> {
            database.runInTransaction(() -> {
                transactionDao.insert(transaction);
                if (transaction.accountId != null && transaction.accountId != 0) {
                    Account account = database.accountDao().getAccountByIdSync(transaction.accountId);
                    if (account != null) {
                        AccountBalanceHelper.applyTransactionToAccount(account, transaction);
                        database.accountDao().update(account);
                    }
                }
            });
            if (callback != null) {
                callback.onComplete(null);
            }
        });
    }

    /**
     * 更新交易并同步调整账户余额（原子操作）
     */
    public void updateWithAccountBalance(Transaction transaction, RepositoryCallback<Integer> callback) {
        executor.execute(() -> {
            database.runInTransaction(() -> {
                Transaction oldTransaction = transactionDao.getTransactionsByIds(
                        java.util.Collections.singletonList(transaction.id)).get(0);
                transactionDao.update(transaction);

                // 原账户与新账户不同，需要分别处理
                Integer oldAccountId = oldTransaction.accountId;
                Integer newAccountId = transaction.accountId;

                if (oldAccountId != null && oldAccountId != 0 &&
                        (newAccountId == null || !newAccountId.equals(oldAccountId))) {
                    Account oldAccount = database.accountDao().getAccountByIdSync(oldAccountId);
                    if (oldAccount != null) {
                        AccountBalanceHelper.revertTransactionFromAccount(oldAccount, oldTransaction);
                        database.accountDao().update(oldAccount);
                    }
                }

                if (newAccountId != null && newAccountId != 0) {
                    Account newAccount = database.accountDao().getAccountByIdSync(newAccountId);
                    if (newAccount != null) {
                        if (newAccountId.equals(oldAccountId)) {
                            AccountBalanceHelper.revertTransactionFromAccount(newAccount, oldTransaction);
                        }
                        AccountBalanceHelper.applyTransactionToAccount(newAccount, transaction);
                        database.accountDao().update(newAccount);
                    }
                }
            });
            if (callback != null) {
                callback.onComplete(1);
            }
        });
    }

    /**
     * 删除交易并同步调整账户余额（原子操作）
     */
    public void deleteWithAccountBalance(Transaction transaction, RepositoryCallback<Integer> callback) {
        executor.execute(() -> {
            database.runInTransaction(() -> {
                if (transaction.accountId != null && transaction.accountId != 0) {
                    Account account = database.accountDao().getAccountByIdSync(transaction.accountId);
                    if (account != null) {
                        AccountBalanceHelper.revertTransactionFromAccount(account, transaction);
                        database.accountDao().update(account);
                    }
                }
                transactionDao.delete(transaction);
            });
            if (callback != null) {
                callback.onComplete(1);
            }
        });
    }

    /**
     * 批量插入交易并同步调整账户余额（原子操作）
     */
    public void insertAllWithAccountBalance(List<Transaction> transactions, RepositoryCallback<Integer> callback) {
        executor.execute(() -> {
            database.runInTransaction(() -> {
                List<Long> ids = transactionDao.insertAll(transactions);
                for (int i = 0; i < transactions.size() && i < ids.size(); i++) {
                    transactions.get(i).id = ids.get(i).intValue();
                }
                for (Transaction transaction : transactions) {
                    if (transaction.accountId != null && transaction.accountId != 0) {
                        Account account = database.accountDao().getAccountByIdSync(transaction.accountId);
                        if (account != null) {
                            AccountBalanceHelper.applyTransactionToAccount(account, transaction);
                            database.accountDao().update(account);
                        }
                    }
                }
            });
            if (callback != null) {
                callback.onComplete(transactions.size());
            }
        });
    }

    /**
     * 拆单操作并同步调整账户余额（原子操作）
     */
    public void deleteAndInsertAllWithAccountBalance(Transaction original, List<Transaction> splitList,
                                                      RepositoryCallback<Integer> callback) {
        executor.execute(() -> {
            database.runInTransaction(() -> {
                if (original.accountId != null && original.accountId != 0) {
                    Account account = database.accountDao().getAccountByIdSync(original.accountId);
                    if (account != null) {
                        AccountBalanceHelper.revertTransactionFromAccount(account, original);
                        database.accountDao().update(account);
                    }
                }
                transactionDao.delete(original);
                List<Long> ids = transactionDao.insertAll(splitList);
                for (int i = 0; i < splitList.size() && i < ids.size(); i++) {
                    splitList.get(i).id = ids.get(i).intValue();
                }
                for (Transaction transaction : splitList) {
                    if (transaction.accountId != null && transaction.accountId != 0) {
                        Account account = database.accountDao().getAccountByIdSync(transaction.accountId);
                        if (account != null) {
                            AccountBalanceHelper.applyTransactionToAccount(account, transaction);
                            database.accountDao().update(account);
                        }
                    }
                }
            });
            if (callback != null) {
                callback.onComplete(splitList.size());
            }
        });
    }

    // ================= 查询操作 =================

    /**
     * 获取所有交易记录（LiveData）
     */
    public LiveData<List<Transaction>> getAllTransactions() {
        return transactionDao.getAllTransactions();
    }

    /**
     * 同步获取所有交易记录
     */
    public List<Transaction> getAllTransactionsSync() {
        return transactionDao.getAllTransactionsSync();
    }

    /**
     * 增量备份专用：按ID列表同步查询交易记录
     */
    public List<Transaction> getTransactionsByIdsSync(List<Integer> ids) {
        if (ids == null || ids.isEmpty()) {
            return java.util.Collections.emptyList();
        }
        return transactionDao.getTransactionsByIds(ids);
    }

    /**
     * 获取指定时间范围内的交易记录
     */
    public LiveData<List<Transaction>> getTransactionsByRange(long start, long end) {
        return transactionDao.getTransactionsByRangeLive(start, end);
    }

    /**
     * 同步获取指定时间范围内的交易记录
     */
    public List<Transaction> getTransactionsByRangeSync(long start, long end) {
        return transactionDao.getTransactionsByRange(start, end);
    }

    /**
     * 高级筛选查询（使用动态 SQL 拼接，确保有效条件可走索引）
     */
    public LiveData<List<Transaction>> getFilteredTransactions(
            long start, long end, Integer type, Integer accountId, Float minAmount, Float maxAmount, String keyword) {
        return transactionDao.getFilteredTransactions(
                TransactionDao.buildFilteredTransactionsQuery(start, end, type, accountId, minAmount, maxAmount, keyword));
    }

    // ================= 统计查询 =================

    /**
     * 获取指定时间段内某类型的总金额
     */
    public LiveData<Double> getTotalAmountByType(long start, long end, TransactionType type) {
        return transactionDao.getTotalAmountByTypeLive(start, end, type.getValue());
    }

    /**
     * 同步获取指定时间段内某类型的总金额
     */
    public double getTotalAmountByTypeSync(long start, long end, TransactionType type) {
        Double result = transactionDao.getTotalAmountByTypeSync(start, end, type.getValue());
        return result != null ? result : 0.0;
    }

    // ================= 分类操作 =================

    /**
     * 批量更新一级分类名称
     */
    public void updateCategoryName(String oldCategory, String newCategory, RepositoryCallback<Integer> callback) {
        executor.execute(() -> {
            transactionDao.updateCategoryName(oldCategory, newCategory);
            if (callback != null) {
                callback.onComplete(1);
            }
        });
    }

    /**
     * 批量更新二级分类名称
     */
    public void updateSubCategoryName(String parentCategory, String oldSubCategory, 
                                       String newSubCategory, RepositoryCallback<Integer> callback) {
        executor.execute(() -> {
            transactionDao.updateSubCategoryName(parentCategory, oldSubCategory, newSubCategory);
            if (callback != null) {
                callback.onComplete(1);
            }
        });
    }

    /**
     * 仅更新指定交易的 photoPath 字段
     */
    public void updatePhotoPath(int transactionId, String photoPath) {
        executor.execute(() -> transactionDao.updatePhotoPath(transactionId, photoPath));
    }

    public TransactionStats getTransactionStatsSync() {
        return transactionDao.getTransactionStatsSync();
    }

    public List<TransactionForDuplicate> getTransactionsForDuplicateSync() {
        return transactionDao.getTransactionsForDuplicateSync();
    }

    // ================= 回调接口 =================

    public interface RepositoryCallback<T> {
        void onComplete(T result);
    }
}
