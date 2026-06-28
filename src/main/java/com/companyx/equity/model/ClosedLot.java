package com.companyx.equity.model;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * Represents the tax record of a share lot that has been fully or partially sold.
 * Used by TaxLotService for STCG/LTCG classification (IRC §1222) and
 * wash-sale detection (IRC §1091).
 */
@Getter
@Builder
public class ClosedLot {

    public enum Term { SHORT, LONG }

    private final String symbol;
    private final LocalDate acquiredDate;
    private final LocalDate soldDate;
    private final BigDecimal quantity;
    /** Proceeds per share at sale. */
    private final BigDecimal proceedsPerShare;
    /** Original cost basis per share at acquisition. */
    private final BigDecimal costBasisPerShare;
    /** Net realized gain/loss for this lot: (proceeds − basis) × quantity. */
    private final BigDecimal gainLoss;
    /** SHORT = held ≤ 365 days; LONG = held > 365 days (IRC §1222). */
    private final Term term;
    /** True when the loss is disallowed by the wash-sale rule (IRC §1091). */
    private final boolean washSale;
    /** Amount of loss disallowed by the wash-sale rule (0 when washSale=false). */
    private final BigDecimal disallowedLoss;

    public static Term computeTerm(LocalDate acquiredDate, LocalDate soldDate) {
        long days = ChronoUnit.DAYS.between(acquiredDate, soldDate);
        return days > 365 ? Term.LONG : Term.SHORT;
    }
}
