package com.companyx.equity;

import com.companyx.equity.model.Transaction;
import com.companyx.equity.model.TransactionType;
import com.companyx.equity.model.User;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDateTime;

/**
 * Test data builder for creating consistent test fixtures
 */
public class TestDataBuilder {
    
    public static User createTestUser(String uid, String password) {
        User user = new User();
        user.setId(1L);
        user.setUid(uid);
        user.setPassword(password);
        user.setRole("USER");
        user.setEnabled(true);
        return user;
    }

    /** User without preset ID for JPA persist tests. */
    public static User createPersistableUser(String uid, String password) {
        User user = createTestUser(uid, password);
        user.setId(null);
        return user;
    }
    
    public static TransactionType createTransactionType(long id, String description) {
        return new TransactionType(id, description);
    }
    
    public static Transaction createTransaction(
            User user,
            TransactionType type,
            String symbol,
            Timestamp timestamp,
            BigDecimal quantity,
            BigDecimal value) {
        return new Transaction(null, timestamp, symbol, quantity, value, user, type);
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
                BigDecimal.valueOf(quantity),
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
                BigDecimal.valueOf(quantity),
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
                BigDecimal.ZERO,
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
                BigDecimal.ZERO,
                BigDecimal.valueOf(amount)
        );
    }
}
