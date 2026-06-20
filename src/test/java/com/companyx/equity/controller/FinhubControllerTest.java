package com.companyx.equity.controller;

import com.companyx.equity.dto.CandleDto;
import com.companyx.equity.dto.MarkDto;
import com.companyx.equity.error.RestExceptionHandler;
import com.companyx.equity.error.VendorConnectivityException;
import com.companyx.equity.repository.FinhubRepository;
import com.companyx.equity.security.JwtAuthenticationEntryPoint;
import com.companyx.equity.security.JwtUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Tests for FinhubController
 */
@WebMvcTest(FinhubController.class)
@Import(RestExceptionHandler.class)
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
class FinhubControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private FinhubRepository finhubRepository;

    @MockBean
    private JwtUtil jwtUtil;

    @MockBean
    private JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;

    @MockBean
    private UserDetailsService userDetailsService;

    @Autowired
    private ObjectMapper objectMapper;
    
    @Test
    public void testGetMark_Success() throws Exception {
        MarkDto mark = new MarkDto();
        mark.setCurrentPrice(BigDecimal.valueOf(150.25));
        mark.setChange(BigDecimal.valueOf(2.50));
        mark.setPrecentChange(1.69f);  // Note: field has typo "precent"
        mark.setDailyHi(BigDecimal.valueOf(152.00));
        mark.setDailyLo(BigDecimal.valueOf(148.00));
        mark.setOpen(BigDecimal.valueOf(149.00));
        mark.setPriorClose(BigDecimal.valueOf(147.75));
        
        when(finhubRepository.getMark("AAPL")).thenReturn(mark);
        
        mockMvc.perform(get("/Mark/AAPL"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.c").value(150.25))
                .andExpect(jsonPath("$.d").value(2.50))
                .andExpect(jsonPath("$.dp").value(1.69));
    }
    
    @Test
    public void testGetMark_InvalidSymbol() throws Exception {
        when(finhubRepository.getMark("INVALID"))
                .thenThrow(new VendorConnectivityException("Symbol not found"));
        
        mockMvc.perform(get("/Mark/INVALID"))
                .andExpect(status().isServiceUnavailable());
    }
    
    @Test
    public void testGetCandle_Success() throws Exception {
        CandleDto candle = new CandleDto();
        candle.setStatus("ok");
        candle.setOpen(Arrays.asList(
                BigDecimal.valueOf(150.0),
                BigDecimal.valueOf(151.0),
                BigDecimal.valueOf(152.0)
        ));
        candle.setHi(Arrays.asList(
                BigDecimal.valueOf(152.0),
                BigDecimal.valueOf(153.0),
                BigDecimal.valueOf(154.0)
        ));
        candle.setLo(Arrays.asList(
                BigDecimal.valueOf(149.0),
                BigDecimal.valueOf(150.0),
                BigDecimal.valueOf(151.0)
        ));
        candle.setClose(Arrays.asList(
                BigDecimal.valueOf(151.0),
                BigDecimal.valueOf(152.0),
                BigDecimal.valueOf(153.0)
        ));
        candle.setVolume(Arrays.asList(
                BigInteger.valueOf(1000000),
                BigInteger.valueOf(1100000),
                BigInteger.valueOf(1200000)
        ));
        candle.setTimestamp(Arrays.asList(
                BigInteger.valueOf(1704067200L), 
                BigInteger.valueOf(1704153600L), 
                BigInteger.valueOf(1704240000L)
        ));
        
        when(finhubRepository.getCandle(anyString(), any(Date.class), any(Date.class)))
                .thenReturn(candle);
        
        mockMvc.perform(get("/Candle/AAPL")
                .param("from", "2024-01-01")
                .param("to", "2024-01-31"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.s").value("ok"))
                .andExpect(jsonPath("$.o").isArray())
                .andExpect(jsonPath("$.o[0]").value(150.0))
                .andExpect(jsonPath("$.c").isArray())
                .andExpect(jsonPath("$.c[2]").value(153.0));
    }
    
    @Test
    public void testGetCandle_MissingFromParameter() throws Exception {
        mockMvc.perform(get("/Candle/AAPL")
                .param("to", "2024-01-31"))
                .andExpect(status().isBadRequest());
    }
    
    @Test
    public void testGetCandle_MissingToParameter() throws Exception {
        mockMvc.perform(get("/Candle/AAPL")
                .param("from", "2024-01-01"))
                .andExpect(status().isBadRequest());
    }
    
    @Test
    public void testGetCandle_InvalidDateFormat() throws Exception {
        mockMvc.perform(get("/Candle/AAPL")
                .param("from", "01/01/2024")
                .param("to", "2024-01-31"))
                .andExpect(status().is4xxClientError());
    }
    
    @Test
    public void testGetCandle_NoData() throws Exception {
        CandleDto candle = new CandleDto();
        candle.setStatus("no_data");
        
        when(finhubRepository.getCandle(anyString(), any(Date.class), any(Date.class)))
                .thenReturn(candle);
        
        mockMvc.perform(get("/Candle/AAPL")
                .param("from", "2024-01-01")
                .param("to", "2024-01-31"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.s").value("no_data"));
    }
}
