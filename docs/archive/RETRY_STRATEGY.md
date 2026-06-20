# Retry Strategy Documentation

## Overview

The equity-pnl-service implements a comprehensive retry strategy using Resilience4j with exponential backoff to handle transient failures gracefully. This document explains the retry configuration, strategy, and implementation details.

---

## Key Features

### 1. **Exponential Backoff**
- Retries use exponential backoff to avoid overwhelming failing services
- Each retry waits longer than the previous: 1s → 2s → 4s → ...
- Configurable multiplier (default: 2x)
- Maximum wait duration prevents excessive delays

### 2. **Configuration-Driven**
- All retry parameters externalized to `application.properties`
- Environment-specific overrides (dev, prod)
- No hardcoded values in code

### 3. **Service-Specific Policies**
- Different retry strategies for different service types
- External APIs: more aggressive retries (4 attempts)
- Database: lighter retries (2-3 attempts)
- Default fallback for unconfigured services

---

## Retry Configuration

### Configuration Hierarchy

```
application.properties (base)
├── resilience4j.retry.configs.default
├── resilience4j.retry.configs.external-api
├── resilience4j.retry.configs.database
└── resilience4j.retry.instances.finhub (instance-specific)
```

### Environment Variables

| Variable | Default | Dev | Prod | Description |
|----------|---------|-----|------|-------------|
| `RETRY_MAX_ATTEMPTS` | 3 | 2 | 3 | Default max retry attempts |
| `RETRY_WAIT_DURATION` | 500ms | 200ms | 500ms | Initial wait before retry |
| `RETRY_BACKOFF_MULTIPLIER` | 2 | 2 | 2 | Exponential multiplier |
| `RETRY_EXTERNAL_API_MAX_ATTEMPTS` | 4 | 3 | 4 | External API max retries |
| `RETRY_EXTERNAL_API_WAIT_DURATION` | 1s | 500ms | 1s | External API initial wait |
| `RETRY_EXTERNAL_API_MAX_WAIT` | 10s | 5s | 15s | External API max wait |
| `RETRY_FINHUB_MAX_ATTEMPTS` | 4 | 3 | 4 | Finhub-specific max retries |
| `RETRY_FINHUB_WAIT_DURATION` | 1s | 500ms | 1s | Finhub initial wait |
| `RETRY_DB_MAX_ATTEMPTS` | 2 | 1 | 3 | Database max retries |
| `RETRY_DB_WAIT_DURATION` | 100ms | 50ms | 200ms | Database initial wait |

---

## Retry Policies

### 1. External API Retry (Finhub)

**Use Case:** External market data API calls (quote, candle data)

**Configuration:**
```properties
resilience4j.retry.instances.finhub.maxAttempts=4
resilience4j.retry.instances.finhub.waitDuration=1s
resilience4j.retry.instances.finhub.enableExponentialBackoff=true
resilience4j.retry.instances.finhub.exponentialBackoffMultiplier=2
resilience4j.retry.instances.finhub.exponentialMaxWaitDuration=10s
```

**Retry Sequence:**
```
Attempt 1: Immediate
Attempt 2: Wait 1s
Attempt 3: Wait 2s
Attempt 4: Wait 4s
Total time: ~7s (plus request times)
```

**Retryable Exceptions:**
- `WebClientRequestException` (network errors)
- `WebClientResponseException` (HTTP errors)
- `ConnectException` (connection failures)
- `IOException` (I/O errors)
- `TimeoutException` (request timeouts)

**Why 4 attempts?**
- External APIs may have transient issues
- Network glitches are common
- Higher retry count justified for critical market data

### 2. Database Retry

**Use Case:** Transient database connection issues

**Configuration:**
```properties
resilience4j.retry.configs.database.maxAttempts=2
resilience4j.retry.configs.database.waitDuration=100ms
resilience4j.retry.configs.database.enableExponentialBackoff=true
```

**Retry Sequence:**
```
Attempt 1: Immediate
Attempt 2: Wait 100ms
Total time: ~100ms (plus query times)
```

**Retryable Exceptions:**
- `TransientDataAccessException`
- `RecoverableDataAccessException`
- `SQLException`

**Why only 2 attempts?**
- Database should be reliable
- Connection pool handles most transient issues
- Persistent DB issues need immediate attention

### 3. Default Retry

**Use Case:** Fallback for unconfigured services

**Configuration:**
```properties
resilience4j.retry.configs.default.maxAttempts=3
resilience4j.retry.configs.default.waitDuration=500ms
resilience4j.retry.configs.default.enableExponentialBackoff=true
resilience4j.retry.configs.default.exponentialBackoffMultiplier=2
```

**Retry Sequence:**
```
Attempt 1: Immediate
Attempt 2: Wait 500ms
Attempt 3: Wait 1s
Total time: ~1.5s (plus operation times)
```

---

## Implementation Details

### Using Retry Annotations

