# Circuit Breaker & Resilience Specification

## Objective
Implement circuit breaker pattern and resilience mechanisms for external Finhub API calls to prevent cascading failures and improve system stability.

## Current State

### Issues
- Basic retry logic (`@Retryable`) but no circuit breaker
- No fallback mechanisms when Finhub is down
- Could cause cascading failures if Finhub experiences outages
- No bulkhead pattern for resource isolation
- No timeout configuration for external calls
- Synchronous blocking calls to external API

## Target State

- Resilience4j circuit breaker implemented
- Fallback mechanisms for degraded operation
- Bulkhead pattern for thread pool isolation
- Proper timeout and retry configuration
- Metrics for circuit breaker states
- Graceful degradation when external services fail

## Implementation Plan

### Step 1: Add Dependencies

**File: `pom.xml`**

```xml
<properties>
    <resilience4j.version>2.1.0</resilience4j.version>
</properties>

<dependencies>
    <!-- Resilience4j -->
    <dependency>
        <groupId>io.github.resilience4j</groupId>
        <artifactId>resilience4j-spring-boot3</artifactId>
        <version>${resilience4j.version}</version>
    </dependency>
    
    <dependency>
        <groupId>io.github.resilience4j</groupId>
        <artifactId>resilience4j-circuitbreaker</artifactId>
        <version>${resilience4j.version}</version>
    </dependency>
    
    <dependency>
        <groupId>io.github.resilience4j</groupId>
        <artifactId>resilience4j-retry</artifactId>
        <version>${resilience4j.version}</version>
    </dependency>
    
    <dependency>
        <groupId>io.github.resilience4j</groupId>
        <artifactId>resilience4j-bulkhead</artifactId>
        <version>${resilience4j.version}</version>
    </dependency>
    
    <dependency>
        <groupId>io.github.resilience4j</groupId>
        <artifactId>resilience4j-timelimiter</artifactId>
        <version>${resilience4j.version}</version>
    </dependency>
    
    <dependency>
        <groupId>io.github.resilience4j</groupId>
        <artifactId>resilience4j-micrometer</artifactId>
        <version>${resilience4j.version}</version>
    </dependency>
    
    <!-- Reactor Core (for reactive support) -->
    <dependency>
        <groupId>io.projectreactor</groupId>
        <artifactId>reactor-core</artifactId>
    </dependency>
</dependencies>
```

### Step 2: Configure Resilience4j

**File: `src/main/resources/application.properties`**

```properties
# Resilience4j Circuit Breaker Configuration
resilience4j.circuitbreaker.configs.default.slidingWindowSize=10
resilience4j.circuitbreaker.configs.default.minimumNumberOfCalls=5
resilience4j.circuitbreaker.configs.default.permittedNumberOfCallsInHalfOpenState=3
resilience4j.circuitbreaker.configs.default.automaticTransitionFromOpenToHalfOpenEnabled=true
resilience4j.circuitbreaker.configs.default.waitDurationInOpenState=60s
resilience4j.circuitbreaker.configs.default.failureRateThreshold=50
resilience4j.circuitbreaker.configs.default.slowCallRateThreshold=50
resilience4j.circuitbreaker.configs.default.slowCallDurationThreshold=5s
resilience4j.circuitbreaker.configs.default.recordExceptions=\
  com.companyx.equity.error.VendorConnectivityException,\
  java.io.IOException,\
  java.util.concurrent.TimeoutException

# Finhub-specific circuit breaker
resilience4j.circuitbreaker.instances.finhub.baseConfig=default
resilience4j.circuitbreaker.instances.finhub.slidingWindowSize=20
resilience4j.circuitbreaker.instances.finhub.minimumNumberOfCalls=10
resilience4j.circuitbreaker.instances.finhub.waitDurationInOpenState=30s

# Retry Configuration
resilience4j.retry.configs.default.maxAttempts=3
resilience4j.retry.configs.default.waitDuration=500ms
resilience4j.retry.configs.default.enableExponentialBackoff=true
resilience4j.retry.configs.default.exponentialBackoffMultiplier=2
resilience4j.retry.configs.default.retryExceptions=\
  com.companyx.equity.error.VendorConnectivityException,\
  java.io.IOException
resilience4j.retry.configs.default.ignoreExceptions=\
  com.companyx.equity.error.ResponseVerificationException

# Finhub-specific retry
resilience4j.retry.instances.finhub.baseConfig=default
resilience4j.retry.instances.finhub.maxAttempts=3
resilience4j.retry.instances.finhub.waitDuration=1s

# Bulkhead Configuration (Thread Pool)
resilience4j.bulkhead.configs.default.maxConcurrentCalls=10
resilience4j.bulkhead.configs.default.maxWaitDuration=500ms

resilience4j.thread-pool-bulkhead.configs.default.maxThreadPoolSize=10
resilience4j.thread-pool-bulkhead.configs.default.coreThreadPoolSize=5
resilience4j.thread-pool-bulkhead.configs.default.queueCapacity=100
resilience4j.thread-pool-bulkhead.configs.default.keepAliveDuration=20ms

# Finhub-specific bulkhead
resilience4j.thread-pool-bulkhead.instances.finhub.baseConfig=default
resilience4j.thread-pool-bulkhead.instances.finhub.maxThreadPoolSize=20
resilience4j.thread-pool-bulkhead.instances.finhub.coreThreadPoolSize=10

# Time Limiter Configuration
resilience4j.timelimiter.configs.default.timeoutDuration=10s
resilience4j.timelimiter.configs.default.cancelRunningFuture=true

resilience4j.timelimiter.instances.finhub.timeoutDuration=5s

# Metrics
resilience4j.circuitbreaker.configs.default.registerHealthIndicator=true
resilience4j.circuitbreaker.configs.default.eventConsumerBufferSize=10
management.health.circuitbreakers.enabled=true
management.endpoint.health.show-details=always
```

