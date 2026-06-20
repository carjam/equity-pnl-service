package com.companyx.equity.provider;

import com.companyx.equity.model.corporateaction.Merger;
import com.companyx.equity.model.corporateaction.Spinoff;
import com.companyx.equity.model.corporateaction.SymbolChange;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FixtureCorporateActionProviderTest {

    private final FixtureCorporateActionProvider provider = new FixtureCorporateActionProvider();

    @Test
    void shouldReturnFoxDisMergerForFoxSymbolInRange() {
        List<Merger> mergers = provider.getMergers(
                "FOX", LocalDate.of(2019, 1, 1), LocalDate.of(2019, 12, 31));

        assertEquals(1, mergers.size());
        assertEquals(FixtureCorporateActionData.FOX_DIS_MERGER, mergers.get(0));
    }

    @Test
    void shouldExcludeFoxMergerOutsideDateRange() {
        List<Merger> mergers = provider.getMergers(
                "FOX", LocalDate.of(2020, 1, 1), LocalDate.of(2020, 12, 31));

        assertTrue(mergers.isEmpty());
    }

    @Test
    void shouldReturnEbayPyplSpinoffForEbaySymbolInRange() {
        List<Spinoff> spinoffs = provider.getSpinoffs(
                "EBAY", LocalDate.of(2015, 1, 1), LocalDate.of(2015, 12, 31));

        assertEquals(1, spinoffs.size());
        assertEquals(FixtureCorporateActionData.EBAY_PYPL_SPINOFF, spinoffs.get(0));
    }

    @Test
    void shouldReturnTwtrCashMergerForTwtrSymbolInRange() {
        List<Merger> mergers = provider.getMergers(
                "TWTR", LocalDate.of(2022, 1, 1), LocalDate.of(2022, 12, 31));

        assertEquals(1, mergers.size());
        assertEquals(FixtureCorporateActionData.TWTR_CASH_MERGER, mergers.get(0));
    }

    @Test
    void shouldReturnFbMetaSymbolChangeForFbSymbolInRange() {
        List<SymbolChange> changes = provider.getSymbolChanges(
                "FB", LocalDate.of(2022, 1, 1), LocalDate.of(2022, 12, 31));

        assertEquals(1, changes.size());
        assertEquals(FixtureCorporateActionData.FB_META_SYMBOL_CHANGE, changes.get(0));
    }

    @Test
    void shouldReturnEmptyForUnknownSymbols() {
        assertTrue(provider.getMergers("AAPL", LocalDate.of(2019, 1, 1), LocalDate.of(2019, 12, 31)).isEmpty());
        assertTrue(provider.getSpinoffs("AAPL", LocalDate.of(2015, 1, 1), LocalDate.of(2015, 12, 31)).isEmpty());
        assertTrue(provider.getSymbolChanges("AAPL", LocalDate.of(2022, 1, 1), LocalDate.of(2022, 12, 31)).isEmpty());
    }

    @Test
    void shouldIdentifyAsFixtureProvider() {
        assertEquals("FIXTURE", provider.getName());
    }
}
