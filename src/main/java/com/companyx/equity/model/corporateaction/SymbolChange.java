package com.companyx.equity.model.corporateaction;

import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;

import java.time.LocalDate;

/**
 * Represents a ticker symbol change with no P&amp;L impact.
 */
@Getter
@EqualsAndHashCode
public class SymbolChange implements CorporateAction {

    private final String oldSymbol;
    private final String newSymbol;
    private final LocalDate date;

    @Builder
    private SymbolChange(String oldSymbol, String newSymbol, LocalDate date) {
        if (oldSymbol == null || oldSymbol.isBlank()) {
            throw new IllegalArgumentException("Old symbol cannot be null or empty");
        }
        if (newSymbol == null || newSymbol.isBlank()) {
            throw new IllegalArgumentException("New symbol cannot be null or empty");
        }
        if (date == null) {
            throw new IllegalArgumentException("Date cannot be null");
        }

        this.oldSymbol = oldSymbol;
        this.newSymbol = newSymbol;
        this.date = date;
    }

    @Override
    public String getSymbol() {
        return oldSymbol;
    }

    @Override
    public CorporateActionType getActionType() {
        return CorporateActionType.SYMBOL_CHANGE;
    }
}
