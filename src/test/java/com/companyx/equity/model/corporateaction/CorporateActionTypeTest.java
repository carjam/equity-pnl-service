package com.companyx.equity.model.corporateaction;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CorporateActionTypeTest {

    @Test
    void shouldHaveAllRequiredTypes() {
        assertNotNull(CorporateActionType.CASH_DIVIDEND);
        assertNotNull(CorporateActionType.STOCK_DIVIDEND);
        assertNotNull(CorporateActionType.FORWARD_SPLIT);
        assertNotNull(CorporateActionType.REVERSE_SPLIT);
        assertNotNull(CorporateActionType.MERGER);
        assertNotNull(CorporateActionType.SPINOFF);
        assertNotNull(CorporateActionType.SYMBOL_CHANGE);
        assertNotNull(CorporateActionType.DELISTING);
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
    void shouldIdentifyComplexTypes() {
        assertTrue(CorporateActionType.MERGER.isComplex());
        assertTrue(CorporateActionType.SPINOFF.isComplex());
        assertFalse(CorporateActionType.CASH_DIVIDEND.isComplex());
    }
}
