# Unit Testing Strategy Specification

## Objective
Establish comprehensive unit test coverage (>70%) for all business logic, controllers, repositories, and utilities using JUnit 5, Mockito, and AssertJ.

## Current State

### Issues
- Only one placeholder test
- 0% code coverage
- No test structure or strategy
- No mocking framework usage
- Critical business logic untested
- No test data builders

## Target State

- 70%+ code coverage
- Unit tests for all services
- Controller tests with MockMvc
- Repository tests with test database
- Comprehensive PnL calculation tests
- Test data builders for complex objects
- Fast test execution (<30 seconds)

## Implementation Plan

### Step 1: Test Dependencies

Already included in Phase 1 dependency upgrade:

```xml
<dependencies>
    <!-- JUnit 5 -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-test</artifactId>
        <scope>test</scope>
    </dependency>
    
    <!-- Mockito (included in spring-boot-starter-test) -->
    <!-- AssertJ (included in spring-boot-starter-test) -->
    
    <!-- Additional test utilities -->
    <dependency>
        <groupId>org.junit.jupiter</groupId>
        <artifactId>junit-jupiter-params</artifactId>
        <scope>test</scope>
    </dependency>
    
    <dependency>
        <groupId>com.h2database</groupId>
        <artifactId>h2</artifactId>
        <scope>test</scope>
    </dependency>
</dependencies>
```

### Step 2: Test Configuration

**File: `src/test/resources/application-test.properties`**

```properties
# Test database
spring.datasource.url=jdbc:h2:mem:testdb;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE
spring.datasource.driver-class-name=org.h2.Driver
spring.datasource.username=sa
spring.datasource.password=

# JPA
spring.jpa.hibernate.ddl-auto=create-drop
spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect
spring.jpa.show-sql=true

# Disable Flyway for tests
spring.flyway.enabled=false

# Mock external services
finhub.url=http://localhost:${wiremock.server.port}
finhub.key=test-key

# JWT
jwt.secret=testSecretKeyForTestingPurposesOnlyMinimum32CharactersLong
jwt.expiration=3600000

# Redis (use embedded or mock)
spring.redis.host=localhost
spring.redis.port=6370

# Disable circuit breaker for unit tests
resilience4j.circuitbreaker.configs.default.registerHealthIndicator=false

# Logging
logging.level.root=ERROR
logging.level.com.companyx.equity=INFO
```

### Step 3: Test Data Builders

**File: `src/test/java/com/companyx/equity/testutil/TestDataBuilder.java`**

```java
package com.companyx.equity.testutil;

import com.companyx.equity.model.Transaction;
import com.companyx.equity.model.TransactionType;
import com.companyx.equity.model.User;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.sql.Timestamp;
import java.time.LocalDate;

public class TestDataBuilder {

    public static User.UserBuilder aUser() {
        return User.builder()
                .id(1)
                .uid("testuser")
                .firstName("Test")
                .lastName("User")
                .password("$2a$10$testpasswordhash")
                .role("ROLE_USER")
                .enabled(true);
    }

    public static TransactionType.TransactionTypeBuilder aTransactionType() {
        return TransactionType.builder()
                .id(1)
                .description("buy");
    }

    public static Transaction.TransactionBuilder aTransaction() {
        return Transaction.builder()
                .id(1)
                .user(aUser().build())
                .transactionType(aTransactionType().build())
                .symbol("AAPL")
                .quantity(BigInteger.valueOf(10))
                .value(BigDecimal.valueOf(1000.00))
                .timestamp(Timestamp.valueOf("2020-01-01 10:00:00"));
    }

    public static Transaction buyTransaction(
            User user, 
            String symbol, 
            int quantity, 
            double value,
            String timestamp
    ) {
        return Transaction.builder()
                .user(user)
                .transactionType(TransactionType.builder()
                        .description(TransactionType.BUY)
                        .build())
                .symbol(symbol)
                .quantity(BigInteger.valueOf(quantity))
                .value(BigDecimal.valueOf(value))
                .timestamp(Timestamp.valueOf(timestamp))
                .build();
    }

    public static Transaction sellTransaction(
            User user,
            String symbol,
            int quantity,
            double value,
            String timestamp
    ) {
        return Transaction.builder()
                .user(user)
                .transactionType(TransactionType.builder()
                        .description(TransactionType.SALE)
                        .build())
                .symbol(symbol)
                .quantity(BigInteger.valueOf(quantity))
                .value(BigDecimal.valueOf(value))
                .timestamp(Timestamp.valueOf(timestamp))
                .build();
    }
}
```

