package com.example.budgetapp.viewmodel;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;

import com.example.budgetapp.data.repository.AccountRepository;
import com.example.budgetapp.database.Account;
import com.example.budgetapp.database.AppDatabase;

import java.util.List;

/**
 * 账户 ViewModel
 */
public class AccountViewModel extends AndroidViewModel {

    private final AccountRepository repository;
    private final LiveData<List<Account>> allAccounts;
    private final LiveData<List<Account>> enabledAccounts;

    public AccountViewModel(@NonNull Application application) {
        super(application);
        AppDatabase database = AppDatabase.getDatabase(application);
        this.repository = new AccountRepository(database);
        this.allAccounts = repository.getAllAccounts();
        this.enabledAccounts = repository.getEnabledAccounts();
    }

    public LiveData<List<Account>> getAllAccounts() {
        return allAccounts;
    }

    public LiveData<List<Account>> getEnabledAccounts() {
        return enabledAccounts;
    }

    public List<Account> getAllAccountsSync() {
        return repository.getAllAccountsSync();
    }

    public void insert(Account account, AccountRepository.RepositoryCallback<Long> callback) {
        repository.insert(account, callback);
    }

    public void update(Account account, AccountRepository.RepositoryCallback<Void> callback) {
        repository.update(account, callback);
    }

    public void delete(Account account, AccountRepository.RepositoryCallback<Void> callback) {
        repository.delete(account, callback);
    }

    public void deleteAndClearReferences(Account account, AccountRepository.RepositoryCallback<Void> callback) {
        repository.deleteAndClearReferences(account, callback);
    }

    public void updateSortOrders(List<Account> accounts, AccountRepository.RepositoryCallback<Void> callback) {
        repository.updateSortOrders(accounts, callback);
    }
}
