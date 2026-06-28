package com.companyx.equity.model.corporateaction;

import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Represents a delisting event that closes a position.
 */
@Getter
@EqualsAndHashCode
public class Delisting implements CorporateAction {

    private final String symbol;
    private final LocalDate date;
    /** Cash paid per share on delisting. Null or zero means the security became worthless. */
    private final BigDecimal cashPerShare;

    @Builder
    private Delisting(String symbol, LocalDate date, BigDecimal cashPerShare) {
        if (symbol == null || symbol.isBlank()) {
            throw new IllegalArgumentException("Symbol cannot be null or empty");
        }
        if (date == null) {
            throw new IllegalArgumentException("Date cannot be null");
        }
        if (cashPerShare != null && cashPerShare.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("cashPerShare cannot be negative");
        }

        this.symbol = symbol;
        this.date = date;
        this.cashPerShare = cashPerShare;
    }

    public boolean isWorthless() {
        return cashPerShare == null || cashPerShare.compareTo(BigDecimal.ZERO) == 0;
    }

    @Override
    public CorporateActionType getActionType() {
        return CorporateActionType.DELISTING;
    }
}
