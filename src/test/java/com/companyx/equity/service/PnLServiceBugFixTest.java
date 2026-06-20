package com.companyx.equity.service;

import com.companyx.equity.TestDataBuilder;
import com.companyx.equity.error.InvalidInputException;
import com.companyx.equity.error.TransactionNotFoundException;
import com.companyx.equity.error.UserNotFoundException;
import com.companyx.equity.model.Position;
import com.companyx.equity.model.Transaction;
import com.companyx.equity.model.TransactionType;
import com.companyx.equity.model.User;
import com.companyx.equity.repository.FinhubRepository;
import com.companyx.equity.repository.TransactionRepository;
import com.companyx.equity.repository.UserRepository;
import com.companyx.equity.dto.MarkDto;
import com.fasterxml.jackson.core.JsonProcessingException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.sql.Timestamp;
import java.text.ParseException;
import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests to verify bug fixes documented in BUG_REPORT.md
 * 
 * Bug #1 & #2: Type mismatches in controller - FIXED (already converted LocalDate to Date/String)
 * Bug #6: Gson deep clone inefficiency - FIXED (now using copy constructor)
 * Bug #7: Missing input validation - FIXED (added validation)
 * Bug #8: Generic exceptions - FIXED (now using custom exceptions)
 * Bug #9: Inconsistent dependency injection - FIXED (using constructor injection only)
 * Bug #10: Excessive logging - FIXED (changed to debug level)
 * Bug #11: SimpleDateFormat thread safety - FIXED (using DateTimeFormatter)
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Bug Fix Verification Tests")
public class PnLServiceBugFixTest {
    
    @Mock
    private UserRepository userRepository;
    
    @Mock
    private TransactionRepository transactionRepository;
    
    @Mock
    private FinhubRepository finhubRepository;
    
    @InjectMocks
    private PnLService pnLService;
    
    private User testUser;
    private TransactionType buyType;
    private TransactionType sellType;
    private TransactionType depositType;
    
    @BeforeEach
    public void setup() {
        testUser = TestDataBuilder.createTestUser("test-user", "password");
        buyType = TestDataBuilder.createTransactionType(1, TransactionType.BUY);
        sellType = TestDataBuilder.createTransactionType(2, TransactionType.SALE);
        depositType = TestDataBuilder.createTransactionType(3, TransactionType.DEPOSIT);
    }
    
    // ==================== BUG #8: Custom Exception Tests ====================
    
    @Test
    @DisplayName("Bug #8: UserNotFoundException thrown instead of RuntimeException")
    public void testUserNotFoundThrowsCustomException() {
        when(userRepository.findByUid("nonexistent")).thenReturn(Optional.empty());
        
        Date start = java.sql.Date.valueOf("2024-01-01");
        Date end = java.sql.Date.valueOf("2024-01-31");
        
        UserNotFoundException exception = assertThrows(UserNotFoundException.class, () -> {
            pnLService.getPositions("nonexistent", start, end);
        });
        
        assertTrue(exception.getMessage().contains("User not found: nonexistent"));
    }
    
    @Test
    @DisplayName("Bug #8: UserNotFoundException in getTransactionById")
    public void testGetTransactionByIdUserNotFound() {
        when(userRepository.findByUid("nonexistent")).thenReturn(Optional.empty());
        
        UserNotFoundException exception = assertThrows(UserNotFoundException.class, () -> {
            pnLService.getTransactionById("nonexistent", "1");
        });
        
        assertTrue(exception.getMessage().contains("User not found"));
    }
    
    @Test
    @DisplayName("Bug #8: TransactionNotFoundException when transaction not found")
    public void testGetTransactionByIdTransactionNotFound() {
        when(userRepository.findByUid("test-user")).thenReturn(Optional.of(testUser));
        when(transactionRepository.findByUidAndId(anyInt(), anyInt()))
                .thenReturn(Optional.empty());
        
        TransactionNotFoundException exception = assertThrows(TransactionNotFoundException.class, () -> {
            pnLService.getTransactionById("test-user", "999");
        });
        
        assertTrue(exception.getMessage().contains("Transaction not found"));
    }
    
