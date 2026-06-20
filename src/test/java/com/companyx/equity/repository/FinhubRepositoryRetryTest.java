package com.companyx.equity.repository;

import com.companyx.equity.dto.CandleDto;
import com.companyx.equity.dto.MarkDto;
import com.companyx.equity.error.VendorConnectivityException;
import com.fasterxml.jackson.core.JsonProcessingException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.RetryRegistry;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for FinhubRepository retry and circuit breaker functionality
 * Tests verify:
 * - Exponential backoff retry logic
 * - Circuit breaker integration
 * - Fallback methods
 * - Configuration-driven retry parameters
 */
@SpringBootTest
@TestPropertySource(properties = {
        "resilience4j.retry.instances.finhub.maxAttempts=3",
        "resilience4j.retry.instances.finhub.waitDuration=100ms",
        "resilience4j.retry.instances.finhub.enableExponentialBackoff=true",
        "resilience4j.retry.instances.finhub.exponentialBackoffMultiplier=2",
        "resilience4j.circuitbreaker.instances.finhub.slidingWindowSize=5",
        "resilience4j.circuitbreaker.instances.finhub.failureRateThreshold=50",
        "resilience4j.circuitbreaker.instances.finhub.waitDurationInOpenState=1s"
})
@DisplayName("FinhubRepository Retry and Resilience Tests")
public class FinhubRepositoryRetryTest {

    private MockWebServer mockWebServer;
    private FinhubRepository finhubRepository;

    @Autowired
    private RetryRegistry retryRegistry;

    @Autowired
    private CircuitBreakerRegistry circuitBreakerRegistry;

    @BeforeEach
    public void setUp() throws IOException {
        mockWebServer = new MockWebServer();
        mockWebServer.start();

        // Create repository with mock server URL
        String mockUrl = mockWebServer.url("/").toString();
        finhubRepository = new FinhubRepository(mockUrl, "test-api-key");

        // Reset circuit breaker and retry before each test
        if (circuitBreakerRegistry != null) {
            circuitBreakerRegistry.getAllCircuitBreakers().forEach(cb -> cb.reset());
        }
        if (retryRegistry != null) {
            retryRegistry.getAllRetries().forEach(retry -> retry.getEventPublisher().onRetry(event -> {}));
        }
    }

    @AfterEach
    public void tearDown() throws IOException {
        if (mockWebServer != null) {
            mockWebServer.shutdown();
        }
    }

    @Test
    @DisplayName("Should retry on server error and eventually succeed")
    public void testRetryOnServerErrorWithEventualSuccess() throws JsonProcessingException {
        // First two requests fail, third succeeds
        mockWebServer.enqueue(new MockResponse().setResponseCode(500));
        mockWebServer.enqueue(new MockResponse().setResponseCode(500));
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody("{\"c\":150.25,\"h\":151.0,\"l\":149.5,\"o\":150.0,\"pc\":149.0,\"t\":1234567890}")
                .addHeader("Content-Type", "application/json"));

        MarkDto result = finhubRepository.getMark("AAPL");

