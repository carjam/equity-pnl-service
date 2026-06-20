# Database Performance & Optimization Specification

## Objective
Optimize database schema, add critical indexes, configure connection pooling, and implement query optimization for production-level performance.

## Current State

### Issues
- No indexes on frequently queried columns
- No connection pool configuration
- `user.uid` not indexed (primary lookup field)
- `transaction(user_id, timestamp)` not indexed (critical for range queries)
- No query performance monitoring
- Using default JPA settings
- No database-level optimizations

## Target State

- Properly indexed tables for all common query patterns
- Configured HikariCP connection pool
- Optimized JPA/Hibernate settings
- Query performance monitoring
- Database-level optimizations
- Support for 1000+ concurrent users

## Implementation Plan

### Step 1: Add Database Indexes

**File: `src/main/resources/db/migration/V1.3__AddPerformanceIndexes.sql`**

```sql
USE equity;

-- Index on user uid (primary lookup field)
ALTER TABLE user 
ADD UNIQUE INDEX idx_uid (uid);

-- Composite index for transaction queries (user + timestamp range)
ALTER TABLE transaction 
ADD INDEX idx_user_timestamp (user_id, timestamp);

-- Index for symbol lookups
ALTER TABLE transaction 
ADD INDEX idx_symbol (symbol);

-- Composite index for user + transaction type queries
ALTER TABLE transaction 
ADD INDEX idx_user_type (user_id, transaction_type_id);

-- Index on transaction_type description
ALTER TABLE transaction_type 
ADD INDEX idx_description (description);

-- Analyze tables to update statistics
ANALYZE TABLE user;
ANALYZE TABLE transaction;
ANALYZE TABLE transaction_type;
```

### Step 2: Optimize Table Definitions

**File: `src/main/resources/db/migration/V1.4__OptimizeTableStructure.sql`**

```sql
USE equity;

-- Optimize user table
ALTER TABLE user
MODIFY COLUMN first_name VARCHAR(50) NULL,
MODIFY COLUMN last_name VARCHAR(50) NULL,
MODIFY COLUMN uid VARCHAR(50) NOT NULL,
ENGINE=InnoDB
ROW_FORMAT=DYNAMIC
DEFAULT CHARSET=utf8mb4
COLLATE=utf8mb4_unicode_ci;

-- Optimize transaction_type table
ALTER TABLE transaction_type
MODIFY COLUMN description VARCHAR(20) NOT NULL,
ENGINE=InnoDB
ROW_FORMAT=DYNAMIC
DEFAULT CHARSET=utf8mb4
COLLATE=utf8mb4_unicode_ci;

-- Optimize transaction table
ALTER TABLE transaction
MODIFY COLUMN symbol VARCHAR(12) NULL,
ENGINE=InnoDB
ROW_FORMAT=DYNAMIC
DEFAULT CHARSET=utf8mb4
COLLATE=utf8mb4_unicode_ci;

-- Add composite index for common query pattern
ALTER TABLE transaction
ADD INDEX idx_user_timestamp_type (user_id, timestamp, transaction_type_id);
```

### Step 3: Configure HikariCP Connection Pool

**File: `src/main/resources/application.properties`**

```properties
# HikariCP Configuration (Production-ready)
spring.datasource.hikari.maximum-pool-size=20
spring.datasource.hikari.minimum-idle=5
spring.datasource.hikari.connection-timeout=30000
spring.datasource.hikari.idle-timeout=600000
spring.datasource.hikari.max-lifetime=1800000
spring.datasource.hikari.connection-test-query=SELECT 1
spring.datasource.hikari.pool-name=EquityPnLHikariPool

# Connection validation
spring.datasource.hikari.validation-timeout=5000
spring.datasource.hikari.leak-detection-threshold=60000

# Statement caching
spring.datasource.hikari.data-source-properties.cachePrepStmts=true
spring.datasource.hikari.data-source-properties.prepStmtCacheSize=250
spring.datasource.hikari.data-source-properties.prepStmtCacheSqlLimit=2048
spring.datasource.hikari.data-source-properties.useServerPrepStmts=true
spring.datasource.hikari.data-source-properties.useLocalSessionState=true
spring.datasource.hikari.data-source-properties.rewriteBatchedStatements=true
spring.datasource.hikari.data-source-properties.cacheResultSetMetadata=true
spring.datasource.hikari.data-source-properties.cacheServerConfiguration=true
spring.datasource.hikari.data-source-properties.elideSetAutoCommits=true
spring.datasource.hikari.data-source-properties.maintainTimeStats=false
```

