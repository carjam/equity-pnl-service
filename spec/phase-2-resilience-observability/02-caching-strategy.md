# Caching Strategy Specification

## Objective
Implement multi-level caching strategy to reduce external API calls to Finhub, improve response times, and reduce costs.

## Current State

### Issues
- Every PnL calculation makes fresh Finhub API calls
- Historical prices refetched despite never changing
- No caching of frequently accessed data
- High latency for repeated queries
- API rate limits may be exceeded
- Unnecessary load on external service

## Target State

- Redis cache for distributed caching
- Caffeine for local in-memory cache (L1)
- Redis as L2 cache for shared state
- Historical prices cached permanently
- Current prices cached with short TTL
- User and transaction type data cached
- Cache hit rate >80% for historical data

## Implementation Plan

### Step 1: Add Dependencies

**File: `pom.xml`**

```xml
<dependencies>
    <!-- Spring Data Redis -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-data-redis</artifactId>
    </dependency>
    
    <!-- Lettuce (Redis client) -->
    <dependency>
        <groupId>io.lettuce</groupId>
        <artifactId>lettuce-core</artifactId>
    </dependency>
    
    <!-- Spring Cache -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-cache</artifactId>
    </dependency>
    
    <!-- Caffeine (local cache) -->
    <dependency>
        <groupId>com.github.ben-manes.caffeine</groupId>
        <artifactId>caffeine</artifactId>
    </dependency>
    
    <!-- Cache metrics -->
    <dependency>
        <groupId>io.micrometer</groupId>
        <artifactId>micrometer-core</artifactId>
    </dependency>
</dependencies>
```

### Step 2: Configure Redis

**File: `src/main/resources/application.properties`**

```properties
# Redis Configuration
spring.redis.host=${REDIS_HOST:localhost}
spring.redis.port=${REDIS_PORT:6379}
spring.redis.password=${REDIS_PASSWORD:}
spring.redis.database=0
spring.redis.timeout=2000ms

# Redis Connection Pool (Lettuce)
spring.redis.lettuce.pool.max-active=20
spring.redis.lettuce.pool.max-idle=10
spring.redis.lettuce.pool.min-idle=5
spring.redis.lettuce.pool.max-wait=2000ms

# Redis SSL (production)
spring.redis.ssl=false

# Cache Configuration
spring.cache.type=redis
spring.cache.redis.time-to-live=600000
spring.cache.redis.cache-null-values=false
spring.cache.redis.use-key-prefix=true
spring.cache.redis.key-prefix=equity-pnl:

# Cache names and TTLs
cache.ttl.historical-marks=2592000000
cache.ttl.current-marks=60000
cache.ttl.users=600000
cache.ttl.transaction-types=3600000
```

**File: `src/main/resources/application-prod.properties`**

```properties
# Production Redis with SSL
spring.redis.ssl=true
spring.redis.password=${REDIS_PASSWORD}
```

### Step 3: Multi-Level Cache Configuration

**File: `src/main/java/com/companyx/equity/config/CacheConfig.java`**

```java
package com.companyx.equity.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Slf4j
@Configuration
@EnableCaching
public class CacheConfig {

    @Value("${cache.ttl.historical-marks:2592000000}") // 30 days default
    private long historicalMarksTtl;

    @Value("${cache.ttl.current-marks:60000}") // 1 minute default
    private long currentMarksTtl;

    @Value("${cache.ttl.users:600000}") // 10 minutes default
    private long usersTtl;

    @Value("${cache.ttl.transaction-types:3600000}") // 1 hour default
    private long transactionTypesTtl;

    /**
     * Redis Cache Manager - L2 Cache (Distributed)
     */
    @Primary
    @Bean
    public CacheManager redisCacheManager(RedisConnectionFactory connectionFactory) {
        log.info("Configuring Redis cache manager");

        // Configure ObjectMapper for Redis serialization
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.activateDefaultTyping(
                BasicPolymorphicTypeValidator.builder()
                        .allowIfBaseType(Object.class)
                        .build(),
                ObjectMapper.DefaultTyping.NON_FINAL
        );

        GenericJackson2JsonRedisSerializer serializer = 
                new GenericJackson2JsonRedisSerializer(objectMapper);

        // Default configuration
        RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(10))
                .serializeKeysWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(serializer))
                .disableCachingNullValues();

        // Per-cache configurations with different TTLs
        Map<String, RedisCacheConfiguration> cacheConfigurations = new HashMap<>();
        
        cacheConfigurations.put("historical-marks", 
                defaultConfig.entryTtl(Duration.ofMillis(historicalMarksTtl)));
        
        cacheConfigurations.put("current-marks", 
                defaultConfig.entryTtl(Duration.ofMillis(currentMarksTtl)));
        
        cacheConfigurations.put("users", 
                defaultConfig.entryTtl(Duration.ofMillis(usersTtl)));
        
        cacheConfigurations.put("transaction-types", 
                defaultConfig.entryTtl(Duration.ofMillis(transactionTypesTtl)));

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(defaultConfig)
                .withInitialCacheConfigurations(cacheConfigurations)
                .transactionAware()
                .build();
    }

    /**
     * Caffeine Cache Manager - L1 Cache (Local)
     */
    @Bean("localCacheManager")
    public CacheManager caffeineCacheManager() {
        log.info("Configuring Caffeine local cache manager");

        CaffeineCacheManager cacheManager = new CaffeineCacheManager(
                "users-local",
                "transaction-types-local"
        );

        cacheManager.setCaffeine(Caffeine.newBuilder()
                .maximumSize(1000)
                .expireAfterWrite(5, TimeUnit.MINUTES)
                .recordStats()
                .evictionListener((key, value, cause) -> 
                        log.debug("Local cache eviction: key={}, cause={}", key, cause))
        );

        return cacheManager;
    }
}
```

