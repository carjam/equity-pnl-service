# Configuration Management Specification

## Objective
Externalize all configuration, eliminate hardcoded credentials, and implement environment-specific profiles for dev, staging, and production environments.

## Current State

### Issues
- Hardcoded database credentials in `application.properties`
- Hardcoded credentials in `pom.xml` Flyway configuration
- No environment separation (dev/staging/prod)
- Sensitive data in version control
- No secrets management

## Target State

### Configuration Structure

```
src/main/resources/
├── application.properties              # Common properties
├── application-dev.properties          # Development overrides
├── application-staging.properties      # Staging overrides
├── application-prod.properties         # Production overrides
└── application-test.properties         # Test overrides
```

## Implementation Plan

### Step 1: Create Base Configuration

**File: `src/main/resources/application.properties`**

```properties
# Application
spring.application.name=equity-pnl-service
server.port=8080

# Profiles
spring.profiles.active=${SPRING_PROFILES_ACTIVE:dev}

# Database (defaults, override per environment)
spring.datasource.url=${DATABASE_URL}
spring.datasource.username=${DATABASE_USERNAME}
spring.datasource.password=${DATABASE_PASSWORD}
spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver

# Hikari Connection Pool
spring.datasource.hikari.maximum-pool-size=${DB_POOL_SIZE:20}
spring.datasource.hikari.minimum-idle=${DB_MIN_IDLE:5}
spring.datasource.hikari.connection-timeout=${DB_CONN_TIMEOUT:30000}
spring.datasource.hikari.idle-timeout=${DB_IDLE_TIMEOUT:600000}
spring.datasource.hikari.max-lifetime=${DB_MAX_LIFETIME:1800000}
spring.datasource.hikari.connection-test-query=SELECT 1

# JPA
spring.jpa.show-sql=false
spring.jpa.hibernate.ddl-auto=validate
spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.MySQL8Dialect
spring.jpa.properties.hibernate.format_sql=true
spring.jpa.properties.hibernate.use_sql_comments=false
spring.jpa.open-in-view=false

# Flyway
spring.flyway.enabled=true
spring.flyway.baseline-on-migrate=true
spring.flyway.locations=classpath:db/migration
spring.flyway.validate-on-migrate=true

# External APIs
finhub.url=${FINHUB_URL:https://finnhub.io/api/v1}
finhub.key=${FINHUB_KEY}

# JWT
jwt.secret=${JWT_SECRET}
jwt.expiration=${JWT_EXPIRATION:86400000}

# Actuator
management.endpoints.web.exposure.include=health,info,metrics,prometheus
management.endpoint.health.show-details=when-authorized
management.endpoint.health.probes.enabled=true
management.health.livenessState.enabled=true
management.health.readinessState.enabled=true
management.metrics.export.prometheus.enabled=true

# Logging
logging.level.root=INFO
logging.level.com.companyx.equity=INFO
logging.pattern.console=%d{yyyy-MM-dd HH:mm:ss} - %logger{36} - %msg%n
logging.pattern.file=%d{yyyy-MM-dd HH:mm:ss} [%thread] %-5level %logger{36} - %msg%n

# Server
server.compression.enabled=true
server.compression.mime-types=application/json,application/xml,text/html,text/xml,text/plain
server.error.include-message=never
server.error.include-stacktrace=never
```

### Step 2: Development Environment

**File: `src/main/resources/application-dev.properties`**

```properties
# Development-specific configuration

# Database
spring.datasource.url=jdbc:mysql://localhost:60333/equity?useSSL=false
spring.datasource.hikari.maximum-pool-size=5

# JPA
spring.jpa.show-sql=true
spring.jpa.properties.hibernate.format_sql=true

# Logging
logging.level.root=INFO
logging.level.com.companyx.equity=DEBUG
logging.level.org.springframework.web=DEBUG
logging.level.org.hibernate.SQL=DEBUG
logging.level.org.hibernate.type.descriptor.sql.BasicBinder=TRACE

# Actuator - expose all endpoints in dev
management.endpoints.web.exposure.include=*
management.endpoint.health.show-details=always

# Security - relaxed CORS for local development
spring.security.debug=true

# Server
server.error.include-message=always
server.error.include-stacktrace=on_param
```

### Step 3: Staging Environment

**File: `src/main/resources/application-staging.properties`**

