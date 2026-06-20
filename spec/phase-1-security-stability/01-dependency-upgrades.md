# Dependency Upgrades Specification

## Objective
Upgrade all outdated and vulnerable dependencies to latest stable versions, reducing security vulnerabilities and improving performance.

## Current State

### Critical Issues
- **Spring Boot**: 2.4.3 (March 2021) → Currently **3+ years outdated**
- **JUnit**: 4.8.1 (2010) → **14+ years outdated**
- **Spring Test**: 4.0.5 (2014) → **10+ years outdated**
- **Gson**: 2.8.5 (2018) → **6+ years outdated**
- **Java**: 11 → Consider Java 17 or 21 LTS

### Known Risks
- Multiple CVEs in outdated Spring Boot versions
- Security patches not applied
- Performance improvements missed
- Missing modern features

## Target State

### Maven POM Updates

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 
         http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.2.5</version> <!-- Latest stable as of spec date -->
        <relativePath />
    </parent>

    <groupId>com.companyx</groupId>
    <artifactId>equity</artifactId>
    <version>1.0.0</version>

    <properties>
        <java.version>17</java.version>
        <maven.compiler.source>17</maven.compiler.source>
        <maven.compiler.target>17</maven.compiler.target>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
        
        <!-- Dependency versions -->
        <lombok.version>1.18.32</lombok.version>
        <gson.version>2.10.1</gson.version>
        <commons-lang3.version>3.14.0</commons-lang3.version>
        <resilience4j.version>2.1.0</resilience4j.version>
    </properties>

    <dependencies>
        <!-- Spring Boot Starters -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>
        
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-data-jpa</artifactId>
        </dependency>
        
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-webflux</artifactId>
        </dependency>
        
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-actuator</artifactId>
        </dependency>
        
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-validation</artifactId>
        </dependency>

        <!-- Database -->
        <dependency>
            <groupId>com.mysql</groupId>
            <artifactId>mysql-connector-j</artifactId>
            <scope>runtime</scope>
        </dependency>
        
        <dependency>
            <groupId>org.flywaydb</groupId>
            <artifactId>flyway-core</artifactId>
        </dependency>
        
        <dependency>
            <groupId>org.flywaydb</groupId>
            <artifactId>flyway-mysql</artifactId>
        </dependency>

        <!-- Utilities -->
        <dependency>
            <groupId>org.projectlombok</groupId>
            <artifactId>lombok</artifactId>
            <version>${lombok.version}</version>
            <scope>provided</scope>
        </dependency>
        
        <dependency>
            <groupId>org.apache.commons</groupId>
            <artifactId>commons-lang3</artifactId>
            <version>${commons-lang3.version}</version>
        </dependency>
        
        <dependency>
            <groupId>com.google.code.gson</groupId>
            <artifactId>gson</artifactId>
            <version>${gson.version}</version>
        </dependency>

        <!-- Testing -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
        
        <dependency>
            <groupId>io.projectreactor</groupId>
            <artifactId>reactor-test</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
                <configuration>
                    <excludes>
                        <exclude>
                            <groupId>org.projectlombok</groupId>
                            <artifactId>lombok</artifactId>
                        </exclude>
                    </excludes>
                </configuration>
            </plugin>
            
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-compiler-plugin</artifactId>
                <configuration>
                    <source>17</source>
                    <target>17</target>
                    <encoding>UTF-8</encoding>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
```

## Migration Steps

### Step 1: Pre-Migration Analysis
```bash
# Run dependency vulnerability scan
mvn org.owasp:dependency-check-maven:check

# Generate dependency tree
mvn dependency:tree > dependencies-before.txt

# Document current test results
mvn clean test
```

### Step 2: Update Java Version
1. Install Java 17 (or 21 LTS)
2. Update `JAVA_HOME` environment variable
3. Update IDE project settings
4. Update Docker base images

### Step 3: Update Spring Boot to 3.x

#### Breaking Changes to Address

**Package Renames (Jakarta EE)**
```java
// Before (javax.*)
import javax.persistence.*;
import javax.validation.*;
import javax.servlet.*;

