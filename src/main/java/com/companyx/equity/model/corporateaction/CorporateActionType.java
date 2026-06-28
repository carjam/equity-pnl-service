package com.companyx.equity.model.corporateaction;

import lombok.Getter;

@Getter
public enum CorporateActionType {
    CASH_DIVIDEND("Cash Dividend", "Cash dividend payment", true, false, false),
    STOCK_DIVIDEND("Stock Dividend", "Stock dividend distribution", true, false, false),
    RETURN_OF_CAPITAL("Return of Capital", "Non-taxable return of capital distribution", true, false, false),
    FORWARD_SPLIT("Forward Split", "Forward stock split", false, true, false),
    REVERSE_SPLIT("Reverse Split", "Reverse stock split", false, true, false),
    MERGER("Merger", "Merger or acquisition", false, false, true),
    SPINOFF("Spinoff", "Spinoff distribution", false, false, true),
    SYMBOL_CHANGE("Symbol Change", "Ticker symbol change", false, false, true),
    DELISTING("Delisting", "Security delisting", false, false, true);

    private final String displayName;
    private final String description;
    private final boolean dividend;
    private final boolean split;
    private final boolean complex;

    CorporateActionType(String displayName, String description, boolean dividend, boolean split, boolean complex) {
        this.displayName = displayName;
        this.description = description;
        this.dividend = dividend;
        this.split = split;
        this.complex = complex;
    }

    public boolean isDividend() {
        return dividend;
    }

    public boolean isSplit() {
        return split;
    }

    public boolean isComplex() {
        return complex;
    }
}
