package com.example.budgetapp.data.repository;

import androidx.lifecycle.LiveData;

import com.example.budgetapp.database.AppDatabase;
import com.example.budgetapp.database.Transaction;
import com.example.budgetapp.database.TransactionDao;
import com.example.budgetapp.database.TransactionForDuplicate;
import com.example.budgetapp.database.TransactionStats;
import com.example.budgetapp.model.TransactionType;

import java.util.List;
import java.util.concurrent.ExecutorService;

/**
 * 交易数据仓库层
 * 负责所有交易相关的数据操作，提供统一的API接口
 */
public class TransactionRepository {

    private final TransactionDao transactionDao;
    private final ExecutorService executor;

    public TransactionRepository(AppDatabase database) {
        this.transactionDao = database.transactionDao();
        this.executor = AppDatabase.databaseWriteExecutor;
    }

    // ================= 基础 CRUD 操作 =================

    /**
     * 插入单条交易记录
     */
    public void insert(Transaction transaction, RepositoryCallback<Void> callback) {
        executor.execute(() -> {
            transactionDao.insert(transaction);
            if (callback != null) {
                callback.onComplete(null);
            }
        });
    }

    /**
     * 批量插入交易记录
     */
    public void insertAll(List<Transaction> transactions, RepositoryCallback<Integer> callback) {
        executor.execute(() -> {
            transactionDao.insertAll(transactions);
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
     * 高级筛选查询
     */
    public LiveData<List<Transaction>> getFilteredTransactions(
            long start, long end, Integer type, Float minAmount, Float maxAmount, String keyword) {
        return transactionDao.getFilteredTransactions(start, end, type, minAmount, maxAmount, keyword);
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

    /**
     * 获取加班总收入
     */
    public LiveData<Double> getOvertimeTotalAmount(long start, long end) {
        return transactionDao.getOvertimeTotalAmountLive(start, end);
    }

    /**
     * 同步获取加班总收入
     */
    public double getOvertimeTotalAmountSync(long start, long end) {
        Double result = transactionDao.getOvertimeTotalAmountSync(start, end);
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
