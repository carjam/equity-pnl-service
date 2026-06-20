package com.companyx.equity.model.corporateaction;

import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.time.LocalDate;

/**
 * Represents a merger or acquisition affecting a held symbol.
 */
@Getter
@EqualsAndHashCode
public class Merger implements CorporateAction {

    private final String symbol;
    private final String acquirerSymbol;
    private final LocalDate date;
    private final MergerType type;
    private final BigDecimal exchangeRatio;
    private final BigDecimal cashPerShare;
    private final BigDecimal acquirerFairValuePerShare;

    @Builder
    private Merger(
            String symbol,
            String acquirerSymbol,
            LocalDate date,
            MergerType type,
            BigDecimal exchangeRatio,
            BigDecimal cashPerShare,
            BigDecimal acquirerFairValuePerShare
    ) {
        if (symbol == null || symbol.isBlank()) {
            throw new IllegalArgumentException("Symbol cannot be null or empty");
        }
        if (date == null) {
            throw new IllegalArgumentException("Date cannot be null");
        }
        if (type == null) {
            throw new IllegalArgumentException("Merger type cannot be null");
        }

        this.symbol = symbol;
        this.acquirerSymbol = acquirerSymbol;
        this.date = date;
        this.type = type;
        this.exchangeRatio = exchangeRatio != null ? exchangeRatio : BigDecimal.ZERO;
        this.cashPerShare = cashPerShare != null ? cashPerShare : BigDecimal.ZERO;
        this.acquirerFairValuePerShare = acquirerFairValuePerShare;
    }

    @Override
    public CorporateActionType getActionType() {
        return CorporateActionType.MERGER;
    }

    public BigInteger applyExchangeRatio(BigInteger quantity) {
        BigDecimal newQuantity = new BigDecimal(quantity).multiply(exchangeRatio);
        return newQuantity.setScale(0, RoundingMode.HALF_UP).toBigInteger();
    }

    public BigDecimal totalCashConsideration(BigInteger quantity) {
        return cashPerShare.multiply(new BigDecimal(quantity.abs()));
    }
}
