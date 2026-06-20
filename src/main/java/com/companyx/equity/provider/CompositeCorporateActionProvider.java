package com.companyx.equity.provider;

import com.companyx.equity.model.corporateaction.Delisting;
import com.companyx.equity.model.corporateaction.Dividend;
import com.companyx.equity.model.corporateaction.Merger;
import com.companyx.equity.model.corporateaction.Spinoff;
import com.companyx.equity.model.corporateaction.StockSplit;
import com.companyx.equity.model.corporateaction.SymbolChange;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Aggregates multiple corporate action providers with secondary-first priority.
 * <p>
 * Today: Finnhub only (dividends + splits). When secondary is enabled, it is consulted
 * first for Phase 2 events. Provider selection guidance: docs/corporate-actions/PROVIDER_STRATEGY.md
 */
@Slf4j
@Component
@Primary
public class CompositeCorporateActionProvider implements CorporateActionProvider {

    private final List<CorporateActionProvider> providers;

    public CompositeCorporateActionProvider(
            FinnhubCorporateActionProvider finnhubProvider,
            @Autowired(required = false) Optional<FixtureCorporateActionProvider> fixtureProvider,
            @Autowired(required = false) Optional<SecondaryCorporateActionProvider> secondaryProvider
    ) {
        List<CorporateActionProvider> ordered = new ArrayList<>();
        fixtureProvider.ifPresent(ordered::add);
        secondaryProvider.ifPresent(ordered::add);
        ordered.add(finnhubProvider);
        this.providers = List.copyOf(ordered);
        log.info("CompositeCorporateActionProvider initialized with {} provider(s): {}",
                providers.size(), getName());
    }

    public List<String> getProviderNames() {
        return providers.stream().map(CorporateActionProvider::getName).toList();
    }

    @Override
    public String getName() {
        return providers.stream()
                .map(CorporateActionProvider::getName)
                .collect(Collectors.joining("+"));
    }

    @Override
    public List<Dividend> getDividends(String symbol, LocalDate from, LocalDate to) {
        return merge(providers, provider -> provider.getDividends(symbol, from, to));
    }

    @Override
    public List<StockSplit> getStockSplits(String symbol, LocalDate from, LocalDate to) {
        return merge(providers, provider -> provider.getStockSplits(symbol, from, to));
    }

    @Override
    public List<Merger> getMergers(String symbol, LocalDate from, LocalDate to) {
        return merge(providers, provider -> provider.getMergers(symbol, from, to));
    }

    @Override
    public List<Spinoff> getSpinoffs(String symbol, LocalDate from, LocalDate to) {
        return merge(providers, provider -> provider.getSpinoffs(symbol, from, to));
    }

    @Override
    public List<SymbolChange> getSymbolChanges(String symbol, LocalDate from, LocalDate to) {
        return merge(providers, provider -> provider.getSymbolChanges(symbol, from, to));
    }

    @Override
    public List<Delisting> getDelistings(String symbol, LocalDate from, LocalDate to) {
        return merge(providers, provider -> provider.getDelistings(symbol, from, to));
    }

    private <T> List<T> merge(
            List<CorporateActionProvider> providers,
            Function<CorporateActionProvider, List<T>> fetch
    ) {
        Set<T> merged = new LinkedHashSet<>();
        for (CorporateActionProvider provider : providers) {
            merged.addAll(fetch.apply(provider));
        }
        return new ArrayList<>(merged);
    }
}
