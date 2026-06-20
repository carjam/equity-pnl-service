package com.companyx.equity.repository;

import com.companyx.equity.dto.CandleDto;
import com.companyx.equity.dto.MarkDto;
import com.companyx.equity.error.ResponseVerificationException;
import com.companyx.equity.error.VendorConnectivityException;
import com.companyx.equity.utility.DateUtils;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Repository;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.util.Date;

@Slf4j
@Repository
public class FinhubRepository {
    private static final String FINHUB_TOKEN_KEY = "X-Finnhub-Token";
    private static final String SYMBOL_KEY = "symbol";
    private static final String RESOLUTION_KEY = "resolution";
    private static final String FROM_KEY = "from";
    private static final String TO_KEY = "to";
    private static final String DAILY = "D";
    
    private final String finhubUrl;
    private final String finhubKey;

    public FinhubRepository(
            @Value("${finhub.url}") String finhubUrl,
            @Value("${finhub.key}") String finhubKey
    ) {
        this.finhubUrl = finhubUrl;
        this.finhubKey = finhubKey;
        log.info("FinhubRepository initialized with URL: {}", finhubUrl);
    }

    @Retry(name = "finhub", fallbackMethod = "getMarkFallback")
    @CircuitBreaker(name = "finhub", fallbackMethod = "getMarkFallback")
    public MarkDto getMark(String symbol) throws JsonProcessingException {
        log.debug("Fetching market quote for symbol: {}", symbol);
        
        Mono<String> result = WebClient.create(finhubUrl)
                .get()
                .uri(uriBuilder -> uriBuilder
                        .path("/quote")
                        .queryParam(SYMBOL_KEY, symbol)
                        .build()
                )
                .headers(this::createHeaders)
                .exchangeToMono(this::verifyStatusCode);

        JsonNode response = verifyResponse(result.block());
        String jsonResponse = new ObjectMapper().writeValueAsString(response);
        MarkDto markDto = new ObjectMapper().readValue(jsonResponse, MarkDto.class);
        
        log.debug("Successfully fetched quote for {}: price={}", symbol, markDto.getCurrentPrice());
        return markDto;
    }

    private MarkDto getMarkFallback(String symbol, Exception e) {
        log.error("Failed to fetch mark for symbol {} after retries and circuit breaker: {}", 
                symbol, e.getMessage());
        throw new VendorConnectivityException(
                String.format("Unable to fetch market data for %s: %s", symbol, e.getMessage()));
    }

    @Retry(name = "finhub", fallbackMethod = "getCandleFallback")
    @CircuitBreaker(name = "finhub", fallbackMethod = "getCandleFallback")
    public CandleDto getCandle(String symbol, Date from, Date to) throws JsonProcessingException {
        log.debug("Fetching candle data for symbol: {} from {} to {}", symbol, from, to);
        
        Mono<String> result = WebClient.create(finhubUrl)
                .get()
                .uri(uriBuilder -> uriBuilder
                        .path("/stock/candle")
                        .queryParam(SYMBOL_KEY, symbol)
                        .queryParam(RESOLUTION_KEY, DAILY)
                        .queryParam(FROM_KEY, DateUtils.epochFromDate(from))
                        .queryParam(TO_KEY, DateUtils.epochFromDate(to))
                        .build()
                )
                .headers(this::createHeaders)
                .exchangeToMono(this::verifyStatusCode);

        JsonNode response = verifyResponse(result.block());
        String jsonResponse = new ObjectMapper().writeValueAsString(response);
        CandleDto candleDto = new ObjectMapper().readValue(jsonResponse, CandleDto.class);
        
        log.debug("Successfully fetched candle data for {}: {} data points", 
                symbol, candleDto.getClose() != null ? candleDto.getClose().size() : 0);
        return candleDto;
    }

    private CandleDto getCandleFallback(String symbol, Date from, Date to, Exception e) {
        log.error("Failed to fetch candle data for symbol {} ({} to {}) after retries and circuit breaker: {}", 
                symbol, from, to, e.getMessage());
        throw new VendorConnectivityException(
                String.format("Unable to fetch historical data for %s: %s", symbol, e.getMessage()));
    }

    private Mono<String> verifyStatusCode(ClientResponse response) {
        if (response.statusCode().equals(HttpStatus.OK)) {
            return response.bodyToMono(String.class);
        } else {
            log.warn("Finhub API returned non-OK status: {}", response.statusCode());
            return response.createException().flatMap(Mono::error);
        }
    }

    private JsonNode verifyResponse(String response) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            JsonFactory factory = mapper.getFactory();
            JsonParser parser = factory.createParser(response);
            return mapper.readTree(parser);
        } catch (IOException | NullPointerException e) {
            log.error("Failed to parse Finhub API response: {}", e.getMessage());
            throw new ResponseVerificationException("Unable to parse external API response: " + e.getMessage());
        }
    }

    private HttpHeaders createHeaders(HttpHeaders httpHeaders) {
        httpHeaders.set(FINHUB_TOKEN_KEY, finhubKey);
        return httpHeaders;
    }
}
