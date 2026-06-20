package com.companyx.equity;

import com.companyx.equity.model.Position;
import com.companyx.equity.service.CorporateActionService;
import org.mockito.stubbing.Answer;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.lenient;

/**
 * Shared Mockito setup so existing PnL tests remain unchanged when corporate actions are enabled.
 */
public final class CorporateActionTestSupport {

    private CorporateActionTestSupport() {
    }

    public static void stubPassThroughCorporateActions(CorporateActionService corporateActionService) {
        Answer<Position> passThrough = invocation -> invocation.getArgument(0);

        lenient().when(corporateActionService.applyPositionAdjustments(
                any(Position.class), anyString(), any(LocalDate.class), any(LocalDate.class)))
                .thenAnswer(passThrough);

        lenient().when(corporateActionService.calculateDividendIncome(
                any(), anyString(), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(BigDecimal.ZERO);

        lenient().when(corporateActionService.applyComplexAdjustments(
                any(Position.class), anyString(), any(LocalDate.class), any(LocalDate.class), any()))
                .thenAnswer(invocation -> new CorporateActionService.ComplexAdjustmentResult(
                        invocation.getArgument(0),
                        Collections.emptyMap(),
                        BigDecimal.ZERO));
    }
}
