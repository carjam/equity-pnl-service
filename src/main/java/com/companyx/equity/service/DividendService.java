package com.companyx.equity.service;

import com.companyx.equity.model.Position;
import com.companyx.equity.model.Transaction;
import com.companyx.equity.model.TransactionType;
import com.companyx.equity.model.corporateaction.Dividend;
import com.companyx.equity.model.corporateaction.DividendType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Collections;
import java.util.Comparator;
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
     * Calculate total dividend income using a quantity timeline.
     *
     * For each cash dividend's ex-date, the quantity is determined by replaying
     * periodTransactions (buys add, sells subtract) from startQuantity. Transactions
     * on the ex-date itself are excluded — the holder-of-record is determined at the
     * close of the day before the ex-date.
     *
     * @param startQuantity      shares held at the start of the reporting period
     * @param periodTransactions transactions that occurred within the reporting period,
     *                           in any order (sorted internally)
     * @param dividends          list of dividends (cash and stock; stock are ignored)
     * @return total dividend income for the period
     */
    public BigDecimal calculateIncome(BigDecimal startQuantity,
                                      List<Transaction> periodTransactions,
                                      List<Dividend> dividends) {
        if (dividends == null || dividends.isEmpty()) {
            log.debug("No dividends to calculate income");
            return BigDecimal.ZERO;
        }

        List<Dividend> cashDividends = dividends.stream()
                .filter(d -> d.getType() == DividendType.CASH)    // ROC and STOCK are not income
                .sorted(Comparator.comparing(Dividend::getExDate))
                .collect(Collectors.toList());

        if (cashDividends.isEmpty()) return BigDecimal.ZERO;

        List<Transaction> sortedTx = (periodTransactions == null ? Collections.<Transaction>emptyList() : periodTransactions)
                .stream()
                .filter(t -> !TransactionType.CASH_TRANS.contains(t.getTransactionType().getDescription()))
                .sorted(Comparator.comparing(Transaction::getTimestamp))
                .collect(Collectors.toList());

        BigDecimal totalIncome = BigDecimal.ZERO;
        BigDecimal currentQuantity = startQuantity;
        int txIdx = 0;

        for (Dividend div : cashDividends) {
            LocalDate exDate = div.getExDate();

            // Apply all transactions that settled strictly before the ex-date.
            // Transactions ON the ex-date do not entitle the buyer to that dividend.
            while (txIdx < sortedTx.size()) {
                Transaction tx = sortedTx.get(txIdx);
                LocalDate txDate = tx.getTimestamp().toInstant()
                        .atZone(ZoneId.systemDefault()).toLocalDate();
                if (!txDate.isBefore(exDate)) break;

                String type = tx.getTransactionType().getDescription();
                if (TransactionType.BUY.equals(type)) {
                    currentQuantity = currentQuantity.add(tx.getQuantity());
                } else if (TransactionType.SALE.equals(type)) {
                    currentQuantity = currentQuantity.subtract(tx.getQuantity());
                }
                txIdx++;
            }

            totalIncome = totalIncome.add(div.getAmount().multiply(currentQuantity));
        }

        log.debug("Calculated dividend income: {} (timeline-aware)", totalIncome);
        return totalIncome;
    }

    /**
     * Convenience overload for callers that know the quantity is constant throughout
     * the period (no share purchases or sales between dividends).
     */
    public BigDecimal calculateIncome(BigDecimal shares, List<Dividend> dividends) {
        return calculateIncome(shares, Collections.emptyList(), dividends);
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
            BigDecimal oldQuantity = adjusted.getQuantity();
            BigDecimal totalBasis = adjusted.getValue();

            // Stock dividend is like a small split: qty increases, basis per share decreases
            BigDecimal ratio = BigDecimal.ONE.add(dividend.getSharesPerShare());
            BigDecimal newQuantity = oldQuantity.multiply(ratio);

            log.debug("Applying stock dividend {} to position: oldQty={}, ratio={}, newQty={}",
                    dividend, oldQuantity, ratio, newQuantity);

            adjusted.setQuantity(newQuantity);

            log.debug("After stock dividend: newQty={}, basis={} (unchanged)", newQuantity, totalBasis);
        }
        
        return adjusted;
    }
    
    /**
     * Apply return-of-capital (ROC) distributions to a position IN MEMORY.
     *
     * ROC is a non-taxable distribution that reduces the cost basis of the position.
     * The position value (which is negative for longs) is increased toward zero by the
     * ROC amount per share × quantity held. If the total ROC exceeds the remaining basis,
     * the excess is recognized as a realized gain and the basis is floored at zero.
     *
     * @param position  The position to adjust
     * @param dividends List of dividends (only RETURN_OF_CAPITAL types are processed)
     * @return A new Position with adjusted value (basis) and realized gain
     */
    public Position applyReturnOfCapital(Position position, List<Dividend> dividends) {
        if (position == null) {
            throw new IllegalArgumentException("Position cannot be null");
        }

        if (dividends == null || dividends.isEmpty()) {
            return copyPosition(position);
        }

        List<Dividend> rocDividends = dividends.stream()
                .filter(d -> d.getType() == DividendType.RETURN_OF_CAPITAL)
                .sorted(Comparator.comparing(Dividend::getExDate))
                .collect(Collectors.toList());

        if (rocDividends.isEmpty()) {
            return copyPosition(position);
        }

        Position adjusted = copyPosition(position);

        for (Dividend roc : rocDividends) {
            BigDecimal qty = adjusted.getQuantity().abs();
            BigDecimal rocAmount = roc.getAmount().multiply(qty);

            // value is negative for long positions; currentBasis is the positive dollar amount
            BigDecimal currentBasis = adjusted.getValue().negate();

            if (rocAmount.compareTo(currentBasis) >= 0) {
                // ROC exceeds remaining basis — floor at zero, excess is realized gain
                BigDecimal excess = rocAmount.subtract(currentBasis);
                adjusted.setValue(BigDecimal.ZERO);
                adjusted.setRealized(adjusted.getRealized().add(excess));
                log.debug("ROC {} exhausted basis; excess {} recognized as realized gain",
                        roc.getSymbol(), excess);
            } else {
                adjusted.setValue(adjusted.getValue().add(rocAmount));
                log.debug("ROC {} reduced basis by {} to {}", roc.getSymbol(), rocAmount, adjusted.getValue());
            }
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
