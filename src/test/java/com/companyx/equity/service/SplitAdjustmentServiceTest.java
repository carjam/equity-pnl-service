package com.companyx.equity.service;

import com.companyx.equity.model.corporateaction.StockSplit;
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

class SplitAdjustmentServiceTest {

    private SplitAdjustmentService service;
    private User testUser;

    @BeforeEach
    void setUp() {
        service = new SplitAdjustmentService();
        testUser = new User();
        testUser.setId(1L);
    }

    @Test
    void shouldReturnUnchangedPositionWhenNoSplits() {
        // Arrange
        Position position = createPosition("AAPL", 100, new BigDecimal("20000.00"));
        List<StockSplit> splits = Collections.emptyList();

        // Act
        Position result = service.applySplits(position, splits);

        // Assert
        assertEquals(0, BigDecimal.valueOf(100).compareTo(result.getQuantity()));
        assertEquals(new BigDecimal("20000.00"), result.getValue());
    }

    @Test
    void shouldApplyForwardSplit4to1() {
        // Arrange
        Position position = createPosition("AAPL", 100, new BigDecimal("20000.00"));
        StockSplit split = StockSplit.builder()
                .symbol("AAPL")
                .date(LocalDate.of(2020, 8, 31))
                .fromFactor(1)
                .toFactor(4)
                .build();

        // Act
        Position result = service.applySplits(position, List.of(split));

        // Assert
        assertEquals(0, BigDecimal.valueOf(400).compareTo(result.getQuantity()), "Quantity should be 4x after 4:1 split");
        assertEquals(new BigDecimal("20000.00"), result.getValue(), "Total basis should remain unchanged");
    }

    @Test
    void shouldApplyReverseSplit1to10() {
        // Arrange
        Position position = createPosition("XYZ", 1000, new BigDecimal("5000.00"));
        StockSplit split = StockSplit.builder()
                .symbol("XYZ")
                .date(LocalDate.of(2024, 1, 15))
                .fromFactor(10)
                .toFactor(1)
                .build();

        // Act
        Position result = service.applySplits(position, List.of(split));

        // Assert
        assertEquals(0, BigDecimal.valueOf(100).compareTo(result.getQuantity()), "Quantity should be 1/10 after 1:10 split");
        assertEquals(new BigDecimal("5000.00"), result.getValue(), "Total basis should remain unchanged");
    }

    @Test
    void shouldApplyFractionalSplit3to2() {
        // Arrange
        Position position = createPosition("TSLA", 100, new BigDecimal("3000.00"));
        StockSplit split = StockSplit.builder()
                .symbol("TSLA")
                .date(LocalDate.of(2022, 8, 25))
                .fromFactor(2)
                .toFactor(3)
                .build();

        // Act
        Position result = service.applySplits(position, List.of(split));

        // Assert
        assertEquals(0, BigDecimal.valueOf(150).compareTo(result.getQuantity()), "Quantity should be 1.5x after 3:2 split");
        assertEquals(new BigDecimal("3000.00"), result.getValue(), "Total basis should remain unchanged");
    }

    @Test
    void shouldApplyMultipleConsecutiveSplits() {
        // Arrange
        Position position = createPosition("AAPL", 100, new BigDecimal("20000.00"));
        
        StockSplit split1 = StockSplit.builder()
                .symbol("AAPL")
                .date(LocalDate.of(2014, 6, 9))
                .fromFactor(1)
                .toFactor(7)
                .build();
        
        StockSplit split2 = StockSplit.builder()
                .symbol("AAPL")
                .date(LocalDate.of(2020, 8, 31))
                .fromFactor(1)
                .toFactor(4)
                .build();

        // Act
        Position result = service.applySplits(position, Arrays.asList(split1, split2));

        // Assert
        // 100 * 7 = 700, then 700 * 4 = 2800
        assertEquals(0, BigDecimal.valueOf(2800).compareTo(result.getQuantity()), "Quantity should be 28x after two splits");
        assertEquals(new BigDecimal("20000.00"), result.getValue(), "Total basis should remain unchanged");
    }

