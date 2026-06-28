package com.companyx.equity.service;

import com.companyx.equity.model.Position;
import com.companyx.equity.model.corporateaction.SymbolChange;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class SymbolMappingServiceTest {

    private final SymbolMappingService symbolMappingService = new SymbolMappingService();

    @Test
    void shouldUpdateSymbolWithoutChangingEconomics() {
        Position position = new Position();
        position.setSymbol("FB");
        position.setQuantity(BigDecimal.valueOf(50));
        position.setValue(new BigDecimal("-5000.00"));
        position.setRealized(new BigDecimal("100.00"));

        SymbolChange change = SymbolChange.builder()
                .oldSymbol("FB")
                .newSymbol("META")
                .date(LocalDate.of(2022, 6, 9))
                .build();

        Position adjusted = symbolMappingService.applySymbolChange(position, change);

        assertEquals("META", adjusted.getSymbol());
        assertEquals(BigDecimal.valueOf(50), adjusted.getQuantity());
        assertEquals(0, new BigDecimal("-5000.00").compareTo(adjusted.getValue()));
        assertEquals(0, new BigDecimal("100.00").compareTo(adjusted.getRealized()));
    }
}