### Step 3: Create Resilience Configuration Class

**File: `src/main/java/com/companyx/equity/config/ResilienceConfig.java`**

```java
package com.companyx.equity.config;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.core.registry.EntryAddedEvent;
import io.github.resilience4j.core.registry.EntryRemovedEvent;
import io.github.resilience4j.core.registry.EntryReplacedEvent;
import io.github.resilience4j.core.registry.RegistryEventConsumer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
public class ResilienceConfig {

    @Bean
    public RegistryEventConsumer<CircuitBreaker> circuitBreakerEventConsumer() {
        return new RegistryEventConsumer<CircuitBreaker>() {
            @Override
            public void onEntryAddedEvent(EntryAddedEvent<CircuitBreaker> entryAddedEvent) {
                CircuitBreaker circuitBreaker = entryAddedEvent.getAddedEntry();
                circuitBreaker.getEventPublisher()
                        .onStateTransition(event -> 
                            log.warn("Circuit Breaker {} transitioned from {} to {}",
                                    event.getCircuitBreakerName(),
                                    event.getStateTransition().getFromState(),
                                    event.getStateTransition().getToState())
                        )
                        .onError(event -> 
                            log.error("Circuit Breaker {} error: {}",
                                    event.getCircuitBreakerName(),
                                    event.getThrowable().getMessage())
                        )
                        .onSuccess(event -> 
                            log.debug("Circuit Breaker {} success", event.getCircuitBreakerName())
                        );
            }

            @Override
            public void onEntryRemovedEvent(EntryRemovedEvent<CircuitBreaker> entryRemoveEvent) {
                log.info("Circuit Breaker {} removed", entryRemoveEvent.getRemovedEntry().getName());
            }

            @Override
            public void onEntryReplacedEvent(EntryReplacedEvent<CircuitBreaker> entryReplacedEvent) {
                log.info("Circuit Breaker {} replaced", entryReplacedEvent.getNewEntry().getName());
            }
        };
    }
}
```

### Step 4: Update FinhubRepository with Resilience4j

**File: `src/main/java/com/companyx/equity/repository/FinhubRepository.java`**