### Step 4: Service Layer Tests

**File: `src/test/java/com/companyx/equity/service/PnLServiceTest.java`**

```java
package com.companyx.equity.service;

import com.companyx.equity.dto.CandleDto;
import com.companyx.equity.dto.MarkDto;
import com.companyx.equity.model.Position;
import com.companyx.equity.model.Transaction;
import com.companyx.equity.model.User;
import com.companyx.equity.repository.FinhubRepository;
import com.companyx.equity.repository.TransactionRepository;
import com.companyx.equity.repository.UserRepository;
import com.companyx.equity.testutil.TestDataBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.security.auth.login.LoginException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("PnL Service Tests")
class PnLServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private FinhubRepository finhubRepository;

    @InjectMocks
    private PnLService pnLService;

    private User testUser;

    @BeforeEach
    void setUp() {
        testUser = TestDataBuilder.aUser().build();
    }

    @Test
    @DisplayName("Should throw LoginException when user not found")
    void shouldThrowLoginExceptionWhenUserNotFound() {
        // Given
        when(userRepository.findByUid(anyString())).thenReturn(Optional.empty());

        // When/Then
        assertThatThrownBy(() -> pnLService.getPositions(
                "unknown", 
                LocalDate.of(2020, 1, 1), 
                LocalDate.of(2020, 12, 31)
        ))
                .isInstanceOf(LoginException.class);
    }

    @Test
    @DisplayName("Should calculate positions correctly for simple buy")
    void shouldCalculatePositionsForSimpleBuy() throws Exception {
        // Given
        when(userRepository.findByUid("testuser")).thenReturn(Optional.of(testUser));
        
        List<Transaction> priorTransactions = Arrays.asList(
                TestDataBuilder.buyTransaction(testUser, "AAPL", 10, 1000.00, "2020-01-01 10:00:00")
        );
        when(transactionRepository.findAllBefore(anyInt(), any()))
                .thenReturn(priorTransactions);
        
        when(transactionRepository.findAllBetween(anyInt(), any(), any()))
                .thenReturn(Arrays.asList());

        MarkDto markDto = MarkDto.builder()
                .currentPrice(BigDecimal.valueOf(150.00))
                .build();
        when(finhubRepository.getMark(anyString()))
                .thenReturn(CompletableFuture.completedFuture(markDto));

        // When
        Map<String, Position> positions = pnLService.getPositions(
                "testuser",
                LocalDate.of(2020, 1, 1),
                LocalDate.of(2020, 12, 31)
        );

        // Then
        assertThat(positions).containsKey("AAPL");
        Position applePosition = positions.get("AAPL");
        
        assertThat(applePosition.getQuantity()).isEqualTo(10);
        assertThat(applePosition.getValue()).isEqualByComparingTo("-1000.00");
        assertThat(applePosition.getPrice()).isEqualByComparingTo("150.00");
        
        // Verify unrealized = (price * quantity) + basis = (150 * 10) + (-1000) = 500
        assertThat(applePosition.getUnrealized()).isEqualByComparingTo("500.00");
    }

    @Test
    @DisplayName("Should calculate realized PnL correctly for buy then sell")
    void shouldCalculateRealizedPnL() throws Exception {
        // Given
        when(userRepository.findByUid("testuser")).thenReturn(Optional.of(testUser));
        
        // Buy 10 shares at $100 each
        List<Transaction> startTransactions = Arrays.asList(
                TestDataBuilder.buyTransaction(testUser, "AAPL", 10, 1000.00, "2020-01-01 10:00:00")
        );
        when(transactionRepository.findAllBefore(anyInt(), any()))
                .thenReturn(startTransactions);
        
        // Sell 5 shares at $150 each within period
        List<Transaction> periodTransactions = Arrays.asList(
                TestDataBuilder.sellTransaction(testUser, "AAPL", 5, 750.00, "2020-06-01 10:00:00")
        );
        when(transactionRepository.findAllBetween(anyInt(), any(), any()))
                .thenReturn(periodTransactions);

        MarkDto markDto = MarkDto.builder()
                .currentPrice(BigDecimal.valueOf(160.00))
                .build();
        when(finhubRepository.getMark(anyString()))
                .thenReturn(CompletableFuture.completedFuture(markDto));

        // When
        Map<String, Position> positions = pnLService.getPositions(
                "testuser",
                LocalDate.of(2020, 1, 2),
                LocalDate.of(2020, 12, 31)
        );

        // Then
        assertThat(positions).containsKey("AAPL");
        Position applePosition = positions.get("AAPL");
        
        // Should have 5 shares remaining
        assertThat(applePosition.getQuantity()).isEqualTo(5);
        
        // Realized PnL = (sold price - bought price) * quantity sold
        // = (150 - 100) * 5 = 250
        assertThat(applePosition.getRealized()).isEqualByComparingTo("250.00");
    }

    @Test
    @DisplayName("Should handle multiple positions correctly")
    void shouldHandleMultiplePositions() throws Exception {
        // Given
        when(userRepository.findByUid("testuser")).thenReturn(Optional.of(testUser));
        
        List<Transaction> transactions = Arrays.asList(
                TestDataBuilder.buyTransaction(testUser, "AAPL", 10, 1000.00, "2020-01-01 10:00:00"),
                TestDataBuilder.buyTransaction(testUser, "GOOGL", 5, 500.00, "2020-01-02 10:00:00")
        );
        when(transactionRepository.findAllBefore(anyInt(), any()))
                .thenReturn(transactions);
        
        when(transactionRepository.findAllBetween(anyInt(), any(), any()))
                .thenReturn(Arrays.asList());

        when(finhubRepository.getMark("AAPL"))
                .thenReturn(CompletableFuture.completedFuture(
                        MarkDto.builder().currentPrice(BigDecimal.valueOf(150.00)).build()
                ));
        when(finhubRepository.getMark("GOOGL"))
                .thenReturn(CompletableFuture.completedFuture(
                        MarkDto.builder().currentPrice(BigDecimal.valueOf(120.00)).build()
                ));

        // When
        Map<String, Position> positions = pnLService.getPositions(
                "testuser",
                LocalDate.of(2020, 1, 1),
                LocalDate.of(2020, 12, 31)
        );

        // Then
        assertThat(positions).hasSize(2);
        assertThat(positions).containsKeys("AAPL", "GOOGL");
        
        assertThat(positions.get("AAPL").getQuantity()).isEqualTo(10);
        assertThat(positions.get("GOOGL").getQuantity()).isEqualTo(5);
    }

    @Test
    @DisplayName("Should handle Finhub failures gracefully")
    void shouldHandleFinhubFailures() throws Exception {
        // Given
        when(userRepository.findByUid("testuser")).thenReturn(Optional.of(testUser));
        
        List<Transaction> transactions = Arrays.asList(
                TestDataBuilder.buyTransaction(testUser, "AAPL", 10, 1000.00, "2020-01-01 10:00:00")
        );
        when(transactionRepository.findAllBefore(anyInt(), any()))
                .thenReturn(transactions);
        
        when(transactionRepository.findAllBetween(anyInt(), any(), any()))
                .thenReturn(Arrays.asList());

        // Finhub fails
        when(finhubRepository.getMark(anyString()))
                .thenReturn(CompletableFuture.failedFuture(
                        new RuntimeException("Finhub unavailable")
                ));

        // When
        Map<String, Position> positions = pnLService.getPositions(
                "testuser",
                LocalDate.of(2020, 1, 1),
                LocalDate.of(2020, 12, 31)
        );

        // Then - should not throw, but position won't have unrealized PnL
        assertThat(positions).containsKey("AAPL");
        Position position = positions.get("AAPL");
        assertThat(position.getQuantity()).isEqualTo(10);
    }
}
```

