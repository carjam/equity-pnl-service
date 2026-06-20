package com.companyx.equity.model.corporateaction;

import java.time.LocalDate;

/**
 * Base interface for all corporate action events.
 * Corporate actions are events initiated by a company that affect its securities.
 */
public interface CorporateAction extends Comparable<CorporateAction> {
    
    /**
     * Get the stock symbol this corporate action applies to
     */
    String getSymbol();
    
    /**
     * Get the effective date of this corporate action
     */
    LocalDate getDate();
    
    /**
     * Get the type of corporate action
     */
    CorporateActionType getActionType();
    
    /**
     * Default comparison by date (earlier actions first)
     */
    @Override
    default int compareTo(CorporateAction other) {
        return this.getDate().compareTo(other.getDate());
    }
}