**File: `src/main/resources/application-dev.properties`**

```properties
# Smaller pool for development
spring.datasource.hikari.maximum-pool-size=5
spring.datasource.hikari.minimum-idle=2
```

**File: `src/main/resources/application-prod.properties`**

```properties
# Larger pool for production
spring.datasource.hikari.maximum-pool-size=30
spring.datasource.hikari.minimum-idle=10
spring.datasource.hikari.leak-detection-threshold=30000
```

### Step 4: Optimize JPA/Hibernate Configuration

**File: `src/main/resources/application.properties`**

```properties
# JPA/Hibernate Optimizations
spring.jpa.open-in-view=false
spring.jpa.properties.hibernate.jdbc.batch_size=20
spring.jpa.properties.hibernate.order_inserts=true
spring.jpa.properties.hibernate.order_updates=true
spring.jpa.properties.hibernate.jdbc.batch_versioned_data=true

# Query optimization
spring.jpa.properties.hibernate.query.in_clause_parameter_padding=true
spring.jpa.properties.hibernate.query.fail_on_pagination_over_collection_fetch=true
spring.jpa.properties.hibernate.query.plan_cache_max_size=2048
spring.jpa.properties.hibernate.query.plan_parameter_metadata_max_size=128

# Second-level cache (optional, consider Redis for distributed cache)
spring.jpa.properties.hibernate.cache.use_second_level_cache=false
spring.jpa.properties.hibernate.cache.use_query_cache=false

# Statistics (enable in staging/dev, disable in prod)
spring.jpa.properties.hibernate.generate_statistics=false

# Connection handling
spring.jpa.properties.hibernate.connection.provider_disables_autocommit=true
spring.jpa.properties.hibernate.connection.autocommit=false
```

### Step 5: Optimize Repository Queries

**File: `src/main/java/com/companyx/equity/repository/TransactionRepository.java`**

```java
package com.companyx.equity.repository;

import com.companyx.equity.model.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import jakarta.persistence.QueryHint;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, Integer> {
    
    @Query("SELECT t FROM Transaction t WHERE t.user.id = :uid AND t.id = :id")
    @QueryHints(@QueryHint(name = "org.hibernate.cacheable", value = "false"))
    Optional<Transaction> findByUidAndId(@Param("uid") int uid, @Param("id") int id);

    @Query("""
        SELECT t FROM Transaction t 
        WHERE t.user.id = :uid 
        AND t.timestamp < :end 
        ORDER BY t.timestamp
    """)
    @QueryHints({
        @QueryHint(name = "org.hibernate.fetchSize", value = "100"),
        @QueryHint(name = "org.hibernate.readOnly", value = "true")
    })
    List<Transaction> findAllBefore(@Param("uid") int uid, @Param("end") LocalDate end);

    @Query("""
        SELECT t FROM Transaction t 
        WHERE t.user.id = :uid 
        AND t.timestamp BETWEEN :start AND :end 
        ORDER BY t.timestamp
    """)
    @QueryHints({
        @QueryHint(name = "org.hibernate.fetchSize", value = "100"),
        @QueryHint(name = "org.hibernate.readOnly", value = "true")
    })
    List<Transaction> findAllBetween(
        @Param("uid") int uid, 
        @Param("start") LocalDate start, 
        @Param("end") LocalDate end
    );
    
    // Count query for pagination support
    @Query("SELECT COUNT(t) FROM Transaction t WHERE t.user.id = :uid")
    long countByUserId(@Param("uid") int uid);
    
    // Optimized query with symbol filter
    @Query("""
        SELECT t FROM Transaction t 
        WHERE t.user.id = :uid 
        AND t.symbol = :symbol 
        AND t.timestamp BETWEEN :start AND :end 
        ORDER BY t.timestamp
    """)
    List<Transaction> findByUserAndSymbolBetween(
        @Param("uid") int uid,
        @Param("symbol") String symbol,
        @Param("start") LocalDate start,
        @Param("end") LocalDate end
    );
}
```

### Step 6: Add Query Performance Monitoring

**File: `src/main/java/com/companyx/equity/config/DatabaseMetricsConfig.java`**

