package com.companyx.equity.service;

import com.companyx.equity.TestDataBuilder;
import com.companyx.equity.CorporateActionTestSupport;
import com.companyx.equity.dto.CandleDto;
import com.companyx.equity.dto.MarkDto;
import com.companyx.equity.model.Position;
import com.companyx.equity.model.Transaction;
import com.companyx.equity.model.TransactionType;
import com.companyx.equity.model.User;
import com.companyx.equity.repository.FinhubRepository;
import com.companyx.equity.repository.TransactionRepository;
import com.companyx.equity.repository.UserRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.lenient;

/**
 * Comprehensive P&L Calculation Tests
 * 
 * These tests establish the ground truth for expected P&L behavior across all scenarios.
 * They serve as acceptance criteria before refactoring to mathematical symmetry.
 * 
 * Test Scenarios:
 * 1. Simple Long Positions (buy → hold/sell)
 * 2. Simple Short Positions (sell → cover)
 * 3. Position Transitions (long→short, short→long)
 * 4. Multiple Buys/Sells (averaging)
 * 5. Partial Closes
 * 6. Edge Cases
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("P&L Calculation - Comprehensive Test Suite")
public class PnLCalculationTest {
    
    @Mock
    private UserRepository userRepository;
    
    @Mock
    private TransactionRepository transactionRepository;
    
    @Mock
    private FinhubRepository finhubRepository;

    @Mock
    private CorporateActionService corporateActionService;
    
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
        CorporateActionTestSupport.stubPassThroughCorporateActions(corporateActionService);
    }
    
    /**
     * Helper method to compare BigDecimal values by numerical value, ignoring scale differences.
     * This avoids issues like comparing 1000.0 vs 1000.000000.
     */
    private void assertBigDecimalEquals(BigDecimal expected, BigDecimal actual, String message) {
        assertEquals(0, expected.compareTo(actual), 
            message + " (expected: " + expected + ", actual: " + actual + ")");
    }
    
    /**
     * Helper method to create a CandleDto mock with a single closing price.
     */
    private CandleDto createCandleDto(double closePrice) {
        CandleDto candle = new CandleDto();
        candle.setClose(Collections.singletonList(BigDecimal.valueOf(closePrice)));
        candle.setStatus("ok");
        return candle;
    }
    
    // ==================== SCENARIO 1: SIMPLE LONG POSITIONS ====================
    
    @Nested
    @DisplayName("Scenario 1: Simple Long Positions")
    class SimpleLongPositions {
        
        @Test
        @DisplayName("1.1 Buy 100 @ $50, Sell 100 @ $60 = $1,000 profit")
        public void testLongPositionProfit() throws JsonProcessingException {
            // Setup: Deposit cash
            when(userRepository.findByUid("test-user")).thenReturn(Optional.of(testUser));
            List<Transaction> priorTrans = Arrays.asList(
                TestDataBuilder.createDepositTransaction(testUser, depositType, 
                    LocalDateTime.of(2024, 1, 1, 10, 0), 10000.0)
            );
            when(transactionRepository.findAllBefore(anyLong(), any(Date.class)))
                .thenReturn(priorTrans);
            
            // Execute: Buy 100 @ $50, Sell 100 @ $60
            List<Transaction> transactions = Arrays.asList(
                TestDataBuilder.createBuyTransaction(testUser, buyType, "AAPL",
                    LocalDateTime.of(2024, 1, 15, 10, 0), 100, 5000.0),
                TestDataBuilder.createSellTransaction(testUser, sellType, "AAPL",
                    LocalDateTime.of(2024, 1, 20, 10, 0), 100, 6000.0)
            );
            when(transactionRepository.findAllBetween(anyLong(), any(Date.class), any(Date.class)))
                .thenReturn(transactions);
            
            Date start = java.sql.Date.valueOf("2024-01-10");
            Date end = java.sql.Date.valueOf("2024-01-31");
            
            // Verify
            Map<String, Position> result = pnLService.getPositions("test-user", start, end);
            
            Position aaplPosition = result.get("AAPL");
            assertNotNull(aaplPosition, "AAPL position should exist");
            
            // Expected: Closed position (qty = 0)
            assertEquals(BigInteger.ZERO, aaplPosition.getQuantity(), 
                "Position should be closed (quantity = 0)");
            
            // Expected: Realized P&L = (60 - 50) * 100 = $1,000
            assertBigDecimalEquals(new BigDecimal("1000.0"), aaplPosition.getRealized(), 
                "Realized P&L should be $1,000 profit");
            
            // Expected: Unrealized = 0 (position closed)
            assertBigDecimalEquals(BigDecimal.ZERO, aaplPosition.getUnrealized(), 
                "Unrealized P&L should be $0 (position closed)");
            
            // Expected: Cash = 10000 - 5000 + 6000 = $11,000
            Position cashPosition = result.get("cash");
            assertBigDecimalEquals(new BigDecimal("11000.0"), cashPosition.getValue(), 
                "Cash should be $11,000");
        }
        
        @Test
        @DisplayName("1.2 Buy 100 @ $50, Sell 100 @ $40 = $1,000 loss")
        public void testLongPositionLoss() throws JsonProcessingException {
            when(userRepository.findByUid("test-user")).thenReturn(Optional.of(testUser));
            
            List<Transaction> priorTrans = Arrays.asList(
                TestDataBuilder.createDepositTransaction(testUser, depositType, 
                    LocalDateTime.of(2024, 1, 1, 10, 0), 10000.0)
            );
            when(transactionRepository.findAllBefore(anyLong(), any(Date.class)))
                .thenReturn(priorTrans);
            
            // Buy 100 @ $50, Sell 100 @ $40 (loss)
            List<Transaction> transactions = Arrays.asList(
                TestDataBuilder.createBuyTransaction(testUser, buyType, "AAPL",
                    LocalDateTime.of(2024, 1, 15, 10, 0), 100, 5000.0),
                TestDataBuilder.createSellTransaction(testUser, sellType, "AAPL",
                    LocalDateTime.of(2024, 1, 20, 10, 0), 100, 4000.0)
            );
            when(transactionRepository.findAllBetween(anyLong(), any(Date.class), any(Date.class)))
                .thenReturn(transactions);
            
            Date start = java.sql.Date.valueOf("2024-01-10");
            Date end = java.sql.Date.valueOf("2024-01-31");
            
            Map<String, Position> result = pnLService.getPositions("test-user", start, end);
            Position aaplPosition = result.get("AAPL");
            
            // Expected: Closed position
            assertEquals(BigInteger.ZERO, aaplPosition.getQuantity());
            
            // Expected: Realized P&L = (40 - 50) * 100 = -$1,000
            assertBigDecimalEquals(new BigDecimal("-1000.0"), aaplPosition.getRealized(), 
                "Realized P&L should be -$1,000 loss");
            
            // Expected: Cash = 10000 - 5000 + 4000 = $9,000
            assertBigDecimalEquals(new BigDecimal("9000.0"), result.get("cash").getValue(), 
                "Cash should be $9,000");
        }
        
        @Test
        @DisplayName("1.3 Buy 100 @ $50, Hold = $0 realized, $500 unrealized @ $55")
        public void testLongPositionHold() throws JsonProcessingException {
            when(userRepository.findByUid("test-user")).thenReturn(Optional.of(testUser));
            
            List<Transaction> priorTrans = Arrays.asList(
                TestDataBuilder.createDepositTransaction(testUser, depositType, 
                    LocalDateTime.of(2024, 1, 1, 10, 0), 10000.0)
            );
            when(transactionRepository.findAllBefore(anyLong(), any(Date.class)))
                .thenReturn(priorTrans);
            
            // Buy 100 @ $50, hold
            List<Transaction> transactions = Arrays.asList(
                TestDataBuilder.createBuyTransaction(testUser, buyType, "AAPL",
                    LocalDateTime.of(2024, 1, 15, 10, 0), 100, 5000.0)
            );
            when(transactionRepository.findAllBetween(anyLong(), any(Date.class), any(Date.class)))
                .thenReturn(transactions);
            
            // Mock Finhub current price and historical candle
            MarkDto mark = new MarkDto();
            mark.setCurrentPrice(BigDecimal.valueOf(55.0));
            lenient().when(finhubRepository.getMark("AAPL")).thenReturn(mark);
            lenient().when(finhubRepository.getCandle(eq("AAPL"), any(Date.class), any(Date.class)))
                .thenReturn(createCandleDto(55.0));
            
            Date start = java.sql.Date.valueOf("2024-01-10");
            Date end = new Date(); // Today
            
            Map<String, Position> result = pnLService.getPositions("test-user", start, end);
            Position aaplPosition = result.get("AAPL");
            
            // Expected: Open long position
            assertEquals(BigInteger.valueOf(100), aaplPosition.getQuantity());
            
            // Expected: No realized P&L (haven't sold)
            assertBigDecimalEquals(BigDecimal.ZERO, aaplPosition.getRealized(), 
                "Realized P&L should be $0 (haven't sold yet)");
            
            // Expected: Unrealized P&L = (55 - 50) * 100 = $500
            assertBigDecimalEquals(new BigDecimal("500.0"), aaplPosition.getUnrealized(), 
                "Unrealized P&L should be $500 profit");
        }
        
        @Test
        @DisplayName("1.4 Buy 100 @ $50, Sell 50 @ $60 = $500 realized, $250 unrealized @ $55")
        public void testPartialSale() throws JsonProcessingException {
            when(userRepository.findByUid("test-user")).thenReturn(Optional.of(testUser));
            
            List<Transaction> priorTrans = Arrays.asList(
                TestDataBuilder.createDepositTransaction(testUser, depositType, 
                    LocalDateTime.of(2024, 1, 1, 10, 0), 10000.0)
            );
            when(transactionRepository.findAllBefore(anyLong(), any(Date.class)))
                .thenReturn(priorTrans);
            
            // Buy 100 @ $50, Sell 50 @ $60
            List<Transaction> transactions = Arrays.asList(
                TestDataBuilder.createBuyTransaction(testUser, buyType, "AAPL",
                    LocalDateTime.of(2024, 1, 15, 10, 0), 100, 5000.0),
                TestDataBuilder.createSellTransaction(testUser, sellType, "AAPL",
                    LocalDateTime.of(2024, 1, 20, 10, 0), 50, 3000.0)
            );
            when(transactionRepository.findAllBetween(anyLong(), any(Date.class), any(Date.class)))
                .thenReturn(transactions);
            
            // Mock Finhub current price and historical candle
            MarkDto mark = new MarkDto();
            mark.setCurrentPrice(BigDecimal.valueOf(55.0));
            lenient().when(finhubRepository.getMark("AAPL")).thenReturn(mark);
            lenient().when(finhubRepository.getCandle(eq("AAPL"), any(Date.class), any(Date.class)))
                .thenReturn(createCandleDto(55.0));
            
            Date start = java.sql.Date.valueOf("2024-01-10");
            Date end = new Date();
            
            Map<String, Position> result = pnLService.getPositions("test-user", start, end);
            Position aaplPosition = result.get("AAPL");
            
            // Expected: Still holding 50 shares
            assertEquals(BigInteger.valueOf(50), aaplPosition.getQuantity());
            
            // Expected: Realized P&L = (60 - 50) * 50 = $500
            assertBigDecimalEquals(new BigDecimal("500.0"), aaplPosition.getRealized(), 
                "Realized P&L should be $500 on 50 shares sold");
            
            // Expected: Unrealized P&L = (55 - 50) * 50 = $250
            assertBigDecimalEquals(new BigDecimal("250.0"), aaplPosition.getUnrealized(), 
                "Unrealized P&L should be $250 on remaining 50 shares");
        }
    }
    
    // ==================== SCENARIO 2: SIMPLE SHORT POSITIONS ====================
    
    @Nested
    @DisplayName("Scenario 2: Simple Short Positions")
    class SimpleShortPositions {
        
        @Test
        @DisplayName("2.1 Short 100 @ $50, Cover 100 @ $40 = $1,000 profit")
        public void testShortPositionProfit() throws JsonProcessingException {
            when(userRepository.findByUid("test-user")).thenReturn(Optional.of(testUser));
            
            List<Transaction> priorTrans = Arrays.asList(
                TestDataBuilder.createDepositTransaction(testUser, depositType, 
                    LocalDateTime.of(2024, 1, 1, 10, 0), 10000.0)
            );
            when(transactionRepository.findAllBefore(anyLong(), any(Date.class)))
                .thenReturn(priorTrans);
            
            // Short 100 @ $50 (sell), Cover @ $40 (buy)
            List<Transaction> transactions = Arrays.asList(
                TestDataBuilder.createSellTransaction(testUser, sellType, "AAPL",
                    LocalDateTime.of(2024, 1, 15, 10, 0), 100, 5000.0),
                TestDataBuilder.createBuyTransaction(testUser, buyType, "AAPL",
                    LocalDateTime.of(2024, 1, 20, 10, 0), 100, 4000.0)
            );
            when(transactionRepository.findAllBetween(anyLong(), any(Date.class), any(Date.class)))
                .thenReturn(transactions);
            
            Date start = java.sql.Date.valueOf("2024-01-10");
            Date end = java.sql.Date.valueOf("2024-01-31");
            
            Map<String, Position> result = pnLService.getPositions("test-user", start, end);
            Position aaplPosition = result.get("AAPL");
            
            // Expected: Closed position
            assertEquals(BigInteger.ZERO, aaplPosition.getQuantity());
            
            // Expected: Realized P&L = (50 - 40) * 100 = $1,000 profit
            assertBigDecimalEquals(new BigDecimal("1000.0"), aaplPosition.getRealized(), 
                "Realized P&L should be $1,000 profit from short");
            
            // Expected: Cash = 10000 + 5000 - 4000 = $11,000
            assertBigDecimalEquals(new BigDecimal("11000.0"), result.get("cash").getValue(), 
                "Cash should be $11,000");
        }
        
        @Test
        @DisplayName("2.2 Short 100 @ $50, Cover 100 @ $60 = $1,000 loss")
        public void testShortPositionLoss() throws JsonProcessingException {
            when(userRepository.findByUid("test-user")).thenReturn(Optional.of(testUser));
            
            List<Transaction> priorTrans = Arrays.asList(
                TestDataBuilder.createDepositTransaction(testUser, depositType, 
                    LocalDateTime.of(2024, 1, 1, 10, 0), 10000.0)
            );
            when(transactionRepository.findAllBefore(anyLong(), any(Date.class)))
                .thenReturn(priorTrans);
            
            // Short 100 @ $50, Cover @ $60 (loss)
            List<Transaction> transactions = Arrays.asList(
                TestDataBuilder.createSellTransaction(testUser, sellType, "AAPL",
                    LocalDateTime.of(2024, 1, 15, 10, 0), 100, 5000.0),
                TestDataBuilder.createBuyTransaction(testUser, buyType, "AAPL",
                    LocalDateTime.of(2024, 1, 20, 10, 0), 100, 6000.0)
            );
            when(transactionRepository.findAllBetween(anyLong(), any(Date.class), any(Date.class)))
                .thenReturn(transactions);
            
            Date start = java.sql.Date.valueOf("2024-01-10");
            Date end = java.sql.Date.valueOf("2024-01-31");
            
            Map<String, Position> result = pnLService.getPositions("test-user", start, end);
            Position aaplPosition = result.get("AAPL");
            
            // Expected: Closed position
            assertEquals(BigInteger.ZERO, aaplPosition.getQuantity());
            
            // Expected: Realized P&L = (50 - 60) * 100 = -$1,000 loss
            assertBigDecimalEquals(new BigDecimal("-1000.0"), aaplPosition.getRealized(), 
                "Realized P&L should be -$1,000 loss from short");
            
            // Expected: Cash = 10000 + 5000 - 6000 = $9,000
            assertBigDecimalEquals(new BigDecimal("9000.0"), result.get("cash").getValue(), 
                "Cash should be $9,000");
        }
        
        @Test
        @DisplayName("2.3 Short 100 @ $50, Hold = $0 realized, -$500 unrealized @ $55")
        public void testShortPositionHold() throws JsonProcessingException {
            when(userRepository.findByUid("test-user")).thenReturn(Optional.of(testUser));
            
            List<Transaction> priorTrans = Arrays.asList(
                TestDataBuilder.createDepositTransaction(testUser, depositType, 
                    LocalDateTime.of(2024, 1, 1, 10, 0), 10000.0)
            );
            when(transactionRepository.findAllBefore(anyLong(), any(Date.class)))
                .thenReturn(priorTrans);
            
            // Short 100 @ $50, hold
            List<Transaction> transactions = Arrays.asList(
                TestDataBuilder.createSellTransaction(testUser, sellType, "AAPL",
                    LocalDateTime.of(2024, 1, 15, 10, 0), 100, 5000.0)
            );
            when(transactionRepository.findAllBetween(anyLong(), any(Date.class), any(Date.class)))
                .thenReturn(transactions);
            
            // Mock Finhub current price and historical candle
            MarkDto mark = new MarkDto();
            mark.setCurrentPrice(BigDecimal.valueOf(55.0));
            lenient().when(finhubRepository.getMark("AAPL")).thenReturn(mark);
            lenient().when(finhubRepository.getCandle(eq("AAPL"), any(Date.class), any(Date.class)))
                .thenReturn(createCandleDto(55.0));
            
            Date start = java.sql.Date.valueOf("2024-01-10");
            Date end = new Date();
            
            Map<String, Position> result = pnLService.getPositions("test-user", start, end);
            Position aaplPosition = result.get("AAPL");
            
            // Expected: Open short position (negative quantity)
            assertEquals(BigInteger.valueOf(-100), aaplPosition.getQuantity());
            
            // Expected: No realized P&L
            assertBigDecimalEquals(BigDecimal.ZERO, aaplPosition.getRealized(), 
                "Realized P&L should be $0 (haven't covered yet)");
            
            // Expected: Unrealized P&L = (50 - 55) * 100 = -$500 loss
            assertBigDecimalEquals(new BigDecimal("-500.0"), aaplPosition.getUnrealized(), 
                "Unrealized P&L should be -$500 loss (price went up)");
        }
    }
    
    // ==================== SCENARIO 3: POSITION TRANSITIONS ====================
    
    @Nested
    @DisplayName("Scenario 3: Position Transitions")
    class PositionTransitions {
        
        @Test
        @DisplayName("3.1 Long to Short: Buy 100 @ $50, Sell 150 @ $60 = close long + open short 50")
        public void testLongToShortTransition() throws JsonProcessingException {
            when(userRepository.findByUid("test-user")).thenReturn(Optional.of(testUser));
            
            List<Transaction> priorTrans = Arrays.asList(
                TestDataBuilder.createDepositTransaction(testUser, depositType, 
                    LocalDateTime.of(2024, 1, 1, 10, 0), 20000.0)
            );
            when(transactionRepository.findAllBefore(anyLong(), any(Date.class)))
                .thenReturn(priorTrans);
            
            // Buy 100 @ $50 (long 100)
            // Sell 150 @ $60 (closes long 100, opens short 50)
            List<Transaction> transactions = Arrays.asList(
                TestDataBuilder.createBuyTransaction(testUser, buyType, "AAPL",
                    LocalDateTime.of(2024, 1, 15, 10, 0), 100, 5000.0),
                TestDataBuilder.createSellTransaction(testUser, sellType, "AAPL",
                    LocalDateTime.of(2024, 1, 20, 10, 0), 150, 9000.0)
            );
            when(transactionRepository.findAllBetween(anyLong(), any(Date.class), any(Date.class)))
                .thenReturn(transactions);
            
            // Mock Finhub current price and historical candle
            MarkDto mark = new MarkDto();
            mark.setCurrentPrice(BigDecimal.valueOf(55.0));
            lenient().when(finhubRepository.getMark("AAPL")).thenReturn(mark);
            lenient().when(finhubRepository.getCandle(eq("AAPL"), any(Date.class), any(Date.class)))
                .thenReturn(createCandleDto(55.0));
            
            Date start = java.sql.Date.valueOf("2024-01-10");
            Date end = new Date();
            
            Map<String, Position> result = pnLService.getPositions("test-user", start, end);
            Position aaplPosition = result.get("AAPL");
            
            // Expected: Short 50 shares
            assertEquals(BigInteger.valueOf(-50), aaplPosition.getQuantity(), 
                "Should have short position of 50 shares");
            
            // Expected: Realized P&L from closing long = (60 - 50) * 100 = $1,000
            assertBigDecimalEquals(new BigDecimal("1000.0"), aaplPosition.getRealized(), 
                "Realized P&L should be $1,000 from closing long position");
            
            // Expected: Unrealized P&L from short = (60 - 55) * 50 = $250 profit
            assertBigDecimalEquals(new BigDecimal("250.0"), aaplPosition.getUnrealized(), 
                "Unrealized P&L should be $250 profit on short position");
        }
        
        @Test
        @DisplayName("3.2 Short to Long: Short 100 @ $50, Buy 150 @ $40 = close short + open long 50")
        public void testShortToLongTransition() throws JsonProcessingException {
            when(userRepository.findByUid("test-user")).thenReturn(Optional.of(testUser));
            
            List<Transaction> priorTrans = Arrays.asList(
                TestDataBuilder.createDepositTransaction(testUser, depositType, 
                    LocalDateTime.of(2024, 1, 1, 10, 0), 20000.0)
            );
            when(transactionRepository.findAllBefore(anyLong(), any(Date.class)))
                .thenReturn(priorTrans);
            
            // Short 100 @ $50 (short 100)
            // Buy 150 @ $40 (closes short 100, opens long 50)
            List<Transaction> transactions = Arrays.asList(
                TestDataBuilder.createSellTransaction(testUser, sellType, "AAPL",
                    LocalDateTime.of(2024, 1, 15, 10, 0), 100, 5000.0),
                TestDataBuilder.createBuyTransaction(testUser, buyType, "AAPL",
                    LocalDateTime.of(2024, 1, 20, 10, 0), 150, 6000.0)
            );
            when(transactionRepository.findAllBetween(anyLong(), any(Date.class), any(Date.class)))
                .thenReturn(transactions);
            
            // Mock Finhub current price and historical candle
            MarkDto mark = new MarkDto();
            mark.setCurrentPrice(BigDecimal.valueOf(45.0));
            lenient().when(finhubRepository.getMark("AAPL")).thenReturn(mark);
            lenient().when(finhubRepository.getCandle(eq("AAPL"), any(Date.class), any(Date.class)))
                .thenReturn(createCandleDto(45.0));
            
            Date start = java.sql.Date.valueOf("2024-01-10");
            Date end = new Date();
            
            Map<String, Position> result = pnLService.getPositions("test-user", start, end);
            Position aaplPosition = result.get("AAPL");
            
            // Expected: Long 50 shares
            assertEquals(BigInteger.valueOf(50), aaplPosition.getQuantity(), 
                "Should have long position of 50 shares");
            
            // Expected: Realized P&L from closing short = (50 - 40) * 100 = $1,000
            assertBigDecimalEquals(new BigDecimal("1000.0"), aaplPosition.getRealized(), 
                "Realized P&L should be $1,000 from closing short position");
            
            // Expected: Unrealized P&L from long = (45 - 40) * 50 = $250 profit
            assertBigDecimalEquals(new BigDecimal("250.0"), aaplPosition.getUnrealized(), 
                "Unrealized P&L should be $250 profit on long position");
        }
    }
    
    // ==================== SCENARIO 4: AVERAGE COST BASIS ====================
    
    @Nested
    @DisplayName("Scenario 4: Average Cost Basis")
    class AverageCostBasis {
        
        @Test
        @DisplayName("4.1 Buy 100 @ $50 + Buy 100 @ $60 = Avg cost $55, then sell 150 @ $65")
        public void testAverageCostMultipleBuys() throws JsonProcessingException {
            when(userRepository.findByUid("test-user")).thenReturn(Optional.of(testUser));
            
            List<Transaction> priorTrans = Arrays.asList(
                TestDataBuilder.createDepositTransaction(testUser, depositType, 
                    LocalDateTime.of(2024, 1, 1, 10, 0), 20000.0)
            );
            when(transactionRepository.findAllBefore(anyLong(), any(Date.class)))
                .thenReturn(priorTrans);
            
            // Buy 100 @ $50 ($5,000)
            // Buy 100 @ $60 ($6,000)
            // Total: 200 shares, $11,000 = $55 avg
            // Sell 150 @ $65
            List<Transaction> transactions = Arrays.asList(
                TestDataBuilder.createBuyTransaction(testUser, buyType, "AAPL",
                    LocalDateTime.of(2024, 1, 15, 10, 0), 100, 5000.0),
                TestDataBuilder.createBuyTransaction(testUser, buyType, "AAPL",
                    LocalDateTime.of(2024, 1, 16, 10, 0), 100, 6000.0),
                TestDataBuilder.createSellTransaction(testUser, sellType, "AAPL",
                    LocalDateTime.of(2024, 1, 20, 10, 0), 150, 9750.0)
            );
            when(transactionRepository.findAllBetween(anyLong(), any(Date.class), any(Date.class)))
                .thenReturn(transactions);
            
            // Mock Finhub current price and historical candle
            MarkDto mark = new MarkDto();
            mark.setCurrentPrice(BigDecimal.valueOf(70.0));
            lenient().when(finhubRepository.getMark("AAPL")).thenReturn(mark);
            lenient().when(finhubRepository.getCandle(eq("AAPL"), any(Date.class), any(Date.class)))
                .thenReturn(createCandleDto(70.0));
            
            Date start = java.sql.Date.valueOf("2024-01-10");
            Date end = new Date();
            
            Map<String, Position> result = pnLService.getPositions("test-user", start, end);
            Position aaplPosition = result.get("AAPL");
            
            // Expected: Holding 50 shares
            assertEquals(BigInteger.valueOf(50), aaplPosition.getQuantity());
            
            // Expected: Realized P&L = (65 - 55) * 150 = $1,500
            assertBigDecimalEquals(new BigDecimal("1500.0"), aaplPosition.getRealized(), 
                "Realized P&L should be $1,500 using average cost of $55");
            
            // Expected: Unrealized P&L = (70 - 55) * 50 = $750
            assertBigDecimalEquals(new BigDecimal("750.0"), aaplPosition.getUnrealized(), 
                "Unrealized P&L should be $750 on remaining 50 shares");
        }
    }
    
    // ==================== SCENARIO 5: EDGE CASES ====================
    
    @Nested
    @DisplayName("Scenario 5: Edge Cases")
    class EdgeCases {
        
        @Test
        @DisplayName("5.1 Zero quantity after multiple round trips")
        public void testMultipleRoundTrips() throws JsonProcessingException {
            when(userRepository.findByUid("test-user")).thenReturn(Optional.of(testUser));
            
            List<Transaction> priorTrans = Arrays.asList(
                TestDataBuilder.createDepositTransaction(testUser, depositType, 
                    LocalDateTime.of(2024, 1, 1, 10, 0), 50000.0)
            );
            when(transactionRepository.findAllBefore(anyLong(), any(Date.class)))
                .thenReturn(priorTrans);
            
            // Multiple round trips
            List<Transaction> transactions = Arrays.asList(
                // Trip 1: Buy 100 @ $50, Sell 100 @ $55
                TestDataBuilder.createBuyTransaction(testUser, buyType, "AAPL",
                    LocalDateTime.of(2024, 1, 10, 10, 0), 100, 5000.0),
                TestDataBuilder.createSellTransaction(testUser, sellType, "AAPL",
                    LocalDateTime.of(2024, 1, 11, 10, 0), 100, 5500.0),
                // Trip 2: Buy 100 @ $60, Sell 100 @ $65
                TestDataBuilder.createBuyTransaction(testUser, buyType, "AAPL",
                    LocalDateTime.of(2024, 1, 12, 10, 0), 100, 6000.0),
                TestDataBuilder.createSellTransaction(testUser, sellType, "AAPL",
                    LocalDateTime.of(2024, 1, 13, 10, 0), 100, 6500.0)
            );
            when(transactionRepository.findAllBetween(anyLong(), any(Date.class), any(Date.class)))
                .thenReturn(transactions);
            
            Date start = java.sql.Date.valueOf("2024-01-09");
            Date end = java.sql.Date.valueOf("2024-01-31");
            
            Map<String, Position> result = pnLService.getPositions("test-user", start, end);
            Position aaplPosition = result.get("AAPL");
            
            // Expected: Flat (no position)
            assertEquals(BigInteger.ZERO, aaplPosition.getQuantity());
            
            // Expected: Realized P&L = Trip1($500) + Trip2($500) = $1,000
            assertBigDecimalEquals(new BigDecimal("1000.0"), aaplPosition.getRealized(), 
                "Total realized P&L should be $1,000 from both round trips");
        }
    }
}
