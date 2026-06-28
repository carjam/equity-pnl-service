package com.companyx.equity.service;

import com.companyx.equity.model.Position;
import com.companyx.equity.model.corporateaction.Merger;
import com.companyx.equity.model.corporateaction.MergerType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class MergerServiceTest {

    private MergerService mergerService;

    @BeforeEach
    void setUp() {
        mergerService = new MergerService();
    }

    @Test
    void shouldTransferStockForStockMerger() {
        Position position = longPosition("XYZ", 100, new BigDecimal("-5000.00"));

        Merger merger = Merger.builder()
                .symbol("XYZ")
                .acquirerSymbol("ABC")
                .date(LocalDate.of(2024, 6, 1))
                .type(MergerType.STOCK_FOR_STOCK)
                .exchangeRatio(new BigDecimal("0.8"))
                .build();

        MergerService.MergerResult result = mergerService.applyMerger(position, merger);

        assertEquals("ABC", result.position().getSymbol());
        assertEquals(0, BigDecimal.valueOf(80).compareTo(result.position().getQuantity()));
        assertEquals(0, new BigDecimal("-5000.00").compareTo(result.position().getValue()));
        assertEquals(0, BigDecimal.ZERO.compareTo(result.additionalRealized()));
    }

    @Test
    void shouldRealizeGainOnCashAcquisition() {
        Position position = longPosition("XYZ", 100, new BigDecimal("-4000.00"));

        Merger merger = Merger.builder()
                .symbol("XYZ")
                .date(LocalDate.of(2024, 6, 1))
                .type(MergerType.CASH_FOR_STOCK)
                .cashPerShare(new BigDecimal("50.00"))
                .build();

        MergerService.MergerResult result = mergerService.applyMerger(position, merger);

        assertEquals(0, BigDecimal.ZERO.compareTo(result.position().getQuantity()));
        assertEquals(0, new BigDecimal("1000.00").compareTo(result.additionalRealized()));
    }

    @Test
    void shouldHandleMixedConsideration() {
        Position position = longPosition("XYZ", 100, new BigDecimal("-4000.00"));

        Merger merger = Merger.builder()
                .symbol("XYZ")
                .acquirerSymbol("ABC")
                .date(LocalDate.of(2024, 6, 1))
                .type(MergerType.MIXED)
                .exchangeRatio(new BigDecimal("0.5"))
                .cashPerShare(new BigDecimal("25.00"))
                .acquirerFairValuePerShare(new BigDecimal("30.00"))
                .build();

        MergerService.MergerResult result = mergerService.applyMerger(position, merger);

        assertEquals("ABC", result.position().getSymbol());
        assertEquals(0, BigDecimal.valueOf(50).compareTo(result.position().getQuantity()));
        assertEquals(0, BigDecimal.ZERO.compareTo(result.additionalRealized()));
        assertEquals(0, new BigDecimal("-1500.00").compareTo(result.position().getValue()));
    }

    private Position longPosition(String symbol, int quantity, BigDecimal basis) {
        Position position = new Position();
        position.setSymbol(symbol);
        position.setQuantity(BigDecimal.valueOf(quantity));
        position.setValue(basis);
        position.setRealized(BigDecimal.ZERO);
        position.setUnrealized(BigDecimal.ZERO);
        return position;
    }
}