### Step 5: Controller Tests

**File: `src/test/java/com/companyx/equity/controller/TransactionControllerTest.java`**

```java
package com.companyx.equity.controller;

import com.companyx.equity.model.Position;
import com.companyx.equity.service.PnLService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(TransactionController.class)
@DisplayName("Transaction Controller Tests")
class TransactionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private PnLService pnLService;

    @Test
    @WithMockUser(username = "testuser")
    @DisplayName("GET /api/v1/pnl should return positions")
    void shouldReturnPositions() throws Exception {
        // Given
        Map<String, Position> positions = new HashMap<>();
        when(pnLService.getPositions(anyString(), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(positions);

        // When/Then
        mockMvc.perform(get("/api/v1/pnl")
                        .param("from", "2020-01-01")
                        .param("to", "2020-12-31")
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/json"));
    }

    @Test
    @DisplayName("GET /api/v1/pnl without auth should return 401")
    void shouldReturn401WithoutAuth() throws Exception {
        mockMvc.perform(get("/api/v1/pnl")
                        .param("from", "2020-01-01")
                        .param("to", "2020-12-31"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "testuser")
    @DisplayName("GET /api/v1/pnl with invalid dates should return 400")
    void shouldReturn400ForInvalidDates() throws Exception {
        mockMvc.perform(get("/api/v1/pnl")
                        .param("from", "2020-12-31")
                        .param("to", "2020-01-01")
                        .with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.validationErrors").exists());
    }

    @Test
    @WithMockUser(username = "testuser")
    @DisplayName("GET /api/v1/pnl with missing params should return 400")
    void shouldReturn400ForMissingParams() throws Exception {
        mockMvc.perform(get("/api/v1/pnl")
                        .with(csrf()))
                .andExpect(status().isBadRequest());
    }
}
```

