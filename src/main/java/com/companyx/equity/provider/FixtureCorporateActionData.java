package com.companyx.equity.provider;

import com.companyx.equity.model.corporateaction.Merger;
import com.companyx.equity.model.corporateaction.MergerType;
import com.companyx.equity.model.corporateaction.Spinoff;
import com.companyx.equity.model.corporateaction.SymbolChange;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Hardcoded real-world corporate action scenarios for dev/demo validation.
 * <p>
 * Each fixture maps to a distinct Phase 2 code path. Sources are SEC filings and
 * company investor relations (see class-level comments on each constant).
 */
public final class FixtureCorporateActionData {

    /** Disney 8-K (March 19, 2019): stock election 0.4517 DIS per FOX share. */
    public static final Merger FOX_DIS_MERGER = Merger.builder()
            .symbol("FOX")
            .acquirerSymbol("DIS")
            .date(LocalDate.of(2019, 3, 20))
            .type(MergerType.STOCK_FOR_STOCK)
            .exchangeRatio(new BigDecimal("0.4517"))
            .build();

    /** Twitter acquisition (October 27, 2022): $54.20 cash per share. */
    public static final Merger TWTR_CASH_MERGER = Merger.builder()
            .symbol("TWTR")
            .date(LocalDate.of(2022, 10, 27))
            .type(MergerType.CASH_FOR_STOCK)
            .cashPerShare(new BigDecimal("54.20"))
            .build();

    /** eBay Form 8937 (July 2015): 1:1 distribution; FMV from July 20 opening prices. */
    public static final Spinoff EBAY_PYPL_SPINOFF = Spinoff.builder()
            .symbol("EBAY")
            .spunoffSymbol("PYPL")
            .date(LocalDate.of(2015, 7, 20))
            .distributionRatio(new BigDecimal("1.0"))
            .build();

    /** Meta ticker change (June 9, 2022): FB renamed to META, no economics change. */
    public static final SymbolChange FB_META_SYMBOL_CHANGE = SymbolChange.builder()
            .oldSymbol("FB")
            .newSymbol("META")
            .date(LocalDate.of(2022, 6, 9))
            .build();

    /** NASDAQ opening price for EBAY on July 20, 2015 (Form 8937). */
    public static final BigDecimal EBAY_PYPL_DISTRIBUTION_EBAY_PRICE = new BigDecimal("26.92");

    /** NASDAQ opening price for PYPL on July 20, 2015 (Form 8937). */
    public static final BigDecimal EBAY_PYPL_DISTRIBUTION_PYPL_PRICE = new BigDecimal("41.63");

    /** Form 8937 parent basis allocation: 39.2706% of aggregate cost basis. */
    public static final BigDecimal EBAY_PYPL_PARENT_BASIS_FRACTION = new BigDecimal("0.392706");

    /** Form 8937 spinoff basis allocation: 60.7294% of aggregate cost basis. */
    public static final BigDecimal EBAY_PYPL_SPINOFF_BASIS_FRACTION = new BigDecimal("0.607294");

    private static final Map<String, List<Merger>> MERGERS_BY_SYMBOL = Map.of(
            FOX_DIS_MERGER.getSymbol(), List.of(FOX_DIS_MERGER),
            TWTR_CASH_MERGER.getSymbol(), List.of(TWTR_CASH_MERGER)
    );

    private static final Map<String, List<Spinoff>> SPINOFFS_BY_SYMBOL = Map.of(
            EBAY_PYPL_SPINOFF.getSymbol(), List.of(EBAY_PYPL_SPINOFF)
    );

    private static final Map<String, List<SymbolChange>> SYMBOL_CHANGES_BY_SYMBOL = Map.of(
            FB_META_SYMBOL_CHANGE.getOldSymbol(), List.of(FB_META_SYMBOL_CHANGE)
    );

    private FixtureCorporateActionData() {
    }

    public static List<Merger> mergersFor(String symbol, LocalDate from, LocalDate to) {
        return MERGERS_BY_SYMBOL.getOrDefault(symbol, List.of()).stream()
                .filter(merger -> inRange(merger.getDate(), from, to))
                .collect(Collectors.toList());
    }

    public static List<Spinoff> spinoffsFor(String symbol, LocalDate from, LocalDate to) {
        return SPINOFFS_BY_SYMBOL.getOrDefault(symbol, List.of()).stream()
                .filter(spinoff -> inRange(spinoff.getDate(), from, to))
                .collect(Collectors.toList());
    }

    public static List<SymbolChange> symbolChangesFor(String symbol, LocalDate from, LocalDate to) {
        return SYMBOL_CHANGES_BY_SYMBOL.getOrDefault(symbol, List.of()).stream()
                .filter(change -> inRange(change.getDate(), from, to))
                .collect(Collectors.toList());
    }

    private static boolean inRange(LocalDate date, LocalDate from, LocalDate to) {
        return !date.isBefore(from) && !date.isAfter(to);
    }
}
