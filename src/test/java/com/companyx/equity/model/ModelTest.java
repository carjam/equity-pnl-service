package com.companyx.equity.model;

import com.companyx.equity.TestDataBuilder;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for domain models
 */
public class ModelTest {
    
    @Test
    public void testTransactionCreation() {
        User user = TestDataBuilder.createTestUser("test-user", "password");
        TransactionType buyType = TestDataBuilder.createTransactionType(1, TransactionType.BUY);
        
        Transaction transaction = TestDataBuilder.createBuyTransaction(
                user, buyType, "AAPL",
                LocalDateTime.of(2024, 1, 15, 10, 0),
                100, 5000.0
        );
        
        assertNotNull(transaction);
        assertEquals("AAPL", transaction.getSymbol());
        assertEquals(BigDecimal.valueOf(100), transaction.getQuantity());
        assertEquals(BigDecimal.valueOf(5000.0), transaction.getValue());
        assertEquals(user, transaction.getUser());
        assertEquals(buyType, transaction.getTransactionType());
    }
    
    @Test
    public void testPositionInitialization() {
        User user = TestDataBuilder.createTestUser("test-user", "password");
        Timestamp timestamp = Timestamp.valueOf(LocalDateTime.now());
        
        Position position = new Position(user, timestamp, "AAPL");
        
        assertNotNull(position);
        assertEquals("AAPL", position.getSymbol());
        assertEquals(BigDecimal.ZERO, position.getQuantity());
        assertEquals(BigDecimal.ZERO, position.getValue());
        assertEquals(BigDecimal.ZERO, position.getRealized());
        assertEquals(BigDecimal.ZERO, position.getUnrealized());
        assertEquals(user, position.getUser());
        assertEquals(timestamp, position.getTimestamp());
    }
    
    @Test
    public void testTransactionTypeConstants() {
        assertEquals("buy", TransactionType.BUY);
        assertEquals("sell", TransactionType.SALE);
        assertEquals("deposit", TransactionType.DEPOSIT);
        assertEquals("withdraw", TransactionType.WITHDRAWAL);
        
        assertTrue(TransactionType.CASH_TRANS.contains(TransactionType.DEPOSIT));
        assertTrue(TransactionType.CASH_TRANS.contains(TransactionType.WITHDRAWAL));
        assertFalse(TransactionType.CASH_TRANS.contains(TransactionType.BUY));
        assertFalse(TransactionType.CASH_TRANS.contains(TransactionType.SALE));
    }
    
    @Test
    public void testUserProperties() {
        User user = new User();
        user.setId(123L);
        user.setUid("test-uid");
        user.setPassword("hashed-password");
        
        assertEquals(123L, user.getId());
        assertEquals("test-uid", user.getUid());
        assertEquals("hashed-password", user.getPassword());
    }
    
    @Test
    public void testPositionToString() {
        User user = TestDataBuilder.createTestUser("test-user", "password");
        Timestamp timestamp = Timestamp.valueOf(LocalDateTime.of(2024, 1, 15, 10, 0));
        
        Position position = new Position(user, timestamp, "AAPL");
        position.setQuantity(BigDecimal.valueOf(100));
        position.setValue(BigDecimal.valueOf(-5000.0));
        position.setRealized(BigDecimal.valueOf(1000.0));
        position.setUnrealized(BigDecimal.valueOf(500.0));
        
        String str = position.toString();
        
        assertNotNull(str);
        assertTrue(str.contains("AAPL"));
        assertTrue(str.contains("100"));
    }
    
    @Test
    public void testTransactionToString() {
        User user = TestDataBuilder.createTestUser("test-user", "password");
        TransactionType buyType = TestDataBuilder.createTransactionType(1, TransactionType.BUY);
        
        Transaction transaction = TestDataBuilder.createBuyTransaction(
                user, buyType, "AAPL",
                LocalDateTime.of(2024, 1, 15, 10, 0),
                100, 5000.0
        );
        
        String str = transaction.toString();
        
        assertNotNull(str);
        assertTrue(str.contains("AAPL"));
        assertTrue(str.contains("buy"));
    }
}
