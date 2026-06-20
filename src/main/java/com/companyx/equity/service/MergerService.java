package com.companyx.equity.service;

import com.companyx.equity.model.Position;
import com.companyx.equity.model.corporateaction.Merger;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;

/**
 * Applies merger and acquisition events to positions.
 */
@Slf4j
@Service
public class MergerService {

    private static final int SCALE = 6;

    public record MergerResult(Position position, BigDecimal additionalRealized) {
    }

    public MergerResult applyMerger(Position position, Merger merger) {
        if (position == null) {
            throw new IllegalArgumentException("Position cannot be null");
        }
        if (merger == null) {
            throw new IllegalArgumentException("Merger cannot be null");
        }
        if (position.getQuantity().equals(BigInteger.ZERO)) {
            return unchanged(position);
        }

        return switch (merger.getType()) {
            case STOCK_FOR_STOCK -> applyStockForStock(position, merger);
            case CASH_FOR_STOCK -> applyCashForStock(position, merger);
            case MIXED -> applyMixed(position, merger);
        };
    }

    private MergerResult applyStockForStock(Position position, Merger merger) {
        if (merger.getAcquirerSymbol() == null || merger.getAcquirerSymbol().isBlank()) {
            throw new IllegalArgumentException("Acquirer symbol required for stock-for-stock merger");
        }

        Position adjusted = copy(position);
        adjusted.setSymbol(merger.getAcquirerSymbol());
        adjusted.setQuantity(merger.applyExchangeRatio(position.getQuantity()));
        return result(adjusted, BigDecimal.ZERO);
    }

    private MergerResult applyCashForStock(Position position, Merger merger) {
        BigDecimal cashReceived = merger.totalCashConsideration(position.getQuantity());
        BigDecimal additionalRealized = cashReceived.add(position.getValue());

        Position adjusted = copy(position);
        adjusted.setQuantity(BigInteger.ZERO);
        adjusted.setValue(BigDecimal.ZERO.setScale(SCALE, RoundingMode.HALF_UP));
        adjusted.setUnrealized(BigDecimal.ZERO);

        log.debug("Cash merger on {}: cash={}, additionalRealized={}", position.getSymbol(), cashReceived, additionalRealized);
        return result(adjusted, additionalRealized);
    }

    private MergerResult applyMixed(Position position, Merger merger) {
        if (merger.getAcquirerSymbol() == null || merger.getAcquirerSymbol().isBlank()) {
            throw new IllegalArgumentException("Acquirer symbol required for mixed merger");
        }
        if (merger.getAcquirerFairValuePerShare() == null) {
            throw new IllegalArgumentException("Acquirer fair value required for mixed merger");
        }

        BigInteger stockQuantity = merger.applyExchangeRatio(position.getQuantity());
        BigDecimal cashReceived = merger.totalCashConsideration(position.getQuantity());
        BigDecimal stockFairValue = merger.getAcquirerFairValuePerShare()
                .multiply(new BigDecimal(stockQuantity.abs()));
        BigDecimal totalFairValue = cashReceived.add(stockFairValue);

        BigDecimal totalBasis = position.getValue();
        BigDecimal cashBasis = totalBasis.multiply(cashReceived.divide(totalFairValue, SCALE, RoundingMode.HALF_UP));
        BigDecimal stockBasis = totalBasis.subtract(cashBasis);
        BigDecimal additionalRealized = cashReceived.add(cashBasis);

        Position adjusted = copy(position);
        adjusted.setSymbol(merger.getAcquirerSymbol());
        adjusted.setQuantity(stockQuantity);
        adjusted.setValue(stockBasis.setScale(SCALE, RoundingMode.HALF_UP));

        return result(adjusted, additionalRealized.setScale(SCALE, RoundingMode.HALF_UP));
    }

    private MergerResult unchanged(Position position) {
        return result(copy(position), BigDecimal.ZERO);
    }

    private MergerResult result(Position position, BigDecimal additionalRealized) {
        return new MergerResult(position, additionalRealized);
    }

    private Position copy(Position original) {
        return new Position(original);
    }
}
