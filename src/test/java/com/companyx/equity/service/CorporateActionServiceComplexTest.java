package com.companyx.equity.service;

import com.companyx.equity.model.Position;
import com.companyx.equity.model.corporateaction.Merger;
import com.companyx.equity.model.corporateaction.MergerType;
import com.companyx.equity.provider.CorporateActionProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CorporateActionServiceComplexTest {

    @Mock
    private CorporateActionProvider corporateActionProvider;

    private CorporateActionService corporateActionService;

    @BeforeEach
    void setUp() {
        corporateActionService = new CorporateActionService(
                corporateActionProvider,
                new SplitAdjustmentService(),
                new DividendService(),
                new MergerService(),
                new SpinoffService(),
                new SymbolMappingService(),
                new DelistingService()
        );
    }

    @Test
    void shouldApplyStockForStockMergerThroughProvider() {
        Position position = longPosition("XYZ", 100, new BigDecimal("-5000.00"));
        Merger merger = Merger.builder()
                .symbol("XYZ")
                .acquirerSymbol("ABC")
                .date(LocalDate.of(2024, 6, 1))
                .type(MergerType.STOCK_FOR_STOCK)
                .exchangeRatio(new BigDecimal("0.8"))
                .build();

        when(corporateActionProvider.getMergers(eq("XYZ"), any(), any())).thenReturn(List.of(merger));
        when(corporateActionProvider.getSymbolChanges(any(), any(), any())).thenReturn(List.of());
        when(corporateActionProvider.getSpinoffs(any(), any(), any())).thenReturn(List.of());
        when(corporateActionProvider.getDelistings(any(), any(), any())).thenReturn(List.of());

        CorporateActionService.ComplexAdjustmentResult result = corporateActionService.applyComplexAdjustments(
                position, "XYZ", LocalDate.of(2024, 1, 1), LocalDate.of(2024, 12, 31), (sym, date) -> BigDecimal.TEN);

        assertEquals("ABC", result.getPosition().getSymbol());
        assertEquals(BigInteger.valueOf(80), result.getPosition().getQuantity());
        assertTrue(result.getAdditionalPositions().isEmpty());
    }

    private Position longPosition(String symbol, int quantity, BigDecimal basis) {
        Position position = new Position();
        position.setSymbol(symbol);
        position.setQuantity(BigInteger.valueOf(quantity));
        position.setValue(basis);
        position.setRealized(BigDecimal.ZERO);
        position.setUnrealized(BigDecimal.ZERO);
        return position;
    }
}
