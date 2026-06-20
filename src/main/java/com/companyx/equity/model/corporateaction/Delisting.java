package com.companyx.equity.model.corporateaction;

import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;

import java.time.LocalDate;

/**
 * Represents a delisting event that closes a position.
 */
@Getter
@EqualsAndHashCode
public class Delisting implements CorporateAction {

    private final String symbol;
    private final LocalDate date;

    @Builder
    private Delisting(String symbol, LocalDate date) {
        if (symbol == null || symbol.isBlank()) {
            throw new IllegalArgumentException("Symbol cannot be null or empty");
        }
        if (date == null) {
            throw new IllegalArgumentException("Date cannot be null");
        }

        this.symbol = symbol;
        this.date = date;
    }

    @Override
    public CorporateActionType getActionType() {
        return CorporateActionType.DELISTING;
    }
}
