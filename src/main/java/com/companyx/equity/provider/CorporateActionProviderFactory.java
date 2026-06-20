package com.companyx.equity.provider;

import com.companyx.equity.provider.CompositeCorporateActionProvider;
import com.companyx.equity.provider.CorporateActionProvider;
import com.companyx.equity.provider.FinnhubCorporateActionProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Resolves corporate action providers for orchestration and future multi-provider routing.
 */
@Component
@RequiredArgsConstructor
public class CorporateActionProviderFactory {

    private final CompositeCorporateActionProvider compositeProvider;
    private final FinnhubCorporateActionProvider finnhubProvider;

    public CorporateActionProvider getPrimaryProvider() {
        return compositeProvider;
    }

    public CorporateActionProvider getFallbackProvider() {
        return finnhubProvider;
    }

    public List<CorporateActionProvider> getAllProviders() {
        return List.of(compositeProvider);
    }

    public List<String> getActiveProviderNames() {
        return compositeProvider.getProviderNames();
    }
}
