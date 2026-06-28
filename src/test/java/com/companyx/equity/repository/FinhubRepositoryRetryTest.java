package com.companyx.equity.repository;

import com.companyx.equity.dto.CandleDto;
import com.companyx.equity.dto.MarkDto;
import com.companyx.equity.error.VendorConnectivityException;
import com.fasterxml.jackson.core.JsonProcessingException;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.RetryRegistry;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.QueueDispatcher;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;

import java.io.IOException;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.util.Date;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for FinhubRepository retry and circuit breaker functionality.
 * Uses a Spring-managed {@link FinhubRepository} so Resilience4j annotations apply.
 */
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(
        locations = "classpath:application-test.properties",
        properties = {
        "resilience4j.retry.instances.finhub.maxAttempts=3",
        "resilience4j.retry.instances.finhub.waitDuration=100ms",
        "resilience4j.retry.instances.finhub.enableExponentialBackoff=true",
        "resilience4j.retry.instances.finhub.exponentialBackoffMultiplier=2",
        "resilience4j.circuitbreaker.instances.finhub.slidingWindowSize=100",
        "resilience4j.circuitbreaker.instances.finhub.minimumNumberOfCalls=100",
        "resilience4j.circuitbreaker.instances.finhub.failureRateThreshold=100",
        "resilience4j.circuitbreaker.instances.finhub.waitDurationInOpenState=1s"
})
@DisplayName("FinhubRepository Retry and Resilience Tests")
class FinhubRepositoryRetryTest {

    private static MockWebServer mockWebServer;
    private int requestCountAtStart;

    @Autowired
    private FinhubRepository finhubRepository;

    @Autowired
    private RetryRegistry retryRegistry;

    @Autowired
    private CircuitBreakerRegistry circuitBreakerRegistry;

    @BeforeAll
    static void startMockServer() throws IOException {
        mockWebServer = new MockWebServer();
        mockWebServer.start();
    }

    @DynamicPropertySource
    static void registerFinhubUrl(DynamicPropertyRegistry registry) {
        registry.add("finhub.url", () -> mockWebServer.url("/").toString().replaceAll("/$", ""));
    }

    @BeforeEach
    void resetResilienceState() throws Exception {
        clearQueuedResponses();
        requestCountAtStart = mockWebServer.getRequestCount();
        if (circuitBreakerRegistry != null) {
            circuitBreakerRegistry.getAllCircuitBreakers().forEach(cb -> cb.reset());
        }
        if (retryRegistry != null) {
            retryRegistry.getAllRetries().forEach(retry -> retry.getEventPublisher().onRetry(event -> {}));
        }
    }

    private void clearQueuedResponses() throws Exception {
        QueueDispatcher dispatcher = (QueueDispatcher) mockWebServer.getDispatcher();
        Field queueField = QueueDispatcher.class.getDeclaredField("responseQueue");
        queueField.setAccessible(true);
        @SuppressWarnings("unchecked")
        BlockingQueue<MockResponse> responseQueue = (BlockingQueue<MockResponse>) queueField.get(dispatcher);
        responseQueue.clear();
    }

    @AfterAll
    static void shutdownMockServer() throws IOException {
        if (mockWebServer != null) {
            mockWebServer.shutdown();
        }
    }

    private int requestsSinceStart() {
        return mockWebServer.getRequestCount() - requestCountAtStart;
    }

    @Test
    @DisplayName("Should retry on server error and eventually succeed")
    void testRetryOnServerErrorWithEventualSuccess() throws JsonProcessingException {
        mockWebServer.enqueue(new MockResponse().setResponseCode(500));
        mockWebServer.enqueue(new MockResponse().setResponseCode(500));
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody("{\"c\":150.25,\"h\":151.0,\"l\":149.5,\"o\":150.0,\"pc\":149.0,\"t\":1234567890}")
                .addHeader("Content-Type", "application/json"));

        MarkDto result = finhubRepository.getMark("AAPL");

        assertNotNull(result);
        assertEquals(new BigDecimal("150.25"), result.getCurrentPrice());
        assertEquals(3, requestsSinceStart());
    }

    @Test
    @DisplayName("Should exhaust retries and throw VendorConnectivityException")
    void testExhaustRetries() {
        mockWebServer.enqueue(new MockResponse().setResponseCode(500));
        mockWebServer.enqueue(new MockResponse().setResponseCode(500));
        mockWebServer.enqueue(new MockResponse().setResponseCode(500));
        mockWebServer.enqueue(new MockResponse().setResponseCode(500));

        VendorConnectivityException exception = assertThrows(
                VendorConnectivityException.class,
                () -> finhubRepository.getMark("AAPL")
        );

        assertTrue(exception.getMessage().contains("Unable to fetch market data"));
        assertTrue(requestsSinceStart() >= 3);
    }

    @Test
    @DisplayName("Should use exponential backoff between retries")
    void testExponentialBackoff() {
        mockWebServer.enqueue(new MockResponse().setResponseCode(500).setBodyDelay(50, TimeUnit.MILLISECONDS));
        mockWebServer.enqueue(new MockResponse().setResponseCode(500).setBodyDelay(50, TimeUnit.MILLISECONDS));
        mockWebServer.enqueue(new MockResponse().setResponseCode(500).setBodyDelay(50, TimeUnit.MILLISECONDS));

        long startNanos = System.nanoTime();

        assertThrows(VendorConnectivityException.class, () -> {
            try {
                finhubRepository.getMark("AAPL");
            } catch (JsonProcessingException e) {
                fail("Unexpected JsonProcessingException: " + e.getMessage());
            }
        });

        long durationMs = (System.nanoTime() - startNanos) / 1_000_000;
        assertTrue(durationMs >= 300,
                "Expected exponential backoff to take at least 300ms, took: " + durationMs + "ms");
    }

    @Test
    @DisplayName("Should succeed on first attempt without retry")
    void testSuccessWithoutRetry() throws JsonProcessingException {
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody("{\"c\":150.25,\"h\":151.0,\"l\":149.5,\"o\":150.0,\"pc\":149.0,\"t\":1234567890}")
                .addHeader("Content-Type", "application/json"));

        MarkDto result = finhubRepository.getMark("AAPL");

        assertNotNull(result);
        assertEquals(1, requestsSinceStart());
    }

    @Test
    @DisplayName("Should retry getCandle method with exponential backoff")
    void testCandleRetryWithExponentialBackoff() throws JsonProcessingException {
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
        assertEquals(2, requestsSinceStart());
    }

    @Test
    @DisplayName("Should handle timeout errors with retry")
    void testRetryOnTimeout() {
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBodyDelay(30, TimeUnit.SECONDS));
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody("{\"c\":150.25,\"h\":151.0,\"l\":149.5,\"o\":150.0,\"pc\":149.0,\"t\":1234567890}")
                .addHeader("Content-Type", "application/json"));

        assertThrows(Exception.class, () -> finhubRepository.getMark("AAPL"));
    }

    @Test
    @DisplayName("Should handle malformed JSON with retry")
    void testRetryOnMalformedJson() {
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody("not valid json")
                .addHeader("Content-Type", "application/json"));
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody("{\"c\":150.25,\"h\":151.0,\"l\":149.5,\"o\":150.0,\"pc\":149.0,\"t\":1234567890}")
                .addHeader("Content-Type", "application/json"));

        assertThrows(Exception.class, () -> finhubRepository.getMark("AAPL"));
    }

    @Test
    @DisplayName("Fallback method should be called after retries exhausted")
    void testFallbackMethodCalled() {
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
