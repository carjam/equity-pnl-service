package com.companyx.equity.service;

import com.companyx.equity.model.ClosedLot;
import com.companyx.equity.model.Transaction;
import com.companyx.equity.model.TransactionType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;

/**
 * FIFO lot matching for tax-lot reporting.
 *
 * Addresses Issue 8 (STCG/LTCG classification per IRC §1222) and
 * Issue 7 (wash-sale rule per IRC §1091).
 *
 * Note: this service operates on the performance P&L transaction history and
 * produces informational tax-lot records. The primary P&L engine continues to
 * use AVCO and is NOT suitable for tax reporting.
 */
@Slf4j
@Service
public class TaxLotService {

    private static final int WASH_SALE_DAYS = 30;
    private static final MathContext PRECISION = MathContext.DECIMAL128;

    /**
     * Applies FIFO lot matching to the supplied transactions and returns a list of
     * closed lots, each annotated with STCG/LTCG term and wash-sale status.
     *
     * @param transactions all transactions for a single symbol, in any order
     * @return closed lots in chronological sale order
     */
    public List<ClosedLot> computeClosedLots(List<Transaction> transactions) {
        if (transactions == null || transactions.isEmpty()) return List.of();

        List<Transaction> sorted = transactions.stream()
                .sorted(Comparator.comparing(t -> t.getTimestamp().toInstant()))
                .toList();

        // Open lots: (acquiredDate, quantity, pricePerShare)
        Deque<OpenLot> openLots = new ArrayDeque<>();
        List<ClosedLot> closedLots = new ArrayList<>();

        for (Transaction tx : sorted) {
            String type = tx.getTransactionType().getDescription();
            LocalDate txDate = tx.getTimestamp().toInstant()
                    .atZone(ZoneId.systemDefault()).toLocalDate();

            if (TransactionType.BUY.equals(type)) {
                BigDecimal qty = tx.getQuantity();
                BigDecimal pricePerShare = qty.compareTo(BigDecimal.ZERO) == 0
                        ? BigDecimal.ZERO
                        : tx.getValue().divide(qty, PRECISION);
                openLots.addLast(new OpenLot(txDate, qty, pricePerShare));

            } else if (TransactionType.SALE.equals(type)) {
                BigDecimal saleQty = tx.getQuantity();
                BigDecimal pricePerShare = saleQty.compareTo(BigDecimal.ZERO) == 0
                        ? BigDecimal.ZERO
                        : tx.getValue().divide(saleQty, PRECISION);

                BigDecimal remaining = saleQty;
                while (remaining.compareTo(BigDecimal.ZERO) > 0 && !openLots.isEmpty()) {
                    OpenLot lot = openLots.peekFirst();
                    BigDecimal consumed = remaining.min(lot.quantity());

                    BigDecimal gainLoss = pricePerShare.subtract(lot.costPerShare(), PRECISION)
                            .multiply(consumed, PRECISION)
                            .setScale(6, RoundingMode.HALF_UP);
                    ClosedLot.Term term = ClosedLot.computeTerm(lot.acquiredDate(), txDate);

                    closedLots.add(ClosedLot.builder()
                            .symbol(tx.getSymbol())
                            .acquiredDate(lot.acquiredDate())
                            .soldDate(txDate)
                            .quantity(consumed)
                            .proceedsPerShare(pricePerShare)
                            .costBasisPerShare(lot.costPerShare())
                            .gainLoss(gainLoss)
                            .term(term)
                            .washSale(false)          // placeholder — populated below
                            .disallowedLoss(BigDecimal.ZERO)
                            .build());

                    remaining = remaining.subtract(consumed);
                    BigDecimal lotRemaining = lot.quantity().subtract(consumed);
                    openLots.pollFirst();
                    if (lotRemaining.compareTo(BigDecimal.ZERO) > 0) {
                        openLots.addFirst(new OpenLot(lot.acquiredDate(), lotRemaining, lot.costPerShare()));
                    }
                }
            }
            // CASH_TRANS (deposits/withdrawals) are ignored for lot tracking
        }

        return applyWashSaleRules(closedLots, sorted);
    }

    /**
     * For each closed lot that resulted in a loss, checks whether the same symbol was
     * purchased within 30 calendar days before or after the sale date (the "wash-sale
     * window"). If so, the loss is flagged as disallowed and the closed lot is annotated.
     */
    private List<ClosedLot> applyWashSaleRules(List<ClosedLot> closedLots, List<Transaction> sortedTx) {
        List<ClosedLot> result = new ArrayList<>(closedLots.size());

        for (ClosedLot lot : closedLots) {
            if (lot.getGainLoss().compareTo(BigDecimal.ZERO) >= 0) {
                // Gains are never wash sales
                result.add(lot);
                continue;
            }

            LocalDate saleDate = lot.getSoldDate();
            LocalDate windowStart = saleDate.minusDays(WASH_SALE_DAYS);
            LocalDate windowEnd = saleDate.plusDays(WASH_SALE_DAYS);

            boolean isWashSale = sortedTx.stream().anyMatch(tx -> {
                if (!TransactionType.BUY.equals(tx.getTransactionType().getDescription())) return false;
                LocalDate buyDate = tx.getTimestamp().toInstant()
                        .atZone(ZoneId.systemDefault()).toLocalDate();
                // Exclude the original buy that was matched against this lot:
                // if the buy was strictly before the sale window start, it's the original lot
                // (already consumed). Only buys within the window matter.
                return !buyDate.isBefore(windowStart) && !buyDate.isAfter(windowEnd)
                        && !buyDate.isBefore(lot.getAcquiredDate().plusDays(1));
            });

            if (isWashSale) {
                BigDecimal disallowed = lot.getGainLoss().abs();
                log.debug("Wash sale detected: {} sold {} at loss {}; disallowed={}",
                        lot.getSymbol(), saleDate, lot.getGainLoss(), disallowed);
                result.add(ClosedLot.builder()
                        .symbol(lot.getSymbol())
                        .acquiredDate(lot.getAcquiredDate())
                        .soldDate(lot.getSoldDate())
                        .quantity(lot.getQuantity())
                        .proceedsPerShare(lot.getProceedsPerShare())
                        .costBasisPerShare(lot.getCostBasisPerShare())
                        .gainLoss(lot.getGainLoss())
                        .term(lot.getTerm())
                        .washSale(true)
                        .disallowedLoss(disallowed)
                        .build());
            } else {
                result.add(lot);
            }
        }

        return result;
    }

    private record OpenLot(LocalDate acquiredDate, BigDecimal quantity, BigDecimal costPerShare) {}
}
