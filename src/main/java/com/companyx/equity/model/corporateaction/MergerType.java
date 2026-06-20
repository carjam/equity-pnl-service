package com.companyx.equity.model.corporateaction;

import lombok.Getter;

@Getter
public enum MergerType {
    STOCK_FOR_STOCK("Stock-for-stock merger"),
    CASH_FOR_STOCK("Cash acquisition"),
    MIXED("Mixed cash and stock consideration");

    private final String description;

    MergerType(String description) {
        this.description = description;
    }
}
