package com.companyx.equity.service;

import com.companyx.equity.model.Position;
import com.companyx.equity.model.corporateaction.Spinoff;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class SpinoffServiceTest {

    private SpinoffService spinoffService;

    @BeforeEach
    void setUp() {
        spinoffService = new SpinoffService();
    }

    @Test
    void shouldAllocateBasisUsingMarketValues() {
        Position parent = longPosition("PARENT", 100, new BigDecimal("-10000.00"));
        Spinoff spinoff = Spinoff.builder()
                .symbol("PARENT")
                .spunoffSymbol("SPIN")
                .date(LocalDate.of(2024, 3, 1))
                .distributionRatio(new BigDecimal("0.1"))
                .build();

        SpinoffService.SpinoffResult result = spinoffService.applySpinoff(
                parent, spinoff, new BigDecimal("90.00"), new BigDecimal("10.00"));

        assertEquals(0, BigDecimal.valueOf(100).compareTo(result.parentPosition().getQuantity()));
        assertEquals(0, BigDecimal.valueOf(10).compareTo(result.spinoffPosition().getQuantity()));
        assertEquals("SPIN", result.spinoffPosition().getSymbol());
        assertEquals(0, new BigDecimal("-9890.11").compareTo(result.parentPosition().getValue()));
        assertEquals(0, new BigDecimal("-109.89").compareTo(result.spinoffPosition().getValue()));
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
