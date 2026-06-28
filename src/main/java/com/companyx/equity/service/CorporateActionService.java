package com.companyx.equity.service;

import com.companyx.equity.model.Position;
import com.companyx.equity.model.Transaction;
import com.companyx.equity.model.corporateaction.CorporateActionType;
import com.companyx.equity.model.corporateaction.Delisting;
import com.companyx.equity.model.corporateaction.Dividend;
import com.companyx.equity.model.corporateaction.Merger;
import com.companyx.equity.model.corporateaction.Spinoff;
import com.companyx.equity.model.corporateaction.StockSplit;
import com.companyx.equity.model.corporateaction.SymbolChange;
import com.companyx.equity.provider.CorporateActionProvider;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Service for applying corporate actions to positions.
 * Orchestrates fetching corporate actions from providers and applying them.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CorporateActionService {

    private final CorporateActionProvider corporateActionProvider;
    private final SplitAdjustmentService splitAdjustmentService;
    private final DividendService dividendService;
    private final MergerService mergerService;
    private final SpinoffService spinoffService;
    private final SymbolMappingService symbolMappingService;
    private final DelistingService delistingService;

    public List<Dividend> getDividends(String symbol, LocalDate from, LocalDate to) {
        return corporateActionProvider.getDividends(symbol, from, to);
    }

    public List<StockSplit> getStockSplits(String symbol, LocalDate from, LocalDate to) {
        return corporateActionProvider.getStockSplits(symbol, from, to);
    }

    public List<Merger> getMergers(String symbol, LocalDate from, LocalDate to) {
        return corporateActionProvider.getMergers(symbol, from, to);
    }

    public List<Spinoff> getSpinoffs(String symbol, LocalDate from, LocalDate to) {
        return corporateActionProvider.getSpinoffs(symbol, from, to);
    }

    public List<SymbolChange> getSymbolChanges(String symbol, LocalDate from, LocalDate to) {
        return corporateActionProvider.getSymbolChanges(symbol, from, to);
    }

    public List<Delisting> getDelistings(String symbol, LocalDate from, LocalDate to) {
        return corporateActionProvider.getDelistings(symbol, from, to);
    }

    public Position applyPositionAdjustments(Position position, String symbol, LocalDate from, LocalDate to) {
        List<Dividend> dividends = corporateActionProvider.getDividends(symbol, from, to);
        List<StockSplit> splits = corporateActionProvider.getStockSplits(symbol, from, to);

        log.debug("Applying {} splits and {} dividends (stock) to {} from {} to {}",
                splits.size(), dividends.size(), symbol, from, to);

        Position afterSplits = splitAdjustmentService.applySplits(position, splits);
        return dividendService.applyStockDividends(afterSplits, dividends);
    }

    /**
     * Timeline-aware dividend income: uses the quantity at each ex-date rather than
     * a flat period-end quantity.
     */
    public BigDecimal calculateDividendIncome(BigInteger startQuantity,
                                               List<Transaction> periodTransactions,
                                               String symbol, LocalDate from, LocalDate to) {
        List<Dividend> dividends = corporateActionProvider.getDividends(symbol, from, to);
        return dividendService.calculateIncome(startQuantity, periodTransactions, dividends);
    }

    /** Convenience overload for constant-quantity scenarios (no intra-period trading). */
    public BigDecimal calculateDividendIncome(BigInteger shares, String symbol, LocalDate from, LocalDate to) {
        List<Dividend> dividends = corporateActionProvider.getDividends(symbol, from, to);
        return dividendService.calculateIncome(shares, dividends);
    }

    /**
     * Applies Phase 2 complex events (mergers, spinoffs, symbol changes, delistings) in chronological order.
     */
    public ComplexAdjustmentResult applyComplexAdjustments(
            Position position,
            String symbol,
            LocalDate from,
            LocalDate to,
            CorporateActionPriceLookup priceLookup
    ) {
        Position current = new Position(position);
        Map<String, Position> additionalPositions = new LinkedHashMap<>();
        BigDecimal additionalRealized = BigDecimal.ZERO;
        String activeSymbol = symbol;

        List<ComplexEvent> events = loadComplexEvents(activeSymbol, from, to);
        events.sort(Comparator.comparing(ComplexEvent::date).thenComparing(ComplexEvent::order));

        for (ComplexEvent event : events) {
            switch (event.type()) {
                case SYMBOL_CHANGE -> {
                    current = symbolMappingService.applySymbolChange(current, event.symbolChange());
                    activeSymbol = current.getSymbol();
                }
                case MERGER -> {
                    MergerService.MergerResult mergerResult = mergerService.applyMerger(current, event.merger());
                    current = mergerResult.position();
                    additionalRealized = additionalRealized.add(mergerResult.additionalRealized());
                    activeSymbol = current.getSymbol();
                }
                case SPINOFF -> {
                    Spinoff spinoff = event.spinoff();
                    SpinoffService.SpinoffResult spinoffResult = spinoffService.applySpinoff(
                            current,
                            spinoff,
                            priceLookup.getPrice(activeSymbol, spinoff.getDate()),
                            priceLookup.getPrice(spinoff.getSpunoffSymbol(), spinoff.getDate())
                    );
                    current = spinoffResult.parentPosition();
                    additionalPositions.put(
                            spinoffResult.spinoffPosition().getSymbol(),
                            spinoffResult.spinoffPosition()
                    );
                }
                case DELISTING -> current = delistingService.applyDelisting(current, event.delisting());
                default -> throw new IllegalStateException("Unsupported complex event: " + event.type());
            }
        }

        return new ComplexAdjustmentResult(current, additionalPositions, additionalRealized);
    }

    public AdjustedPosition applyToPosition(Position position, String symbol, LocalDate from, LocalDate to) {
        log.info("Applying corporate actions to {} from {} to {}", symbol, from, to);

        List<Dividend> dividends = corporateActionProvider.getDividends(symbol, from, to);
        List<StockSplit> splits = corporateActionProvider.getStockSplits(symbol, from, to);

        Position adjusted = applyPositionAdjustments(position, symbol, from, to);
        BigDecimal dividendIncome = calculateDividendIncome(adjusted.getQuantity(), symbol, from, to);

        log.info("Corporate actions applied: finalQty={}, dividendIncome={}",
                adjusted.getQuantity(), dividendIncome);

        return new AdjustedPosition(adjusted, dividendIncome, splits, dividends);
    }

    private List<ComplexEvent> loadComplexEvents(String symbol, LocalDate from, LocalDate to) {
        List<ComplexEvent> events = new ArrayList<>();

        corporateActionProvider.getSymbolChanges(symbol, from, to).stream()
                .map(change -> new ComplexEvent(change.getDate(), 0, CorporateActionType.SYMBOL_CHANGE, change, null, null, null))
                .forEach(events::add);

        corporateActionProvider.getMergers(symbol, from, to).stream()
                .map(merger -> new ComplexEvent(merger.getDate(), 1, CorporateActionType.MERGER, null, merger, null, null))
                .forEach(events::add);

        corporateActionProvider.getSpinoffs(symbol, from, to).stream()
                .map(spinoff -> new ComplexEvent(spinoff.getDate(), 2, CorporateActionType.SPINOFF, null, null, spinoff, null))
                .forEach(events::add);

        corporateActionProvider.getDelistings(symbol, from, to).stream()
                .map(delisting -> new ComplexEvent(delisting.getDate(), 3, CorporateActionType.DELISTING, null, null, null, delisting))
                .forEach(events::add);

        return events;
    }

    private record ComplexEvent(
            LocalDate date,
            int order,
            CorporateActionType type,
            SymbolChange symbolChange,
            Merger merger,
            Spinoff spinoff,
            Delisting delisting
    ) {
    }

    @Data
    public static class ComplexAdjustmentResult {
        private final Position position;
        private final Map<String, Position> additionalPositions;
        private final BigDecimal additionalRealized;
    }

    @Data
    public static class AdjustedPosition {
        private final Position position;
        private final BigDecimal dividendIncome;
        private final List<StockSplit> splitsApplied;
        private final List<Dividend> dividendsApplied;
    }
}
