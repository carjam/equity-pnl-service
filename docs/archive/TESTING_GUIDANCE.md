# Testing Guidance

## Overview

This document provides guidance for running tests periodically during development to ensure code quality and catch regressions early.

## When to Run Tests

### Required Test Runs
- ✅ After fixing bugs or adding features
- ✅ Before committing code changes
- ✅ After refactoring existing code
- ✅ When compilation issues are resolved
- ✅ After dependency updates

### Recommended Test Runs
- After modifying service layer code
- After changing entity models or database schema
- When modifying business logic (e.g., P&L calculations)
- Before creating a pull request

## Quick Test Commands

### Run All Tests
```powershell
.\mvnw.cmd test
```

### Run Specific Test Class
```powershell
# P&L calculation tests
.\mvnw.cmd test -Dtest=PnLCalculationTest

# Service layer tests
.\mvnw.cmd test -Dtest=PnLServiceTest

# Bug fix verification tests
.\mvnw.cmd test -Dtest=PnLServiceBugFixTest

# Repository tests
.\mvnw.cmd test -Dtest=TransactionRepositoryTest
```

### Run Tests for Specific Package
```powershell
# All service tests
.\mvnw.cmd test -Dtest="com.companyx.equity.service.*Test"

# All repository tests
.\mvnw.cmd test -Dtest="com.companyx.equity.repository.*Test"

# All controller tests
.\mvnw.cmd test -Dtest="com.companyx.equity.controller.*Test"
```

### Run Tests with Coverage (if enabled)
```powershell
.\mvnw.cmd test jacoco:report
```

## Test Output Interpretation

### Success Indicators
```
[INFO] Tests run: 14, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

### Failure Indicators
```
[ERROR] Tests run: 11, Failures: 6, Errors: 5, Skipped: 0
[ERROR] BUILD FAILURE
```

### Understanding Test Results
- **Failures**: Assertion failures - expected vs. actual mismatch
- **Errors**: Exceptions during test execution (e.g., NullPointerException)
- **Skipped**: Tests marked with `@Disabled` or conditional execution

## Test Categories

### Unit Tests
- **Purpose**: Test individual components in isolation
- **Speed**: Fast (milliseconds per test)
- **Location**: `src/test/java/com/companyx/equity/service/`
- **Examples**: `PnLServiceTest`, `DateUtilsTest`

### Integration Tests
- **Purpose**: Test component interactions and database access
- **Speed**: Slower (seconds per test)
- **Location**: `src/test/java/com/companyx/equity/`
- **Examples**: `IntegrationTest`, `TransactionRepositoryTest`

### Controller Tests
- **Purpose**: Test API endpoints and request/response handling
- **Speed**: Medium (hundreds of milliseconds per test)
- **Location**: `src/test/java/com/companyx/equity/controller/`
- **Examples**: `TransactionControllerTest`, `AuthControllerTest`

## Debugging Failed Tests

### 1. Read the Failure Message
```
[ERROR] PnLCalculationTest$SimpleLongPositions.testLongPositionProfit:106 
        NullPointerException: Cannot invoke getClose() because the return value is null
```

### 2. Check Test Reports
```powershell
# Open detailed HTML reports
start target/surefire-reports/index.html
```

### 3. Run Single Test with Debug Output
```powershell
.\mvnw.cmd test -Dtest=PnLCalculationTest#testLongPositionProfit -X
```

### 4. Common Issues and Solutions

| Issue | Solution |
|-------|----------|
| `NullPointerException` | Add mock setup or verify data initialization |
| `AssertionFailedError` | Check expected vs actual values, update assertions |
| `NoSuchBeanDefinitionException` | Add missing `@MockBean` or configure test context |
| `JsonProcessingException` | Verify JSON test data format |
| `DataIntegrityViolationException` | Check test database constraints and seed data |

## Test-Driven Development Workflow

### Recommended Cycle
1. **Write Test**: Create failing test for new feature
2. **Run Test**: Verify it fails as expected
   ```powershell
   .\mvnw.cmd test -Dtest=YourNewTest
   ```
3. **Implement**: Write minimal code to make test pass
4. **Run Test**: Verify it passes
5. **Refactor**: Clean up code while keeping tests green
6. **Run All Tests**: Ensure no regressions
   ```powershell
   .\mvnw.cmd test
   ```

## Performance Tips

### Faster Test Execution
```powershell
# Run tests in parallel (4 threads)
.\mvnw.cmd test -T 4

# Skip slow integration tests
.\mvnw.cmd test -DexcludedGroups=integration

# Run only fast unit tests
.\mvnw.cmd test -Dgroups=unit
```

### Test Database Optimization
- Use H2 in-memory database for tests (already configured)
- Keep test data minimal
- Use `@Transactional` for automatic rollback

## Continuous Integration

### GitHub Actions Example
```yaml
- name: Run tests
  run: ./mvnw test
  
- name: Generate coverage report
  run: ./mvnw jacoco:report
```

## Test Configuration

### Test Properties
- **File**: `src/test/resources/application-test.properties`
- **Features**:
  - Fast retry timeouts (100ms)
  - In-memory H2 database
  - Debug-level logging for troubleshooting

### Test Dependencies
- JUnit 5 (Jupiter)
- Mockito for mocking
- Spring Boot Test
- H2 Database for tests
- MockWebServer for HTTP client testing

## Current Test Status

### Implemented Tests
- ✅ P&L Service (core logic)
- ✅ P&L Bug Fixes (verification)
- ✅ P&L Calculation (comprehensive scenarios)
- ✅ Transaction Repository
- ✅ Finhub Repository (retry behavior)
- ✅ Controllers (with security)
- ✅ Error Handling
- ✅ JWT Utilities
- ✅ Date Utilities

### Known Test Issues
- ⚠️ P&L Calculation tests need BigDecimal assertion tolerance
- ⚠️ Some tests need mock Finhub price data for unrealized P&L

## Quick Reference

| Command | Purpose | When to Use |
|---------|---------|-------------|
| `.\mvnw.cmd test` | Run all tests | Before commit, after major changes |
| `.\mvnw.cmd test -Dtest=ClassTest` | Single test class | Focused debugging |
| `.\mvnw.cmd compile` | Compile only | Check for compilation errors |
| `.\mvnw.cmd clean test` | Fresh test run | After dependency changes |
| `.\mvnw.cmd test -DskipTests` | Skip tests | Quick package for local testing |

---

**Best Practice**: Run `.\mvnw.cmd test` before committing any code changes. Tests are your safety net!