### Step 4: Create Cache Service for Marks

**File: `src/main/java/com/companyx/equity/service/MarketDataCacheService.java`**

```java
package com.companyx.equity.service;

import com.companyx.equity.dto.CandleDto;
import com.companyx.equity.dto.MarkDto;
import com.companyx.equity.repository.FinhubRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Service
@RequiredArgsConstructor
public class MarketDataCacheService {

    private final FinhubRepository finhubRepository;

    /**
     * Get current mark - cached for 1 minute
     */
    @Cacheable(
            value = "current-marks",
            key = "#symbol",
            unless = "#result == null || #result.currentPrice == null"
    )
    public MarkDto getCurrentMark(String symbol) throws Exception {
        log.info("Cache miss for current mark: {}", symbol);
        CompletableFuture<MarkDto> future = finhubRepository.getMark(symbol);
        return future.get();
    }

    /**
     * Get historical mark - cached for 30 days (effectively permanent)
     */
    @Cacheable(
            value = "historical-marks",
            key = "#symbol + '-' + #date.toString()",
            unless = "#result == null"
    )
    public BigDecimal getHistoricalMark(String symbol, LocalDate date) throws Exception {
        log.info("Cache miss for historical mark: {} on {}", symbol, date);
        CompletableFuture<CandleDto> future = finhubRepository.getCandle(symbol, date, date);
        CandleDto candle = future.get();
        
        if (candle.getClose() == null || candle.getClose().isEmpty()) {
            log.warn("No price data for {} on {}", symbol, date);
            return BigDecimal.ZERO;
        }
        
        return candle.getClose().get(0);
    }

    /**
     * Get candle data for date range - cache individual days
     */
    public CandleDto getCandleData(String symbol, LocalDate from, LocalDate to) throws Exception {
        // For small date ranges, fetch individual cached days
        long daysBetween = java.time.temporal.ChronoUnit.DAYS.between(from, to);
        
        if (daysBetween <= 7) {
            // Use individual day caching for small ranges
            log.debug("Using individual day caching for {}: {} days", symbol, daysBetween);
            // Implementation would aggregate individual day caches
        }
        
        // For larger ranges, fetch directly
        log.info("Fetching candle data directly for {}: {} to {}", symbol, from, to);
        return finhubRepository.getCandle(symbol, from, to).get();
    }
}
```

### Step 5: Update PnLService to Use Cache

**File: `src/main/java/com/companyx/equity/service/PnLService.java`** (relevant methods)

```java
@Service
@RequiredArgsConstructor
public class PnLService {
    // ...
    private final MarketDataCacheService marketDataCacheService;

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
                    // Current price - cached for 1 minute
                    MarkDto mark = marketDataCacheService.getCurrentMark(sym);
                    price = mark.getCurrentPrice();
                } else {
                    // Historical price - cached permanently
                    price = marketDataCacheService.getHistoricalMark(sym, end);
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
            }
        }
        
        return positions;
    }
}
```

### Step 6: Cache User and Transaction Types

**File: `src/main/java/com/companyx/equity/repository/UserRepository.java`**

