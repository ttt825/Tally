package com.example.budgetapp.utils;

import com.example.budgetapp.database.Account;
import com.example.budgetapp.database.Transaction;
import com.example.budgetapp.model.TransactionType;

/**
 * 账户余额计算工具类
 */
public class AccountBalanceHelper {

    private AccountBalanceHelper() {
    }

    /**
     * 计算交易对账户余额的净影响（正值为增加，负值为减少）
     */
    public static double getBalanceDelta(Transaction transaction) {
        if (transaction == null || transaction.accountId == null || transaction.accountId == 0) {
            return 0.0;
        }
        TransactionType type = TransactionType.fromValue(transaction.type);
        switch (type) {
            case INCOME:
            case LIABILITY:
                return transaction.amount;
            case EXPENSE:
            case LEND:
                return -transaction.amount;
            default:
                return 0.0;
        }
    }

    /**
     * 将交易应用到账户余额
     */
    public static void applyTransactionToAccount(Account account, Transaction transaction) {
        if (account == null || transaction == null) {
            return;
        }
        account.balance += getBalanceDelta(transaction);
    }

    /**
     * 撤销交易对账户余额的影响
     */
    public static void revertTransactionFromAccount(Account account, Transaction transaction) {
        if (account == null || transaction == null) {
            return;
        }
        account.balance -= getBalanceDelta(transaction);
    }
}