```java
package com.companyx.equity.repository;

import com.companyx.equity.config.FinhubProperties;
import com.companyx.equity.dto.CandleDto;
import com.companyx.equity.dto.MarkDto;
import com.companyx.equity.error.ResponseVerificationException;
import com.companyx.equity.error.VendorConnectivityException;
import com.companyx.equity.utility.DateUtils;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.github.resilience4j.timelimiter.annotation.TimeLimiter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Repository;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Collections;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Repository
@RequiredArgsConstructor
public class FinhubRepository {

    private final FinhubProperties finhubProperties;
    private final WebClient.Builder webClientBuilder;
    private final ObjectMapper objectMapper;

    private static final String FINHUB_TOKEN_KEY = "X-Finnhub-Token";
    private static final String SYMBOL_KEY = "symbol";
    private static final String RESOLUTION_KEY = "resolution";
    private static final String FROM_KEY = "from";
    private static final String TO_KEY = "to";
    private static final String DAILY = "D";

    @CircuitBreaker(name = "finhub", fallbackMethod = "getMarkFallback")
    @Retry(name = "finhub")
    @Bulkhead(name = "finhub", type = Bulkhead.Type.THREADPOOL)
    @TimeLimiter(name = "finhub")
    public CompletableFuture<MarkDto> getMark(String symbol) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return getMarkSync(symbol);
            } catch (JsonProcessingException e) {
                throw new VendorConnectivityException("Failed to parse Finhub response", e);
            }
        });
    }

    private MarkDto getMarkSync(String symbol) throws JsonProcessingException {
        final String QUOTE = "/quote";

        log.debug("Fetching mark for symbol: {}", symbol);

        Mono<String> result = webClientBuilder
                .baseUrl(finhubProperties.getUrl())
                .build()
                .get()
                .uri(uriBuilder -> uriBuilder
                        .path(QUOTE)
                        .queryParam(SYMBOL_KEY, symbol)
                        .build()
                )
                .headers(this::createHeaders)
                .retrieve()
                .onStatus(
                        status -> status.is4xxClientError() || status.is5xxServerError(),
                        response -> response.bodyToMono(String.class)
                                .flatMap(body -> Mono.error(
                                        new VendorConnectivityException("Finhub API error: " + body)
                                ))
                )
                .bodyToMono(String.class)
                .timeout(Duration.ofSeconds(5))
                .onErrorMap(ex -> new VendorConnectivityException("Failed to reach Finhub", ex));

        String responseBody = result.block();
        JsonNode response = objectMapper.readTree(responseBody);
        return objectMapper.treeToValue(response, MarkDto.class);
    }

    // Fallback method for getMark
    private CompletableFuture<MarkDto> getMarkFallback(String symbol, Exception ex) {
        log.warn("Fallback triggered for getMark({}): {}", symbol, ex.getMessage());
        
        // Return cached or default values
        MarkDto fallback = MarkDto.builder()
                .currentPrice(BigDecimal.ZERO)
                .build();
        
        return CompletableFuture.completedFuture(fallback);
    }

    @CircuitBreaker(name = "finhub", fallbackMethod = "getCandleFallback")
    @Retry(name = "finhub")
    @Bulkhead(name = "finhub", type = Bulkhead.Type.THREADPOOL)
    @TimeLimiter(name = "finhub")
    public CompletableFuture<CandleDto> getCandle(String symbol, LocalDate from, LocalDate to) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return getCandleSync(symbol, from, to);
            } catch (JsonProcessingException e) {
                throw new VendorConnectivityException("Failed to parse Finhub response", e);
            }
        });
    }

    private CandleDto getCandleSync(String symbol, LocalDate from, LocalDate to) 
            throws JsonProcessingException {
        final String CANDLE = "/stock/candle";

        log.debug("Fetching candle for symbol: {} from {} to {}", symbol, from, to);

        Mono<String> result = webClientBuilder
                .baseUrl(finhubProperties.getUrl())
                .build()
                .get()
                .uri(uriBuilder -> uriBuilder
                        .path(CANDLE)
                        .queryParam(SYMBOL_KEY, symbol)
                        .queryParam(RESOLUTION_KEY, DAILY)
                        .queryParam(FROM_KEY, DateUtils.epochFromDate(from))
                        .queryParam(TO_KEY, DateUtils.epochFromDate(to))
                        .build()
                )
                .headers(this::createHeaders)
                .retrieve()
                .onStatus(
                        status -> status.is4xxClientError() || status.is5xxServerError(),
                        response -> response.bodyToMono(String.class)
                                .flatMap(body -> Mono.error(
                                        new VendorConnectivityException("Finhub API error: " + body)
                                ))
                )
                .bodyToMono(String.class)
                .timeout(Duration.ofSeconds(10))
                .onErrorMap(ex -> new VendorConnectivityException("Failed to reach Finhub", ex));

        String responseBody = result.block();
        JsonNode response = objectMapper.readTree(responseBody);
        return objectMapper.treeToValue(response, CandleDto.class);
    }

    // Fallback method for getCandle
    private CompletableFuture<CandleDto> getCandleFallback(
            String symbol, 
            LocalDate from, 
            LocalDate to, 
            Exception ex
    ) {
        log.warn("Fallback triggered for getCandle({}, {}, {}): {}", 
                symbol, from, to, ex.getMessage());
        
        // Return empty candle data
        CandleDto fallback = CandleDto.builder()
                .status("no_data")
                .close(Collections.emptyList())
                .build();
        
        return CompletableFuture.completedFuture(fallback);
    }

    private void createHeaders(HttpHeaders httpHeaders) {
        httpHeaders.set(FINHUB_TOKEN_KEY, finhubProperties.getKey());
    }
}
```

