package com.companyx.equity.model.corporateaction;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DividendTypeTest {

    @Test
    void shouldHaveCashAndStockTypes() {
        // Act & Assert - Verify both types exist
        assertNotNull(DividendType.CASH);
        assertNotNull(DividendType.STOCK);
    }

    @Test
    void shouldHaveCorrectDisplayNames() {
        // Act & Assert
        assertEquals("Cash", DividendType.CASH.getDisplayName());
        assertEquals("Stock", DividendType.STOCK.getDisplayName());
    }

    @Test
    void shouldIdentifyCashType() {
        // Act & Assert
        assertTrue(DividendType.CASH.isCash());
        assertFalse(DividendType.STOCK.isCash());
    }

    @Test
    void shouldIdentifyStockType() {
        // Act & Assert
        assertFalse(DividendType.CASH.isStock());
        assertTrue(DividendType.STOCK.isStock());
    }
}
