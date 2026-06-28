package com.companyx.equity.controller;

import com.companyx.equity.TestDataBuilder;
import com.companyx.equity.error.TransactionNotFoundException;
import com.companyx.equity.model.Position;
import com.companyx.equity.model.Transaction;
import com.companyx.equity.model.TransactionType;
import com.companyx.equity.model.User;
import com.companyx.equity.model.ClosedLot;
import com.companyx.equity.security.JwtAuthenticationEntryPoint;
import com.companyx.equity.security.JwtUtil;
import com.companyx.equity.service.PnLService;
import com.companyx.equity.service.TaxLotService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.*;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(TransactionController.class)
@ActiveProfiles("test")
@WithMockUser(username = "test-user")
class TransactionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PnLService pnLService;

    @MockBean
    private TaxLotService taxLotService;

    @MockBean
    private JwtUtil jwtUtil;

    @MockBean
    private JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;

    @MockBean
    private UserDetailsService userDetailsService;

    @Autowired
    private ObjectMapper objectMapper;

    private User testUser;
    private TransactionType buyType;

    @BeforeEach
    void setup() {
        testUser = TestDataBuilder.createTestUser("test-user", "password");
        buyType = TestDataBuilder.createTransactionType(1, TransactionType.BUY);
    }

    @Test
    void testPnlBetween_Success() throws Exception {
        Map<String, Position> positions = new HashMap<>();
        Position aaplPosition = new Position(
                testUser,
                Timestamp.valueOf(LocalDateTime.now()),
                "AAPL"
        );
        aaplPosition.setQuantity(BigDecimal.valueOf(100));
        aaplPosition.setValue(BigDecimal.valueOf(-5000));
        aaplPosition.setRealized(BigDecimal.valueOf(1000));
        aaplPosition.setUnrealized(BigDecimal.valueOf(500));
        aaplPosition.setPrice(BigDecimal.valueOf(55));
        positions.put("AAPL", aaplPosition);

        when(pnLService.getPositions(eq("test-user"), any(), any()))
                .thenReturn(positions);

        mockMvc.perform(get("/api/v1/pnl")
                        .param("from", "2024-01-01")
                        .param("to", "2024-01-31"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.positions.AAPL").exists())
                .andExpect(jsonPath("$.positions.AAPL.quantity").value(100))
                .andExpect(jsonPath("$.positions.AAPL.symbol").value("AAPL"));
    }

    @Test
    void testPnlBetween_IncludesAvcoMethodologyDisclosure() throws Exception {
        when(pnLService.getPositions(eq("test-user"), any(), any()))
                .thenReturn(Collections.emptyMap());

        mockMvc.perform(get("/api/v1/pnl")
                        .param("from", "2024-01-01")
                        .param("to", "2024-12-31"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.methodology").exists())
                .andExpect(jsonPath("$.methodology.basis").value("AVCO"))
                .andExpect(jsonPath("$.methodology.reportingType").value("performance"))
                .andExpect(jsonPath("$.methodology.disclaimer").isString())
                .andExpect(jsonPath("$.methodology.period.from").value("2024-01-01"))
                .andExpect(jsonPath("$.methodology.period.to").value("2024-12-31"));
    }

    @Test
    void testPnlBetween_MethodologyDisclaimerMentionsTaxReporting() throws Exception {
        when(pnLService.getPositions(eq("test-user"), any(), any()))
                .thenReturn(Collections.emptyMap());

        mockMvc.perform(get("/api/v1/pnl")
                        .param("from", "2024-01-01")
                        .param("to", "2024-12-31"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.methodology.disclaimer",
                        org.hamcrest.Matchers.containsStringIgnoringCase("tax")));
    }

    @Test
    void testPnlBetween_MissingFromParameter() throws Exception {
        mockMvc.perform(get("/api/v1/pnl")
                        .param("to", "2024-01-31"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testPnlBetween_MissingToParameter() throws Exception {
        mockMvc.perform(get("/api/v1/pnl")
                        .param("from", "2024-01-01"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testPnlBetween_InvalidDateFormat() throws Exception {
        mockMvc.perform(get("/api/v1/pnl")
                        .param("from", "01/01/2024")
                        .param("to", "2024-01-31"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testPnlBetween_EmptyResult() throws Exception {
        when(pnLService.getPositions(eq("test-user"), any(), any()))
                .thenReturn(Collections.emptyMap());

        mockMvc.perform(get("/api/v1/pnl")
                        .param("from", "2024-01-01")
                        .param("to", "2024-01-31"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.positions").isEmpty())
                .andExpect(jsonPath("$.methodology").exists());
    }

    @Test
    void testShowTransaction_Success() throws Exception {
        Transaction transaction = TestDataBuilder.createBuyTransaction(
                testUser, buyType, "AAPL",
                LocalDateTime.of(2024, 1, 15, 10, 0),
                100, 5000.0
        );

        when(pnLService.getTransactionById("test-user", "123"))
                .thenReturn(transaction);

        mockMvc.perform(get("/api/v1/transactions/123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.symbol").value("AAPL"))
                .andExpect(jsonPath("$.quantity").value(100));
    }

    @Test
    void testShowTransaction_NotFound() throws Exception {
        when(pnLService.getTransactionById("test-user", "999"))
                .thenThrow(new TransactionNotFoundException("test-user", 999L));

        mockMvc.perform(get("/api/v1/transactions/999"))
                .andExpect(status().isNotFound());
    }

    @Test
    void testFindBetween_WithBothDates() throws Exception {
        List<Transaction> transactions = Arrays.asList(
                TestDataBuilder.createBuyTransaction(testUser, buyType, "AAPL",
                        LocalDateTime.of(2024, 1, 15, 10, 0), 100, 5000.0),
                TestDataBuilder.createBuyTransaction(testUser, buyType, "GOOGL",
                        LocalDateTime.of(2024, 1, 16, 10, 0), 50, 7500.0)
        );

        when(pnLService.getTransactionsByDates(eq("test-user"), any(), any()))
                .thenReturn(transactions);

        mockMvc.perform(get("/api/v1/transactions")
                        .param("from", "2024-01-01")
                        .param("to", "2024-01-31"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].symbol").value("AAPL"))
                .andExpect(jsonPath("$[1].symbol").value("GOOGL"));
    }

    @Test
    void testFindBetween_WithoutDates() throws Exception {
        List<Transaction> transactions = List.of(
                TestDataBuilder.createBuyTransaction(testUser, buyType, "AAPL",
                        LocalDateTime.of(2024, 1, 15, 10, 0), 100, 5000.0)
        );

        when(pnLService.getTransactionsByDates(eq("test-user"), any(), any()))
                .thenReturn(transactions);

        mockMvc.perform(get("/api/v1/transactions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));
    }

    @Test
    void testFindBetween_OnlyFromDate() throws Exception {
        when(pnLService.getTransactionsByDates(eq("test-user"), any(), any()))
                .thenReturn(Collections.emptyList());

        mockMvc.perform(get("/api/v1/transactions")
                        .param("from", "2024-01-01"))
                .andExpect(status().isOk());
    }

    @Test
    void testFindBetween_OnlyToDate() throws Exception {
        when(pnLService.getTransactionsByDates(eq("test-user"), any(), any()))
                .thenReturn(Collections.emptyList());

        mockMvc.perform(get("/api/v1/transactions")
                        .param("to", "2024-01-31"))
                .andExpect(status().isOk());
    }

    @Test
    @WithAnonymousUser
    void testPnlWithoutAuthentication_ShouldFail() throws Exception {
        mockMvc.perform(get("/api/v1/pnl")
                        .param("from", "2024-01-01")
                        .param("to", "2024-01-31"))
                .andExpect(status().is4xxClientError());
    }

    @Test
    @WithAnonymousUser
    void testTransactionsWithoutAuthentication_ShouldFail() throws Exception {
        mockMvc.perform(get("/api/v1/transactions"))
                .andExpect(status().is4xxClientError());
    }

    // ─── Tax-lot endpoint (Issues 7 & 8) ────────────────────────────────────

    @Test
    void testTaxLots_ReturnsSummaryFields() throws Exception {
        ClosedLot lot = ClosedLot.builder()
                .symbol("AAPL")
                .acquiredDate(java.time.LocalDate.of(2022, 1, 1))
                .soldDate(java.time.LocalDate.of(2023, 6, 1))
                .quantity(new BigDecimal("100"))
                .proceedsPerShare(new BigDecimal("150.00"))
                .costBasisPerShare(new BigDecimal("100.00"))
                .gainLoss(new BigDecimal("5000.00"))
                .term(ClosedLot.Term.LONG)
                .washSale(false)
                .disallowedLoss(BigDecimal.ZERO)
                .build();

        when(pnLService.getAllTransactionsBySymbol(eq("test-user"), eq("AAPL")))
                .thenReturn(List.of());
        when(taxLotService.computeClosedLots(any())).thenReturn(List.of(lot));

        mockMvc.perform(get("/api/v1/pnl/tax-lots")
                        .param("symbol", "AAPL")
                        .param("from", "2023-01-01")
                        .param("to", "2023-12-31"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.symbol").value("AAPL"))
                .andExpect(jsonPath("$.longTermGainLoss").value(5000.00))
                .andExpect(jsonPath("$.shortTermGainLoss").value(0))
                .andExpect(jsonPath("$.totalGainLoss").value(5000.00))
                .andExpect(jsonPath("$.washSaleDisallowedLoss").value(0))
                .andExpect(jsonPath("$.disclaimer").exists())
                .andExpect(jsonPath("$.closedLots").isArray())
                .andExpect(jsonPath("$.closedLots[0].term").value("LONG"));
    }

    @Test
    void testTaxLots_FlagsWashSale() throws Exception {
        ClosedLot washLot = ClosedLot.builder()
                .symbol("ENPH")
                .acquiredDate(java.time.LocalDate.of(2023, 1, 1))
                .soldDate(java.time.LocalDate.of(2023, 3, 1))
                .quantity(new BigDecimal("100"))
                .proceedsPerShare(new BigDecimal("80.00"))
                .costBasisPerShare(new BigDecimal("100.00"))
                .gainLoss(new BigDecimal("-2000.00"))
                .term(ClosedLot.Term.SHORT)
                .washSale(true)
                .disallowedLoss(new BigDecimal("2000.00"))
                .build();

        when(pnLService.getAllTransactionsBySymbol(eq("test-user"), eq("ENPH")))
                .thenReturn(List.of());
        when(taxLotService.computeClosedLots(any())).thenReturn(List.of(washLot));

        mockMvc.perform(get("/api/v1/pnl/tax-lots")
                        .param("symbol", "ENPH")
                        .param("from", "2023-01-01")
                        .param("to", "2023-12-31"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.washSaleDisallowedLoss").value(2000.00))
                .andExpect(jsonPath("$.closedLots[0].washSale").value(true))
                .andExpect(jsonPath("$.closedLots[0].disallowedLoss").value(2000.00));
    }

    @Test
    @WithAnonymousUser
    void testTaxLotsWithoutAuthentication_ShouldFail() throws Exception {
        mockMvc.perform(get("/api/v1/pnl/tax-lots")
                        .param("symbol", "AAPL")
                        .param("from", "2023-01-01")
                        .param("to", "2023-12-31"))
                .andExpect(status().is4xxClientError());
    }
}