```java
@Retry(name = "finhub", fallbackMethod = "getMarkFallback")
@CircuitBreaker(name = "finhub", fallbackMethod = "getMarkFallback")
public MarkDto getMark(String symbol) throws JsonProcessingException {
    // API call implementation
}

private MarkDto getMarkFallback(String symbol, Exception e) {
    log.error("Failed to fetch mark for symbol {} after retries: {}", symbol, e.getMessage());
    throw new VendorConnectivityException(
        String.format("Unable to fetch market data for %s: %s", symbol, e.getMessage()));
}
```

**Key Points:**
- `@Retry` enables retry logic with named configuration
- `@CircuitBreaker` adds circuit breaker protection
- `fallbackMethod` must match signature with added `Exception` parameter
- Exceptions thrown in fallback propagate to caller

### Circuit Breaker Integration

Retries work with circuit breakers:
1. **Closed state:** Retries execute normally
2. **Open state:** Requests fail fast, no retries
3. **Half-open state:** Limited retries to test service recovery

---

## Endpoint Retry Strategy

### Endpoints WITH Retry

| Endpoint | Retry Policy | Justification |
|----------|--------------|---------------|
| `FinhubRepository.getMark()` | `finhub` (4 attempts) | External API, critical data |
| `FinhubRepository.getCandle()` | `finhub` (4 attempts) | External API, historical data |

### Endpoints WITHOUT Retry

| Endpoint | Reason |
|----------|--------|
| `TransactionController.*` | Local DB reads, no network |
| `AuthController.login()` | Security: avoid account lockout |
| `AuthController.register()` | Write operation: not idempotent |
| Database writes | Risk of duplicates |

**Why no retries on writes?**
- Risk of duplicate transactions
- Not idempotent by default
- Database should be reliable
- Better to fail fast and let client retry

---

## Monitoring and Observability

### Retry Event Logging

The `ResilienceConfig` class logs all retry events:

```java
retry.getEventPublisher()
    .onRetry(event -> 
        log.warn("Retry '{}' attempt #{} - waiting {}ms before next attempt. Error: {}",
                event.getName(),
                event.getNumberOfRetryAttempts(),
                event.getWaitInterval().toMillis(),
                event.getLastThrowable().getMessage())
    )
```

### Log Levels

- **DEBUG:** Successful retries
- **WARN:** Individual retry attempts
- **ERROR:** Exhausted retries

### Example Logs

```
WARN  Retry 'finhub' attempt #1 - waiting 1000ms before next attempt. Error: Connection timeout
WARN  Retry 'finhub' attempt #2 - waiting 2000ms before next attempt. Error: Connection timeout
ERROR Retry 'finhub' exhausted all 4 attempts. Final error: Connection timeout
```

---

## Testing

### Test Coverage

1. **Exponential backoff verification**
   - Measures actual wait times between retries
   - Validates multiplier configuration

2. **Retry exhaustion**
   - Verifies max attempts respected
   - Confirms fallback invocation

3. **Eventual success**
   - Tests recovery after initial failures
   - Validates correct result returned

4. **Circuit breaker integration**
   - Tests open circuit prevents retries
   - Validates half-open state behavior

### Running Tests

```bash
# Run all retry tests
mvn test -Dtest=FinhubRepositoryRetryTest

# Run specific test
mvn test -Dtest=FinhubRepositoryRetryTest#testExponentialBackoff
```

---

## Best Practices

### ✅ DO

- Use retries for transient network failures
- Configure exponential backoff
- Set reasonable max wait durations
- Log retry attempts for monitoring
- Combine with circuit breakers
- Use fallback methods for graceful degradation

### ❌ DON'T

- Retry write operations (unless idempotent)
- Use fixed delays (causes thundering herd)
- Set excessive retry counts
- Retry authentication failures
- Ignore retry exhaustion errors
- Retry on client errors (4xx)

---

## Troubleshooting

### Issue: Too Many Retries

**Symptom:** Requests taking too long

**Solution:**
```properties
RETRY_FINHUB_MAX_ATTEMPTS=2
RETRY_FINHUB_WAIT_DURATION=500ms
```

### Issue: Not Enough Retries

**Symptom:** Failing on transient issues

**Solution:**
```properties
RETRY_FINHUB_MAX_ATTEMPTS=5
RETRY_EXTERNAL_API_MAX_WAIT=20s
```

### Issue: Retries Not Happening

**Check:**
1. Exception type is in `retryExceptions` list
2. Circuit breaker is not OPEN
3. Resilience4j configuration loaded
4. Annotation on public method

---

## Migration Notes

### From Spring Retry to Resilience4j

**Changes Made:**
1. Removed `@EnableRetry` from `FinhubRepository`
2. Replaced `@Retryable` with `@Retry`
3. Replaced `@Recover` with `fallbackMethod`
4. Removed hardcoded `BACKOFF_DELAY = 30`
5. Added configuration-driven retry parameters

**Benefits:**
- True exponential backoff (Spring Retry used fixed delay)
- Better observability with event listeners
- Circuit breaker integration
- Configuration-driven (no hardcoded values)
- More flexible exception handling

---

## References

- [Resilience4j Retry Documentation](https://resilience4j.readme.io/docs/retry)
- [Exponential Backoff Pattern](https://en.wikipedia.org/wiki/Exponential_backoff)
- [Circuit Breaker Pattern](https://martinfowler.com/bliki/CircuitBreaker.html)