```java
package com.companyx.equity.config;

import com.zaxxer.hikari.HikariDataSource;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.db.PostgreSQLDatabaseMetrics;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import jakarta.annotation.PostConstruct;
import javax.sql.DataSource;

@Slf4j
@Configuration
@EnableScheduling
public class DatabaseMetricsConfig {

    @Autowired
    private DataSource dataSource;

    @Autowired
    private MeterRegistry meterRegistry;

    @PostConstruct
    public void registerMetrics() {
        if (dataSource instanceof HikariDataSource hikariDataSource) {
            hikariDataSource.setMetricRegistry(meterRegistry);
            log.info("HikariCP metrics registered");
        }
    }

    @Scheduled(fixedRate = 60000) // Log every minute
    public void logConnectionPoolStats() {
        if (dataSource instanceof HikariDataSource hikariDataSource) {
            log.debug("Connection Pool Stats - Active: {}, Idle: {}, Waiting: {}, Total: {}",
                    hikariDataSource.getHikariPoolMXBean().getActiveConnections(),
                    hikariDataSource.getHikariPoolMXBean().getIdleConnections(),
                    hikariDataSource.getHikariPoolMXBean().getThreadsAwaitingConnection(),
                    hikariDataSource.getHikariPoolMXBean().getTotalConnections());
        }
    }
}
```

### Step 7: Add Slow Query Logging

**File: `src/main/resources/application-dev.properties`**

```properties
# Hibernate slow query logging (dev only)
spring.jpa.properties.hibernate.session.events.log.LOG_QUERIES_SLOWER_THAN_MS=100
logging.level.org.hibernate.SQL=DEBUG
logging.level.org.hibernate.type.descriptor.sql.BasicBinder=TRACE
logging.level.org.hibernate.stat=DEBUG
```

**File: `src/main/resources/application-staging.properties`**

```properties
# Hibernate slow query logging (staging)
spring.jpa.properties.hibernate.session.events.log.LOG_QUERIES_SLOWER_THAN_MS=200
logging.level.org.hibernate.SQL=INFO
```

### Step 8: Database Connection Health Check

**File: `src/main/java/com/companyx/equity/health/DatabaseHealthIndicator.java`**

```java
package com.companyx.equity.health;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class DatabaseHealthIndicator implements HealthIndicator {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public Health health() {
        try {
            jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            return Health.up()
                    .withDetail("database", "MySQL")
                    .withDetail("status", "Connected")
                    .build();
        } catch (Exception e) {
            log.error("Database health check failed", e);
            return Health.down()
                    .withDetail("database", "MySQL")
                    .withDetail("status", "Disconnected")
                    .withDetail("error", e.getMessage())
                    .build();
        }
    }
}
```

### Step 9: Query Result Caching (Application Level)

**File: `src/main/java/com/companyx/equity/config/CacheConfig.java`**

```java
package com.companyx.equity.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

@Configuration
@EnableCaching
public class CacheConfig {

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager(
                "users",
                "transactionTypes"
        );
        cacheManager.setCaffeine(Caffeine.newBuilder()
                .maximumSize(1000)
                .expireAfterWrite(10, TimeUnit.MINUTES)
                .recordStats());
        return cacheManager;
    }
}
```

**File: `src/main/java/com/companyx/equity/repository/UserRepository.java`**

```java
package com.companyx.equity.repository;

import com.companyx.equity.model.User;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Integer> {
    
    @Cacheable(value = "users", key = "#uid")
    Optional<User> findByUid(String uid);
}
```

### Step 10: Add Database Dependencies

**File: `pom.xml`**

```xml
<!-- Caffeine cache -->
<dependency>
    <groupId>com.github.ben-manes.caffeine</groupId>
    <artifactId>caffeine</artifactId>
</dependency>

<!-- Connection pool metrics -->
<dependency>
    <groupId>io.micrometer</groupId>
    <artifactId>micrometer-core</artifactId>
</dependency>
```

## MySQL Server Configuration

**File: `docker-compose.yml` - MySQL Service**

```yaml
equity-db:
  image: mysql:8.0
  restart: always
  environment:
    MYSQL_ROOT_PASSWORD: ${MYSQL_ROOT_PASSWORD}
    MYSQL_DATABASE: ${MYSQL_DATABASE}
    MYSQL_USER: ${MYSQL_USER}
    MYSQL_PASSWORD: ${MYSQL_PASSWORD}
  command: 
    - --character-set-server=utf8mb4
    - --collation-server=utf8mb4_unicode_ci
    - --max_connections=200
    - --innodb_buffer_pool_size=256M
    - --innodb_log_file_size=64M
    - --innodb_flush_log_at_trx_commit=2
    - --innodb_flush_method=O_DIRECT
    - --query_cache_size=0
    - --query_cache_type=0
    - --slow_query_log=1
    - --slow_query_log_file=/var/log/mysql/slow.log
    - --long_query_time=2
  volumes:
    - equity-db-data:/var/lib/mysql
    - ./logs/mysql:/var/log/mysql
```

