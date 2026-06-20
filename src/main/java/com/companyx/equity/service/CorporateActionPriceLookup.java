package com.companyx.equity.service;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Resolves historical prices for complex corporate action calculations (e.g. spinoff basis allocation).
 */
@FunctionalInterface
public interface CorporateActionPriceLookup {

    BigDecimal getPrice(String symbol, LocalDate date);
}
