package com.example.budgetapp.viewmodel;

import android.util.Log;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;

import com.example.budgetapp.BackupManager;
import com.example.budgetapp.data.repository.TransactionRepository;
import com.example.budgetapp.database.AppDatabase;
import com.example.budgetapp.database.Transaction;
import com.example.budgetapp.database.TransactionForDuplicate;
import com.example.budgetapp.database.TransactionStats;
import com.example.budgetapp.model.TransactionType;
import com.example.budgetapp.utils.ThreadPoolManager;
import com.example.budgetapp.widget.WidgetUtils;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 交易记录 ViewModel
 * 负责管理交易相关的数据和业务逻辑
 */
public class TransactionViewModel extends AndroidViewModel {

    private final TransactionRepository repository;

    // 动态查询所需变量
    private final MutableLiveData<long[]> currentRangeFilter = new MutableLiveData<>();
    private final LiveData<List<Transaction>> rangeTransactions;

    // 自动备份状态通知
    private final MutableLiveData<Boolean> backupTriggered = new MutableLiveData<>();

    private final MutableLiveData<String> backupFailureMessage = new MutableLiveData<>();

    // 【新增】标记是否跳过自动备份计数（用于导入操作），使用 AtomicBoolean 防止竞态条件
    private final AtomicBoolean skipAutoBackupCount = new AtomicBoolean(false);

    public TransactionViewModel(@NonNull Application application) {
        super(application);
        AppDatabase database = AppDatabase.getDatabase(application);
        this.repository = new TransactionRepository(database);

        // 利用 Transformations.switchMap 实现动态查询
        rangeTransactions = Transformations.switchMap(currentRangeFilter, range -> {
            if (range == null || range.length != 2) {
                return new MutableLiveData<>();
            }
            return repository.getTransactionsByRange(range[0], range[1]);
        });
    }

    // ================= 基础 CRUD =================

    public LiveData<List<Transaction>> getAllTransactions() {
        return repository.getAllTransactions();
    }

    /**
     * 添加交易记录（触发自动备份计数）
     */
    public void addTransaction(Transaction transaction) {
        repository.insert(transaction, id -> {
            notifyWidgetUpdate();
            BackupManager.markTransactionDirty(getApplication(), transaction.id);
            triggerAutoBackup();
        });
    }

    /**
     * 更新交易记录（触发自动备份计数）
     */
    public void updateTransaction(Transaction transaction) {
        repository.update(transaction, result -> {
            notifyWidgetUpdate();
            BackupManager.markTransactionDirty(getApplication(), transaction.id);
            triggerAutoBackup();
        });
    }

    /**
     * 删除交易记录（触发自动备份计数）
     */
    public void deleteTransaction(Transaction transaction) {
        int deletedId = transaction.id;
        repository.delete(transaction, result -> {
            notifyWidgetUpdate();
            BackupManager.markTransactionDeleted(getApplication(), deletedId);
            triggerAutoBackup();
        });
    }

    // ================= 批量操作 =================

    /**
     * 批量添加交易记录（用于"记多笔"）- 只触发一次自动备份计数
     */
    public void addBatchTransactions(List<Transaction> transactions, RepositoryCallback<Integer> callback) {
        repository.insertAll(transactions, count -> {
            notifyWidgetUpdate();
            for (Transaction t : transactions) {
                BackupManager.markTransactionDirty(getApplication(), t.id);
            }
            triggerAutoBackup();
            if (callback != null) {
                callback.onComplete(count);
            }
        });
    }

    /**
     * 拆单操作：删除原始账单并批量插入拆分账单 - 只触发一次自动备份计数
     */
    public void splitTransaction(Transaction original, List<Transaction> splitList, RepositoryCallback<Integer> callback) {
        int deletedId = original.id;
        repository.deleteAndInsertAll(original, splitList, count -> {
            notifyWidgetUpdate();
            BackupManager.markTransactionDeleted(getApplication(), deletedId);
            for (Transaction t : splitList) {
                BackupManager.markTransactionDirty(getApplication(), t.id);
            }
            triggerAutoBackup();
            if (callback != null) {
                callback.onComplete(count);
            }
        });
    }

    /**
     * 批量插入交易记录（用于导入）- 不触发自动备份计数
     */
    public void insertTransactions(List<Transaction> transactions, RepositoryCallback<Integer> callback) {
        // 【关键】设置跳过自动备份计数标志
        skipAutoBackupCount.set(true);
        repository.insertAll(transactions, count -> {
            notifyWidgetUpdate();
            // 【关键】导入不触发自动备份计数，只更新Widget
            if (callback != null) {
                callback.onComplete(count);
            }
        });
    }

