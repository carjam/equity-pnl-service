package com.companyx.equity.controller;

import com.companyx.equity.dto.DividendsResponse;
import com.companyx.equity.dto.SplitsResponse;
import com.companyx.equity.model.corporateaction.Dividend;
import com.companyx.equity.model.corporateaction.DividendType;
import com.companyx.equity.model.corporateaction.Merger;
import com.companyx.equity.model.corporateaction.MergerType;
import com.companyx.equity.model.corporateaction.StockSplit;
import com.companyx.equity.model.corporateaction.SymbolChange;
import com.companyx.equity.provider.CorporateActionProviderFactory;
import com.companyx.equity.security.JwtAuthenticationEntryPoint;
import com.companyx.equity.security.JwtUtil;
import com.companyx.equity.service.CorporateActionService;
import com.companyx.equity.service.PnLService;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(CorporateActionController.class)
@WithMockUser(username = "test-user")
class CorporateActionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CorporateActionService corporateActionService;

    @MockBean
    private CorporateActionProviderFactory providerFactory;

    @MockBean
    private PnLService pnLService;

    @MockBean
    private JwtUtil jwtUtil;

    @MockBean
    private JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;

    @MockBean
    private UserDetailsService userDetailsService;

    @Test
    void shouldReturnDividends() throws Exception {
        Dividend dividend = Dividend.builder()
                .symbol("AAPL")
                .exDate(LocalDate.of(2024, 8, 9))
                .amount(new BigDecimal("0.25"))
                .type(DividendType.CASH)
                .build();

        when(corporateActionService.getDividends(eq("AAPL"), any(), any()))
                .thenReturn(List.of(dividend));

        mockMvc.perform(get("/api/v1/corporate-actions/dividends")
                        .param("symbol", "AAPL")
                        .param("from", "2024-01-01")
                        .param("to", "2024-12-31"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.symbol").value("AAPL"))
                .andExpect(jsonPath("$.count").value(1))
                .andExpect(jsonPath("$.dividends[0].amount").value(0.25));
    }

    @Test
    void shouldReturnSplits() throws Exception {
        StockSplit split = StockSplit.builder()
                .symbol("AAPL")
                .date(LocalDate.of(2020, 8, 31))
                .fromFactor(1)
                .toFactor(4)
                .build();

        when(corporateActionService.getStockSplits(eq("AAPL"), any(), any()))
                .thenReturn(List.of(split));

        mockMvc.perform(get("/api/v1/corporate-actions/splits")
                        .param("symbol", "AAPL")
                        .param("from", "2020-01-01")
                        .param("to", "2020-12-31"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.symbol").value("AAPL"))
                .andExpect(jsonPath("$.splits[0].toFactor").value(4));
    }

    @Test
    void shouldListCorporateActions() throws Exception {
        when(corporateActionService.getDividends(eq("AAPL"), any(), any()))
                .thenReturn(List.of());
        when(corporateActionService.getStockSplits(eq("AAPL"), any(), any()))
                .thenReturn(List.of());
        when(corporateActionService.getMergers(eq("AAPL"), any(), any()))
                .thenReturn(List.of());
        when(corporateActionService.getSpinoffs(eq("AAPL"), any(), any()))
                .thenReturn(List.of());
        when(corporateActionService.getSymbolChanges(eq("AAPL"), any(), any()))
                .thenReturn(List.of());
        when(corporateActionService.getDelistings(eq("AAPL"), any(), any()))
                .thenReturn(List.of());

        mockMvc.perform(get("/api/v1/corporate-actions")
                        .param("symbol", "AAPL")
                        .param("from", "2024-01-01")
                        .param("to", "2024-12-31"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.symbol").value("AAPL"))
                .andExpect(jsonPath("$.actions").isArray());
    }

    @Test
    void shouldReturnTotalReturn() throws Exception {
        when(pnLService.getPositions(eq("test-user"), any(), any()))
                .thenReturn(java.util.Map.of());
        when(corporateActionService.calculateDividendIncome(any(), eq("AAPL"), any(), any()))
                .thenReturn(BigDecimal.ZERO);

        mockMvc.perform(get("/api/v1/pnl/total-return")
                        .param("symbol", "AAPL")
                        .param("from", "2024-01-01")
                        .param("to", "2024-12-31"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.symbol").value("AAPL"))
                .andExpect(jsonPath("$.totalReturn").value(0));
    }

    @Test
    void shouldReturnMergers() throws Exception {
        Merger merger = Merger.builder()
                .symbol("XYZ")
                .acquirerSymbol("ABC")
                .date(LocalDate.of(2024, 6, 1))
                .type(MergerType.STOCK_FOR_STOCK)
                .exchangeRatio(new BigDecimal("0.8"))
                .build();

        when(corporateActionService.getMergers(eq("XYZ"), any(), any()))
                .thenReturn(List.of(merger));

        mockMvc.perform(get("/api/v1/corporate-actions/mergers")
                        .param("symbol", "XYZ")
                        .param("from", "2024-01-01")
                        .param("to", "2024-12-31"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.symbol").value("XYZ"))
                .andExpect(jsonPath("$.count").value(1))
                .andExpect(jsonPath("$.mergers[0].acquirerSymbol").value("ABC"));
    }

    @Test
    void shouldReturnSymbolChanges() throws Exception {
        SymbolChange change = SymbolChange.builder()
                .oldSymbol("FB")
                .newSymbol("META")
                .date(LocalDate.of(2022, 6, 9))
                .build();

        when(corporateActionService.getSymbolChanges(eq("FB"), any(), any()))
                .thenReturn(List.of(change));

        mockMvc.perform(get("/api/v1/corporate-actions/symbol-changes")
                        .param("symbol", "FB")
                        .param("from", "2022-01-01")
                        .param("to", "2022-12-31"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.symbolChanges[0].newSymbol").value("META"));
    }

    @Test
    void shouldListActiveProviders() throws Exception {
        when(providerFactory.getActiveProviderNames()).thenReturn(List.of("FINNHUB"));

        mockMvc.perform(get("/api/v1/corporate-actions/providers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.providers[0]").value("FINNHUB"));
    }
}