### Step 5: Update Service to Handle Async Calls

**File: `src/main/java/com/companyx/equity/service/PnLService.java`** (relevant excerpt)

```java
// Update calculateUnrealized to handle CompletableFuture
private Map<String, Position> calculateUnrealized(
        Map<String, Position> positions, 
        LocalDate end
) {
    LocalDate today = LocalDate.now();
    
    for (String sym : positions.keySet()) {
        if (sym.equals(CASH)) {
            continue;
        }
        
        log.info("Calculating unrealized for {}", sym);
        
        try {
            BigDecimal price;
            if (!end.isBefore(today)) {
                // Current price
                price = finhubRepository.getMark(sym)
                        .get() // Wait for result
                        .getCurrentPrice();
            } else {
                // Historical price
                CandleDto candle = finhubRepository.getCandle(sym, end, end)
                        .get(); // Wait for result
                        
                if (candle.getClose() == null || candle.getClose().isEmpty()) {
                    log.warn("No price data available for {} on {}", sym, end);
                    price = BigDecimal.ZERO;
                } else {
                    price = candle.getClose().get(0);
                }
            }
            
            Position position = positions.get(sym);
            BigDecimal basis = position.getValue();
            BigInteger quantity = position.getQuantity();
            BigDecimal unrealized = (price.multiply(new BigDecimal(quantity))).add(basis);
            
            position.setUnrealized(unrealized);
            position.setPrice(price);
            positions.put(sym, position);
            
        } catch (Exception e) {
            log.error("Failed to calculate unrealized PnL for {}: {}", sym, e.getMessage());
            // Continue with other positions
        }
    }
    
    return positions;
}
```

### Step 6: Circuit Breaker Health Indicator

**File: `src/main/java/com/companyx/equity/health/CircuitBreakerHealthIndicator.java`**

```java
package com.companyx.equity.health;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CircuitBreakerHealthIndicator implements HealthIndicator {

    private final CircuitBreakerRegistry circuitBreakerRegistry;

    @Override
    public Health health() {
        circuitBreakerRegistry.getAllCircuitBreakers().forEach(cb -> {
            CircuitBreaker.State state = cb.getState();
            if (state == CircuitBreaker.State.OPEN || state == CircuitBreaker.State.FORCED_OPEN) {
                return Health.down()
                        .withDetail("circuitBreaker", cb.getName())
                        .withDetail("state", state)
                        .withDetail("failureRate", cb.getMetrics().getFailureRate())
                        .build();
            }
        });

        return Health.up()
                .withDetail("circuitBreakers", "All healthy")
                .build();
    }
}
```

### Step 7: WebClient Configuration

**File: `src/main/java/com/companyx/equity/config/WebClientConfig.java`**

```java
package com.companyx.equity.config;

import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

@Configuration
public class WebClientConfig {

    @Bean
    public WebClient.Builder webClientBuilder() {
        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 5000)
                .responseTimeout(Duration.ofSeconds(5))
                .doOnConnected(conn -> conn
                        .addHandlerLast(new ReadTimeoutHandler(5, TimeUnit.SECONDS))
                        .addHandlerLast(new WriteTimeoutHandler(5, TimeUnit.SECONDS)));

        return WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(httpClient));
    }
}
```

### Step 8: Monitoring Circuit Breaker Metrics

Circuit breaker metrics are automatically exposed via Actuator:

```bash
# View circuit breaker state
curl http://localhost:8080/actuator/circuitbreakers

# View circuit breaker events
curl http://localhost:8080/actuator/circuitbreakerevents

# View metrics
curl http://localhost:8080/actuator/metrics/resilience4j.circuitbreaker.calls
```

## Testing

### Unit Tests

**File: `src/test/java/com/companyx/equity/repository/FinhubRepositoryTest.java`**