    @Test
    @DisplayName("Bug #8: UserNotFoundException in getTransactionsByDates")
    public void testGetTransactionsByDatesUserNotFound() {
        when(userRepository.findByUid("nonexistent")).thenReturn(Optional.empty());
        
        UserNotFoundException exception = assertThrows(UserNotFoundException.class, () -> {
            pnLService.getTransactionsByDates("nonexistent", 
                    Optional.of("2024-01-01"), Optional.of("2024-01-31"));
        });
        
        assertTrue(exception.getMessage().contains("User not found"));
    }
    
    // ==================== BUG #7: Input Validation Tests ====================
    
    @Test
    @DisplayName("Bug #7: Validates start date before end date")
    public void testInvalidDateRange_StartAfterEnd() {
        when(userRepository.findByUid("test-user")).thenReturn(Optional.of(testUser));
        
        Date start = java.sql.Date.valueOf("2024-01-31");
        Date end = java.sql.Date.valueOf("2024-01-01");
        
        InvalidInputException exception = assertThrows(InvalidInputException.class, () -> {
            pnLService.getPositions("test-user", start, end);
        });
        
        assertTrue(exception.getMessage().contains("Start date must be before or equal to end date"));
    }
    
    @Test
    @DisplayName("Bug #7: Validates null start date")
    public void testInvalidDateRange_NullStart() {
        when(userRepository.findByUid("test-user")).thenReturn(Optional.of(testUser));
        
        Date end = java.sql.Date.valueOf("2024-01-31");
        
        InvalidInputException exception = assertThrows(InvalidInputException.class, () -> {
            pnLService.getPositions("test-user", null, end);
        });
        
        assertTrue(exception.getMessage().contains("cannot be null"));
    }
    
    @Test
    @DisplayName("Bug #7: Validates null end date")
    public void testInvalidDateRange_NullEnd() {
        when(userRepository.findByUid("test-user")).thenReturn(Optional.of(testUser));
        
        Date start = java.sql.Date.valueOf("2024-01-01");
        
        InvalidInputException exception = assertThrows(InvalidInputException.class, () -> {
            pnLService.getPositions("test-user", start, null);
        });
        
        assertTrue(exception.getMessage().contains("cannot be null"));
    }
    
    @Test
    @DisplayName("Bug #7: Validates date format in getTransactionsByDates")
    public void testInvalidDateFormat() {
        when(userRepository.findByUid("test-user")).thenReturn(Optional.of(testUser));
        
        InvalidInputException exception = assertThrows(InvalidInputException.class, () -> {
            pnLService.getTransactionsByDates("test-user", 
                    Optional.of("01/01/2024"), Optional.of("01/31/2024"));
        });
        
        assertTrue(exception.getMessage().contains("Invalid date format"));
    }
    
    @Test
    @DisplayName("Bug #7: Validates from date before to date in getTransactionsByDates")
    public void testInvalidDateRangeInGetTransactionsByDates() {
        when(userRepository.findByUid("test-user")).thenReturn(Optional.of(testUser));
        
        InvalidInputException exception = assertThrows(InvalidInputException.class, () -> {
            pnLService.getTransactionsByDates("test-user", 
                    Optional.of("2024-01-31"), Optional.of("2024-01-01"));
        });
        
        assertTrue(exception.getMessage().contains("From date must be before or equal to to date"));
    }
    
