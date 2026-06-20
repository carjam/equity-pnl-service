package com.companyx.equity.model.corporateaction;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class DividendTest {

    @Test
    void shouldCreateCashDividendWithAllFields() {
        // Arrange & Act
        LocalDate exDate = LocalDate.of(2024, 8, 9);
        LocalDate payDate = LocalDate.of(2024, 8, 12);
        LocalDate recordDate = LocalDate.of(2024, 8, 10);
        BigDecimal amount = new BigDecimal("0.25");
        
        Dividend dividend = Dividend.builder()
                .symbol("AAPL")
                .exDate(exDate)
                .payDate(payDate)
                .recordDate(recordDate)
                .amount(amount)
                .currency("USD")
                .type(DividendType.CASH)
                .frequency(4)
                .build();

        // Assert
        assertEquals("AAPL", dividend.getSymbol());
        assertEquals(exDate, dividend.getExDate());
        assertEquals(payDate, dividend.getPayDate());
        assertEquals(recordDate, dividend.getRecordDate());
        assertEquals(amount, dividend.getAmount());
        assertEquals("USD", dividend.getCurrency());
        assertEquals(DividendType.CASH, dividend.getType());
        assertEquals(4, dividend.getFrequency());
        assertEquals(CorporateActionType.CASH_DIVIDEND, dividend.getActionType());
    }

    @Test
    void shouldCreateStockDividendWithSharesPerShare() {
        // Arrange & Act
        LocalDate exDate = LocalDate.of(2024, 6, 1);
        BigDecimal sharesPerShare = new BigDecimal("0.05"); // 5% stock dividend
        
        Dividend dividend = Dividend.builder()
                .symbol("KO")
                .exDate(exDate)
                .amount(BigDecimal.ZERO)
                .type(DividendType.STOCK)
                .sharesPerShare(sharesPerShare)
                .build();

        // Assert
        assertEquals(DividendType.STOCK, dividend.getType());
        assertEquals(sharesPerShare, dividend.getSharesPerShare());
        assertEquals(CorporateActionType.STOCK_DIVIDEND, dividend.getActionType());
    }

    @Test
    void shouldThrowExceptionWhenAmountIsNegative() {
        // Arrange, Act & Assert
        assertThrows(IllegalArgumentException.class, () -> {
            Dividend.builder()
                    .symbol("AAPL")
                    .exDate(LocalDate.now())
                    .amount(new BigDecimal("-1.00"))
                    .type(DividendType.CASH)
                    .build();
        });
    }

    @Test
    void shouldThrowExceptionWhenSymbolIsNull() {
        // Arrange, Act & Assert
        assertThrows(IllegalArgumentException.class, () -> {
            Dividend.builder()
                    .symbol(null)
                    .exDate(LocalDate.now())
                    .amount(new BigDecimal("0.25"))
                    .type(DividendType.CASH)
                    .build();
        });
    }

    @Test
    void shouldThrowExceptionWhenExDateIsNull() {
        // Arrange, Act & Assert
        assertThrows(IllegalArgumentException.class, () -> {
            Dividend.builder()
                    .symbol("AAPL")
                    .exDate(null)
                    .amount(new BigDecimal("0.25"))
                    .type(DividendType.CASH)
                    .build();
        });
    }

    @Test
    void shouldThrowExceptionWhenTypeIsNull() {
        // Arrange, Act & Assert
        assertThrows(IllegalArgumentException.class, () -> {
            Dividend.builder()
                    .symbol("AAPL")
                    .exDate(LocalDate.now())
                    .amount(new BigDecimal("0.25"))
                    .type(null)
                    .build();
        });
    }

    @Test
    void shouldDefaultCurrencyToUSDWhenNotProvided() {
        // Arrange & Act
        Dividend dividend = Dividend.builder()
                .symbol("AAPL")
                .exDate(LocalDate.now())
                .amount(new BigDecimal("0.25"))
                .type(DividendType.CASH)
                .build();

        // Assert
        assertEquals("USD", dividend.getCurrency());
    }

    @Test
    void shouldCalculatePaymentForShares() {
        // Arrange
        Dividend dividend = Dividend.builder()
                .symbol("AAPL")
                .exDate(LocalDate.now())
                .amount(new BigDecimal("0.25"))
                .type(DividendType.CASH)
                .build();

        // Act
        BigDecimal payment = dividend.calculatePayment(100);

        // Assert
        assertEquals(new BigDecimal("25.00"), payment);
    }

    @Test
    void shouldReturnZeroPaymentForStockDividend() {
        // Arrange
        Dividend dividend = Dividend.builder()
                .symbol("KO")
                .exDate(LocalDate.now())
                .amount(BigDecimal.ZERO)
                .type(DividendType.STOCK)
                .sharesPerShare(new BigDecimal("0.05"))
                .build();

        // Act
        BigDecimal payment = dividend.calculatePayment(100);

        // Assert
        assertEquals(BigDecimal.ZERO, payment);
    }

    @Test
    void shouldCompareDividendsByExDate() {
        // Arrange
        Dividend earlier = Dividend.builder()
                .symbol("AAPL")
                .exDate(LocalDate.of(2024, 1, 1))
                .amount(new BigDecimal("0.25"))
                .type(DividendType.CASH)
                .build();

        Dividend later = Dividend.builder()
                .symbol("AAPL")
                .exDate(LocalDate.of(2024, 6, 1))
                .amount(new BigDecimal("0.25"))
                .type(DividendType.CASH)
                .build();

        // Act & Assert
        assertTrue(earlier.compareTo(later) < 0);
        assertTrue(later.compareTo(earlier) > 0);
        assertEquals(0, earlier.compareTo(earlier));
    }

    @Test
    void shouldImplementEqualsAndHashCodeCorrectly() {
        // Arrange
        LocalDate exDate = LocalDate.of(2024, 8, 9);
        
        Dividend dividend1 = Dividend.builder()
                .symbol("AAPL")
                .exDate(exDate)
                .amount(new BigDecimal("0.25"))
                .type(DividendType.CASH)
                .build();

        Dividend dividend2 = Dividend.builder()
                .symbol("AAPL")
                .exDate(exDate)
                .amount(new BigDecimal("0.25"))
                .type(DividendType.CASH)
                .build();

        Dividend dividend3 = Dividend.builder()
                .symbol("MSFT")
                .exDate(exDate)
                .amount(new BigDecimal("0.25"))
                .type(DividendType.CASH)
                .build();

        // Act & Assert
        assertEquals(dividend1, dividend2);
        assertEquals(dividend1.hashCode(), dividend2.hashCode());
        assertNotEquals(dividend1, dividend3);
    }

    @Test
    void shouldGenerateReadableToString() {
        // Arrange
        Dividend dividend = Dividend.builder()
                .symbol("AAPL")
                .exDate(LocalDate.of(2024, 8, 9))
                .amount(new BigDecimal("0.25"))
                .type(DividendType.CASH)
                .build();

        // Act
        String toString = dividend.toString();

        // Assert
        assertNotNull(toString);
        assertTrue(toString.contains("AAPL"), "ToString should contain symbol");
        assertTrue(toString.contains("0.25") || toString.contains("0.2"), "ToString should contain amount");
        assertTrue(toString.contains("CASH") || toString.contains("Cash"), "ToString should contain type");
    }
}
