package com.companyx.equity.provider;

import com.companyx.equity.model.corporateaction.Delisting;
import com.companyx.equity.model.corporateaction.Dividend;
import com.companyx.equity.model.corporateaction.Merger;
import com.companyx.equity.model.corporateaction.Spinoff;
import com.companyx.equity.model.corporateaction.StockSplit;
import com.companyx.equity.model.corporateaction.SymbolChange;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

/**
 * Optional secondary provider for M&amp;A and complex corporate actions.
 * <p>
 * <b>Default:</b> leave {@code corporate-actions.secondary.enabled=false} and use Finnhub only
 * (splits + dividends). Phase 2 processing is implemented; without a secondary source,
 * complex events are simply not applied.
 * <p>
 * <b>Paid APIs (fastest path):</b> Polygon (~$99/mo), Databento (~$99–199/mo), QUODD (enterprise).
 * Wire HTTP client in {@link #fetchComplexEvents}.
 * <p>
 * <b>SEC EDGAR ($0/month):</b> viable long-term alternative—one-time parser work against
 * 8-K / DEF 14A / S-4 filings. Higher upfront cost, no recurring API fees. See
 * {@code docs/corporate-actions/PROVIDER_STRATEGY.md}.
 * <p>
 * Enable with {@code corporate-actions.secondary.enabled=true} and configure URL/API key.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "corporate-actions.secondary.enabled", havingValue = "true")
public class SecondaryCorporateActionProvider implements CorporateActionProvider {

    private final String baseUrl;
    private final String apiKey;

    public SecondaryCorporateActionProvider(
            @Value("${corporate-actions.secondary.url:}") String baseUrl,
            @Value("${corporate-actions.secondary.api-key:}") String apiKey
    ) {
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("Secondary corporate action provider enabled but corporate-actions.secondary.api-key is not set");
        } else {
            log.info("Secondary corporate action provider ready (url={})", baseUrl);
        }
    }

    @Override
    public String getName() {
        return "SECONDARY";
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
        return fetchComplexEvents("mergers", symbol, from, to);
    }

    @Override
    public List<Spinoff> getSpinoffs(String symbol, LocalDate from, LocalDate to) {
        return fetchComplexEvents("spinoffs", symbol, from, to);
    }

    @Override
    public List<SymbolChange> getSymbolChanges(String symbol, LocalDate from, LocalDate to) {
        return fetchComplexEvents("symbol-changes", symbol, from, to);
    }

    @Override
    public List<Delisting> getDelistings(String symbol, LocalDate from, LocalDate to) {
        return fetchComplexEvents("delistings", symbol, from, to);
    }

    private <T> List<T> fetchComplexEvents(String eventType, String symbol, LocalDate from, LocalDate to) {
        if (apiKey == null || apiKey.isBlank() || baseUrl == null || baseUrl.isBlank()) {
            return Collections.emptyList();
        }
        // TODO: paid API client and/or EdgarCorporateActionProvider — see docs/corporate-actions/PROVIDER_STRATEGY.md
        log.debug("Secondary provider {} fetch for {} not yet implemented; returning empty", eventType, symbol);
        return Collections.emptyList();
    }
}
