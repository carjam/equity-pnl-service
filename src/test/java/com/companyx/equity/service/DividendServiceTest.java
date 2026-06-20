package com.companyx.equity.service;

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
        BigInteger shares = BigInteger.valueOf(100);
        List<Dividend> dividends = Collections.emptyList();

        // Act
        BigDecimal income = service.calculateIncome(shares, dividends);

        // Assert
        assertEquals(BigDecimal.ZERO, income);
    }

    @Test
    void shouldCalculateSingleCashDividendIncome() {
        // Arrange
        BigInteger shares = BigInteger.valueOf(100);
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
        BigInteger shares = BigInteger.valueOf(100);
        
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
        BigInteger shares = BigInteger.valueOf(100);
        
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
        BigInteger shares = BigInteger.ZERO;
        Dividend dividend = createCashDividend("AAPL", LocalDate.of(2024, 8, 9), "0.25");

        // Act
        BigDecimal income = service.calculateIncome(shares, List.of(dividend));

        // Assert
        assertEquals(0, income.compareTo(BigDecimal.ZERO));
    }

    @Test
    void shouldHandleNullDividendsList() {
        // Arrange
        BigInteger shares = BigInteger.valueOf(100);

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
        assertEquals(BigInteger.valueOf(105), result.getQuantity(), "100 shares + 5% = 105 shares");
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
        // 100 * 1.05 = 105, then 105 * 1.10 = 115.5 ≈ 116 (rounded)
        assertEquals(BigInteger.valueOf(116), result.getQuantity());
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
        assertEquals(BigInteger.valueOf(105), result.getQuantity(), "Only stock dividend should affect quantity");
    }

    @Test
    void shouldReturnUnchangedPositionWhenNoStockDividends() {
        // Arrange
        Position position = createPosition("AAPL", 100, new BigDecimal("20000.00"));
        List<Dividend> dividends = Collections.emptyList();

        // Act
        Position result = service.applyStockDividends(position, dividends);

        // Assert
        assertEquals(BigInteger.valueOf(100), result.getQuantity());
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
        BigInteger shares = BigInteger.valueOf(10000);
        Dividend dividend = createCashDividend("AAPL", LocalDate.of(2024, 8, 9), "0.25");

        // Act
        BigDecimal income = service.calculateIncome(shares, List.of(dividend));

        // Assert
        assertEquals(new BigDecimal("2500.00"), income);
    }

    @Test
    void shouldHandleNegativeSharesInIncomeCalculation() {
        // Arrange - short position
        BigInteger shares = BigInteger.valueOf(-100);
        Dividend dividend = createCashDividend("AAPL", LocalDate.of(2024, 8, 9), "0.25");

        // Act
        BigDecimal income = service.calculateIncome(shares, List.of(dividend));

        // Assert
        assertEquals(new BigDecimal("-25.00"), income, "Short positions have negative dividend income");
    }

    @Test
    void shouldSortDividendsByDateBeforeCalculation() {
        // Arrange
        BigInteger shares = BigInteger.valueOf(100);
        
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
        BigInteger shares = BigInteger.valueOf(100);
        Dividend dividend = createCashDividend("MSFT", LocalDate.of(2024, 8, 9), "0.83");

        // Act
        BigDecimal income = service.calculateIncome(shares, List.of(dividend));

        // Assert
        assertEquals(new BigDecimal("83.00"), income);
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
        position.setQuantity(BigInteger.valueOf(quantity));
        position.setValue(value);
        position.setTimestamp(new Timestamp(System.currentTimeMillis()));
        position.setUser(testUser);
        position.setRealized(BigDecimal.ZERO);
        position.setUnrealized(BigDecimal.ZERO);
        return position;
    }
}
