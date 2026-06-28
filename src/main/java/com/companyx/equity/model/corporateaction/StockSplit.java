package com.companyx.equity.model.corporateaction;

import lombok.*;

import java.math.BigDecimal;
import java.math.MathContext;
import java.time.LocalDate;

/**
 * Represents a stock split event.
 * 
 * Forward split (e.g., 4:1): Shareholders receive 4 shares for every 1 they own
 * Reverse split (e.g., 1:10): Every 10 shares are combined into 1 share
 * 
 * Split Ratio = toFactor / fromFactor
 * - Forward split: ratio > 1 (e.g., 4:1 = 4.0)
 * - Reverse split: ratio < 1 (e.g., 1:10 = 0.1)
 * - Fractional split: ratio can be any positive value (e.g., 3:2 = 1.5)
 */
@Getter
@EqualsAndHashCode
public class StockSplit implements CorporateAction {
    
    private final String symbol;
    private final LocalDate date;           // Effective date of the split
    private final int fromFactor;           // Denominator (what you had before)
    private final int toFactor;             // Numerator (what you get)
    private final BigDecimal splitRatio;    // Calculated: toFactor / fromFactor
    
    @Builder
    private StockSplit(String symbol, LocalDate date, int fromFactor, int toFactor) {
        // Validation
        if (symbol == null || symbol.trim().isEmpty()) {
            throw new IllegalArgumentException("Symbol cannot be null or empty");
        }
        if (date == null) {
            throw new IllegalArgumentException("Date cannot be null");
        }
        if (fromFactor <= 0) {
            throw new IllegalArgumentException("From factor must be positive");
        }
        if (toFactor <= 0) {
            throw new IllegalArgumentException("To factor must be positive");
        }
        
        // Assignment
        this.symbol = symbol;
        this.date = date;
        this.fromFactor = fromFactor;
        this.toFactor = toFactor;
        
        // Calculate split ratio with sufficient precision for fractional splits
        this.splitRatio = BigDecimal.valueOf(toFactor)
                .divide(BigDecimal.valueOf(fromFactor), MathContext.DECIMAL128);
    }
    
    @Override
    public CorporateActionType getActionType() {
        return splitRatio.compareTo(BigDecimal.ONE) >= 0 
                ? CorporateActionType.FORWARD_SPLIT 
                : CorporateActionType.REVERSE_SPLIT;
    }
    
    /**
     * Apply this split to a share quantity.
     * New Quantity = Old Quantity × Split Ratio
     *
     * Fractional shares are preserved (e.g. a 3:2 split on 101 shares yields 151.5).
     */
    public BigDecimal applyToQuantity(BigDecimal originalQuantity) {
        return originalQuantity.multiply(splitRatio, MathContext.DECIMAL128);
    }

    /**
     * Apply this split to cost basis per share.
     * New Cost Basis = Old Cost Basis ÷ Split Ratio
     *
     * Note: Total basis (quantity × cost per share) remains unchanged.
     */
    public BigDecimal applyToCostBasis(BigDecimal originalBasisPerShare) {
        return originalBasisPerShare.divide(splitRatio, MathContext.DECIMAL128);
    }
    
    @Override
    public String toString() {
        return String.format("%s %d-for-%d Split on %s (ratio: %.2f)", 
                symbol, toFactor, fromFactor, date, splitRatio);
    }
}
