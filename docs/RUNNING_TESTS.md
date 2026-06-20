# Running Tests - Quick Start Guide

## Prerequisites

1. **Java 17+** installed
2. **Maven 3.6+** installed (or use IDE's built-in Maven)
3. No database required (tests use H2 in-memory)

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

# Run multiple test classes
mvn test -Dtest=PnLServiceTest,TransactionControllerTest

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
[INFO] Tests run: 170, Failures: 0, Errors: 0, Skipped: 0
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
Tests use H2 in-memory database. If you see errors:
1. Check `src/test/resources/application-test.properties`
2. Ensure H2 is in `pom.xml` dependencies with `test` scope

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

1. **PnL Calculation Tests**: May fail due to sign convention issues in implementation
   - See `BUG_REPORT.md` for details
   - Tests are correct; implementation needs verification

2. **Type Mismatch Bugs**: FIXED in `TransactionController`
   - Conversion from LocalDate to Date added
   - Tests should now pass

3. **Timezone Tests**: FIXED in `DateUtils`
   - Now uses "America/Los_Angeles" instead of "GMT-7"
   - Tests should pass year-round

## CI/CD Integration

### GitHub Actions Example

```yaml
name: Tests
on: [push, pull_request]
jobs:
  test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      - uses: actions/setup-java@v3
        with:
          java-version: '17'
          distribution: 'temurin'
      - name: Run tests
        run: mvn test
      - name: Generate coverage
        run: mvn jacoco:report
      - name: Upload coverage
        uses: codecov/codecov-action@v3
```

## Next Steps After Running Tests

1. ✅ Review test output for failures
2. ✅ Check coverage report for gaps
3. ✅ Fix failing tests (especially PnL calculations)
4. ✅ Address issues in `BUG_REPORT.md`
5. ✅ Add more tests as code evolves

## Getting Help

- **Test Documentation**: See `TEST_DOCUMENTATION.md`
- **Bug Report**: See `BUG_REPORT.md`
- **Coverage Report**: See `TEST_COVERAGE_REPORT.md`
- **JUnit 5 Docs**: https://junit.org/junit5/docs/current/user-guide/
- **Mockito Docs**: https://javadoc.io/doc/org.mockito/mockito-core/latest/org/mockito/Mockito.html
- **Spring Test Docs**: https://docs.spring.io/spring-boot/docs/current/reference/html/features.html#features.testing

---

*For detailed analysis of bugs found and test coverage, see `BUG_REPORT.md` and `TEST_COVERAGE_REPORT.md`*
