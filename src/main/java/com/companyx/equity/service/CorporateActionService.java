package com.companyx.equity.service;

import com.companyx.equity.model.Position;
import com.companyx.equity.model.corporateaction.Dividend;
import com.companyx.equity.model.corporateaction.StockSplit;
import com.companyx.equity.provider.CorporateActionProvider;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Service for applying corporate actions to positions.
 * Orchestrates fetching corporate actions from providers and applying them.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CorporateActionService {
    
    private final CorporateActionProvider corporateActionProvider;
    private final SplitAdjustmentService splitAdjustmentService;
    private final DividendService dividendService;
    
    /**
     * Apply all corporate actions to a position for a date range.
     * Returns an adjusted position with dividend income calculated.
     * 
     * @param position Original position
     * @param symbol Stock symbol
     * @param from Start date for corporate actions
     * @param to End date for corporate actions
     * @return AdjustedPosition with splits/dividends applied
     */
    public AdjustedPosition applyToPosition(Position position, String symbol, LocalDate from, LocalDate to) {
        log.info("Applying corporate actions to {} from {} to {}", symbol, from, to);
        
        // Fetch corporate actions from provider (cached)
        List<Dividend> dividends = corporateActionProvider.getDividends(symbol, from, to);
        List<StockSplit> splits = corporateActionProvider.getStockSplits(symbol, from, to);
        
        log.debug("Found {} dividends and {} splits for {}", dividends.size(), splits.size(), symbol);
        
        // Apply stock splits to adjust quantity and basis
        Position afterSplits = splitAdjustmentService.applySplits(position, splits);
        
        // Apply stock dividends to further adjust quantity
        Position afterStockDividends = dividendService.applyStockDividends(afterSplits, dividends);
        
        // Calculate cash dividend income
        BigDecimal dividendIncome = dividendService.calculateIncome(afterStockDividends.getQuantity(), dividends);
        
        log.info("Corporate actions applied: finalQty={}, dividendIncome={}", 
                afterStockDividends.getQuantity(), dividendIncome);
        
        return new AdjustedPosition(afterStockDividends, dividendIncome, splits, dividends);
    }
    
    /**
     * Result of applying corporate actions to a position
     */
    @Data
    public static class AdjustedPosition {
        private final Position position;
        private final BigDecimal dividendIncome;
        private final List<StockSplit> splitsApplied;
        private final List<Dividend> dividendsApplied;
    }
}
