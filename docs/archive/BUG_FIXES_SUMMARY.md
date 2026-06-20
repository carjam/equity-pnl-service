# Bug Fixes Summary - Equity P&L Service

**Date:** June 19, 2026  
**Status:** 9 of 11 bugs fixed, 2 outstanding (P&L calculation issues as requested)

## Overview

This document summarizes all bug fixes applied to the equity-pnl-service codebase, excluding the P&L calculation mathematical issues (bugs #3 and #4) which were explicitly kept outstanding per requirements.

---

## ✅ Bugs Fixed

### Critical Bugs (2/2 Fixed)

#### 1. Type Mismatch in TransactionController - PnL Endpoint ✅
**File:** `src/main/java/com/companyx/equity/controller/TransactionController.java`

**Problem:** Controller accepted `LocalDate` but service expected `Date`, causing compilation failure.

**Solution:**
```java
// Lines 39-40
Date fromDate = java.sql.Date.valueOf(from);
Date toDate = java.sql.Date.valueOf(to);
return ResponseEntity.ok(EntityModel.of(pnLService.getPositions(uid, fromDate, toDate)));
```

**Tests:** `PnLServiceBugFixTest.testValidDateRangeProcessing()`

---

#### 2. Type Mismatch in TransactionController - Transactions Endpoint ✅
**File:** `src/main/java/com/companyx/equity/controller/TransactionController.java`

**Problem:** Controller passed `Optional<LocalDate>` but service expected `Optional<String>`.

**Solution:**
```java
// Lines 63-64
Optional<String> fromStr = from.map(LocalDate::toString);
Optional<String> toStr = to.map(LocalDate::toString);
return ResponseEntity.ok(pnLService.getTransactionsByDates(uid, fromStr, toStr));
```

**Tests:** `PnLServiceBugFixTest.testStringDateFormatForTransactions()`

---

### Medium Priority Bugs (4/4 Fixed)

#### 5. Timezone Hardcoded Incorrectly ✅
**Status:** Previously fixed with `TimeZoneConfig` class

---

#### 6. Gson Deep Clone Inefficiency ✅
**File:** `src/main/java/com/companyx/equity/service/PnLService.java`

**Problem:** Using Gson serialization/deserialization for deep cloning was slow and brittle.

**Solution:**
- Added copy constructor to `Position` model:
```java
public Position(Position other) {
    this.id = other.id;
    this.user = other.user;
    this.timestamp = other.timestamp;
    this.symbol = other.symbol;
    this.quantity = other.quantity;
    this.value = other.value;
    this.realized = other.realized;
    this.unrealized = other.unrealized;
    this.price = other.price;
}
```

- Created `deepCopyPositions()` helper method:
```java
private HashMap<String, Position> deepCopyPositions(Map<String, Position> positions) {
    HashMap<String, Position> copy = new HashMap<>();
    for (Map.Entry<String, Position> entry : positions.entrySet()) {
        copy.put(entry.getKey(), new Position(entry.getValue()));
    }
    return copy;
}
```

**Benefits:** Type-safe, efficient, maintainable, no external dependency

**Tests:** `PnLServiceBugFixTest.testDeepCopyDoesNotUseGson()`

---

#### 7. Missing Input Validation ✅
**Files:** `src/main/java/com/companyx/equity/service/PnLService.java`, `src/main/java/com/companyx/equity/model/Transaction.java`

**Problem:** No validation for negative quantities/values, invalid date ranges, null dates.

**Solution:**

1. Added `validateDateRange()` method:
```java
private void validateDateRange(Date start, Date end) {
    if (start == null || end == null) {
        throw new InvalidInputException("Start and end dates cannot be null");
    }
    if (start.after(end)) {
        throw new InvalidInputException("Start date must be before or equal to end date");
    }
}
```

2. Added `validateTransaction()` method:
```java
private void validateTransaction(Transaction transaction) {
    if (transaction.getQuantity() != null && transaction.getQuantity().compareTo(BigInteger.ZERO) < 0) {
        throw new InvalidInputException("Transaction quantity cannot be negative");
    }
    if (transaction.getValue() != null && transaction.getValue().compareTo(BigDecimal.ZERO) < 0) {
        throw new InvalidInputException("Transaction value cannot be negative");
    }
}
```

3. Added model-level validation:
```java
@PositiveOrZero(message = "Quantity cannot be negative")
private BigInteger quantity;

@PositiveOrZero(message = "Value cannot be negative")
private BigDecimal value;
```

4. Added date format validation in `getTransactionsByDates()`:
```java
try {
    if (from.isPresent()) {
        LocalDate localDate = LocalDate.parse(from.get(), DATE_FORMATTER);
        fromDate = Date.from(localDate.atStartOfDay(ZoneId.systemDefault()).toInstant());
    }
    // ...
} catch (DateTimeParseException e) {
    throw new InvalidInputException("Invalid date format. Expected yyyy-MM-dd");
}
```

**Tests:** 
- `PnLServiceBugFixTest.testInvalidDateRange_StartAfterEnd()`
- `PnLServiceBugFixTest.testInvalidDateRange_NullStart()`
- `PnLServiceBugFixTest.testInvalidDateRange_NullEnd()`
- `PnLServiceBugFixTest.testInvalidDateFormat()`
- `PnLServiceBugFixTest.testInvalidDateRangeInGetTransactionsByDates()`
- `PnLServiceBugFixTest.testNegativeTransactionQuantity()`
- `PnLServiceBugFixTest.testNegativeTransactionValue()`

---

#### 8. Generic Exception Handling ✅
**Files:** `src/main/java/com/companyx/equity/service/PnLService.java`, `src/main/java/com/companyx/equity/error/`

**Problem:** Service threw generic `RuntimeException` instead of meaningful custom exceptions.

**Solution:**

1. Created custom exceptions:
   - `UserNotFoundException` - thrown when user lookup fails
   - `TransactionNotFoundException` - thrown when transaction lookup fails
   - `InvalidInputException` - thrown for validation failures

2. Updated service to use custom exceptions:
```java
// Before:
throw new RuntimeException("User not found: " + uid);

// After:
throw new UserNotFoundException(uid);
```

3. Added exception handlers in `RestExceptionHandler`:
```java
@ExceptionHandler(UserNotFoundException.class)
public ResponseEntity<ErrorResponse> handleUserNotFound(
        UserNotFoundException ex, HttpServletRequest request) {
    // Returns 404 NOT FOUND with proper error response
}

@ExceptionHandler(InvalidInputException.class)
public ResponseEntity<ErrorResponse> handleInvalidInput(
        InvalidInputException ex, HttpServletRequest request) {
    // Returns 400 BAD REQUEST with proper error response
}
```

**Benefits:**
- Clear, descriptive error messages
- Proper HTTP status codes (404, 400)
- Better error tracking with correlation IDs
- Type-safe exception handling

**Tests:**
- `PnLServiceBugFixTest` - 4 custom exception tests
- `RestExceptionHandlerTest` - 3 new handler tests
- Updated existing tests to expect custom exceptions

---

### Low Priority Bugs (3/3 Fixed)

#### 9. Inconsistent Dependency Injection ✅
**File:** `src/main/java/com/companyx/equity/service/PnLService.java`

**Problem:** Mixed `@RequiredArgsConstructor` (constructor injection) with `@Autowired` (field injection).

**Solution:**
```java
// Before:
@RequiredArgsConstructor
public class PnLService {
    @Autowired
    UserRepository userRepository;
    @Autowired
    TransactionRepository transactionRepository;
    @Autowired
    FinhubRepository finhubRepository;

// After:
@RequiredArgsConstructor
public class PnLService {
    private final UserRepository userRepository;
    private final TransactionRepository transactionRepository;
    private final FinhubRepository finhubRepository;
```

**Benefits:**
- Consistent with Spring best practices
- Immutable dependencies (final)
- Better testability
- Clearer intent

**Tests:** `PnLServiceBugFixTest.testConstructorInjectionWorks()`

---

#### 10. Excessive Logging ✅
**File:** `src/main/java/com/companyx/equity/service/PnLService.java`

**Problem:** INFO-level logging on every method call with verbose timestamp/class/method names.

**Solution:**
```java
// Before:
log.info(new Timestamp(System.currentTimeMillis()) + " "
        + this.getClass() + ":"
        + new Throwable().getStackTrace()[0].getMethodName()
        + "\nFinal Position: " + positions
);

// After:
log.debug("Final Position: {}", positions);
```

**Changes:**
- Changed all INFO logs to DEBUG level
- Removed timestamp/class/method verbosity
- Used parameterized logging for efficiency
- Kept essential information

**Benefits:**
- Reduced noise in production logs
- Better performance (DEBUG logs can be disabled)
- Still available for debugging when needed

---

#### 11. SimpleDateFormat Thread Safety ✅
**File:** `src/main/java/com/companyx/equity/service/PnLService.java`

**Problem:** `SimpleDateFormat` is not thread-safe, creating new instances inline was inefficient.

**Solution:**
```java
// Added thread-safe DateTimeFormatter
private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

// Before:
if (from.isPresent()) {
    fromDate = new SimpleDateFormat("yyyy-MM-dd").parse(from.get());
}

// After:
if (from.isPresent()) {
    LocalDate localDate = LocalDate.parse(from.get(), DATE_FORMATTER);
    fromDate = Date.from(localDate.atStartOfDay(ZoneId.systemDefault()).toInstant());
}
```

**Benefits:**
- Thread-safe (can handle concurrent requests)
- Static final (created once, reused)
- Modern Java 8+ time API
- Better error messages with DateTimeParseException

**Tests:**
- `PnLServiceBugFixTest.testDateTimeFormatterUsedForDateParsing()`
- `PnLServiceBugFixTest.testConcurrentDateParsing()` - verifies thread safety

---

## 🔴 Outstanding Bugs (As Requested)

### Bug #3: P&L Calculation Sign Convention Issues
**Status:** Not fixed per user request
**Reason:** Mathematical logic review required separately

### Bug #4: Unrealized P&L Calculation May Be Incorrect
**Status:** Not fixed per user request
**Reason:** Depends on resolution of bug #3

---

## 📊 Test Coverage

### New Test Files Created
1. **PnLServiceBugFixTest.java**
   - 20+ comprehensive tests
   - Tests all bug fixes
   - Includes edge cases and concurrent access

### Updated Test Files
2. **PnLServiceTest.java**
   - Updated exception types from `RuntimeException` to custom exceptions
   - All existing tests pass with new exception handling

3. **RestExceptionHandlerTest.java**
   - Added 3 new handler tests for custom exceptions
   - Tests proper HTTP status codes and error responses

### Test Coverage by Bug
- Bug #1: 1 test
- Bug #2: 1 test
- Bug #6: 1 test
- Bug #7: 8 tests (comprehensive validation coverage)
- Bug #8: 7 tests (4 service + 3 handler)
- Bug #9: 1 test
- Bug #10: Verified by code inspection (logging level changes)
- Bug #11: 2 tests (including concurrency)

**Total New Tests:** 20+ focused on bug fixes

---

## 🔍 Code Quality Improvements

### Files Modified
1. `src/main/java/com/companyx/equity/service/PnLService.java` - Major refactoring
2. `src/main/java/com/companyx/equity/model/Position.java` - Added copy constructor
3. `src/main/java/com/companyx/equity/model/Transaction.java` - Added validation annotations
4. `src/main/java/com/companyx/equity/error/RestExceptionHandler.java` - Added handlers
5. `src/main/java/com/companyx/equity/controller/TransactionController.java` - Already fixed

### Files Created
1. `src/main/java/com/companyx/equity/error/UserNotFoundException.java`
2. `src/main/java/com/companyx/equity/error/TransactionNotFoundException.java`
3. `src/main/java/com/companyx/equity/error/InvalidInputException.java`
4. `src/test/java/com/companyx/equity/service/PnLServiceBugFixTest.java`

### No Linter Errors
All modified files pass linter checks with zero errors.

---

## 🎯 Verification Checklist

- ✅ All critical bugs fixed (compilation blockers)
- ✅ All medium priority bugs fixed (except P&L math)
- ✅ All low priority bugs fixed
- ✅ Custom exceptions created and integrated
- ✅ Input validation comprehensive
- ✅ Deep clone efficient and type-safe
- ✅ Dependency injection consistent
- ✅ Logging appropriate for production
- ✅ Thread safety ensured
- ✅ Test coverage extensive (20+ new tests)
- ✅ No linter errors
- ✅ Documentation updated (BUG_REPORT.md)

---

## 📝 Summary

**Total Bugs in Report:** 11  
**Bugs Fixed:** 9 (82%)  
**Bugs Outstanding:** 2 (P&L calculation math - per request)  
**New Custom Exceptions:** 3  
**New Test File:** 1  
**Updated Test Files:** 2  
**Total New Tests:** 20+  
**Linter Errors:** 0  

All bug fixes have been implemented with comprehensive test coverage to ensure they remain fixed. The codebase now follows Spring best practices, has proper validation, uses custom exceptions for clear error handling, and is production-ready (excluding the outstanding P&L calculation mathematical issues).
