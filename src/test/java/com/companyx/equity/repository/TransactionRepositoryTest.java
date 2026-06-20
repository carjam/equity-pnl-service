package com.companyx.equity.repository;

import com.companyx.equity.TestDataBuilder;
import com.companyx.equity.model.Transaction;
import com.companyx.equity.model.TransactionType;
import com.companyx.equity.model.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for TransactionRepository
 */
@DataJpaTest
@ActiveProfiles("test")
@TestPropertySource(locations = "classpath:application-test.properties")
class TransactionRepositoryTest {
    
    @Autowired
    private TestEntityManager entityManager;
    
    @Autowired
    private TransactionRepository transactionRepository;
    
    private User testUser;
    private TransactionType buyType;
    private TransactionType sellType;
    
    @BeforeEach
    public void setup() {
        testUser = TestDataBuilder.createPersistableUser("txn-user-" + UUID.randomUUID(), "password");
        testUser = entityManager.persistAndFlush(testUser);
        
        buyType = TestDataBuilder.createTransactionType(0, TransactionType.BUY);
        buyType = entityManager.persistAndFlush(buyType);
        
        sellType = TestDataBuilder.createTransactionType(0, TransactionType.SALE);
        sellType = entityManager.persistAndFlush(sellType);
    }
    
    @Test
    public void testFindByUidAndId() {
        Transaction transaction = TestDataBuilder.createBuyTransaction(
                testUser, buyType, "AAPL",
                LocalDateTime.of(2024, 1, 15, 10, 0),
                100, 5000.0
        );
        transaction = entityManager.persistAndFlush(transaction);
        
        Optional<Transaction> found = transactionRepository.findByUidAndId(
                testUser.getId(), transaction.getId());
        
        assertTrue(found.isPresent());
        assertEquals("AAPL", found.get().getSymbol());
        assertEquals(transaction.getId(), found.get().getId());
    }
    
    @Test
    public void testFindByUidAndId_NotFound() {
        Optional<Transaction> found = transactionRepository.findByUidAndId(
                testUser.getId(), 99999);
        
        assertFalse(found.isPresent());
    }
    
    @Test
    public void testFindAllBefore() {
        // Create transactions
        Transaction t1 = TestDataBuilder.createBuyTransaction(
                testUser, buyType, "AAPL",
                LocalDateTime.of(2024, 1, 10, 10, 0),
                100, 5000.0
        );
        Transaction t2 = TestDataBuilder.createBuyTransaction(
                testUser, buyType, "GOOGL",
                LocalDateTime.of(2024, 1, 15, 10, 0),
                50, 7500.0
        );
        Transaction t3 = TestDataBuilder.createBuyTransaction(
                testUser, buyType, "MSFT",
                LocalDateTime.of(2024, 1, 25, 10, 0),
                200, 10000.0
        );
        
        entityManager.persist(t1);
        entityManager.persist(t2);
        entityManager.persist(t3);
        entityManager.flush();
        
        Date cutoffDate = java.sql.Date.valueOf("2024-01-20");
        List<Transaction> results = transactionRepository.findAllBefore(testUser.getId(), cutoffDate);
        
        assertEquals(2, results.size());
        assertEquals("AAPL", results.get(0).getSymbol());
        assertEquals("GOOGL", results.get(1).getSymbol());
    }
    
    @Test
    public void testFindAllBetween() {
        Transaction t1 = TestDataBuilder.createBuyTransaction(
                testUser, buyType, "AAPL",
                LocalDateTime.of(2024, 1, 5, 10, 0),
                100, 5000.0
        );
        Transaction t2 = TestDataBuilder.createBuyTransaction(
                testUser, buyType, "GOOGL",
                LocalDateTime.of(2024, 1, 15, 10, 0),
                50, 7500.0
        );
        Transaction t3 = TestDataBuilder.createBuyTransaction(
                testUser, buyType, "MSFT",
                LocalDateTime.of(2024, 1, 25, 10, 0),
                200, 10000.0
        );
        Transaction t4 = TestDataBuilder.createBuyTransaction(
                testUser, buyType, "TSLA",
                LocalDateTime.of(2024, 2, 5, 10, 0),
                50, 12500.0
        );
        
        entityManager.persist(t1);
        entityManager.persist(t2);
        entityManager.persist(t3);
        entityManager.persist(t4);
        entityManager.flush();
        
        Date startDate = java.sql.Date.valueOf("2024-01-10");
        Date endDate = java.sql.Date.valueOf("2024-01-31");
        
        List<Transaction> results = transactionRepository.findAllBetween(
                testUser.getId(), startDate, endDate);
        
        assertEquals(2, results.size());
        assertEquals("GOOGL", results.get(0).getSymbol());
        assertEquals("MSFT", results.get(1).getSymbol());
    }
    
    @Test
    public void testFindAllBetween_EmptyResult() {
        Date startDate = java.sql.Date.valueOf("2024-01-01");
        Date endDate = java.sql.Date.valueOf("2024-01-31");
        
        List<Transaction> results = transactionRepository.findAllBetween(
                testUser.getId(), startDate, endDate);
        
        assertTrue(results.isEmpty());
    }
    
    @Test
    public void testCountByUserId() {
        Transaction t1 = TestDataBuilder.createBuyTransaction(
                testUser, buyType, "AAPL",
                LocalDateTime.of(2024, 1, 15, 10, 0),
                100, 5000.0
        );
        Transaction t2 = TestDataBuilder.createBuyTransaction(
                testUser, buyType, "GOOGL",
                LocalDateTime.of(2024, 1, 16, 10, 0),
                50, 7500.0
        );
        
        entityManager.persist(t1);
        entityManager.persist(t2);
        entityManager.flush();
        
        long count = transactionRepository.countByUserId(testUser.getId());
        
        assertEquals(2, count);
    }
    
    @Test
    public void testTransactionOrdering() {
        Transaction t1 = TestDataBuilder.createBuyTransaction(
                testUser, buyType, "AAPL",
                LocalDateTime.of(2024, 1, 25, 10, 0),
                100, 5000.0
        );
        Transaction t2 = TestDataBuilder.createBuyTransaction(
                testUser, buyType, "GOOGL",
                LocalDateTime.of(2024, 1, 10, 10, 0),
                50, 7500.0
        );
        Transaction t3 = TestDataBuilder.createBuyTransaction(
                testUser, buyType, "MSFT",
                LocalDateTime.of(2024, 1, 20, 10, 0),
                200, 10000.0
        );
        
        entityManager.persist(t1);
        entityManager.persist(t2);
        entityManager.persist(t3);
        entityManager.flush();
        
        Date startDate = java.sql.Date.valueOf("2024-01-01");
        Date endDate = java.sql.Date.valueOf("2024-01-31");
        
        List<Transaction> results = transactionRepository.findAllBetween(
                testUser.getId(), startDate, endDate);
        
        // Should be ordered by timestamp
        assertEquals("GOOGL", results.get(0).getSymbol());
        assertEquals("MSFT", results.get(1).getSymbol());
        assertEquals("AAPL", results.get(2).getSymbol());
    }
}
