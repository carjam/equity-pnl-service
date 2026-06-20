package com.companyx.equity.provider;

import com.companyx.equity.model.corporateaction.Merger;
import com.companyx.equity.model.corporateaction.MergerType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CompositeCorporateActionProviderTest {

    @Test
    void shouldMergeResultsFromSecondaryAndFinnhub() {
        FinnhubCorporateActionProvider finnhub = mock(FinnhubCorporateActionProvider.class);
        SecondaryCorporateActionProvider secondary = mock(SecondaryCorporateActionProvider.class);

        when(finnhub.getName()).thenReturn("FINNHUB");
        when(secondary.getName()).thenReturn("SECONDARY");

        Merger secondaryMerger = Merger.builder()
                .symbol("XYZ")
                .acquirerSymbol("ABC")
                .date(LocalDate.of(2024, 6, 1))
                .type(MergerType.STOCK_FOR_STOCK)
                .exchangeRatio(new BigDecimal("0.8"))
                .build();

        when(secondary.getMergers("XYZ", LocalDate.of(2024, 1, 1), LocalDate.of(2024, 12, 31)))
                .thenReturn(List.of(secondaryMerger));
        when(finnhub.getMergers("XYZ", LocalDate.of(2024, 1, 1), LocalDate.of(2024, 12, 31)))
                .thenReturn(List.of());

        CompositeCorporateActionProvider composite =
                new CompositeCorporateActionProvider(finnhub, Optional.empty(), Optional.of(secondary));

        List<Merger> mergers = composite.getMergers(
                "XYZ", LocalDate.of(2024, 1, 1), LocalDate.of(2024, 12, 31));

        assertEquals(1, mergers.size());
        assertEquals("SECONDARY+FINNHUB", composite.getName());
        assertEquals(List.of("SECONDARY", "FINNHUB"), composite.getProviderNames());
    }
}
