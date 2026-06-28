package com.companyx.equity.model.corporateaction;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Represents a dividend payment (cash or stock).
 * 
 * Cash dividends: Direct cash payment to shareholders
 * Stock dividends: Additional shares distributed to shareholders
 */
@Getter
@EqualsAndHashCode
public class Dividend implements CorporateAction {
    
    private final String symbol;
    private final LocalDate exDate;          // Ex-dividend date (when stock trades without dividend)
    private final LocalDate payDate;         // Payment date
    private final LocalDate recordDate;      // Record date (shareholders of record receive dividend)
    private final BigDecimal amount;         // Amount per share (USD for cash, 0 for stock)
    private final String currency;
    private final DividendType type;
    private final Integer frequency;         // Dividends per year (1=annual, 4=quarterly, etc.)
    private final BigDecimal sharesPerShare; // For stock dividends (e.g., 0.05 = 5% stock dividend)
    
    @Builder
    private Dividend(String symbol, LocalDate exDate, LocalDate payDate, LocalDate recordDate,
                    BigDecimal amount, String currency, DividendType type, 
                    Integer frequency, BigDecimal sharesPerShare) {
        // Validation
        if (symbol == null || symbol.trim().isEmpty()) {
            throw new IllegalArgumentException("Symbol cannot be null or empty");
        }
        if (exDate == null) {
            throw new IllegalArgumentException("Ex-date cannot be null");
        }
        if (amount == null) {
            throw new IllegalArgumentException("Amount cannot be null");
        }
        if (amount.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Amount cannot be negative");
        }
        if (type == null) {
            throw new IllegalArgumentException("Dividend type cannot be null");
        }
        
        // Assignment with defaults
        this.symbol = symbol;
        this.exDate = exDate;
        this.payDate = payDate;
        this.recordDate = recordDate;
        this.amount = amount;
        this.currency = (currency == null || currency.trim().isEmpty()) ? "USD" : currency;
        this.type = type;
        this.frequency = frequency;
        this.sharesPerShare = sharesPerShare;
    }
    
    @Override
    public LocalDate getDate() {
        return exDate;
    }
    
    @Override
    public CorporateActionType getActionType() {
        return switch (type) {
            case CASH -> CorporateActionType.CASH_DIVIDEND;
            case STOCK -> CorporateActionType.STOCK_DIVIDEND;
            case RETURN_OF_CAPITAL -> CorporateActionType.RETURN_OF_CAPITAL;
        };
    }
    
    /**
     * Calculate the total cash payment for a given number of shares.
     * Returns zero for stock dividends.
     */
    public BigDecimal calculatePayment(int shares) {
        if (type == DividendType.STOCK) {
            return BigDecimal.ZERO;
        }
        return amount.multiply(BigDecimal.valueOf(shares));
    }
    
    @Override
    public String toString() {
        if (type == DividendType.CASH) {
            return String.format("%s Cash Dividend: $%.2f on %s", 
                    symbol, amount, exDate);
        } else {
            return String.format("%s Stock Dividend: %.2f%% on %s", 
                    symbol, sharesPerShare != null ? sharesPerShare.multiply(BigDecimal.valueOf(100)) : BigDecimal.ZERO, exDate);
        }
    }
}
