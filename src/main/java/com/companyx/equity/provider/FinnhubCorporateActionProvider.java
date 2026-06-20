package com.companyx.equity.provider;

import com.companyx.equity.dto.FinnhubDividendDto;
import com.companyx.equity.dto.FinnhubSplitDto;
import com.companyx.equity.model.corporateaction.Dividend;
import com.companyx.equity.model.corporateaction.DividendType;
import com.companyx.equity.model.corporateaction.StockSplit;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Finnhub implementation of CorporateActionProvider.
 * Fetches dividends and stock splits from Finnhub API.
 */
@Slf4j
@Component
public class FinnhubCorporateActionProvider implements CorporateActionProvider {
    
    private static final String FINHUB_TOKEN_KEY = "X-Finnhub-Token";
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    
    private final String finhubUrl;
    private final String finhubKey;
    private final ObjectMapper objectMapper;
    
    public FinnhubCorporateActionProvider(
            @Value("${finhub.url}") String finhubUrl,
            @Value("${finhub.key}") String finhubKey
    ) {
        this.finhubUrl = finhubUrl;
        this.finhubKey = finhubKey;
        this.objectMapper = new ObjectMapper();
        log.info("FinnhubCorporateActionProvider initialized");
    }
    
    @Override
    public String getName() {
        return "FINNHUB";
    }
    
    @Override
    @Cacheable(value = "corporate-actions-dividends", key = "#symbol + '-' + #from + '-' + #to")
    @Retry(name = "finhub", fallbackMethod = "getDividendsFallback")
    @CircuitBreaker(name = "finhub", fallbackMethod = "getDividendsFallback")
    public List<Dividend> getDividends(String symbol, LocalDate from, LocalDate to) {
        log.debug("Fetching dividends for {} from {} to {}", symbol, from, to);
        
        try {
            String response = WebClient.create(finhubUrl)
                    .get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/stock/dividend")
                            .queryParam("symbol", symbol)
                            .queryParam("from", from.format(DATE_FORMATTER))
                            .queryParam("to", to.format(DATE_FORMATTER))
                            .build()
                    )
                    .header(FINHUB_TOKEN_KEY, finhubKey)
                    .exchangeToMono(this::verifyStatusCode)
                    .block();
            
            FinnhubDividendDto[] dtos = objectMapper.readValue(response, FinnhubDividendDto[].class);
            
            List<Dividend> dividends = List.of(dtos).stream()
                    .map(this::mapToDividend)
                    .collect(Collectors.toList());
            
            log.debug("Fetched {} dividends for {}", dividends.size(), symbol);
            return dividends;
            
        } catch (Exception e) {
            log.error("Failed to fetch dividends from Finnhub: {}", e.getMessage());
            return Collections.emptyList();
        }
    }
    
    @Override
    @Cacheable(value = "corporate-actions-splits", key = "#symbol + '-' + #from + '-' + #to")
    @Retry(name = "finhub", fallbackMethod = "getStockSplitsFallback")
    @CircuitBreaker(name = "finhub", fallbackMethod = "getStockSplitsFallback")
    public List<StockSplit> getStockSplits(String symbol, LocalDate from, LocalDate to) {
        log.debug("Fetching stock splits for {} from {} to {}", symbol, from, to);
        
        try {
            String response = WebClient.create(finhubUrl)
                    .get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/stock/split")
                            .queryParam("symbol", symbol)
                            .queryParam("from", from.format(DATE_FORMATTER))
                            .queryParam("to", to.format(DATE_FORMATTER))
                            .build()
                    )
                    .header(FINHUB_TOKEN_KEY, finhubKey)
                    .exchangeToMono(this::verifyStatusCode)
                    .block();
            
            FinnhubSplitDto[] dtos = objectMapper.readValue(response, FinnhubSplitDto[].class);
            
            List<StockSplit> splits = List.of(dtos).stream()
                    .map(this::mapToStockSplit)
                    .collect(Collectors.toList());
            
            log.debug("Fetched {} splits for {}", splits.size(), symbol);
            return splits;
            
        } catch (Exception e) {
            log.error("Failed to fetch splits from Finnhub: {}", e.getMessage());
            return Collections.emptyList();
        }
    }
    
    private Dividend mapToDividend(FinnhubDividendDto dto) {
        return Dividend.builder()
                .symbol(dto.getSymbol())
                .exDate(LocalDate.parse(dto.getExDividendDate(), DATE_FORMATTER))
                .payDate(dto.getPayDate() != null ? LocalDate.parse(dto.getPayDate(), DATE_FORMATTER) : null)
                .recordDate(dto.getRecordDate() != null ? LocalDate.parse(dto.getRecordDate(), DATE_FORMATTER) : null)
                .amount(new BigDecimal(dto.getAmount().toString()))
                .currency(dto.getCurrency() != null ? dto.getCurrency() : "USD")
                .type(DividendType.CASH)
                .frequency(dto.getFrequency())
                .build();
    }
    
    private StockSplit mapToStockSplit(FinnhubSplitDto dto) {
        return StockSplit.builder()
                .symbol(dto.getSymbol())
                .date(LocalDate.parse(dto.getDate(), DATE_FORMATTER))
                .fromFactor(dto.getFromFactor())
                .toFactor(dto.getToFactor())
                .build();
    }
    
    private Mono<String> verifyStatusCode(ClientResponse response) {
        if (response.statusCode().equals(HttpStatus.OK)) {
            return response.bodyToMono(String.class);
        } else {
            log.warn("Finnhub API returned non-OK status: {}", response.statusCode());
            return response.createException().flatMap(Mono::error);
        }
    }
    
    private List<Dividend> getDividendsFallback(String symbol, LocalDate from, LocalDate to, Throwable t) {
        log.warn("Returning empty dividend list due to API failure for {}", symbol);
        return Collections.emptyList();
    }
    
    private List<StockSplit> getStockSplitsFallback(String symbol, LocalDate from, LocalDate to, Throwable t) {
        log.warn("Returning empty split list due to API failure for {}", symbol);
        return Collections.emptyList();
    }
}