```properties
# Staging-specific configuration

# Database
spring.datasource.url=jdbc:mysql://staging-db.internal:3306/equity?useSSL=true&requireSSL=true
spring.datasource.hikari.maximum-pool-size=15

# JPA
spring.jpa.show-sql=false

# Logging
logging.level.root=INFO
logging.level.com.companyx.equity=INFO
logging.file.name=/var/log/equity-pnl/application.log
logging.file.max-size=10MB
logging.file.max-history=30

# Actuator
management.endpoints.web.exposure.include=health,info,metrics,prometheus
management.endpoint.health.show-details=when-authorized

# Server
server.error.include-message=never
server.error.include-stacktrace=never
```

### Step 4: Production Environment

**File: `src/main/resources/application-prod.properties`**

```properties
# Production-specific configuration

# Database
spring.datasource.url=jdbc:mysql://prod-db.internal:3306/equity?useSSL=true&requireSSL=true&serverTimezone=UTC
spring.datasource.hikari.maximum-pool-size=20
spring.datasource.hikari.leak-detection-threshold=60000

# JPA
spring.jpa.show-sql=false
spring.jpa.properties.hibernate.generate_statistics=false

# Logging
logging.level.root=WARN
logging.level.com.companyx.equity=INFO
logging.file.name=/var/log/equity-pnl/application.log
logging.file.max-size=50MB
logging.file.max-history=90
logging.pattern.file=%d{yyyy-MM-dd HH:mm:ss} [%thread] %-5level [%X{correlationId}] %logger{36} - %msg%n

# Actuator
management.endpoints.web.exposure.include=health,info,metrics,prometheus
management.endpoint.health.show-details=never

# Server
server.error.include-message=never
server.error.include-stacktrace=never
server.tomcat.threads.max=200
server.tomcat.accept-count=100
server.tomcat.connection-timeout=20000

# SSL/TLS
server.ssl.enabled=true
server.ssl.key-store=${SSL_KEYSTORE_PATH}
server.ssl.key-store-password=${SSL_KEYSTORE_PASSWORD}
server.ssl.key-store-type=PKCS12
server.ssl.key-alias=equity-pnl
```

### Step 5: Test Environment

**File: `src/main/resources/application-test.properties`**

```properties
# Test-specific configuration

# Use H2 in-memory database for tests
spring.datasource.url=jdbc:h2:mem:testdb
spring.datasource.driver-class-name=org.h2.Driver
spring.datasource.username=sa
spring.datasource.password=

# JPA
spring.jpa.hibernate.ddl-auto=create-drop
spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect

# Flyway
spring.flyway.enabled=false

# Disable external API calls in tests
finhub.url=http://localhost:${wiremock.server.port}
finhub.key=test-key

# JWT
jwt.secret=testSecretKeyForTestingPurposesOnlyMinimum32Chars
jwt.expiration=3600000

# Logging
logging.level.root=ERROR
logging.level.com.companyx.equity=DEBUG

# Actuator
management.endpoints.web.exposure.include=health
```

### Step 6: Environment Variables Template

**File: `.env.template`**

```bash
# Database Configuration
DATABASE_URL=jdbc:mysql://equity-db:3306/equity
DATABASE_USERNAME=equity_user
DATABASE_PASSWORD=changeme

# Database Pool Settings
DB_POOL_SIZE=20
DB_MIN_IDLE=5
DB_CONN_TIMEOUT=30000
DB_IDLE_TIMEOUT=600000
DB_MAX_LIFETIME=1800000

# External APIs
FINHUB_URL=https://finnhub.io/api/v1
FINHUB_KEY=your_finhub_api_key_here

# JWT Configuration
JWT_SECRET=your_jwt_secret_minimum_256_bits_64_characters
JWT_EXPIRATION=86400000

# Spring Profile
SPRING_PROFILES_ACTIVE=dev

# SSL/TLS (Production only)
SSL_KEYSTORE_PATH=/path/to/keystore.p12
SSL_KEYSTORE_PASSWORD=changeme

# MySQL Configuration (for docker-compose)
MYSQL_ROOT_PASSWORD=root_changeme
MYSQL_DATABASE=equity
MYSQL_USER=equity_user
MYSQL_PASSWORD=changeme
```

### Step 7: Update .gitignore

```gitignore
# Environment files with secrets
.env
.env.local
.env.*.local

# SSL certificates
*.p12
*.jks
*.pem
*.key
*.crt

# IDE
.idea/
*.iml

# Build
target/
*.log

# OS
.DS_Store
```

