package com.companyx.equity.model.corporateaction;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CorporateActionTypeTest {

    @Test
    void shouldHaveAllRequiredTypes() {
        // Act & Assert - Verify all expected types exist
        assertNotNull(CorporateActionType.CASH_DIVIDEND);
        assertNotNull(CorporateActionType.STOCK_DIVIDEND);
        assertNotNull(CorporateActionType.FORWARD_SPLIT);
        assertNotNull(CorporateActionType.REVERSE_SPLIT);
    }

    @Test
    void shouldHaveCorrectDisplayNames() {
        // Act & Assert
        assertEquals("Cash Dividend", CorporateActionType.CASH_DIVIDEND.getDisplayName());
        assertEquals("Stock Dividend", CorporateActionType.STOCK_DIVIDEND.getDisplayName());
        assertEquals("Forward Split", CorporateActionType.FORWARD_SPLIT.getDisplayName());
        assertEquals("Reverse Split", CorporateActionType.REVERSE_SPLIT.getDisplayName());
    }

    @Test
    void shouldIdentifyDividendTypes() {
        // Act & Assert
        assertTrue(CorporateActionType.CASH_DIVIDEND.isDividend());
        assertTrue(CorporateActionType.STOCK_DIVIDEND.isDividend());
        assertFalse(CorporateActionType.FORWARD_SPLIT.isDividend());
        assertFalse(CorporateActionType.REVERSE_SPLIT.isDividend());
    }

    @Test
    void shouldIdentifySplitTypes() {
        // Act & Assert
        assertFalse(CorporateActionType.CASH_DIVIDEND.isSplit());
        assertFalse(CorporateActionType.STOCK_DIVIDEND.isSplit());
        assertTrue(CorporateActionType.FORWARD_SPLIT.isSplit());
        assertTrue(CorporateActionType.REVERSE_SPLIT.isSplit());
    }

    @Test
    void shouldGetCorrectDescription() {
        // Act & Assert
        assertEquals("Cash dividend payment", CorporateActionType.CASH_DIVIDEND.getDescription());
        assertEquals("Stock dividend distribution", CorporateActionType.STOCK_DIVIDEND.getDescription());
        assertEquals("Forward stock split", CorporateActionType.FORWARD_SPLIT.getDescription());
        assertEquals("Reverse stock split", CorporateActionType.REVERSE_SPLIT.getDescription());
    }
}
