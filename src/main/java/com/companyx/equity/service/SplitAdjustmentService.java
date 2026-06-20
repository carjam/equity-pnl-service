package com.companyx.equity.service;

import com.companyx.equity.model.Position;
import com.companyx.equity.model.corporateaction.StockSplit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Service for applying stock splits to positions.
 * 
 * Stock splits adjust share quantities while preserving total cost basis.
 * Formula:
 *   New Quantity = Old Quantity × Split Ratio
 *   Total Basis = Unchanged
 *   New Cost per Share = Old Cost / Split Ratio
 */
@Slf4j
@Service
public class SplitAdjustmentService {
    
    /**
     * Apply stock splits to a position IN MEMORY.
     * Splits are applied in chronological order (earliest first).
     * 
     * @param position The position to adjust
     * @param splits List of splits to apply (will be sorted by date)
     * @return A new Position object with adjusted quantity and basis
     */
    public Position applySplits(Position position, List<StockSplit> splits) {
        if (position == null) {
            throw new IllegalArgumentException("Position cannot be null");
        }
        
        if (splits == null || splits.isEmpty()) {
            log.debug("No splits to apply for {}", position.getSymbol());
            return copyPosition(position);
        }
        
        // Sort splits by date (earliest first) to apply them in chronological order
        List<StockSplit> sortedSplits = splits.stream()
                .sorted()
                .collect(Collectors.toList());
        
        log.info("Applying {} splits to position for {}", sortedSplits.size(), position.getSymbol());
        
        Position adjusted = copyPosition(position);
        
        for (StockSplit split : sortedSplits) {
            BigInteger oldQuantity = adjusted.getQuantity();
            BigDecimal totalBasis = adjusted.getValue();
            
            log.debug("Applying {} to position: oldQty={}, basis={}", 
                    split, oldQuantity, totalBasis);
            
            // Apply split to quantity: newQty = oldQty × ratio
            BigInteger newQuantity = split.applyToQuantity(oldQuantity);
            
            // Total basis remains unchanged
            adjusted.setQuantity(newQuantity);
            
            log.debug("After split: newQty={}, basis={} (unchanged)", 
                    newQuantity, totalBasis);
        }
        
        return adjusted;
    }
    
    /**
     * Create a copy of a position to avoid modifying the original
     */
    private Position copyPosition(Position original) {
        return new Position(original);
    }
}