### Step 8: Docker Configuration

**File: `docker-compose.yml`**

```yaml
version: '3.8'

services:
  equity-db:
    image: mysql:8.0
    restart: always
    environment:
      MYSQL_ROOT_PASSWORD: ${MYSQL_ROOT_PASSWORD}
      MYSQL_DATABASE: ${MYSQL_DATABASE}
      MYSQL_USER: ${MYSQL_USER}
      MYSQL_PASSWORD: ${MYSQL_PASSWORD}
    ports:
      - "60333:3306"
    volumes:
      - equity-db-data:/var/lib/mysql
    networks:
      - equity-network
    healthcheck:
      test: ["CMD", "mysqladmin", "ping", "-h", "localhost"]
      timeout: 20s
      retries: 10

  app:
    build:
      context: .
      dockerfile: Dockerfile
    restart: on-failure
    environment:
      SPRING_PROFILES_ACTIVE: ${SPRING_PROFILES_ACTIVE:-dev}
      DATABASE_URL: jdbc:mysql://equity-db:3306/${MYSQL_DATABASE}
      DATABASE_USERNAME: ${MYSQL_USER}
      DATABASE_PASSWORD: ${MYSQL_PASSWORD}
      FINHUB_KEY: ${FINHUB_KEY}
      FINHUB_URL: ${FINHUB_URL:-https://finnhub.io/api/v1}
      JWT_SECRET: ${JWT_SECRET}
      JWT_EXPIRATION: ${JWT_EXPIRATION:-86400000}
    ports:
      - "8080:8080"
    depends_on:
      equity-db:
        condition: service_healthy
    networks:
      - equity-network
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:8080/actuator/health"]
      interval: 30s
      timeout: 10s
      retries: 3
      start_period: 40s

networks:
  equity-network:
    driver: bridge

volumes:
  equity-db-data:
```

### Step 9: Update pom.xml - Remove Hardcoded Credentials

**File: `pom.xml`**

```xml
<!-- Remove or comment out Flyway plugin configuration with hardcoded credentials -->
<!-- Flyway migrations will run automatically via Spring Boot -->

<!-- If manual Flyway execution needed, use environment variables: -->
<!-- mvn flyway:migrate -Dflyway.url=${DATABASE_URL} -Dflyway.user=${DATABASE_USERNAME} -Dflyway.password=${DATABASE_PASSWORD} -->
```

### Step 10: Configuration Class for Type-Safe Properties

**File: `src/main/java/com/companyx/equity/config/FinhubProperties.java`**

```java
package com.companyx.equity.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotBlank;

@Data
@Validated
@Configuration
@ConfigurationProperties(prefix = "finhub")
public class FinhubProperties {
    
    @NotBlank(message = "Finhub URL is required")
    private String url;
    
    @NotBlank(message = "Finhub API key is required")
    private String key;
}
```

**File: `src/main/java/com/companyx/equity/config/JwtProperties.java`**

```java
package com.companyx.equity.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

@Data
@Validated
@Configuration
@ConfigurationProperties(prefix = "jwt")
public class JwtProperties {
    
    @NotBlank(message = "JWT secret is required")
    private String secret;
    
    @Min(value = 60000, message = "JWT expiration must be at least 1 minute")
    private Long expiration;
}
```

### Step 11: Update Repository to Use Type-Safe Config

**File: `src/main/java/com/companyx/equity/repository/FinhubRepository.java`**

```java
package com.companyx.equity.repository;

import com.companyx.equity.config.FinhubProperties;
// ... other imports

@Slf4j
@Configuration
@EnableRetry
@RequiredArgsConstructor
public class FinhubRepository {
    private final FinhubProperties finhubProperties;
    
    @Retryable(maxAttempts = 3, backoff = @Backoff(delay = 30))
    public MarkDto getMark(String symbol) throws JsonProcessingException {
        final String QUOTE = "/quote";

        Mono<String> result = WebClient.create(finhubProperties.getUrl())
                .get()
                .uri(uriBuilder -> uriBuilder
                    .path(QUOTE)
                    .queryParam("symbol", symbol)
                    .build()
                )
                .headers(httpHeaders -> createHeaders(httpHeaders))
                .exchangeToMono(response -> verifyStatusCode(response));
        
        // ... rest of implementation
    }
    
    private HttpHeaders createHeaders(HttpHeaders httpHeaders) {
        httpHeaders.set("X-Finnhub-Token", finhubProperties.getKey());
        return httpHeaders;
    }
}
```

