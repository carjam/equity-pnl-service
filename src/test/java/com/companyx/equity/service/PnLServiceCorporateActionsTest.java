package com.companyx.equity.service;

import com.companyx.equity.model.Position;
import com.companyx.equity.model.corporateaction.Dividend;
import com.companyx.equity.model.corporateaction.StockSplit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.companyx.equity.TestDataBuilder;
import com.companyx.equity.dto.CandleDto;
import com.companyx.equity.dto.MarkDto;
import com.companyx.equity.model.User;
import com.companyx.equity.repository.FinhubRepository;
import com.companyx.equity.repository.TransactionRepository;
import com.companyx.equity.repository.UserRepository;
import com.fasterxml.jackson.core.JsonProcessingException;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests PnLService integration with corporate actions.
 */
@ExtendWith(MockitoExtension.class)
class PnLServiceCorporateActionsTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private FinhubRepository finhubRepository;

    @Mock
    private CorporateActionService corporateActionService;

    @InjectMocks
    private PnLService pnLService;

    private User testUser;

    @BeforeEach
    void setUp() {
        testUser = TestDataBuilder.createTestUser("test-user", "password");
    }

    @Test
    void shouldApplySplitAdjustmentBeforeCalculatingUnrealized() throws JsonProcessingException {
        when(userRepository.findByUid("test-user")).thenReturn(Optional.of(testUser));

        List<com.companyx.equity.model.Transaction> prior = List.of(
                TestDataBuilder.createDepositTransaction(testUser,
                        TestDataBuilder.createTransactionType(3, com.companyx.equity.model.TransactionType.DEPOSIT),
                        LocalDateTime.of(2020, 1, 1, 10, 0), 50000.0),
                TestDataBuilder.createBuyTransaction(testUser,
                        TestDataBuilder.createTransactionType(1, com.companyx.equity.model.TransactionType.BUY),
                        "AAPL", LocalDateTime.of(2020, 1, 15, 10, 0), 100, 20000.0)
        );
        when(transactionRepository.findAllBefore(anyInt(), any(Date.class))).thenReturn(prior);
        when(transactionRepository.findAllBetween(anyInt(), any(Date.class), any(Date.class)))
                .thenReturn(Collections.emptyList());
        when(transactionRepository.findEarliestByUserAndSymbol(1, "AAPL"))
                .thenReturn(Optional.of(java.sql.Timestamp.valueOf(LocalDateTime.of(2020, 1, 15, 10, 0))));

        Position rawPosition = new Position();
        rawPosition.setSymbol("AAPL");
        rawPosition.setQuantity(BigInteger.valueOf(100));
        rawPosition.setValue(new BigDecimal("-20000.00"));
        rawPosition.setRealized(BigDecimal.ZERO);
        rawPosition.setUnrealized(BigDecimal.ZERO);

        Position splitAdjusted = new Position(rawPosition);
        splitAdjusted.setQuantity(BigInteger.valueOf(400));

        when(corporateActionService.applyPositionAdjustments(any(), eq("AAPL"), any(), any()))
                .thenAnswer(inv -> {
                    Position p = inv.getArgument(0);
                    if (p.getQuantity().equals(BigInteger.valueOf(100))) {
                        return splitAdjusted;
                    }
                    return p;
                });
        when(corporateActionService.calculateDividendIncome(any(), eq("AAPL"), any(), any()))
                .thenReturn(BigDecimal.ZERO);
        when(corporateActionService.applyComplexAdjustments(any(), anyString(), any(), any(), any()))
                .thenAnswer(invocation -> new CorporateActionService.ComplexAdjustmentResult(
                        invocation.getArgument(0),
                        java.util.Collections.emptyMap(),
                        BigDecimal.ZERO));

        MarkDto mark = new MarkDto();
        mark.setCurrentPrice(new BigDecimal("50.00"));
        when(finhubRepository.getMark("AAPL")).thenReturn(mark);

        Date start = java.sql.Date.valueOf("2020-01-01");
        Date end = java.sql.Date.valueOf(LocalDate.now());

        Map<String, Position> result = pnLService.getPositions("test-user", start, end);

        Position aapl = result.get("AAPL");
        assertNotNull(aapl);
        assertEquals(BigInteger.valueOf(400), aapl.getQuantity());
        assertEquals(0, new BigDecimal("0.00").compareTo(aapl.getUnrealized()),
                "After 4:1 split at $50, unrealized P&L should be break-even");
    }

    @Test
    void shouldAddDividendIncomeToRealizedPnL() throws JsonProcessingException {
        when(userRepository.findByUid("test-user")).thenReturn(Optional.of(testUser));

        when(transactionRepository.findAllBefore(anyInt(), any(Date.class))).thenReturn(List.of(
                TestDataBuilder.createDepositTransaction(testUser,
                        TestDataBuilder.createTransactionType(3, com.companyx.equity.model.TransactionType.DEPOSIT),
                        LocalDateTime.of(2024, 1, 1, 10, 0), 10000.0),
                TestDataBuilder.createBuyTransaction(testUser,
                        TestDataBuilder.createTransactionType(1, com.companyx.equity.model.TransactionType.BUY),
                        "KO", LocalDateTime.of(2024, 1, 15, 10, 0), 100, 6000.0)
        ));
        when(transactionRepository.findAllBetween(anyInt(), any(Date.class), any(Date.class)))
                .thenReturn(Collections.emptyList());
        when(transactionRepository.findEarliestByUserAndSymbol(1, "KO"))
                .thenReturn(Optional.of(java.sql.Timestamp.valueOf(LocalDateTime.of(2024, 1, 15, 10, 0))));

        when(corporateActionService.applyPositionAdjustments(any(), eq("KO"), any(), any()))
                .thenAnswer(inv -> inv.getArgument(0));
        when(corporateActionService.applyComplexAdjustments(any(), eq("KO"), any(), any(), any()))
                .thenAnswer(invocation -> new CorporateActionService.ComplexAdjustmentResult(
                        invocation.getArgument(0),
                        java.util.Collections.emptyMap(),
                        BigDecimal.ZERO));
        when(corporateActionService.calculateDividendIncome(any(), eq("KO"), any(), any()))
                .thenReturn(new BigDecimal("100.00"));

        CandleDto candle = new CandleDto();
        candle.setClose(List.of(new BigDecimal("63.00")));
        candle.setStatus("ok");
        when(finhubRepository.getCandle(eq("KO"), any(Date.class), any(Date.class))).thenReturn(candle);

        Date start = java.sql.Date.valueOf("2024-01-01");
        Date end = java.sql.Date.valueOf("2024-12-31");

        Map<String, Position> result = pnLService.getPositions("test-user", start, end);

        Position ko = result.get("KO");
        assertNotNull(ko);
        assertEquals(0, new BigDecimal("100.00").compareTo(ko.getRealized()),
                "Quarterly dividends should be included in period realized P&L");
    }

    @Test
    void shouldSkipCorporateActionsForCashPosition() throws JsonProcessingException {
        when(userRepository.findByUid("test-user")).thenReturn(Optional.of(testUser));
        when(transactionRepository.findAllBefore(anyInt(), any(Date.class))).thenReturn(List.of(
                TestDataBuilder.createDepositTransaction(testUser,
                        TestDataBuilder.createTransactionType(3, com.companyx.equity.model.TransactionType.DEPOSIT),
                        LocalDateTime.of(2024, 1, 1, 10, 0), 10000.0)
        ));
        when(transactionRepository.findAllBetween(anyInt(), any(Date.class), any(Date.class)))
                .thenReturn(Collections.emptyList());

        Date start = java.sql.Date.valueOf("2024-01-01");
        Date end = java.sql.Date.valueOf("2024-01-31");

        pnLService.getPositions("test-user", start, end);

        verify(corporateActionService, never()).applyPositionAdjustments(any(), eq("cash"), any(), any());
    }
}