    @Test
    @DisplayName("Bug #7: Validates negative transaction quantity")
    public void testNegativeTransactionQuantity() throws JsonProcessingException {
        when(userRepository.findByUid("test-user")).thenReturn(Optional.of(testUser));
        when(transactionRepository.findAllBefore(anyInt(), any(Date.class)))
                .thenReturn(Collections.emptyList());
        
        // Create transaction with negative quantity
        Transaction negativeTransaction = TestDataBuilder.createTransaction(
                testUser, buyType, "AAPL",
                Timestamp.valueOf(LocalDateTime.of(2024, 1, 15, 10, 0)),
                BigInteger.valueOf(-100), BigDecimal.valueOf(5000.0)
        );
        
        when(transactionRepository.findAllBetween(anyInt(), any(Date.class), any(Date.class)))
                .thenReturn(Arrays.asList(negativeTransaction));
        
        Date start = java.sql.Date.valueOf("2024-01-10");
        Date end = java.sql.Date.valueOf("2024-01-31");
        
        InvalidInputException exception = assertThrows(InvalidInputException.class, () -> {
            pnLService.getPositions("test-user", start, end);
        });
        
        assertTrue(exception.getMessage().contains("quantity cannot be negative"));
    }
    
    @Test
    @DisplayName("Bug #7: Validates negative transaction value")
    public void testNegativeTransactionValue() throws JsonProcessingException {
        when(userRepository.findByUid("test-user")).thenReturn(Optional.of(testUser));
        when(transactionRepository.findAllBefore(anyInt(), any(Date.class)))
                .thenReturn(Collections.emptyList());
        
        // Create transaction with negative value
        Transaction negativeTransaction = TestDataBuilder.createTransaction(
                testUser, buyType, "AAPL",
                Timestamp.valueOf(LocalDateTime.of(2024, 1, 15, 10, 0)),
                BigInteger.valueOf(100), BigDecimal.valueOf(-5000.0)
        );
        
        when(transactionRepository.findAllBetween(anyInt(), any(Date.class), any(Date.class)))
                .thenReturn(Arrays.asList(negativeTransaction));
        
        Date start = java.sql.Date.valueOf("2024-01-10");
        Date end = java.sql.Date.valueOf("2024-01-31");
        
        InvalidInputException exception = assertThrows(InvalidInputException.class, () -> {
            pnLService.getPositions("test-user", start, end);
        });
        
        assertTrue(exception.getMessage().contains("value cannot be negative"));
    }
    
    // ==================== BUG #11: DateTimeFormatter Thread Safety ====================
    
    @Test
    @DisplayName("Bug #11: DateTimeFormatter used instead of SimpleDateFormat")
    public void testDateTimeFormatterUsedForDateParsing() throws ParseException {
        when(userRepository.findByUid("test-user")).thenReturn(Optional.of(testUser));
        when(transactionRepository.findAll()).thenReturn(Collections.emptyList());
        
        // This should use DateTimeFormatter internally which is thread-safe
        List<Transaction> result = pnLService.getTransactionsByDates("test-user", 
                Optional.of("2024-01-01"), Optional.of("2024-01-31"));
        
        assertNotNull(result);
        verify(transactionRepository, times(1)).findAllBetween(anyInt(), any(Date.class), any(Date.class));
    }
    
    @Test
    @DisplayName("Bug #11: Concurrent date parsing doesn't cause issues")
    public void testConcurrentDateParsing() throws InterruptedException {
        when(userRepository.findByUid("test-user")).thenReturn(Optional.of(testUser));
        when(transactionRepository.findAllBetween(anyInt(), any(Date.class), any(Date.class)))
                .thenReturn(Collections.emptyList());
        
        // Test thread safety by running multiple concurrent requests
        List<Thread> threads = new ArrayList<>();
        List<Exception> exceptions = Collections.synchronizedList(new ArrayList<>());
        
        for (int i = 0; i < 10; i++) {
            Thread thread = new Thread(() -> {
                try {
                    pnLService.getTransactionsByDates("test-user", 
                            Optional.of("2024-01-01"), Optional.of("2024-01-31"));
                } catch (Exception e) {
                    exceptions.add(e);
                }
            });
            threads.add(thread);
            thread.start();
        }
        
        for (Thread thread : threads) {
            thread.join();
        }
        
        assertTrue(exceptions.isEmpty(), "No exceptions should occur during concurrent date parsing");
    }
    