// After (jakarta.*)
import jakarta.persistence.*;
import jakarta.validation.*;
import jakarta.servlet.*;
```

**Property Changes**
```properties
# Before
spring.datasource.url=jdbc:mysql://equity-db:3306/equity?autoReconnect=true&useSSL=false

# After
spring.datasource.url=jdbc:mysql://equity-db:3306/equity
spring.datasource.hikari.connection-timeout=30000
```

**Flyway Configuration Changes**
```properties
# Before
flyway.baseline-on-migrate=true

# After
spring.flyway.baseline-on-migrate=true
spring.flyway.locations=classpath:db/migration
```

### Step 4: Update Dependencies Incrementally

1. **Update parent POM to Spring Boot 3.2.5**
2. **Fix compilation errors** (javax → jakarta)
3. **Run tests** - fix any failures
4. **Update test dependencies** (JUnit 4 → 5)
5. **Update other dependencies** one at a time
6. **Run full test suite after each update**

### Step 5: Code Changes Required

#### Update Imports
```java
// TransactionController.java
- import javax.security.auth.login.LoginException;
+ import jakarta.security.auth.login.LoginException;

// Transaction.java, User.java, Position.java, TransactionType.java
- import javax.persistence.*;
+ import jakarta.persistence.*;

// Models with validation
+ import jakarta.validation.constraints.NotNull;
```

#### Update NotNull Annotations
```java
// Before
import com.sun.istack.NotNull;

// After
import jakarta.validation.constraints.NotNull;
```

#### Update Test Framework
```java
// Before (JUnit 4)
import org.junit.Test;
public class EquityTest {
    @Test
    public void testSomething() {
        assert(true);
    }
}

// After (JUnit 5)
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class EquityTest {
    @Test
    void testSomething() {
        assertTrue(true);
    }
}
```

### Step 6: Post-Migration Validation

```bash
# Clean build
mvn clean install

# Run all tests
mvn test

# Run dependency vulnerability scan again
mvn org.owasp:dependency-check-maven:check

# Compare dependency trees
mvn dependency:tree > dependencies-after.txt
diff dependencies-before.txt dependencies-after.txt

# Integration test
mvn verify

# Build Docker image
docker build -t equity-pnl-service:upgraded .

# Run smoke tests
docker-compose up -d
curl http://localhost:8080/actuator/health
```

## Acceptance Criteria

- [ ] All dependencies updated to latest stable versions
- [ ] Zero high or critical CVEs in OWASP dependency scan
- [ ] All unit tests passing
- [ ] All integration tests passing
- [ ] Application starts successfully
- [ ] Actuator endpoints responding
- [ ] No runtime warnings about deprecated APIs
- [ ] Docker image builds successfully
- [ ] Performance benchmarks match or exceed baseline

## Testing Strategy

### Unit Tests
- Run existing test suite
- Verify no regressions

### Integration Tests
- Test all REST endpoints
- Verify database connectivity
- Test Finhub integration

### Smoke Tests
- Application startup
- Health check endpoint
- Database migrations
- Basic PnL calculation

## Rollback Plan

1. Keep backup of current `pom.xml`
2. Tag git repository before changes: `git tag pre-upgrade`
3. If issues arise:
   ```bash
   git checkout pre-upgrade
   mvn clean install
   docker-compose up --build
   ```

## Dependencies

- None (this is the first phase)

## Estimated Effort

- Pre-migration analysis: 0.5 days
- Java & Spring Boot upgrade: 2 days
- Code migration (javax → jakarta): 1 day
- Test migration (JUnit 4 → 5): 1 day
- Testing & validation: 1.5 days
- **Total: 6 days**

## References

- [Spring Boot 3.0 Migration Guide](https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-3.0-Migration-Guide)
- [JUnit 5 Migration Guide](https://junit.org/junit5/docs/current/user-guide/#migrating-from-junit4)
- [OWASP Dependency Check](https://jeremylong.github.io/DependencyCheck/dependency-check-maven/)
- [Java 17 Features](https://openjdk.org/projects/jdk/17/)

## Notes

- Consider updating to Java 21 LTS for longer support window
- Remove obsolete dependencies (reactor-spring is outdated)
- Consolidate duplicate dependencies in POM
- Update Maven plugins to latest versions
