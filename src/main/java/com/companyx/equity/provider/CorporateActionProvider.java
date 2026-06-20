package com.companyx.equity.provider;

import com.companyx.equity.model.corporateaction.Delisting;
import com.companyx.equity.model.corporateaction.Dividend;
import com.companyx.equity.model.corporateaction.Merger;
import com.companyx.equity.model.corporateaction.Spinoff;
import com.companyx.equity.model.corporateaction.StockSplit;
import com.companyx.equity.model.corporateaction.SymbolChange;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

/**
 * Interface for corporate action data providers.
 * Implementations fetch corporate action data from external sources.
 */
public interface CorporateActionProvider {

    String getName();

    List<Dividend> getDividends(String symbol, LocalDate from, LocalDate to);

    List<StockSplit> getStockSplits(String symbol, LocalDate from, LocalDate to);

    default List<Merger> getMergers(String symbol, LocalDate from, LocalDate to) {
        return Collections.emptyList();
    }

    default List<Spinoff> getSpinoffs(String symbol, LocalDate from, LocalDate to) {
        return Collections.emptyList();
    }

    default List<SymbolChange> getSymbolChanges(String symbol, LocalDate from, LocalDate to) {
        return Collections.emptyList();
    }

    default List<Delisting> getDelistings(String symbol, LocalDate from, LocalDate to) {
        return Collections.emptyList();
    }
}