## Performance Testing

### Step 11: Create Performance Test

**File: `src/test/java/com/companyx/equity/performance/PerformanceTest.java`**

```java
package com.companyx.equity.performance;

import com.companyx.equity.model.Transaction;
import com.companyx.equity.repository.TransactionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.util.StopWatch;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
class PerformanceTest {

    @Autowired
    private TransactionRepository transactionRepository;

    @Test
    void testTransactionQueryPerformance() {
        StopWatch stopWatch = new StopWatch();
        
        stopWatch.start("Query transactions");
        List<Transaction> transactions = transactionRepository.findAllBetween(
                1,
                LocalDate.of(2020, 1, 1),
                LocalDate.of(2021, 12, 31)
        );
        stopWatch.stop();

        System.out.println("Found " + transactions.size() + " transactions");
        System.out.println(stopWatch.prettyPrint());

        // Assert query completes in reasonable time (adjust based on data size)
        assertTrue(stopWatch.getLastTaskTimeMillis() < 1000, 
                "Query took longer than expected: " + stopWatch.getLastTaskTimeMillis() + "ms");
    }
}
```

### Step 12: Database Explain Plan Analysis

Add query analysis capability:

```java
package com.companyx.equity.utility;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class QueryAnalyzer {

    private final EntityManager entityManager;

    public void explainQuery(String jpql) {
        Query query = entityManager.createQuery(jpql);
        String sql = query.unwrap(org.hibernate.query.Query.class)
                .getQueryString();
        
        List<Object[]> results = entityManager.createNativeQuery(
                "EXPLAIN " + sql
        ).getResultList();

        log.info("Query Plan for: {}", jpql);
        results.forEach(row -> log.info("Plan: {}", (Object) row));
    }
}
```

## Monitoring Queries

### Slow Query Dashboard

Create monitoring for slow queries using actuator:

```properties
# Actuator endpoints
management.endpoint.metrics.enabled=true
management.metrics.distribution.percentiles-histogram.http.server.requests=true
management.metrics.tags.application=equity-pnl-service
```

## Acceptance Criteria

- [ ] All recommended indexes created
- [ ] `user.uid` has unique index
- [ ] `transaction(user_id, timestamp)` composite index added
- [ ] HikariCP connection pool configured
- [ ] Connection pool size optimized for environment
- [ ] JPA/Hibernate optimization settings applied
- [ ] Query hints added to repository methods
- [ ] Database health check implemented
- [ ] Slow query logging enabled in dev/staging
- [ ] Connection pool metrics exposed
- [ ] Query performance tests created
- [ ] All queries complete in <500ms for typical data sets
- [ ] Connection pool saturation never occurs under load

## Performance Benchmarks

### Before Optimization
- PnL query (1 year): ~2000ms
- Transaction list (1 year): ~1500ms
- Connection pool exhaustion at 50 concurrent users

### After Optimization (Target)
- PnL query (1 year): <500ms
- Transaction list (1 year): <300ms
- Support 1000+ concurrent users
- Connection pool utilization <80%
- Zero connection timeouts

## Dependencies

- 01-dependency-upgrades.md (requires Spring Boot 3.x)
- 03-configuration-management.md (for environment-specific settings)

## Estimated Effort

- Create and test migrations: 1 day
- Configure connection pooling: 0.5 days
- Optimize JPA/Hibernate: 0.5 days
- Add monitoring and health checks: 1 day
- Performance testing: 1 day
- **Total: 4 days**

## References

- [HikariCP Configuration](https://github.com/brettwooldridge/HikariCP#configuration-knobs-baby)
- [Hibernate Performance Tuning](https://docs.jboss.org/hibernate/orm/6.0/userguide/html_single/Hibernate_User_Guide.html#performance)
- [MySQL Performance Tuning](https://dev.mysql.com/doc/refman/8.0/en/optimization.html)
- [Effective Indexing Strategies](https://use-the-index-luke.com/)