    @Test
    void shouldHandleZeroQuantityPosition() {
        // Arrange
        Position position = createPosition("AAPL", 0, BigDecimal.ZERO);
        StockSplit split = StockSplit.builder()
                .symbol("AAPL")
                .date(LocalDate.of(2020, 8, 31))
                .fromFactor(1)
                .toFactor(4)
                .build();

        // Act
        Position result = service.applySplits(position, List.of(split));

        // Assert
        assertEquals(0, BigDecimal.ZERO.compareTo(result.getQuantity()));
        assertEquals(BigDecimal.ZERO, result.getValue());
    }

    @Test
    void shouldPreserveSymbol() {
        // Arrange
        Position position = createPosition("AAPL", 100, new BigDecimal("20000.00"));
        StockSplit split = StockSplit.builder()
                .symbol("AAPL")
                .date(LocalDate.of(2020, 8, 31))
                .fromFactor(1)
                .toFactor(4)
                .build();

        // Act
        Position result = service.applySplits(position, List.of(split));

        // Assert
        assertEquals("AAPL", result.getSymbol());
    }

    @Test
    void shouldPreserveOtherPositionFields() {
        // Arrange
        Position position = createPosition("AAPL", 100, new BigDecimal("20000.00"));
        position.setRealized(new BigDecimal("500.00"));
        position.setUnrealized(new BigDecimal("1000.00"));
        position.setPrice(new BigDecimal("210.00"));
        
        StockSplit split = StockSplit.builder()
                .symbol("AAPL")
                .date(LocalDate.of(2020, 8, 31))
                .fromFactor(1)
                .toFactor(4)
                .build();

        // Act
        Position result = service.applySplits(position, List.of(split));

        // Assert
        assertEquals(new BigDecimal("500.00"), result.getRealized(), "Realized should be preserved");
        assertEquals(new BigDecimal("1000.00"), result.getUnrealized(), "Unrealized should be preserved");
        assertEquals(new BigDecimal("210.00"), result.getPrice(), "Price should be preserved");
    }

    @Test
    void shouldHandleNegativeQuantity() {
        // Arrange - short position
        Position position = createPosition("AAPL", -100, new BigDecimal("20000.00"));
        StockSplit split = StockSplit.builder()
                .symbol("AAPL")
                .date(LocalDate.of(2020, 8, 31))
                .fromFactor(1)
                .toFactor(4)
                .build();

        // Act
        Position result = service.applySplits(position, List.of(split));

        // Assert
        assertEquals(0, BigDecimal.valueOf(-400).compareTo(result.getQuantity()), "Short position should also be adjusted");
        assertEquals(new BigDecimal("20000.00"), result.getValue());
    }

    @Test
    void shouldHandleNullSplitsList() {
        // Arrange
        Position position = createPosition("AAPL", 100, new BigDecimal("20000.00"));

        // Act
        Position result = service.applySplits(position, null);

        // Assert
        assertEquals(position.getQuantity(), result.getQuantity());
        assertEquals(position.getValue(), result.getValue());
    }