```java
@Repository
public interface UserRepository extends JpaRepository<User, Integer> {
    
    @Cacheable(value = "users", key = "#uid")
    Optional<User> findByUid(String uid);
}
```

**File: `src/main/java/com/companyx/equity/repository/TransactionTypeRepository.java`**

```java
package com.companyx.equity.repository;

import com.companyx.equity.model.TransactionType;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TransactionTypeRepository extends JpaRepository<TransactionType, Integer> {
    
    @Cacheable(value = "transaction-types", key = "#description")
    Optional<TransactionType> findByDescription(String description);
    
    @Cacheable(value = "transaction-types", key = "'all'")
    List<TransactionType> findAll();
}
```

### Step 7: Cache Monitoring

**File: `src/main/java/com/companyx/equity/config/CacheMetricsConfig.java`**

```java
package com.companyx.equity.config;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.stats.CacheStats;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.cache.CaffeineCacheMetrics;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCache;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import jakarta.annotation.PostConstruct;

@Slf4j
@Configuration
@EnableScheduling
public class CacheMetricsConfig {

    @Autowired
    private CacheManager cacheManager;

    @Autowired(required = false)
    private CacheManager localCacheManager;

    @Autowired
    private MeterRegistry meterRegistry;

    @PostConstruct
    public void registerCacheMetrics() {
        if (localCacheManager != null) {
            localCacheManager.getCacheNames().forEach(cacheName -> {
                var cache = localCacheManager.getCache(cacheName);
                if (cache instanceof CaffeineCache) {
                    Cache<Object, Object> nativeCache = 
                            ((CaffeineCache) cache).getNativeCache();
                    CaffeineCacheMetrics.monitor(meterRegistry, nativeCache, cacheName);
                    log.info("Registered metrics for Caffeine cache: {}", cacheName);
                }
            });
        }
    }

    @Scheduled(fixedRate = 60000) // Log every minute
    public void logCacheStats() {
        if (localCacheManager != null) {
            localCacheManager.getCacheNames().forEach(cacheName -> {
                var cache = localCacheManager.getCache(cacheName);
                if (cache instanceof CaffeineCache) {
                    Cache<Object, Object> nativeCache = 
                            ((CaffeineCache) cache).getNativeCache();
                    CacheStats stats = nativeCache.stats();
                    
                    double hitRate = stats.hitRate() * 100;
                    log.info("Cache [{}] stats - Hit Rate: {:.2f}%, Hits: {}, Misses: {}, Size: {}",
                            cacheName,
                            hitRate,
                            stats.hitCount(),
                            stats.missCount(),
                            nativeCache.estimatedSize());
                }
            });
        }
    }
}
```

### Step 8: Cache Warming (Optional)

**File: `src/main/java/com/companyx/equity/service/CacheWarmingService.java`**

```java
package com.companyx.equity.service;

import com.companyx.equity.model.TransactionType;
import com.companyx.equity.repository.TransactionTypeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class CacheWarmingService {

    private final TransactionTypeRepository transactionTypeRepository;

    @EventListener(ApplicationReadyEvent.class)
    public void warmupCaches() {
        log.info("Starting cache warmup...");
        
        try {
            // Preload transaction types
            List<TransactionType> types = transactionTypeRepository.findAll();
            log.info("Warmed up transaction types cache: {} entries", types.size());
            
            // Add more warmup logic as needed
            
        } catch (Exception e) {
            log.error("Cache warmup failed", e);
        }
        
        log.info("Cache warmup complete");
    }
}
```

### Step 9: Docker Compose with Redis

**File: `docker-compose.yml`**

```yaml
services:
  equity-db:
    # ... existing config ...

  redis:
    image: redis:7-alpine
    restart: always
    ports:
      - "6379:6379"
    volumes:
      - redis-data:/data
    command: redis-server --appendonly yes --requirepass ${REDIS_PASSWORD:-}
    networks:
      - equity-network
    healthcheck:
      test: ["CMD", "redis-cli", "ping"]
      interval: 10s
      timeout: 3s
      retries: 3

  app:
    # ... existing config ...
    environment:
      # ... existing vars ...
      REDIS_HOST: redis
      REDIS_PORT: 6379
      REDIS_PASSWORD: ${REDIS_PASSWORD:-}
    depends_on:
      equity-db:
        condition: service_healthy
      redis:
        condition: service_healthy

volumes:
  equity-db-data:
  redis-data:
```

