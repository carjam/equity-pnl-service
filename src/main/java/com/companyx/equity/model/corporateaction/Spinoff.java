package com.companyx.equity.model.corporateaction;

import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;

import java.math.BigDecimal;
import java.math.MathContext;
import java.time.LocalDate;

/**
 * Represents a spinoff distributing shares of a subsidiary to existing shareholders.
 */
@Getter
@EqualsAndHashCode
public class Spinoff implements CorporateAction {

    private final String symbol;
    private final String spunoffSymbol;
    private final LocalDate date;
    private final BigDecimal distributionRatio;

    @Builder
    private Spinoff(String symbol, String spunoffSymbol, LocalDate date, BigDecimal distributionRatio) {
        if (symbol == null || symbol.isBlank()) {
            throw new IllegalArgumentException("Symbol cannot be null or empty");
        }
        if (spunoffSymbol == null || spunoffSymbol.isBlank()) {
            throw new IllegalArgumentException("Spunoff symbol cannot be null or empty");
        }
        if (date == null) {
            throw new IllegalArgumentException("Date cannot be null");
        }
        if (distributionRatio == null || distributionRatio.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Distribution ratio must be positive");
        }

        this.symbol = symbol;
        this.spunoffSymbol = spunoffSymbol;
        this.date = date;
        this.distributionRatio = distributionRatio;
    }

    @Override
    public CorporateActionType getActionType() {
        return CorporateActionType.SPINOFF;
    }

    public BigDecimal spunoffQuantity(BigDecimal parentQuantity) {
        return parentQuantity.multiply(distributionRatio, MathContext.DECIMAL128);
    }
}