    /**
     * 批量插入交易记录（用于导入）- 异步版本，不触发自动备份计数
     */
    public void insertTransactionsSync(List<Transaction> transactions, TransactionRepository.RepositoryCallback<Integer> callback) {
        if (transactions == null || transactions.isEmpty()) {
            if (callback != null) callback.onComplete(0);
            return;
        }
        skipAutoBackupCount.set(true);
        repository.insertAll(transactions, count -> {
            notifyWidgetUpdate();
            if (callback != null) callback.onComplete(count);
        });
    }

    /**
     * 同步获取所有交易记录（用于导出）
     */
    public List<Transaction> getAllTransactionsSync() {
        return repository.getAllTransactionsSync();
    }

    public TransactionStats getTransactionStatsSync() {
        return repository.getTransactionStatsSync();
    }

    public List<TransactionForDuplicate> getTransactionsForDuplicateSync() {
        return repository.getTransactionsForDuplicateSync();
    }

    // ================= 范围查询 =================

    /**
     * 设置当前要查看的时间范围
     */
    public void setDateRange(long startMillis, long endMillis) {
        currentRangeFilter.setValue(new long[]{startMillis, endMillis});
    }

    /**
     * 观察此 LiveData 获取按需加载的账单数据
     */
    public LiveData<List<Transaction>> getRangeTransactions() {
        return rangeTransactions;
    }

    // ================= 统计查询 =================

    /**
     * 获取指定时间段的收支总额
     */
    public LiveData<Double> getTotalAmountByType(long start, long end, TransactionType type) {
        return repository.getTotalAmountByType(start, end, type);
    }

    /**
     * 同步获取指定时间段的收支总额
     */
    public double getTotalAmountByTypeSync(long start, long end, TransactionType type) {
        return repository.getTotalAmountByTypeSync(start, end, type);
    }

    // ================= 筛选查询 =================

    /**
     * 高级筛选查询
     */
    public LiveData<List<Transaction>> getFilteredTransactions(
            long start, long end, Integer type, Float minAmount, Float maxAmount, String keyword) {
        return repository.getFilteredTransactions(start, end, type, minAmount, maxAmount, keyword);
    }

    // ================= 分类操作 =================

    public void updateCategoryName(String oldCategory, String newCategory) {
        repository.updateCategoryName(oldCategory, newCategory, null);
    }

    public void updateSubCategoryName(String parentCategory, String oldSubCategory, String newSubCategory) {
        repository.updateSubCategoryName(parentCategory, oldSubCategory, newSubCategory, null);
    }

    public void clearPhotoPath(int transactionId) {
        repository.updatePhotoPath(transactionId, "");
    }

    // ================= 通知与备份 =================

    public LiveData<Boolean> getBackupTriggered() {
        return backupTriggered;
    }

    public LiveData<String> getBackupFailureMessage() {
        return backupFailureMessage;
    }

    private void notifyWidgetUpdate() {
        WidgetUtils.updateAllWidgets(getApplication());
    }

    /**
     * 【优化】触发自动备份，不再查询全量数据，由 BackupManager 自行决定增量或全量备份
     */
    private void triggerAutoBackup() {
        ThreadPoolManager.getInstance().executeBackground(() -> {
            try {
                if (skipAutoBackupCount.getAndSet(false)) {
                    backupTriggered.postValue(false);
                    return;
                }
                
                BackupManager.BackupResult result =
                        BackupManager.incrementChangeCountAndBackup(getApplication());
                if (result.success) {
                    backupTriggered.postValue(true);
                } else {
                    backupTriggered.postValue(false);
                    if (result.errorMessage != null) {
                        backupFailureMessage.postValue(result.errorMessage);
                    }
                }
            } catch (Exception e) {
                Log.e("Tally", "Error", e);
                backupTriggered.postValue(false);
            }
        });
    }

    /**
     * 统一触发备份和Widget更新（导入完成后调用）- 不触发自动备份计数
     */
    public void triggerBackupAndWidgetUpdate() {
        notifyWidgetUpdate();
        // 【关键】导入不触发自动备份计数
        // 只更新Widget，不调用triggerAutoBackup()
    }

    // ================= 回调接口 =================

    public interface RepositoryCallback<T> {
        void onComplete(T result);
    }
}
