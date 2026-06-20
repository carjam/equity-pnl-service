package com.companyx.equity.provider;

import com.companyx.equity.model.corporateaction.Delisting;
import com.companyx.equity.model.corporateaction.Dividend;
import com.companyx.equity.model.corporateaction.Merger;
import com.companyx.equity.model.corporateaction.Spinoff;
import com.companyx.equity.model.corporateaction.StockSplit;
import com.companyx.equity.model.corporateaction.SymbolChange;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

/**
 * Dev/demo provider with hardcoded real-world corporate action scenarios.
 * <p>
 * Enable with {@code corporate-actions.fixture.enabled=true} (on by default in the dev profile).
 * See {@link FixtureCorporateActionData} for event definitions.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "corporate-actions.fixture.enabled", havingValue = "true")
public class FixtureCorporateActionProvider implements CorporateActionProvider {

    @Override
    public String getName() {
        return "FIXTURE";
    }

    @Override
    public List<Dividend> getDividends(String symbol, LocalDate from, LocalDate to) {
        return Collections.emptyList();
    }

    @Override
    public List<StockSplit> getStockSplits(String symbol, LocalDate from, LocalDate to) {
        return Collections.emptyList();
    }

    @Override
    public List<Merger> getMergers(String symbol, LocalDate from, LocalDate to) {
        List<Merger> mergers = FixtureCorporateActionData.mergersFor(symbol, from, to);
        if (!mergers.isEmpty()) {
            log.debug("Fixture provider returning {} merger(s) for {}", mergers.size(), symbol);
        }
        return mergers;
    }

    @Override
    public List<Spinoff> getSpinoffs(String symbol, LocalDate from, LocalDate to) {
        List<Spinoff> spinoffs = FixtureCorporateActionData.spinoffsFor(symbol, from, to);
        if (!spinoffs.isEmpty()) {
            log.debug("Fixture provider returning {} spinoff(s) for {}", spinoffs.size(), symbol);
        }
        return spinoffs;
    }

    @Override
    public List<SymbolChange> getSymbolChanges(String symbol, LocalDate from, LocalDate to) {
        List<SymbolChange> changes = FixtureCorporateActionData.symbolChangesFor(symbol, from, to);
        if (!changes.isEmpty()) {
            log.debug("Fixture provider returning {} symbol change(s) for {}", changes.size(), symbol);
        }
        return changes;
    }

    @Override
    public List<Delisting> getDelistings(String symbol, LocalDate from, LocalDate to) {
        return Collections.emptyList();
    }
}