### Step 6: Repository Tests

**File: `src/test/java/com/companyx/equity/repository/TransactionRepositoryTest.java`**

```java
package com.companyx.equity.repository;

import com.companyx.equity.model.Transaction;
import com.companyx.equity.model.TransactionType;
import com.companyx.equity.model.User;
import com.companyx.equity.testutil.TestDataBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

@DataJpaTest
@DisplayName("Transaction Repository Tests")
class TransactionRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private TransactionRepository transactionRepository;

    private User testUser;
    private TransactionType buyType;

    @BeforeEach
    void setUp() {
        testUser = TestDataBuilder.aUser().build();
        entityManager.persist(testUser);

        buyType = TestDataBuilder.aTransactionType()
                .description("buy")
                .build();
        entityManager.persist(buyType);

        entityManager.flush();
    }

    @Test
    @DisplayName("Should find transactions before date")
    void shouldFindTransactionsBefore() {
        // Given
        Transaction t1 = TestDataBuilder.buyTransaction(
                testUser, "AAPL", 10, 1000.00, "2020-01-01 10:00:00"
        );
        t1.setTransactionType(buyType);
        
        Transaction t2 = TestDataBuilder.buyTransaction(
                testUser, "GOOGL", 5, 500.00, "2020-06-01 10:00:00"
        );
        t2.setTransactionType(buyType);

        entityManager.persist(t1);
        entityManager.persist(t2);
        entityManager.flush();

        // When
        List<Transaction> result = transactionRepository.findAllBefore(
                testUser.getId(),
                LocalDate.of(2020, 3, 1)
        );

        // Then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getSymbol()).isEqualTo("AAPL");
    }

    @Test
    @DisplayName("Should find transactions between dates")
    void shouldFindTransactionsBetween() {
        // Given
        Transaction t1 = TestDataBuilder.buyTransaction(
                testUser, "AAPL", 10, 1000.00, "2020-01-01 10:00:00"
        );
        t1.setTransactionType(buyType);
        
        Transaction t2 = TestDataBuilder.buyTransaction(
                testUser, "GOOGL", 5, 500.00, "2020-06-01 10:00:00"
        );
        t2.setTransactionType(buyType);
        
        Transaction t3 = TestDataBuilder.buyTransaction(
                testUser, "MSFT", 7, 700.00, "2020-12-01 10:00:00"
        );
        t3.setTransactionType(buyType);

        entityManager.persist(t1);
        entityManager.persist(t2);
        entityManager.persist(t3);
        entityManager.flush();

        // When
        List<Transaction> result = transactionRepository.findAllBetween(
                testUser.getId(),
                LocalDate.of(2020, 3, 1),
                LocalDate.of(2020, 9, 1)
        );

        // Then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getSymbol()).isEqualTo("GOOGL");
    }
}
```

