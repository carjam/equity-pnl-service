package com.companyx.equity.service;

import com.companyx.equity.model.Transaction;
import com.companyx.equity.model.TransactionType;
import com.companyx.equity.model.corporateaction.Dividend;
import com.companyx.equity.model.corporateaction.DividendType;
import com.companyx.equity.model.Position;
import com.companyx.equity.model.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DividendServiceTest {

    private DividendService service;
    private User testUser;

    @BeforeEach
    void setUp() {
        service = new DividendService();
        testUser = new User();
        testUser.setId(1L);
    }

    @Test
    void shouldReturnZeroIncomeWhenNoDividends() {
        // Arrange
        BigDecimal shares = BigDecimal.valueOf(100);
        List<Dividend> dividends = Collections.emptyList();

        // Act
        BigDecimal income = service.calculateIncome(shares, dividends);

        // Assert
        assertEquals(BigDecimal.ZERO, income);
    }

    @Test
    void shouldCalculateSingleCashDividendIncome() {
        // Arrange
        BigDecimal shares = BigDecimal.valueOf(100);
        Dividend dividend = Dividend.builder()
                .symbol("AAPL")
                .exDate(LocalDate.of(2024, 8, 9))
                .amount(new BigDecimal("0.25"))
                .type(DividendType.CASH)
                .build();

        // Act
        BigDecimal income = service.calculateIncome(shares, List.of(dividend));

        // Assert
        assertEquals(new BigDecimal("25.00"), income);
    }

    @Test
    void shouldCalculateMultipleCashDividendsIncome() {
        // Arrange
        BigDecimal shares = BigDecimal.valueOf(100);
        
        Dividend q1 = createCashDividend("AAPL", LocalDate.of(2024, 2, 9), "0.25");
        Dividend q2 = createCashDividend("AAPL", LocalDate.of(2024, 5, 9), "0.25");
        Dividend q3 = createCashDividend("AAPL", LocalDate.of(2024, 8, 9), "0.25");
        Dividend q4 = createCashDividend("AAPL", LocalDate.of(2024, 11, 9), "0.25");

        // Act
        BigDecimal income = service.calculateIncome(shares, Arrays.asList(q1, q2, q3, q4));

        // Assert
        assertEquals(new BigDecimal("100.00"), income, "4 quarters × $0.25 × 100 shares = $100");
    }

    @Test
    void shouldIgnoreStockDividendsInIncomeCalculation() {
        // Arrange
        BigDecimal shares = BigDecimal.valueOf(100);
        
        Dividend cashDiv = createCashDividend("KO", LocalDate.of(2024, 8, 9), "1.00");
        Dividend stockDiv = Dividend.builder()
                .symbol("KO")
                .exDate(LocalDate.of(2024, 6, 1))
                .amount(BigDecimal.ZERO)
                .type(DividendType.STOCK)
                .sharesPerShare(new BigDecimal("0.05"))
                .build();

        // Act
        BigDecimal income = service.calculateIncome(shares, Arrays.asList(cashDiv, stockDiv));

        // Assert
        assertEquals(new BigDecimal("100.00"), income, "Only cash dividend should be included");
    }

    @Test
    void shouldHandleZeroShares() {
        // Arrange
        BigDecimal shares = BigDecimal.ZERO;
        Dividend dividend = createCashDividend("AAPL", LocalDate.of(2024, 8, 9), "0.25");

        // Act
        BigDecimal income = service.calculateIncome(shares, List.of(dividend));

        // Assert
        assertEquals(0, income.compareTo(BigDecimal.ZERO));
    }

    @Test
    void shouldHandleNullDividendsList() {
        // Arrange
        BigDecimal shares = BigDecimal.valueOf(100);

        // Act
        BigDecimal income = service.calculateIncome(shares, null);

        // Assert
        assertEquals(BigDecimal.ZERO, income);
    }

    @Test
    void shouldApplyStockDividendToPosition() {
        // Arrange
        Position position = createPosition("KO", 100, new BigDecimal("6000.00"));
        
        Dividend stockDiv = Dividend.builder()
                .symbol("KO")
                .exDate(LocalDate.of(2024, 6, 1))
                .amount(BigDecimal.ZERO)
                .type(DividendType.STOCK)
                .sharesPerShare(new BigDecimal("0.05")) // 5% stock dividend
                .build();

        // Act
        Position result = service.applyStockDividends(position, List.of(stockDiv));

        // Assert
        assertEquals(0, BigDecimal.valueOf(105).compareTo(result.getQuantity()), "100 shares + 5% = 105 shares");
        assertEquals(new BigDecimal("6000.00"), result.getValue(), "Total basis unchanged");
    }

    @Test
    void shouldApplyMultipleStockDividends() {
        // Arrange
        Position position = createPosition("KO", 100, new BigDecimal("6000.00"));
        
        Dividend div1 = Dividend.builder()
                .symbol("KO")
                .exDate(LocalDate.of(2024, 1, 1))
                .amount(BigDecimal.ZERO)
                .type(DividendType.STOCK)
                .sharesPerShare(new BigDecimal("0.05")) // 5%
                .build();
        
        Dividend div2 = Dividend.builder()
                .symbol("KO")
                .exDate(LocalDate.of(2024, 6, 1))
                .amount(BigDecimal.ZERO)
                .type(DividendType.STOCK)
                .sharesPerShare(new BigDecimal("0.10")) // 10%
                .build();

        // Act
        Position result = service.applyStockDividends(position, Arrays.asList(div1, div2));

        // Assert
        // 100 × 1.05 = 105, then 105 × 1.10 = 115.5 (fractional shares preserved, not rounded)
        assertEquals(0, new BigDecimal("115.5").compareTo(result.getQuantity()));
        assertEquals(new BigDecimal("6000.00"), result.getValue(), "Total basis unchanged");
    }

    @Test
    void shouldIgnoreCashDividendsInStockDividendApplication() {
        // Arrange
        Position position = createPosition("KO", 100, new BigDecimal("6000.00"));
        
        Dividend cashDiv = createCashDividend("KO", LocalDate.of(2024, 3, 1), "1.00");
        Dividend stockDiv = Dividend.builder()
                .symbol("KO")
                .exDate(LocalDate.of(2024, 6, 1))
                .amount(BigDecimal.ZERO)
                .type(DividendType.STOCK)
                .sharesPerShare(new BigDecimal("0.05"))
                .build();

        // Act
        Position result = service.applyStockDividends(position, Arrays.asList(cashDiv, stockDiv));

        // Assert
        assertEquals(0, BigDecimal.valueOf(105).compareTo(result.getQuantity()), "Only stock dividend should affect quantity");
    }

    @Test
    void shouldReturnUnchangedPositionWhenNoStockDividends() {
        // Arrange
        Position position = createPosition("AAPL", 100, new BigDecimal("20000.00"));
        List<Dividend> dividends = Collections.emptyList();

        // Act
        Position result = service.applyStockDividends(position, dividends);

        // Assert
        assertEquals(0, BigDecimal.valueOf(100).compareTo(result.getQuantity()));
        assertEquals(new BigDecimal("20000.00"), result.getValue());
    }

    @Test
    void shouldHandleNullDividendsListInStockDividendApplication() {
        // Arrange
        Position position = createPosition("AAPL", 100, new BigDecimal("20000.00"));

        // Act
        Position result = service.applyStockDividends(position, null);

        // Assert
        assertEquals(position.getQuantity(), result.getQuantity());
        assertEquals(position.getValue(), result.getValue());
    }

    @Test
    void shouldThrowExceptionWhenPositionIsNull() {
        // Arrange
        Dividend dividend = Dividend.builder()
                .symbol("KO")
                .exDate(LocalDate.of(2024, 6, 1))
                .amount(BigDecimal.ZERO)
                .type(DividendType.STOCK)
                .sharesPerShare(new BigDecimal("0.05"))
                .build();

        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> {
            service.applyStockDividends(null, List.of(dividend));
        });
    }

    @Test
    void shouldCalculateIncomeForLargePositions() {
        // Arrange
        BigDecimal shares = BigDecimal.valueOf(10000);
        Dividend dividend = createCashDividend("AAPL", LocalDate.of(2024, 8, 9), "0.25");

        // Act
        BigDecimal income = service.calculateIncome(shares, List.of(dividend));

        // Assert
        assertEquals(new BigDecimal("2500.00"), income);
    }

    @Test
    void shouldHandleNegativeSharesInIncomeCalculation() {
        // Arrange - short position
        BigDecimal shares = BigDecimal.valueOf(-100);
        Dividend dividend = createCashDividend("AAPL", LocalDate.of(2024, 8, 9), "0.25");

        // Act
        BigDecimal income = service.calculateIncome(shares, List.of(dividend));

        // Assert
        assertEquals(new BigDecimal("-25.00"), income, "Short positions have negative dividend income");
    }

    @Test
    void shouldSortDividendsByDateBeforeCalculation() {
        // Arrange
        BigDecimal shares = BigDecimal.valueOf(100);
        
        Dividend div3 = createCashDividend("AAPL", LocalDate.of(2024, 8, 9), "0.25");
        Dividend div1 = createCashDividend("AAPL", LocalDate.of(2024, 2, 9), "0.25");
        Dividend div2 = createCashDividend("AAPL", LocalDate.of(2024, 5, 9), "0.25");

        // Act - dividends provided out of order
        BigDecimal income = service.calculateIncome(shares, Arrays.asList(div3, div1, div2));

        // Assert
        assertEquals(new BigDecimal("75.00"), income, "Order shouldn't matter for cash dividends");
    }

    @Test
    void shouldCalculateIncomeForFractionalDividends() {
        // Arrange
        BigDecimal shares = BigDecimal.valueOf(100);
        Dividend dividend = createCashDividend("MSFT", LocalDate.of(2024, 8, 9), "0.83");

        // Act
        BigDecimal income = service.calculateIncome(shares, List.of(dividend));

        // Assert
        assertEquals(new BigDecimal("83.00"), income);
    }

    // ==================== TRANSACTION-AWARE INCOME (ex-date quantity) ====================

    @Test
    void calculateIncome_withTransactions_buyAfterExDate_usesPreBuyQuantity() {
        // 100 shares at start; dividend ex Jan 15; buy 100 more on Jan 20
        // Only the 100 held at ex-date should receive the dividend
        Dividend div = createCashDividend("AAPL", LocalDate.of(2024, 1, 15), "1.00");
        Transaction buy = createBuyTransaction(100, LocalDate.of(2024, 1, 20));

        BigDecimal income = service.calculateIncome(BigDecimal.valueOf(100), List.of(buy), List.of(div));

        assertEquals(new BigDecimal("100.00"), income,
                "Buy after ex-date must not inflate that dividend");
    }

    @Test
    void calculateIncome_withTransactions_buyBeforeExDate_usesPostBuyQuantity() {
        // 100 shares at start; buy 50 more on Jan 10; dividend ex Jan 15
        Dividend div = createCashDividend("AAPL", LocalDate.of(2024, 1, 15), "1.00");
        Transaction buy = createBuyTransaction(50, LocalDate.of(2024, 1, 10));

        BigDecimal income = service.calculateIncome(BigDecimal.valueOf(100), List.of(buy), List.of(div));

        assertEquals(new BigDecimal("150.00"), income,
                "Buy before ex-date must be included in that dividend");
    }

    @Test
    void calculateIncome_withTransactions_sellBeforeExDate_usesReducedQuantity() {
        // 200 shares at start; sell 50 on Jan 10; dividend ex Jan 15
        Dividend div = createCashDividend("AAPL", LocalDate.of(2024, 1, 15), "1.00");
        Transaction sell = createSellTransaction(50, LocalDate.of(2024, 1, 10));

        BigDecimal income = service.calculateIncome(BigDecimal.valueOf(200), List.of(sell), List.of(div));

        assertEquals(new BigDecimal("150.00"), income,
                "Sell before ex-date must reduce that dividend");
    }

    @Test
    void calculateIncome_withTransactions_transactionOnExDate_doesNotAffectThatDividend() {
        // Ex-date semantics: must hold shares at close of day BEFORE ex-date.
        // A buy ON the ex-date does not entitle the buyer to that dividend.
        Dividend div = createCashDividend("AAPL", LocalDate.of(2024, 1, 15), "1.00");
        Transaction buy = createBuyTransaction(100, LocalDate.of(2024, 1, 15));

        BigDecimal income = service.calculateIncome(BigDecimal.valueOf(100), List.of(buy), List.of(div));

        assertEquals(new BigDecimal("100.00"), income,
                "Buy ON ex-date must not count for that dividend");
    }

    @Test
    void calculateIncome_withTransactions_twoDividendsBuyBetween_correctPerDividend() {
        // 100 shares; div1 Jan 15; buy 100 more Feb 1; div2 Feb 15
        // div1 income: 100 × $1 = $100; div2 income: 200 × $1 = $200; total $300
        Dividend div1 = createCashDividend("AAPL", LocalDate.of(2024, 1, 15), "1.00");
        Dividend div2 = createCashDividend("AAPL", LocalDate.of(2024, 2, 15), "1.00");
        Transaction buy = createBuyTransaction(100, LocalDate.of(2024, 2, 1));

        BigDecimal income = service.calculateIncome(
                BigDecimal.valueOf(100), List.of(buy), List.of(div1, div2));

        assertEquals(new BigDecimal("300.00"), income,
                "Each dividend must use the quantity held at its own ex-date");
    }

    @Test
    void calculateIncome_withTransactions_sellBetweenDividends_correctPerDividend() {
        // 200 shares; div1 Jan 15; sell 100 Feb 1; div2 Feb 15
        // div1 income: 200 × $1 = $200; div2 income: 100 × $1 = $100; total $300
        Dividend div1 = createCashDividend("AAPL", LocalDate.of(2024, 1, 15), "1.00");
        Dividend div2 = createCashDividend("AAPL", LocalDate.of(2024, 2, 15), "1.00");
        Transaction sell = createSellTransaction(100, LocalDate.of(2024, 2, 1));

        BigDecimal income = service.calculateIncome(
                BigDecimal.valueOf(200), List.of(sell), List.of(div1, div2));

        assertEquals(new BigDecimal("300.00"), income,
                "Sell between dividends must only affect subsequent dividends");
    }

    @Test
    void calculateIncome_withTransactions_emptyTransactions_behavesLikeConstantQuantity() {
        Dividend div = createCashDividend("AAPL", LocalDate.of(2024, 1, 15), "0.25");

        BigDecimal income = service.calculateIncome(
                BigDecimal.valueOf(100), Collections.emptyList(), List.of(div));

        assertEquals(new BigDecimal("25.00"), income);
    }

    @Test
    void calculateIncome_withTransactions_shortPositionBuyToPartialCover_correctPerDividend() {
        // Short 200 shares; div1 Jan 15 (cost = -200); cover 100 on Feb 1; div2 Feb 15 (cost = -100)
        Dividend div1 = createCashDividend("AAPL", LocalDate.of(2024, 1, 15), "1.00");
        Dividend div2 = createCashDividend("AAPL", LocalDate.of(2024, 2, 15), "1.00");
        Transaction cover = createBuyTransaction(100, LocalDate.of(2024, 2, 1));

        BigDecimal income = service.calculateIncome(
                BigDecimal.valueOf(-200), List.of(cover), List.of(div1, div2));

        assertEquals(new BigDecimal("-300.00"), income,
                "Short positions pay dividends; partial cover reduces obligation");
    }

    // Helper methods
    private Dividend createCashDividend(String symbol, LocalDate exDate, String amount) {
        return Dividend.builder()
                .symbol(symbol)
                .exDate(exDate)
                .amount(new BigDecimal(amount))
                .type(DividendType.CASH)
                .build();
    }

    private Position createPosition(String symbol, long quantity, BigDecimal value) {
        Position position = new Position();
        position.setSymbol(symbol);
        position.setQuantity(BigDecimal.valueOf(quantity));
        position.setValue(value);
        position.setTimestamp(new Timestamp(System.currentTimeMillis()));
        position.setUser(testUser);
        position.setRealized(BigDecimal.ZERO);
        position.setUnrealized(BigDecimal.ZERO);
        return position;
    }

    private Transaction createBuyTransaction(int qty, LocalDate date) {
        TransactionType buyType = new TransactionType(1L, TransactionType.BUY);
        return new Transaction(null,
                Timestamp.valueOf(date.atTime(12, 0)),
                "AAPL",
                BigDecimal.valueOf(qty),
                BigDecimal.valueOf(qty * 10L),
                testUser,
                buyType);
    }

    private Transaction createSellTransaction(int qty, LocalDate date) {
        TransactionType sellType = new TransactionType(2L, TransactionType.SALE);
        return new Transaction(null,
                Timestamp.valueOf(date.atTime(12, 0)),
                "AAPL",
                BigDecimal.valueOf(qty),
                BigDecimal.valueOf(qty * 10L),
                testUser,
                sellType);
    }
}
