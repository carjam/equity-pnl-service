package com.companyx.equity.model.corporateaction;

import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
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

    public BigInteger spunoffQuantity(BigInteger parentQuantity) {
        BigDecimal spunoff = new BigDecimal(parentQuantity).multiply(distributionRatio);
        return spunoff.setScale(0, RoundingMode.HALF_UP).toBigInteger();
    }
}
