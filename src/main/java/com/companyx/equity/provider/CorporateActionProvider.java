package com.companyx.equity.provider;

import com.companyx.equity.model.corporateaction.Dividend;
import com.companyx.equity.model.corporateaction.StockSplit;

import java.time.LocalDate;
import java.util.List;

/**
 * Interface for corporate action data providers.
 * Implementations fetch corporate action data from external sources.
 */
public interface CorporateActionProvider {
    
    /**
     * Get the name of this provider
     */
    String getName();
    
    /**
     * Fetch dividend data for a symbol within a date range
     * 
     * @param symbol Stock symbol
     * @param from Start date (inclusive)
     * @param to End date (inclusive)
     * @return List of dividends
     */
    List<Dividend> getDividends(String symbol, LocalDate from, LocalDate to);
    
    /**
     * Fetch stock split data for a symbol within a date range
     * 
     * @param symbol Stock symbol
     * @param from Start date (inclusive)
     * @param to End date (inclusive)
     * @return List of stock splits
     */
    List<StockSplit> getStockSplits(String symbol, LocalDate from, LocalDate to);
}