## Kubernetes ConfigMap & Secret (Future)

**File: `k8s/configmap.yaml`**

```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: equity-pnl-config
data:
  SPRING_PROFILES_ACTIVE: "prod"
  FINHUB_URL: "https://finnhub.io/api/v1"
  DB_POOL_SIZE: "20"
  JWT_EXPIRATION: "86400000"
```

**File: `k8s/secret.yaml`**

```yaml
apiVersion: v1
kind: Secret
metadata:
  name: equity-pnl-secrets
type: Opaque
data:
  DATABASE_URL: <base64-encoded>
  DATABASE_USERNAME: <base64-encoded>
  DATABASE_PASSWORD: <base64-encoded>
  FINHUB_KEY: <base64-encoded>
  JWT_SECRET: <base64-encoded>
```

## Security Best Practices

### 1. Generate Strong JWT Secret

```bash
# Generate 256-bit secret (64 hex characters)
openssl rand -hex 32
```

### 2. Rotate Secrets Regularly

- JWT secret: Every 90 days
- Database password: Every 90 days
- API keys: Per vendor policy

### 3. Use Secrets Manager (Production)

Consider using:
- AWS Secrets Manager
- HashiCorp Vault
- Azure Key Vault
- Google Secret Manager

### 4. Never Commit Secrets

```bash
# Check for accidentally committed secrets
git log -p | grep -i "password\|secret\|key" | head -20

# Use git-secrets to prevent commits
git secrets --install
git secrets --register-aws
```

## Testing Configuration

### Test Profile Activation

```bash
# Test dev profile
SPRING_PROFILES_ACTIVE=dev mvn spring-boot:run

# Test staging profile
SPRING_PROFILES_ACTIVE=staging mvn spring-boot:run

# Test prod profile (with all required env vars)
SPRING_PROFILES_ACTIVE=prod \
DATABASE_URL=... \
DATABASE_USERNAME=... \
DATABASE_PASSWORD=... \
FINHUB_KEY=... \
JWT_SECRET=... \
mvn spring-boot:run
```

### Validate Configuration

```java
@SpringBootTest
class ConfigurationTest {
    
    @Autowired
    private FinhubProperties finhubProperties;
    
    @Autowired
    private JwtProperties jwtProperties;
    
    @Test
    void shouldLoadFinhubConfiguration() {
        assertNotNull(finhubProperties.getUrl());
        assertNotNull(finhubProperties.getKey());
        assertTrue(finhubProperties.getUrl().startsWith("http"));
    }
    
    @Test
    void shouldLoadJwtConfiguration() {
        assertNotNull(jwtProperties.getSecret());
        assertTrue(jwtProperties.getExpiration() > 0);
        assertTrue(jwtProperties.getSecret().length() >= 32);
    }
}
```

## Acceptance Criteria

- [ ] No hardcoded credentials in source code
- [ ] All secrets externalized to environment variables
- [ ] Separate configuration for dev, staging, prod
- [ ] `.env` file in `.gitignore`
- [ ] `.env.template` provided for reference
- [ ] Type-safe configuration properties
- [ ] Configuration validation on startup
- [ ] Docker Compose uses environment variables
- [ ] Flyway configuration removed from pom.xml
- [ ] All configuration tests pass

## Migration Checklist

1. [ ] Create `.env` file from template
2. [ ] Set all required environment variables
3. [ ] Test application startup in dev profile
4. [ ] Remove hardcoded values from properties files
5. [ ] Update docker-compose.yml
6. [ ] Test with Docker Compose
7. [ ] Document environment variables in README
8. [ ] Update deployment documentation

## Dependencies

- None (can run in parallel with other Phase 1 tasks)

## Estimated Effort

- Create configuration files: 1 day
- Update code to use type-safe config: 0.5 days
- Docker & environment setup: 0.5 days
- Testing & validation: 1 day
- **Total: 3 days**

## References

- [Spring Boot Externalized Configuration](https://docs.spring.io/spring-boot/docs/current/reference/html/features.html#features.external-config)
- [12-Factor App Config](https://12factor.net/config)
- [OWASP Secrets Management](https://cheatsheetseries.owasp.org/cheatsheets/Secrets_Management_Cheat_Sheet.html)
