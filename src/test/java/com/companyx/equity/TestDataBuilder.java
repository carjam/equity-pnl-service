package com.companyx.equity;

import com.companyx.equity.model.Transaction;
import com.companyx.equity.model.TransactionType;
import com.companyx.equity.model.User;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.sql.Timestamp;
import java.time.LocalDateTime;

/**
 * Test data builder for creating consistent test fixtures
 */
public class TestDataBuilder {
    
    public static User createTestUser(String uid, String password) {
        User user = new User();
        user.setId(1);
        user.setUid(uid);
        user.setPassword(password);
        return user;
    }
    
    public static TransactionType createTransactionType(int id, String description) {
        return new TransactionType(id, description);
    }
    
    public static Transaction createTransaction(
            User user,
            TransactionType type,
            String symbol,
            Timestamp timestamp,
            BigInteger quantity,
            BigDecimal value) {
        return new Transaction(0, timestamp, symbol, quantity, value, user, type);
    }
    
    public static Transaction createBuyTransaction(
            User user,
            TransactionType buyType,
            String symbol,
            LocalDateTime dateTime,
            int quantity,
            double totalValue) {
        return createTransaction(
                user,
                buyType,
                symbol,
                Timestamp.valueOf(dateTime),
                BigInteger.valueOf(quantity),
                BigDecimal.valueOf(totalValue)
        );
    }
    
    public static Transaction createSellTransaction(
            User user,
            TransactionType sellType,
            String symbol,
            LocalDateTime dateTime,
            int quantity,
            double totalValue) {
        return createTransaction(
                user,
                sellType,
                symbol,
                Timestamp.valueOf(dateTime),
                BigInteger.valueOf(quantity),
                BigDecimal.valueOf(totalValue)
        );
    }
    
    public static Transaction createDepositTransaction(
            User user,
            TransactionType depositType,
            LocalDateTime dateTime,
            double amount) {
        return createTransaction(
                user,
                depositType,
                null,
                Timestamp.valueOf(dateTime),
                BigInteger.ZERO,
                BigDecimal.valueOf(amount)
        );
    }
    
    public static Transaction createWithdrawalTransaction(
            User user,
            TransactionType withdrawalType,
            LocalDateTime dateTime,
            double amount) {
        return createTransaction(
                user,
                withdrawalType,
                null,
                Timestamp.valueOf(dateTime),
                BigInteger.ZERO,
                BigDecimal.valueOf(amount)
        );
    }
}
