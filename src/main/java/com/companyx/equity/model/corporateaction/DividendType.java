package com.companyx.equity.model.corporateaction;

import lombok.Getter;

@Getter
public enum DividendType {
    CASH("Cash"),
    STOCK("Stock");

    private final String displayName;

    DividendType(String displayName) {
        this.displayName = displayName;
    }

    public boolean isCash() {
        return this == CASH;
    }

    public boolean isStock() {
        return this == STOCK;
    }
}