### Step 10: Cache Eviction API (Admin)

**File: `src/main/java/com/companyx/equity/controller/AdminController.java`**

```java
package com.companyx.equity.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.CacheManager;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Objects;

@Slf4j
@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
public class AdminController {

    private final CacheManager cacheManager;

    @PostMapping("/cache/evict/{cacheName}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<String> evictCache(@PathVariable String cacheName) {
        var cache = cacheManager.getCache(cacheName);
        if (cache != null) {
            cache.clear();
            log.info("Evicted cache: {}", cacheName);
            return ResponseEntity.ok("Cache evicted: " + cacheName);
        }
        return ResponseEntity.notFound().build();
    }

    @PostMapping("/cache/evict-all")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<String> evictAllCaches() {
        cacheManager.getCacheNames().forEach(cacheName -> {
            var cache = cacheManager.getCache(cacheName);
            if (cache != null) {
                cache.clear();
            }
        });
        log.info("Evicted all caches");
        return ResponseEntity.ok("All caches evicted");
    }
}
```

## Testing

**File: `src/test/java/com/companyx/equity/service/CacheTest.java`**

```java
package com.companyx.equity.service;

import com.companyx.equity.dto.MarkDto;
import com.companyx.equity.repository.FinhubRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.cache.CacheManager;

import java.math.BigDecimal;
import java.util.concurrent.CompletableFuture;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@SpringBootTest
class CacheTest {

    @Autowired
    private MarketDataCacheService marketDataCacheService;

    @MockBean
    private FinhubRepository finhubRepository;

    @Autowired
    private CacheManager cacheManager;

    @Test
    void shouldCacheCurrentMarks() throws Exception {
        MarkDto mark = MarkDto.builder()
                .currentPrice(BigDecimal.valueOf(150.00))
                .build();

        when(finhubRepository.getMark(anyString()))
                .thenReturn(CompletableFuture.completedFuture(mark));

        // First call - cache miss
        marketDataCacheService.getCurrentMark("AAPL");
        verify(finhubRepository, times(1)).getMark("AAPL");

        // Second call - cache hit
        marketDataCacheService.getCurrentMark("AAPL");
        verify(finhubRepository, times(1)).getMark("AAPL"); // Still 1 call

        // Clear cache
        cacheManager.getCache("current-marks").clear();

        // Third call - cache miss again
        marketDataCacheService.getCurrentMark("AAPL");
        verify(finhubRepository, times(2)).getMark("AAPL");
    }
}
```

## Monitoring

```bash
# View Redis cache keys
redis-cli KEYS "equity-pnl:*"

# View cache stats via Actuator
curl http://localhost:8080/actuator/metrics/cache.gets
curl http://localhost:8080/actuator/metrics/cache.puts

# View Caffeine stats
curl http://localhost:8080/actuator/metrics/cache.evictions
```

## Acceptance Criteria

- [ ] Redis installed and configured
- [ ] Multi-level caching (Caffeine L1, Redis L2) implemented
- [ ] Historical marks cached with 30-day TTL
- [ ] Current marks cached with 1-minute TTL
- [ ] Users cached with 10-minute TTL
- [ ] Transaction types cached with 1-hour TTL
- [ ] Cache metrics exposed via Actuator
- [ ] Cache hit rate >80% for historical data
- [ ] Cache warming on application startup
- [ ] Admin API for cache eviction
- [ ] Cache tests passing
- [ ] Response times reduced by >50% for cached data

## Performance Impact

### Before Caching
- Every PnL query: 3-5 Finhub API calls
- Average response time: 2000ms
- High API costs

### After Caching
- PnL queries with cache hits: 0 Finhub calls
- Average response time: <200ms (90% faster)
- 80%+ reduction in API calls
- Reduced costs

## Dependencies

- Phase 1 complete
- 01-circuit-breaker-resilience.md (resilience for cache misses)

## Estimated Effort

- Redis setup and configuration: 1 day
- Multi-level cache implementation: 2 days
- Update services to use cache: 1.5 days
- Monitoring and admin API: 0.5 days
- Testing: 1 day
- **Total: 6 days**

## References

- [Spring Cache Abstraction](https://docs.spring.io/spring-framework/reference/integration/cache.html)
- [Spring Data Redis](https://docs.spring.io/spring-data/redis/docs/current/reference/html/)
- [Caffeine Cache](https://github.com/ben-manes/caffeine)
- [Redis Best Practices](https://redis.io/docs/manual/patterns/)
