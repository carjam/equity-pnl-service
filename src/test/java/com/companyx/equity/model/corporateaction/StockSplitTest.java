package com.companyx.equity.model.corporateaction;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class StockSplitTest {

    @Test
    void shouldCreateForwardSplit4to1() {
        // Arrange & Act
        StockSplit split = StockSplit.builder()
                .symbol("AAPL")
                .date(LocalDate.of(2020, 8, 31))
                .fromFactor(1)
                .toFactor(4)
                .build();

        // Assert
        assertEquals("AAPL", split.getSymbol());
        assertEquals(LocalDate.of(2020, 8, 31), split.getDate());
        assertEquals(1, split.getFromFactor());
        assertEquals(4, split.getToFactor());
        assertEquals(0, split.getSplitRatio().compareTo(new BigDecimal("4.0")));
        assertEquals(CorporateActionType.FORWARD_SPLIT, split.getActionType());
    }

    @Test
    void shouldCreateReverseSplit1to10() {
        // Arrange & Act
        StockSplit split = StockSplit.builder()
                .symbol("XYZ")
                .date(LocalDate.of(2024, 1, 15))
                .fromFactor(10)
                .toFactor(1)
                .build();

        // Assert
        assertEquals(10, split.getFromFactor());
        assertEquals(1, split.getToFactor());
        assertEquals(0, split.getSplitRatio().compareTo(new BigDecimal("0.1")));
        assertEquals(CorporateActionType.REVERSE_SPLIT, split.getActionType());
    }

    @Test
    void shouldCreateFractionalSplit3to2() {
        // Arrange & Act
        StockSplit split = StockSplit.builder()
                .symbol("TSLA")
                .date(LocalDate.of(2022, 8, 25))
                .fromFactor(2)
                .toFactor(3)
                .build();

        // Assert
        assertEquals(0, split.getSplitRatio().compareTo(new BigDecimal("1.5")));
        assertEquals(CorporateActionType.FORWARD_SPLIT, split.getActionType());
    }

    @Test
    void shouldApplySplitToQuantity() {
        // Arrange
        StockSplit split = StockSplit.builder()
                .symbol("AAPL")
                .date(LocalDate.of(2020, 8, 31))
                .fromFactor(1)
                .toFactor(4)
                .build();

        BigInteger originalQuantity = BigInteger.valueOf(100);

        // Act
        BigInteger newQuantity = split.applyToQuantity(originalQuantity);

        // Assert
        assertEquals(BigInteger.valueOf(400), newQuantity);
    }

    @Test
    void shouldApplyReverseSplitToQuantity() {
        // Arrange
        StockSplit split = StockSplit.builder()
                .symbol("XYZ")
                .date(LocalDate.of(2024, 1, 15))
                .fromFactor(10)
                .toFactor(1)
                .build();

        BigInteger originalQuantity = BigInteger.valueOf(1000);

        // Act
        BigInteger newQuantity = split.applyToQuantity(originalQuantity);

        // Assert
        assertEquals(BigInteger.valueOf(100), newQuantity);
    }

    @Test
    void shouldApplySplitToCostBasis() {
        // Arrange
        StockSplit split = StockSplit.builder()
                .symbol("AAPL")
                .date(LocalDate.of(2020, 8, 31))
                .fromFactor(1)
                .toFactor(4)
                .build();

        BigDecimal originalBasisPerShare = new BigDecimal("200.00");

        // Act
        BigDecimal newBasisPerShare = split.applyToCostBasis(originalBasisPerShare);

        // Assert
        assertEquals(new BigDecimal("50.00"), newBasisPerShare);
    }

    @Test
    void shouldApplyReverseSplitToCostBasis() {
        // Arrange
        StockSplit split = StockSplit.builder()
                .symbol("XYZ")
                .date(LocalDate.of(2024, 1, 15))
                .fromFactor(10)
                .toFactor(1)
                .build();

        BigDecimal originalBasisPerShare = new BigDecimal("5.00");

        // Act
        BigDecimal newBasisPerShare = split.applyToCostBasis(originalBasisPerShare);

        // Assert
        assertEquals(new BigDecimal("50.00"), newBasisPerShare);
    }

    @Test
    void shouldVerifyTotalBasisUnchanged() {
        // Arrange
        StockSplit split = StockSplit.builder()
                .symbol("AAPL")
                .date(LocalDate.of(2020, 8, 31))
                .fromFactor(1)
                .toFactor(4)
                .build();

        BigInteger originalQuantity = BigInteger.valueOf(100);
        BigDecimal originalBasisPerShare = new BigDecimal("200.00");
        BigDecimal totalBasis = new BigDecimal(originalQuantity).multiply(originalBasisPerShare);

        // Act
        BigInteger newQuantity = split.applyToQuantity(originalQuantity);
        BigDecimal newBasisPerShare = split.applyToCostBasis(originalBasisPerShare);
        BigDecimal newTotalBasis = new BigDecimal(newQuantity).multiply(newBasisPerShare);

        // Assert
        assertEquals(totalBasis, newTotalBasis);
        assertEquals(new BigDecimal("20000.00"), totalBasis);
        assertEquals(new BigDecimal("20000.00"), newTotalBasis);
    }

    @Test
    void shouldThrowExceptionWhenSymbolIsNull() {
        // Arrange, Act & Assert
        assertThrows(IllegalArgumentException.class, () -> {
            StockSplit.builder()
                    .symbol(null)
                    .date(LocalDate.now())
                    .fromFactor(1)
                    .toFactor(4)
                    .build();
        });
    }

    @Test
    void shouldThrowExceptionWhenDateIsNull() {
        // Arrange, Act & Assert
        assertThrows(IllegalArgumentException.class, () -> {
            StockSplit.builder()
                    .symbol("AAPL")
                    .date(null)
                    .fromFactor(1)
                    .toFactor(4)
                    .build();
        });
    }

    @Test
    void shouldThrowExceptionWhenFromFactorIsZero() {
        // Arrange, Act & Assert
        assertThrows(IllegalArgumentException.class, () -> {
            StockSplit.builder()
                    .symbol("AAPL")
                    .date(LocalDate.now())
                    .fromFactor(0)
                    .toFactor(4)
                    .build();
        });
    }

    @Test
    void shouldThrowExceptionWhenToFactorIsZero() {
        // Arrange, Act & Assert
        assertThrows(IllegalArgumentException.class, () -> {
            StockSplit.builder()
                    .symbol("AAPL")
                    .date(LocalDate.now())
                    .fromFactor(1)
                    .toFactor(0)
                    .build();
        });
    }

    @Test
    void shouldThrowExceptionWhenFromFactorIsNegative() {
        // Arrange, Act & Assert
        assertThrows(IllegalArgumentException.class, () -> {
            StockSplit.builder()
                    .symbol("AAPL")
                    .date(LocalDate.now())
                    .fromFactor(-1)
                    .toFactor(4)
                    .build();
        });
    }

    @Test
    void shouldCompareSplitsByDate() {
        // Arrange
        StockSplit earlier = StockSplit.builder()
                .symbol("AAPL")
                .date(LocalDate.of(2020, 8, 31))
                .fromFactor(1)
                .toFactor(4)
                .build();

        StockSplit later = StockSplit.builder()
                .symbol("AAPL")
                .date(LocalDate.of(2024, 1, 1))
                .fromFactor(1)
                .toFactor(2)
                .build();

        // Act & Assert
        assertTrue(earlier.compareTo(later) < 0);
        assertTrue(later.compareTo(earlier) > 0);
        assertEquals(0, earlier.compareTo(earlier));
    }

    @Test
    void shouldImplementEqualsAndHashCodeCorrectly() {
        // Arrange
        LocalDate date = LocalDate.of(2020, 8, 31);
        
        StockSplit split1 = StockSplit.builder()
                .symbol("AAPL")
                .date(date)
                .fromFactor(1)
                .toFactor(4)
                .build();

        StockSplit split2 = StockSplit.builder()
                .symbol("AAPL")
                .date(date)
                .fromFactor(1)
                .toFactor(4)
                .build();

        StockSplit split3 = StockSplit.builder()
                .symbol("MSFT")
                .date(date)
                .fromFactor(1)
                .toFactor(4)
                .build();

        // Act & Assert
        assertEquals(split1, split2);
        assertEquals(split1.hashCode(), split2.hashCode());
        assertNotEquals(split1, split3);
    }

    @Test
    void shouldGenerateReadableToString() {
        // Arrange
        StockSplit split = StockSplit.builder()
                .symbol("AAPL")
                .date(LocalDate.of(2020, 8, 31))
                .fromFactor(1)
                .toFactor(4)
                .build();

        // Act
        String toString = split.toString();

        // Assert
        assertTrue(toString.contains("AAPL"));
        assertTrue(toString.contains("4-for-1") || toString.contains("4:1"));
        assertTrue(toString.contains("2020-08-31"));
    }

    @Test
    void shouldHandleFractionalSplitCalculations() {
        // Arrange
        StockSplit split = StockSplit.builder()
                .symbol("TSLA")
                .date(LocalDate.of(2022, 8, 25))
                .fromFactor(2)
                .toFactor(3)
                .build();

        BigInteger originalQuantity = BigInteger.valueOf(100);
        BigDecimal originalBasisPerShare = new BigDecimal("30.00");

        // Act
        BigInteger newQuantity = split.applyToQuantity(originalQuantity);
        BigDecimal newBasisPerShare = split.applyToCostBasis(originalBasisPerShare);

        // Assert
        assertEquals(BigInteger.valueOf(150), newQuantity);
        assertEquals(new BigDecimal("20.00"), newBasisPerShare);
        
        // Verify total basis unchanged
        BigDecimal oldTotal = new BigDecimal(originalQuantity).multiply(originalBasisPerShare);
        BigDecimal newTotal = new BigDecimal(newQuantity).multiply(newBasisPerShare);
        assertEquals(oldTotal, newTotal);
    }
}
