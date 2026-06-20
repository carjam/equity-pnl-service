package com.companyx.equity.model.corporateaction;

import lombok.Getter;

@Getter
public enum CorporateActionType {
    CASH_DIVIDEND("Cash Dividend", "Cash dividend payment", true, false),
    STOCK_DIVIDEND("Stock Dividend", "Stock dividend distribution", true, false),
    FORWARD_SPLIT("Forward Split", "Forward stock split", false, true),
    REVERSE_SPLIT("Reverse Split", "Reverse stock split", false, true);

    private final String displayName;
    private final String description;
    private final boolean dividend;
    private final boolean split;

    CorporateActionType(String displayName, String description, boolean dividend, boolean split) {
        this.displayName = displayName;
        this.description = description;
        this.dividend = dividend;
        this.split = split;
    }

    public boolean isDividend() {
        return dividend;
    }

    public boolean isSplit() {
        return split;
    }
}
