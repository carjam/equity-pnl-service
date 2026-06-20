# Running Tests - Quick Start Guide

## Prerequisites

1. **Java 21** installed (Temurin, Oracle, etc.)
2. **Maven 3.6+** installed (or use IDE's built-in Maven)
3. No database required (tests use H2 in-memory)

If you see `release version 21 not supported`, the build is using an older JDK. Point `JAVA_HOME` at JDK 21 and use `.\mvnw.cmd` (not a system `mvn` tied to Java 17). In Cursor/VS Code: **Java: Configure Java Runtime** → select **JavaSE-21**.

## Running the Application (dev + Swagger)

**PowerShell (recommended on Windows):**
```powershell
$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-21.0.7.6-hotspot"
.\mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=dev
```

**Git Bash:** use Unix-style `JAVA_HOME` and `./mvnw` (not `.\mvnw.cmd`). If you see `cannot execute: required file not found`, run `git checkout mvnw` to refresh LF line endings, or use `mvnw.cmd` from PowerShell instead.

```bash
export JAVA_HOME="/c/Program Files/Eclipse Adoptium/jdk-21.0.7.6-hotspot"
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

Swagger UI: http://localhost:8080/swagger-ui.html

## Running All Tests

```bash
# From project root
mvn test

# With detailed output
mvn test -X

# Skip tests (if needed)
mvn install -DskipTests
```

## Running Specific Tests

```bash
# Run single test class
mvn test -Dtest=PnLServiceTest

# Run specific test method
mvn test -Dtest=PnLServiceTest#testSimpleLongPosition_BuyAndSellForProfit

# Run corporate action tests
mvn test -Dtest=RealWorldCorporateActionsPnLEndToEndTest,CorporateActionsPnLEndToEndTest,FixtureCorporateActionProviderTest

# Run tests matching pattern
mvn test -Dtest=*ServiceTest
```

## Running Tests in IDE

### IntelliJ IDEA
1. Right-click on `src/test/java` folder
2. Select "Run 'All Tests'"
3. Or right-click individual test class/method and select "Run"

### Eclipse
1. Right-click on project
2. Select "Run As" > "JUnit Test"

### VS Code
1. Install "Java Test Runner" extension
2. Click play button next to test class/method

## Test Coverage Report

```bash
# Generate coverage report (requires jacoco plugin)
mvn clean test jacoco:report

# View report
open target/site/jacoco/index.html  # Mac
start target/site/jacoco/index.html # Windows
```

### Adding JaCoCo to pom.xml

If not already present, add to `pom.xml`:

```xml
<build>
    <plugins>
        <plugin>
            <groupId>org.jacoco</groupId>
            <artifactId>jacoco-maven-plugin</artifactId>
            <version>0.8.10</version>
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
            </executions>
        </plugin>
    </plugins>
</build>
```

## Understanding Test Results

### Successful Run
```
[INFO] Tests run: 257, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

### Failed Test Example
```
[ERROR] testSimpleLongPosition_BuyAndSellForProfit  Time elapsed: 0.123 s  <<< FAILURE!
java.lang.AssertionError: expected: <1000.0> but was: <900.0>
```

## Test Organization

```
src/test/java/com/companyx/equity/
├── TestDataBuilder.java          # Shared test utilities
├── IntegrationTest.java          # Full E2E tests
├── controller/                   # Controller tests (MockMvc)
│   ├── AuthControllerTest
│   ├── CorporateActionControllerTest
│   ├── FinhubControllerTest
│   └── TransactionControllerTest
├── dto/                          # DTO validation tests
│   ├── AuthDtoTest
│   └── PnLQueryRequestTest
├── error/                        # Exception handler tests
│   └── RestExceptionHandlerTest
├── model/                        # Domain model tests
│   └── ModelTest
├── repository/                   # Database integration tests
│   ├── TransactionRepositoryTest
│   └── UserRepositoryTest
├── security/                     # Security & JWT tests
│   └── JwtUtilTest
├── service/                      # Business logic tests
│   └── PnLServiceTest
└── utility/                      # Utility tests
    └── DateUtilsTest
```

## Troubleshooting

### Maven Not Found
```bash
# Check Maven installation
mvn --version

# Install Maven (Mac)
brew install maven

# Install Maven (Windows - use Chocolatey)
choco install maven
```

### Tests Fail to Compile
```bash
# Clean and rebuild
mvn clean compile test-compile

# Update dependencies
mvn dependency:resolve
```

### H2 Database Issues
Tests use H2 in-memory database via `src/test/resources/application-test.properties`:
- Profile: `@ActiveProfiles("test")` on integration and repository tests
- Flyway disabled; Hibernate `create-drop`
- Each Spring context gets an isolated DB (`jdbc:h2:mem:equity_${random.uuid}`)

If you see errors:
1. Confirm tests use the `test` profile
2. Ensure H2 is in `pom.xml` with `test` scope

### Port Already in Use
If integration tests fail with port conflicts:
```bash
# Kill process using port 8080
# Mac/Linux
lsof -ti:8080 | xargs kill -9

# Windows
netstat -ano | findstr :8080
taskkill /PID <PID> /F
```

## Test Data

Tests use in-memory data created by `TestDataBuilder`:
- Users: "test-user", "integration-test-user"
- Passwords: "password123" (encoded)
- Transaction types: buy, sell, deposit, withdraw
- Sample stocks: AAPL, GOOGL, MSFT, TSLA

## Known Issues

None blocking as of June 20, 2026. All 257 tests pass.

For historical bug analysis see `BUG_REPORT.md`. For deferred features see `FUTURE_ENHANCEMENTS.md`.

## CI/CD Integration

GitHub Actions runs on every push and pull request to `main`:

- Workflow: [`.github/workflows/ci.yml`](../.github/workflows/ci.yml)
- **Maven Test:** JDK 21, `./mvnw test -B`
- **OWASP Dependency-Check:** `./mvnw dependency-check:check -B` (fails on CVSS ≥ 7; skips NVD update when cache restores)
- **Docker Build:** pushes to `ghcr.io/<repo>` on push to `main` (after tests pass)

Add an **`NVD_API_KEY`** repository secret ([NIST NVD API key](https://nvd.nist.gov/developers/request-an-api-key)) for faster, more reliable NVD updates. Without it, the first OWASP run can take 30+ minutes.

See also [PORTFOLIO_DEMO.md](PORTFOLIO_DEMO.md) for CI talking points.

### GitHub Actions Example (reference — implemented in repo)

```yaml
name: CI
on:
  push:
    branches: [main]
  pull_request:
    branches: [main]
jobs:
  test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          java-version: '21'
          distribution: 'temurin'
          cache: maven
      - run: ./mvnw test -B
```

## Next Steps After Running Tests

1. Review test output for failures
2. Check coverage report for gaps (optional)
3. See [PORTFOLIO_DEMO.md](PORTFOLIO_DEMO.md) for demo paths (tests-only, Swagger, Docker staging)
4. See [PROJECT_STATUS.md](PROJECT_STATUS.md) for deferred production items

## Getting Help

- **Test Documentation**: See `TEST_DOCUMENTATION.md`
- **Bug Report**: See `BUG_REPORT.md`
- **Coverage Report**: See `TEST_COVERAGE_REPORT.md`
- **JUnit 5 Docs**: https://junit.org/junit5/docs/current/user-guide/
- **Mockito Docs**: https://javadoc.io/doc/org.mockito/mockito-core/latest/org/mockito/Mockito.html
- **Spring Test Docs**: https://docs.spring.io/spring-boot/docs/current/reference/html/features.html#features.testing

---

*For detailed analysis of bugs found and test coverage, see `BUG_REPORT.md` and `TEST_COVERAGE_REPORT.md`*