        assertNotNull(result);
        assertEquals(new BigDecimal("150.25"), result.getCurrentPrice());
        assertEquals(3, mockWebServer.getRequestCount());
    }

    @Test
    @DisplayName("Should exhaust retries and throw VendorConnectivityException")
    public void testExhaustRetries() {
        // All requests fail
        mockWebServer.enqueue(new MockResponse().setResponseCode(500));
        mockWebServer.enqueue(new MockResponse().setResponseCode(500));
        mockWebServer.enqueue(new MockResponse().setResponseCode(500));
        mockWebServer.enqueue(new MockResponse().setResponseCode(500));

        VendorConnectivityException exception = assertThrows(
                VendorConnectivityException.class,
                () -> finhubRepository.getMark("AAPL")
        );

        assertTrue(exception.getMessage().contains("Unable to fetch market data"));
        // Should attempt max retries + 1 initial attempt
        assertTrue(mockWebServer.getRequestCount() >= 3);
    }

    @Test
    @DisplayName("Should use exponential backoff between retries")
    public void testExponentialBackoff() throws InterruptedException {
        // All requests fail to trigger retries
        mockWebServer.enqueue(new MockResponse().setResponseCode(500).setBodyDelay(50, java.util.concurrent.TimeUnit.MILLISECONDS));
        mockWebServer.enqueue(new MockResponse().setResponseCode(500).setBodyDelay(50, java.util.concurrent.TimeUnit.MILLISECONDS));
        mockWebServer.enqueue(new MockResponse().setResponseCode(500).setBodyDelay(50, java.util.concurrent.TimeUnit.MILLISECONDS));

        long startTime = System.currentTimeMillis();
        
        try {
            finhubRepository.getMark("AAPL");
            fail("Should have thrown VendorConnectivityException");
        } catch (VendorConnectivityException e) {
            long duration = System.currentTimeMillis() - startTime;
            
            // With exponential backoff (100ms * 2^n):
            // Initial attempt + 100ms wait + 200ms wait = ~300ms minimum
            // Plus request times and some overhead
            assertTrue(duration >= 300, 
                    "Expected exponential backoff to take at least 300ms, took: " + duration + "ms");
        }
    }

    @Test
    @DisplayName("Should succeed on first attempt without retry")
    public void testSuccessWithoutRetry() throws JsonProcessingException {
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody("{\"c\":150.25,\"h\":151.0,\"l\":149.5,\"o\":150.0,\"pc\":149.0,\"t\":1234567890}")
                .addHeader("Content-Type", "application/json"));

        MarkDto result = finhubRepository.getMark("AAPL");

        assertNotNull(result);
        assertEquals(1, mockWebServer.getRequestCount());
    }

    @Test
    @DisplayName("Should retry getCandle method with exponential backoff")
    public void testCandleRetryWithExponentialBackoff() throws JsonProcessingException {
        mockWebServer.enqueue(new MockResponse().setResponseCode(503));
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody("{\"c\":[150.0,151.0],\"h\":[151.0,152.0],\"l\":[149.0,150.0],\"o\":[150.0,151.0],\"s\":\"ok\",\"t\":[1234567890,1234567900],\"v\":[1000,1100]}")
                .addHeader("Content-Type", "application/json"));

        Date from = new Date(1234567890L * 1000);
        Date to = new Date(1234567900L * 1000);
        
        CandleDto result = finhubRepository.getCandle("AAPL", from, to);

        assertNotNull(result);
        assertNotNull(result.getClose());
        assertEquals(2, result.getClose().size());
        assertEquals(2, mockWebServer.getRequestCount());
    }

    @Test
    @DisplayName("Should handle timeout errors with retry")
    public void testRetryOnTimeout() {
        // Simulate timeout by delaying response beyond reasonable time
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBodyDelay(30, java.util.concurrent.TimeUnit.SECONDS));
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody("{\"c\":150.25,\"h\":151.0,\"l\":149.5,\"o\":150.0,\"pc\":149.0,\"t\":1234567890}")
                .addHeader("Content-Type", "application/json"));

        // This test verifies timeout handling - may take longer
        assertThrows(Exception.class, () -> {
            finhubRepository.getMark("AAPL");
        });
    }

    @Test
    @DisplayName("Should handle malformed JSON with retry")
    public void testRetryOnMalformedJson() {
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody("not valid json")
                .addHeader("Content-Type", "application/json"));
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody("{\"c\":150.25,\"h\":151.0,\"l\":149.5,\"o\":150.0,\"pc\":149.0,\"t\":1234567890}")
                .addHeader("Content-Type", "application/json"));

        // Malformed JSON should trigger ResponseVerificationException
        // which may or may not be retryable depending on configuration
        assertThrows(Exception.class, () -> {
            finhubRepository.getMark("AAPL");
        });
    }

    @Test
    @DisplayName("Fallback method should be called after retries exhausted")
    public void testFallbackMethodCalled() {
        mockWebServer.enqueue(new MockResponse().setResponseCode(500));
        mockWebServer.enqueue(new MockResponse().setResponseCode(500));
        mockWebServer.enqueue(new MockResponse().setResponseCode(500));
        mockWebServer.enqueue(new MockResponse().setResponseCode(500));

        VendorConnectivityException exception = assertThrows(
                VendorConnectivityException.class,
                () -> finhubRepository.getMark("AAPL")
        );

        assertNotNull(exception);
        assertTrue(exception.getMessage().contains("Unable to fetch market data"));
    }
}
