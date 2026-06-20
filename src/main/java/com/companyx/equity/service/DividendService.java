package com.companyx.equity.service;

import com.companyx.equity.model.Position;
import com.companyx.equity.model.corporateaction.Dividend;
import com.companyx.equity.model.corporateaction.DividendType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Service for calculating dividend income and applying stock dividends to positions.
 * 
 * Cash dividends: Tracked as income, doesn't affect position
 * Stock dividends: Increase share quantity, preserving total basis (like a small split)
 */
@Slf4j
@Service
public class DividendService {
    
    /**
     * Calculate total dividend income for a position.
     * Only cash dividends contribute to income; stock dividends affect quantity.
     * 
     * @param shares Number of shares held (can be negative for short positions)
     * @param dividends List of dividends
     * @return Total dividend income
     */
    public BigDecimal calculateIncome(BigInteger shares, List<Dividend> dividends) {
        if (dividends == null || dividends.isEmpty()) {
            log.debug("No dividends to calculate income");
            return BigDecimal.ZERO;
        }
        
        BigDecimal totalIncome = dividends.stream()
                .filter(d -> d.getType() == DividendType.CASH)
                .map(d -> d.getAmount().multiply(new BigDecimal(shares)))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        log.debug("Calculated dividend income: {} for {} shares", totalIncome, shares);
        return totalIncome;
    }
    
    /**
     * Apply stock dividends to a position IN MEMORY.
     * Stock dividends increase share quantity while preserving total basis.
     * 
     * Formula: New Quantity = Old Quantity × (1 + Dividend Rate)
     * Total Basis = Unchanged
     * 
     * @param position The position to adjust
     * @param dividends List of dividends (cash dividends are ignored)
     * @return A new Position object with adjusted quantity
     */
    public Position applyStockDividends(Position position, List<Dividend> dividends) {
        if (position == null) {
            throw new IllegalArgumentException("Position cannot be null");
        }
        
        if (dividends == null || dividends.isEmpty()) {
            log.debug("No dividends to apply for {}", position.getSymbol());
            return copyPosition(position);
        }
        
        // Filter for stock dividends only
        List<Dividend> stockDividends = dividends.stream()
                .filter(d -> d.getType() == DividendType.STOCK)
                .sorted()
                .collect(Collectors.toList());
        
        if (stockDividends.isEmpty()) {
            log.debug("No stock dividends to apply for {}", position.getSymbol());
            return copyPosition(position);
        }
        
        log.info("Applying {} stock dividends to position for {}", stockDividends.size(), position.getSymbol());
        
        Position adjusted = copyPosition(position);
        
        for (Dividend dividend : stockDividends) {
            BigInteger oldQuantity = adjusted.getQuantity();
            BigDecimal totalBasis = adjusted.getValue();
            
            // Stock dividend is like a small split: qty increases, basis per share decreases
            BigDecimal ratio = BigDecimal.ONE.add(dividend.getSharesPerShare());
            BigDecimal newQuantityDecimal = new BigDecimal(oldQuantity).multiply(ratio);
            BigInteger newQuantity = newQuantityDecimal.setScale(0, java.math.RoundingMode.HALF_UP).toBigInteger();
            
            log.debug("Applying stock dividend {} to position: oldQty={}, ratio={}, newQty={}", 
                    dividend, oldQuantity, ratio, newQuantity);
            
            adjusted.setQuantity(newQuantity);
            
            log.debug("After stock dividend: newQty={}, basis={} (unchanged)", 
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