```java
package com.companyx.equity.repository;

import com.companyx.equity.config.FinhubProperties;
import com.companyx.equity.dto.MarkDto;
import com.companyx.equity.error.VendorConnectivityException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.web.reactive.function.client.WebClient;

import java.math.BigDecimal;
import java.util.concurrent.CompletableFuture;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class FinhubRepositoryTest {

    private WireMockServer wireMockServer;
    private FinhubRepository finhubRepository;
    
    @Autowired
    private CircuitBreakerRegistry circuitBreakerRegistry;

    @BeforeEach
    void setUp() {
        wireMockServer = new WireMockServer(8089);
        wireMockServer.start();

        FinhubProperties properties = new FinhubProperties();
        properties.setUrl("http://localhost:8089");
        properties.setKey("test-key");

        finhubRepository = new FinhubRepository(
                properties,
                WebClient.builder(),
                new ObjectMapper()
        );

        // Reset circuit breaker
        circuitBreakerRegistry.getAllCircuitBreakers()
                .forEach(cb -> cb.reset());
    }

    @AfterEach
    void tearDown() {
        wireMockServer.stop();
    }

    @Test
    void shouldGetMarkSuccessfully() throws Exception {
        wireMockServer.stubFor(get(urlPathEqualTo("/quote"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"c\": 150.25}")));

        CompletableFuture<MarkDto> result = finhubRepository.getMark("AAPL");
        MarkDto mark = result.get();

        assertNotNull(mark);
        assertEquals(new BigDecimal("150.25"), mark.getCurrentPrice());
    }

    @Test
    void shouldTriggerCircuitBreakerAfterFailures() {
        // Simulate failures
        wireMockServer.stubFor(get(urlPathEqualTo("/quote"))
                .willReturn(aResponse().withStatus(500)));

        // Make multiple failed calls to open circuit
        for (int i = 0; i < 10; i++) {
            try {
                finhubRepository.getMark("AAPL").get();
            } catch (Exception e) {
                // Expected
            }
        }

        // Verify circuit breaker is open
        var circuitBreaker = circuitBreakerRegistry.circuitBreaker("finhub");
        assertEquals(CircuitBreaker.State.OPEN, circuitBreaker.getState());
    }

    @Test
    void shouldUseFallbackWhenCircuitOpen() throws Exception {
        // Open circuit
        var circuitBreaker = circuitBreakerRegistry.circuitBreaker("finhub");
        circuitBreaker.transitionToOpenState();

        // Call should use fallback
        CompletableFuture<MarkDto> result = finhubRepository.getMark("AAPL");
        MarkDto mark = result.get();

        // Fallback returns zero price
        assertEquals(BigDecimal.ZERO, mark.getCurrentPrice());
    }
}
```

## Acceptance Criteria

- [ ] Resilience4j dependencies added
- [ ] Circuit breaker configured for Finhub calls
- [ ] Retry mechanism configured with exponential backoff
- [ ] Bulkhead (thread pool) configured for resource isolation
- [ ] Timeout configured for external calls
- [ ] Fallback methods implemented
- [ ] Circuit breaker events logged
- [ ] Circuit breaker metrics exposed via Actuator
- [ ] Circuit breaker health indicator added
- [ ] WebClient configured with proper timeouts
- [ ] Unit tests for circuit breaker behavior
- [ ] Circuit breaker opens after threshold failures
- [ ] Fallback provides degraded service
- [ ] Circuit breaker recovers to half-open and closed states

## Performance Impact

### Before
- Single Finhub failure blocks calling thread
- Cascading failures possible
- No resource isolation

### After
- Circuit breaker prevents repeated failures
- Fallback provides immediate response
- Thread pool isolation prevents resource exhaustion
- System remains responsive during Finhub outages

## Dependencies

- Phase 1 complete (Spring Boot 3.x, security, configuration)

## Estimated Effort

- Add dependencies and configuration: 1 day
- Refactor FinhubRepository: 2 days
- Implement fallback mechanisms: 1 day
- Add monitoring and health checks: 0.5 days
- Testing: 1.5 days
- **Total: 6 days**

## References

- [Resilience4j Documentation](https://resilience4j.readme.io/)
- [Circuit Breaker Pattern](https://martinfowler.com/bliki/CircuitBreaker.html)
- [Spring Cloud Circuit Breaker](https://spring.io/projects/spring-cloud-circuitbreaker)