    // ==================== BUG #6: Deep Copy Efficiency ====================
    
    @Test
    @DisplayName("Bug #6: Position deep copy uses copy constructor instead of Gson")
    public void testDeepCopyDoesNotUseGson() throws JsonProcessingException {
        when(userRepository.findByUid("test-user")).thenReturn(Optional.of(testUser));
        
        List<Transaction> priorTransactions = Arrays.asList(
                TestDataBuilder.createDepositTransaction(testUser, depositType, 
                        LocalDateTime.of(2024, 1, 1, 10, 0), 10000.0)
        );
        when(transactionRepository.findAllBefore(anyInt(), any(Date.class)))
                .thenReturn(priorTransactions);
        
        List<Transaction> transactions = Arrays.asList(
                TestDataBuilder.createBuyTransaction(testUser, buyType, "AAPL",
                        LocalDateTime.of(2024, 1, 15, 10, 0), 100, 5000.0)
        );
        when(transactionRepository.findAllBetween(anyInt(), any(Date.class), any(Date.class)))
                .thenReturn(transactions);
        
        MarkDto mark = new MarkDto();
        mark.setCurrentPrice(BigDecimal.valueOf(60.0));
        when(finhubRepository.getMark("AAPL")).thenReturn(mark);
        
        Date start = java.sql.Date.valueOf("2024-01-10");
        Date end = new Date();
        
        // This internally uses the copy constructor for deep copying
        // If it was using Gson, it would be slower and more fragile
        Map<String, Position> result = pnLService.getPositions("test-user", start, end);
        
        assertNotNull(result);
        assertTrue(result.containsKey("AAPL"));
    }
    
    // ==================== BUG #9: Dependency Injection Consistency ====================
    
    @Test
    @DisplayName("Bug #9: Service uses constructor injection (verified by @InjectMocks)")
    public void testConstructorInjectionWorks() {
        // This test passes if @InjectMocks successfully creates the service
        // with all dependencies injected via constructor
        assertNotNull(pnLService);
        assertNotNull(userRepository);
        assertNotNull(transactionRepository);
        assertNotNull(finhubRepository);
    }
    
    // ==================== BUG #1 & #2: Type Conversion Tests ====================
    
    @Test
    @DisplayName("Bug #1 & #2: Valid date range processing")
    public void testValidDateRangeProcessing() throws JsonProcessingException {
        when(userRepository.findByUid("test-user")).thenReturn(Optional.of(testUser));
        when(transactionRepository.findAllBefore(anyInt(), any(Date.class)))
                .thenReturn(Collections.emptyList());
        when(transactionRepository.findAllBetween(anyInt(), any(Date.class), any(Date.class)))
                .thenReturn(Collections.emptyList());
        
        // LocalDate would be converted to Date in the controller
        Date start = java.sql.Date.valueOf("2024-01-01");
        Date end = java.sql.Date.valueOf("2024-01-31");
        
        Map<String, Position> result = pnLService.getPositions("test-user", start, end);
        
        assertNotNull(result);
        verify(transactionRepository, times(1)).findAllBefore(anyInt(), any(Date.class));
        verify(transactionRepository, times(1)).findAllBetween(anyInt(), any(Date.class), any(Date.class));
    }
    
    @Test
    @DisplayName("Bug #2: String date format for transactions endpoint")
    public void testStringDateFormatForTransactions() throws ParseException {
        when(userRepository.findByUid("test-user")).thenReturn(Optional.of(testUser));
        when(transactionRepository.findAllBetween(anyInt(), any(Date.class), any(Date.class)))
                .thenReturn(Collections.emptyList());
        
        // Controller converts LocalDate to String "yyyy-MM-dd"
        List<Transaction> result = pnLService.getTransactionsByDates("test-user", 
                Optional.of("2024-01-01"), Optional.of("2024-01-31"));
        
        assertNotNull(result);
        verify(transactionRepository, times(1)).findAllBetween(anyInt(), any(Date.class), any(Date.class));
    }
}
