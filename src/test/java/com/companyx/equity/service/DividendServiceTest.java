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

    // ─── Qualified dividend income breakdown ─────────────────────────────────

    @Test
    void shouldReturnOnlyQualifiedIncome() {
        BigDecimal shares = BigDecimal.valueOf(100);
        Dividend qualified = Dividend.builder()
                .symbol("AAPL")
                .exDate(LocalDate.of(2024, 8, 9))
                .amount(new BigDecimal("0.25"))
                .type(DividendType.CASH)
                .qualified(true)
                .build();
        Dividend ordinary = Dividend.builder()
                .symbol("AAPL")
                .exDate(LocalDate.of(2024, 11, 9))
                .amount(new BigDecimal("1.00"))
                .type(DividendType.CASH)
                .qualified(false)
                .build();

        BigDecimal income = service.calculateQualifiedIncome(shares, Arrays.asList(qualified, ordinary));

        assertEquals(0, new BigDecimal("25.00").compareTo(income),
                "Only the qualified dividend (100 × $0.25) should be returned");
    }

    @Test
    void shouldReturnOnlyOrdinaryIncome() {
        BigDecimal shares = BigDecimal.valueOf(100);
        Dividend qualified = Dividend.builder()
                .symbol("AAPL")
                .exDate(LocalDate.of(2024, 8, 9))
                .amount(new BigDecimal("0.25"))
                .type(DividendType.CASH)
                .qualified(true)
                .build();
        Dividend ordinary = Dividend.builder()
                .symbol("AAPL")
                .exDate(LocalDate.of(2024, 11, 9))
                .amount(new BigDecimal("1.00"))
                .type(DividendType.CASH)
                .qualified(false)
                .build();

        BigDecimal income = service.calculateOrdinaryIncome(shares, Arrays.asList(qualified, ordinary));

        assertEquals(0, new BigDecimal("100.00").compareTo(income),
                "Only the ordinary dividend (100 × $1.00) should be returned");
    }

    @Test
    void shouldTreatUnknownQualifiedStatusAsOrdinary() {
        // Dividends with null qualified flag (undetermined) are conservative-defaulted to ordinary
        BigDecimal shares = BigDecimal.valueOf(100);
        Dividend unknown = Dividend.builder()
                .symbol("XYZ")
                .exDate(LocalDate.of(2024, 6, 1))
                .amount(new BigDecimal("0.50"))
                .type(DividendType.CASH)
                .build(); // no .qualified(...)

        BigDecimal ordinary = service.calculateOrdinaryIncome(shares, List.of(unknown));
        BigDecimal qualified = service.calculateQualifiedIncome(shares, List.of(unknown));

        assertEquals(0, new BigDecimal("50.00").compareTo(ordinary),
                "Unknown qualified status defaults to ordinary income");
        assertEquals(0, BigDecimal.ZERO.compareTo(qualified),
                "Unknown qualified status should not appear in qualified income total");
    }

    @Test
    void shouldSumQualifiedAndOrdinaryToTotal() {
        BigDecimal shares = BigDecimal.valueOf(200);
        Dividend q = Dividend.builder().symbol("MSFT").exDate(LocalDate.of(2024, 5, 15))
                .amount(new BigDecimal("0.75")).type(DividendType.CASH).qualified(true).build();
        Dividend o = Dividend.builder().symbol("MSFT").exDate(LocalDate.of(2024, 8, 15))
                .amount(new BigDecimal("0.50")).type(DividendType.CASH).qualified(false).build();

        BigDecimal total = service.calculateIncome(shares, Arrays.asList(q, o));
        BigDecimal qualifiedIncome = service.calculateQualifiedIncome(shares, Arrays.asList(q, o));
        BigDecimal ordinaryIncome = service.calculateOrdinaryIncome(shares, Arrays.asList(q, o));

        assertEquals(0, total.compareTo(qualifiedIncome.add(ordinaryIncome)),
                "Qualified + Ordinary must equal Total income");
    }

    // ─── Return of Capital (ROC) tests ───────────────────────────────────────

    @Test
    void shouldReduceBasisByRocAmount() {
        // 100 shares with $1,000 cost basis; $1/share ROC → basis becomes $900
        Position position = createPosition("ENB", 100, new BigDecimal("-1000.00"));
        Dividend roc = Dividend.builder()
                .symbol("ENB")
                .exDate(LocalDate.of(2024, 3, 15))
                .amount(new BigDecimal("1.00"))
                .type(DividendType.RETURN_OF_CAPITAL)
                .build();

        Position result = service.applyReturnOfCapital(position, List.of(roc));

        assertEquals(0, new BigDecimal("-900.00").compareTo(result.getValue()),
                "100 shares × $1.00 ROC should reduce $1,000 basis to $900");
        assertEquals(0, BigDecimal.ZERO.compareTo(result.getRealized()),
                "No realized gain when basis covers the ROC");
        assertEquals(0, BigDecimal.valueOf(100).compareTo(result.getQuantity()),
                "Quantity unchanged by ROC");
    }

    @Test
    void shouldIgnoreRocInCashIncomeCalculation() {
        // ROC is not income — cash income must exclude it
        BigDecimal shares = BigDecimal.valueOf(100);
        Dividend cash = createCashDividend("ENB", LocalDate.of(2024, 8, 9), "0.86");
        Dividend roc = Dividend.builder()
                .symbol("ENB")
                .exDate(LocalDate.of(2024, 6, 15))
                .amount(new BigDecimal("1.00"))
                .type(DividendType.RETURN_OF_CAPITAL)
                .build();

        BigDecimal income = service.calculateIncome(shares, Arrays.asList(cash, roc));

        assertEquals(0, new BigDecimal("86.00").compareTo(income),
                "Only the cash dividend counts as income; ROC is a basis reduction");
    }

    @Test
    void shouldCapRocAtZeroBasisAndRecognizeExcess() {
        // Basis = $500; ROC of $8/share on 100 shares = $800 — excess $300 is a capital gain
        Position position = createPosition("XYZ", 100, new BigDecimal("-500.00"));
        Dividend roc = Dividend.builder()
                .symbol("XYZ")
                .exDate(LocalDate.of(2024, 5, 1))
                .amount(new BigDecimal("8.00"))
                .type(DividendType.RETURN_OF_CAPITAL)
                .build();

        Position result = service.applyReturnOfCapital(position, List.of(roc));

        assertEquals(0, BigDecimal.ZERO.compareTo(result.getValue()),
                "Basis cannot go below zero");
        assertEquals(0, new BigDecimal("300.00").compareTo(result.getRealized()),
                "Excess ROC ($800 − $500 basis) becomes $300 realized gain");
    }

    @Test
    void shouldAccumulateMultipleRocDistributions() {
        // Two quarterly ROC payments; basis steps down after each
        Position position = createPosition("BPY", 200, new BigDecimal("-2000.00"));
        Dividend roc1 = Dividend.builder()
                .symbol("BPY")
                .exDate(LocalDate.of(2024, 3, 1))
                .amount(new BigDecimal("2.00"))
                .type(DividendType.RETURN_OF_CAPITAL)
                .build();
        Dividend roc2 = Dividend.builder()
                .symbol("BPY")
                .exDate(LocalDate.of(2024, 6, 1))
                .amount(new BigDecimal("1.50"))
                .type(DividendType.RETURN_OF_CAPITAL)
                .build();

        Position result = service.applyReturnOfCapital(position, Arrays.asList(roc1, roc2));

        // 200 × $2.00 = $400 → basis $1,600; 200 × $1.50 = $300 → basis $1,300
        assertEquals(0, new BigDecimal("-1300.00").compareTo(result.getValue()),
                "Two ROC payments should reduce $2,000 basis by $700 to $1,300");
        assertEquals(0, BigDecimal.ZERO.compareTo(result.getRealized()),
                "No realized gain; basis still positive");
    }

    @Test
    void shouldReturnUnchangedPositionWhenNoRocDividends() {
        // Passing only cash dividends to applyReturnOfCapital should be a no-op
        Position position = createPosition("AAPL", 100, new BigDecimal("-15000.00"));
        Dividend cash = createCashDividend("AAPL", LocalDate.of(2024, 8, 9), "0.25");

        Position result = service.applyReturnOfCapital(position, List.of(cash));

        assertEquals(0, new BigDecimal("-15000.00").compareTo(result.getValue()),
                "Cash dividends must not alter basis in applyReturnOfCapital");
    }
}