    @Test
    void shouldThrowExceptionWhenPositionIsNull() {
        // Arrange
        StockSplit split = StockSplit.builder()
                .symbol("AAPL")
                .date(LocalDate.of(2020, 8, 31))
                .fromFactor(1)
                .toFactor(4)
                .build();

        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> {
            service.applySplits(null, List.of(split));
        });
    }

    @Test
    void shouldSortSplitsByDateBeforeApplying() {
        // Arrange
        Position position = createPosition("AAPL", 100, new BigDecimal("20000.00"));
        
        // Splits provided in wrong order
        StockSplit split2 = StockSplit.builder()
                .symbol("AAPL")
                .date(LocalDate.of(2020, 8, 31))
                .fromFactor(1)
                .toFactor(4)
                .build();
        
        StockSplit split1 = StockSplit.builder()
                .symbol("AAPL")
                .date(LocalDate.of(2014, 6, 9))
                .fromFactor(1)
                .toFactor(7)
                .build();

        // Act - splits provided in reverse chronological order
        Position result = service.applySplits(position, Arrays.asList(split2, split1));

        // Assert - should apply in chronological order (7x then 4x = 28x)
        assertEquals(0, BigDecimal.valueOf(2800).compareTo(result.getQuantity()));
    }

    @Test
    void shouldCalculateCostBasisPerShareCorrectly() {
        // Arrange
        Position position = createPosition("AAPL", 100, new BigDecimal("20000.00"));
        StockSplit split = StockSplit.builder()
                .symbol("AAPL")
                .date(LocalDate.of(2020, 8, 31))
                .fromFactor(1)
                .toFactor(4)
                .build();

        // Act
        Position result = service.applySplits(position, List.of(split));
        BigDecimal costBasisPerShare = result.getValue()
                .divide(result.getQuantity(), 2, java.math.RoundingMode.HALF_UP)
                .abs();

        // Assert
        assertEquals(0, BigDecimal.valueOf(400).compareTo(result.getQuantity()));
        assertEquals(new BigDecimal("50.00"), costBasisPerShare, "Cost basis per share should be $50 after 4:1 split from $200");
    }

    @Test
    void shouldHandleVeryLargeSplitRatio() {
        // Arrange
        Position position = createPosition("MEME", 1, new BigDecimal("100.00"));
        StockSplit split = StockSplit.builder()
                .symbol("MEME")
                .date(LocalDate.of(2024, 1, 1))
                .fromFactor(1)
                .toFactor(1000)
                .build();

        // Act
        Position result = service.applySplits(position, List.of(split));

        // Assert
        assertEquals(0, BigDecimal.valueOf(1000).compareTo(result.getQuantity()));
        assertEquals(new BigDecimal("100.00"), result.getValue());
    }

    @Test
    void shouldHandleVerySmallSplitRatio() {
        // Arrange
        Position position = createPosition("PENNY", 10000, new BigDecimal("100.00"));
        StockSplit split = StockSplit.builder()
                .symbol("PENNY")
                .date(LocalDate.of(2024, 1, 1))
                .fromFactor(1000)
                .toFactor(1)
                .build();

        // Act
        Position result = service.applySplits(position, List.of(split));

        // Assert
        assertEquals(0, BigDecimal.valueOf(10).compareTo(result.getQuantity()));
        assertEquals(new BigDecimal("100.00"), result.getValue());
    }

    @Test
    void shouldPreserveFractionalSharesFrom3to2Split() {
        // 3:2 split on 101 shares: exact result is 151.5 — no rounding to whole shares
        Position position = createPosition("AAPL", 101, new BigDecimal("-10100.00"));
        StockSplit split = StockSplit.builder()
                .symbol("AAPL")
                .date(LocalDate.of(2024, 1, 1))
                .fromFactor(2)
                .toFactor(3)
                .build();

        Position result = service.applySplits(position, List.of(split));

        assertEquals(0, new BigDecimal("151.5").compareTo(result.getQuantity()),
                "3:2 split on 101 shares should yield 151.5 fractional shares, not rounded to 152");
        assertEquals(new BigDecimal("-10100.00"), result.getValue(), "Basis must be unchanged after split");
    }

    // Helper method to create test positions
    private Position createPosition(String symbol, long quantity, BigDecimal value) {
        Position position = new Position();
        position.setSymbol(symbol);
        position.setQuantity(new BigDecimal(quantity));
        position.setValue(value);
        position.setTimestamp(new Timestamp(System.currentTimeMillis()));
        position.setUser(testUser);
        position.setRealized(BigDecimal.ZERO);
        position.setUnrealized(BigDecimal.ZERO);
        return position;
    }
}