### Step 7: Parametrized Tests

**File: `src/test/java/com/companyx/equity/utility/DateUtilsTest.java`**

```java
package com.companyx.equity.utility;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.*;

@DisplayName("Date Utils Tests")
class DateUtilsTest {

    @ParameterizedTest(name = "{0} should convert to epoch {1}")
    @CsvSource({
            "2020-01-01, 1577836800",
            "2021-01-01, 1609459200",
            "2022-06-15, 1655251200"
    })
    @DisplayName("Should convert LocalDate to epoch correctly")
    void shouldConvertToEpoch(LocalDate date, long expectedEpoch) {
        // When
        long actual = DateUtils.epochFromDate(date);

        // Then
        assertThat(actual).isEqualTo(expectedEpoch);
    }
}
```

### Step 8: Coverage Configuration

**File: `pom.xml`**

```xml
<build>
    <plugins>
        <!-- JaCoCo for code coverage -->
        <plugin>
            <groupId>org.jacoco</groupId>
            <artifactId>jacoco-maven-plugin</artifactId>
            <version>0.8.11</version>
            <executions>
                <execution>
                    <goals>
                        <goal>prepare-agent</goal>
                    </goals>
                </execution>
                <execution>
                    <id>report</id>
                    <phase>test</phase>
                    <goals>
                        <goal>report</goal>
                    </goals>
                </execution>
                <execution>
                    <id>jacoco-check</id>
                    <goals>
                        <goal>check</goal>
                    </goals>
                    <configuration>
                        <rules>
                            <rule>
                                <element>PACKAGE</element>
                                <limits>
                                    <limit>
                                        <counter>LINE</counter>
                                        <value>COVEREDRATIO</value>
                                        <minimum>0.70</minimum>
                                    </limit>
                                </limits>
                            </rule>
                        </rules>
                    </configuration>
                </execution>
            </executions>
        </plugin>
    </plugins>
</build>
```

### Step 9: Run Tests

```bash
# Run all tests
mvn test

# Run tests with coverage
mvn clean test jacoco:report

# View coverage report
open target/site/jacoco/index.html

# Run specific test
mvn test -Dtest=PnLServiceTest

# Run tests matching pattern
mvn test -Dtest=*ServiceTest
```

## Acceptance Criteria

- [ ] All existing code has >70% test coverage
- [ ] Service layer fully tested with mocks
- [ ] Controller tests use MockMvc
- [ ] Repository tests use H2 test database
- [ ] Test data builders for complex objects
- [ ] Parametrized tests for utilities
- [ ] JaCoCo coverage reports generated
- [ ] All tests pass in <30 seconds
- [ ] CI integration (fail build if coverage <70%)
- [ ] Clear test naming and organization

## Test Organization

```
src/test/java/
└── com/companyx/equity/
    ├── controller/
    │   ├── TransactionControllerTest.java
    │   └── FinhubControllerTest.java
    ├── service/
    │   ├── PnLServiceTest.java
    │   └── MarketDataCacheServiceTest.java
    ├── repository/
    │   ├── TransactionRepositoryTest.java
    │   └── UserRepositoryTest.java
    ├── security/
    │   ├── JwtUtilTest.java
    │   └── JwtAuthenticationFilterTest.java
    ├── validation/
    │   └── ValidationTest.java
    ├── utility/
    │   └── DateUtilsTest.java
    └── testutil/
        └── TestDataBuilder.java
```

## Dependencies

- Phase 1 complete (Spring Boot 3.x, JUnit 5)

## Estimated Effort

- Test infrastructure setup: 0.5 days
- Service layer tests: 2 days
- Controller tests: 1.5 days
- Repository tests: 1 day
- Utility and validation tests: 1 day
- Coverage configuration and CI: 0.5 days
- **Total: 6.5 days**

## References

- [JUnit 5 User Guide](https://junit.org/junit5/docs/current/user-guide/)
- [Mockito Documentation](https://javadoc.io/doc/org.mockito/mockito-core/latest/org/mockito/Mockito.html)
- [AssertJ Documentation](https://assertj.github.io/doc/)
- [Spring Boot Testing](https://docs.spring.io/spring-boot/docs/current/reference/html/features.html#features.testing)
