# Input Validation Specification

## Objective
Implement comprehensive input validation across all API endpoints to prevent security vulnerabilities, data corruption, and improve API reliability.

## Current State

### Issues
- No validation on any controller inputs
- Date formats not validated
- `uid` parameter not sanitized (now replaced with authentication)
- No request size limits
- No bounds checking on date ranges
- Vulnerable to injection attacks
- Poor error messages for invalid input

## Target State

- All request parameters validated
- Proper error responses with validation details
- Protection against common attack vectors
- Business rule validation
- Sanitized and normalized inputs

## Implementation Plan

### Step 1: Add Validation Dependencies

Already included in Phase 1 dependency upgrade:

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-validation</artifactId>
</dependency>
```

### Step 2: Create Request DTOs

**File: `src/main/java/com/companyx/equity/dto/PnLQueryRequest.java`**

```java
package com.companyx.equity.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PnLQueryRequest {
    
    @NotNull(message = "From date is required")
    @PastOrPresent(message = "From date cannot be in the future")
    private LocalDate from;
    
    @NotNull(message = "To date is required")
    @PastOrPresent(message = "To date cannot be in the future")
    private LocalDate to;
    
    @AssertTrue(message = "To date must be after or equal to from date")
    public boolean isValidDateRange() {
        if (from == null || to == null) {
            return true; // Let @NotNull handle null checks
        }
        return !to.isBefore(from);
    }
    
    @AssertTrue(message = "Date range cannot exceed 5 years")
    public boolean isReasonableDateRange() {
        if (from == null || to == null) {
            return true;
        }
        return from.plusYears(5).isAfter(to);
    }
}
```

**File: `src/main/java/com/companyx/equity/dto/TransactionQueryRequest.java`**

```java
package com.companyx.equity.dto;

import jakarta.validation.constraints.PastOrPresent;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TransactionQueryRequest {
    
    @PastOrPresent(message = "From date cannot be in the future")
    private LocalDate from;
    
    @PastOrPresent(message = "To date cannot be in the future")
    private LocalDate to;
    
    @AssertTrue(message = "To date must be after or equal to from date")
    public boolean isValidDateRange() {
        if (from == null || to == null) {
            return true;
        }
        return !to.isBefore(from);
    }
}
```

**File: `src/main/java/com/companyx/equity/dto/MarkQueryRequest.java`**

```java
package com.companyx.equity.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MarkQueryRequest {
    
    @NotBlank(message = "Symbol is required")
    @Size(min = 1, max = 10, message = "Symbol must be between 1 and 10 characters")
    @Pattern(regexp = "^[A-Z0-9.]+$", message = "Symbol must contain only uppercase letters, numbers, and dots")
    private String symbol;
}
```

**File: `src/main/java/com/companyx/equity/dto/CandleQueryRequest.java`**

```java
package com.companyx.equity.dto;

import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CandleQueryRequest {
    
    @NotBlank(message = "Symbol is required")
    @Size(min = 1, max = 10, message = "Symbol must be between 1 and 10 characters")
    @Pattern(regexp = "^[A-Z0-9.]+$", message = "Symbol must contain only uppercase letters, numbers, and dots")
    private String symbol;
    
    @NotNull(message = "From date is required")
    @PastOrPresent(message = "From date cannot be in the future")
    private LocalDate from;
    
    @NotNull(message = "To date is required")
    @PastOrPresent(message = "To date cannot be in the future")
    private LocalDate to;
    
    @AssertTrue(message = "To date must be after or equal to from date")
    public boolean isValidDateRange() {
        if (from == null || to == null) {
            return true;
        }
        return !to.isBefore(from);
    }
    
    @AssertTrue(message = "Date range cannot exceed 1 year for candle data")
    public boolean isReasonableDateRange() {
        if (from == null || to == null) {
            return true;
        }
        return from.plusYears(1).isAfter(to);
    }
}
```

### Step 3: Custom Validators

**File: `src/main/java/com/companyx/equity/validation/DateRange.java`**

```java
package com.companyx.equity.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.*;

@Target({ElementType.TYPE, ElementType.ANNOTATION_TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = DateRangeValidator.class)
@Documented
public @interface DateRange {
    String message() default "Invalid date range";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
    
    String startDate();
    String endDate();
    int maxYears() default 5;
}
```

**File: `src/main/java/com/companyx/equity/validation/DateRangeValidator.java`**

```java
package com.companyx.equity.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import org.springframework.beans.BeanWrapper;
import org.springframework.beans.BeanWrapperImpl;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

public class DateRangeValidator implements ConstraintValidator<DateRange, Object> {
    
    private String startDate;
    private String endDate;
    private int maxYears;

    @Override
    public void initialize(DateRange constraintAnnotation) {
        this.startDate = constraintAnnotation.startDate();
        this.endDate = constraintAnnotation.endDate();
        this.maxYears = constraintAnnotation.maxYears();
    }

    @Override
    public boolean isValid(Object value, ConstraintValidatorContext context) {
        BeanWrapper beanWrapper = new BeanWrapperImpl(value);
        LocalDate start = (LocalDate) beanWrapper.getPropertyValue(startDate);
        LocalDate end = (LocalDate) beanWrapper.getPropertyValue(endDate);

        if (start == null || end == null) {
            return true;
        }

        if (end.isBefore(start)) {
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate("End date must be after start date")
                    .addConstraintViolation();
            return false;
        }

        long years = ChronoUnit.YEARS.between(start, end);
        if (years > maxYears) {
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate(
                    String.format("Date range cannot exceed %d years", maxYears))
                    .addConstraintViolation();
            return false;
        }

        return true;
    }
}
```

### Step 4: Update Controllers with Validation

**File: `src/main/java/com/companyx/equity/controller/TransactionController.java`**

```java
package com.companyx.equity.controller;

import com.companyx.equity.dto.PnLQueryRequest;
import com.companyx.equity.dto.TransactionQueryRequest;
import com.companyx.equity.model.Position;
import com.companyx.equity.model.Transaction;
import com.companyx.equity.service.PnLService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.hateoas.EntityModel;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Validated
public class TransactionController {
    
    private final PnLService pnLService;

    @GetMapping("/pnl")
    public ResponseEntity<EntityModel<Map<String, Position>>> pnlBetween(
            Authentication authentication,
            @Valid @ModelAttribute PnLQueryRequest request
    ) {
        String uid = authentication.getName();
        Map<String, Position> positions = pnLService.getPositions(
                uid, 
                request.getFrom(), 
                request.getTo()
        );
        return ResponseEntity.ok(EntityModel.of(positions));
    }

    @GetMapping("/transactions/{id}")
    public ResponseEntity<EntityModel<Transaction>> show(
            Authentication authentication,
            @PathVariable @Positive(message = "Transaction ID must be positive") Long id
    ) {
        String uid = authentication.getName();
        Transaction transaction = pnLService.getTransactionById(uid, id);
        return ResponseEntity.ok(EntityModel.of(transaction));
    }

    @GetMapping("/transactions")
    public ResponseEntity<List<Transaction>> findBetween(
            Authentication authentication,
            @Valid @ModelAttribute TransactionQueryRequest request
    ) {
        String uid = authentication.getName();
        List<Transaction> transactions = pnLService.getTransactionsByDates(
                uid,
                request.getFrom(),
                request.getTo()
        );
        return ResponseEntity.ok(transactions);
    }
}
```

**File: `src/main/java/com/companyx/equity/controller/FinhubController.java`**

```java
package com.companyx.equity.controller;

import com.companyx.equity.dto.CandleDto;
import com.companyx.equity.dto.CandleQueryRequest;
import com.companyx.equity.dto.MarkDto;
import com.companyx.equity.dto.MarkQueryRequest;
import com.companyx.equity.repository.FinhubRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.hateoas.EntityModel;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Validated
public class FinhubController {

    private final FinhubRepository finhubRepository;

    @GetMapping("/marks/{symbol}")
    public ResponseEntity<EntityModel<MarkDto>> mark(
            @PathVariable String symbol
    ) throws JsonProcessingException {
        // Validate symbol format
        if (!symbol.matches("^[A-Z0-9.]+$")) {
            throw new IllegalArgumentException("Invalid symbol format");
        }
        
        MarkDto result = finhubRepository.getMark(symbol.toUpperCase());
        log.info("Retrieved mark for symbol: {}", symbol);
        return ResponseEntity.ok(EntityModel.of(result));
    }

    @GetMapping("/candles")
    public ResponseEntity<EntityModel<CandleDto>> candle(
            @Valid @ModelAttribute CandleQueryRequest request
    ) throws JsonProcessingException {
        CandleDto result = finhubRepository.getCandle(
                request.getSymbol().toUpperCase(),
                request.getFrom(),
                request.getTo()
        );
        log.info("Retrieved candle data for symbol: {} from {} to {}", 
                request.getSymbol(), request.getFrom(), request.getTo());
        return ResponseEntity.ok(EntityModel.of(result));
    }
}
```

### Step 5: Validation Error Response DTO

**File: `src/main/java/com/companyx/equity/dto/ErrorResponse.java`**

```java
package com.companyx.equity.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ErrorResponse {
    private Instant timestamp;
    private int status;
    private String error;
    private String message;
    private String path;
    private String correlationId;
    private List<ValidationError> validationErrors;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ValidationError {
        private String field;
        private String message;
        private Object rejectedValue;
    }
}
```

### Step 6: Update Exception Handler

**File: `src/main/java/com/companyx/equity/error/RestExceptionHandler.java`**

```java
package com.companyx.equity.error;

import com.companyx.equity.dto.ErrorResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice
@Slf4j
public class RestExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(
            MethodArgumentNotValidException ex,
            HttpServletRequest request
    ) {
        String correlationId = UUID.randomUUID().toString();
        
        List<ErrorResponse.ValidationError> validationErrors = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(error -> ErrorResponse.ValidationError.builder()
                        .field(error.getField())
                        .message(error.getDefaultMessage())
                        .rejectedValue(error.getRejectedValue())
                        .build())
                .collect(Collectors.toList());

        ErrorResponse errorResponse = ErrorResponse.builder()
                .timestamp(Instant.now())
                .status(HttpStatus.BAD_REQUEST.value())
                .error("Validation Failed")
                .message("Invalid input parameters")
                .path(request.getRequestURI())
                .correlationId(correlationId)
                .validationErrors(validationErrors)
                .build();

        log.warn("Validation error [{}]: {} errors on {}", 
                correlationId, validationErrors.size(), request.getRequestURI());
        
        return ResponseEntity.badRequest().body(errorResponse);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolation(
            ConstraintViolationException ex,
            HttpServletRequest request
    ) {
        String correlationId = UUID.randomUUID().toString();
        
        List<ErrorResponse.ValidationError> validationErrors = ex.getConstraintViolations()
                .stream()
                .map(violation -> ErrorResponse.ValidationError.builder()
                        .field(getFieldName(violation))
                        .message(violation.getMessage())
                        .rejectedValue(violation.getInvalidValue())
                        .build())
                .collect(Collectors.toList());

        ErrorResponse errorResponse = ErrorResponse.builder()
                .timestamp(Instant.now())
                .status(HttpStatus.BAD_REQUEST.value())
                .error("Validation Failed")
                .message("Invalid input parameters")
                .path(request.getRequestURI())
                .correlationId(correlationId)
                .validationErrors(validationErrors)
                .build();

        log.warn("Constraint violation [{}]: {} errors on {}", 
                correlationId, validationErrors.size(), request.getRequestURI());
        
        return ResponseEntity.badRequest().body(errorResponse);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(
            MethodArgumentTypeMismatchException ex,
            HttpServletRequest request
    ) {
        String correlationId = UUID.randomUUID().toString();
        
        ErrorResponse errorResponse = ErrorResponse.builder()
                .timestamp(Instant.now())
                .status(HttpStatus.BAD_REQUEST.value())
                .error("Type Mismatch")
                .message(String.format("Invalid value '%s' for parameter '%s'", 
                        ex.getValue(), ex.getName()))
                .path(request.getRequestURI())
                .correlationId(correlationId)
                .build();

        log.warn("Type mismatch [{}]: {} on {}", correlationId, ex.getMessage(), request.getRequestURI());
        
        return ResponseEntity.badRequest().body(errorResponse);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(
            IllegalArgumentException ex,
            HttpServletRequest request
    ) {
        String correlationId = UUID.randomUUID().toString();
        
        ErrorResponse errorResponse = ErrorResponse.builder()
                .timestamp(Instant.now())
                .status(HttpStatus.BAD_REQUEST.value())
                .error("Invalid Argument")
                .message(ex.getMessage())
                .path(request.getRequestURI())
                .correlationId(correlationId)
                .build();

        log.warn("Illegal argument [{}]: {} on {}", correlationId, ex.getMessage(), request.getRequestURI());
        
        return ResponseEntity.badRequest().body(errorResponse);
    }

    @ExceptionHandler(ResponseVerificationException.class)
    public ResponseEntity<ErrorResponse> handleResponseVerification(
            ResponseVerificationException ex,
            HttpServletRequest request
    ) {
        String correlationId = UUID.randomUUID().toString();
        
        ErrorResponse errorResponse = ErrorResponse.builder()
                .timestamp(Instant.now())
                .status(HttpStatus.BAD_GATEWAY.value())
                .error("External Service Error")
                .message("Unable to process external service response")
                .path(request.getRequestURI())
                .correlationId(correlationId)
                .build();

        log.error("External service error [{}]: {}", correlationId, ex.getMessage());
        
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(errorResponse);
    }

    @ExceptionHandler(VendorConnectivityException.class)
    public ResponseEntity<ErrorResponse> handleVendorConnectivity(
            VendorConnectivityException ex,
            HttpServletRequest request
    ) {
        String correlationId = UUID.randomUUID().toString();
        
        ErrorResponse errorResponse = ErrorResponse.builder()
                .timestamp(Instant.now())
                .status(HttpStatus.SERVICE_UNAVAILABLE.value())
                .error("Service Unavailable")
                .message("External service temporarily unavailable")
                .path(request.getRequestURI())
                .correlationId(correlationId)
                .build();

        log.error("Vendor connectivity error [{}]: {}", correlationId, ex.getMessage());
        
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(errorResponse);
    }

    @ExceptionHandler(UnexpectedValueException.class)
    public ResponseEntity<ErrorResponse> handleUnexpectedValue(
            UnexpectedValueException ex,
            HttpServletRequest request
    ) {
        String correlationId = UUID.randomUUID().toString();
        
        ErrorResponse errorResponse = ErrorResponse.builder()
                .timestamp(Instant.now())
                .status(HttpStatus.INTERNAL_SERVER_ERROR.value())
                .error("Internal Server Error")
                .message("An unexpected error occurred")
                .path(request.getRequestURI())
                .correlationId(correlationId)
                .build();

        log.error("Unexpected value error [{}]: {}", correlationId, ex.getMessage());
        
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
    }

    private String getFieldName(ConstraintViolation<?> violation) {
        String propertyPath = violation.getPropertyPath().toString();
        String[] parts = propertyPath.split("\\.");
        return parts.length > 0 ? parts[parts.length - 1] : propertyPath;
    }
}
```

### Step 7: Request Size Limits

**File: `src/main/resources/application.properties`**

```properties
# Request size limits
server.tomcat.max-http-form-post-size=2MB
server.tomcat.max-swallow-size=2MB
spring.servlet.multipart.max-file-size=10MB
spring.servlet.multipart.max-request-size=10MB

# Connection timeouts
server.tomcat.connection-timeout=20000
```

### Step 8: Input Sanitization Utility

**File: `src/main/java/com/companyx/equity/utility/InputSanitizer.java`**

```java
package com.companyx.equity.utility;

import org.apache.commons.lang3.StringUtils;

public class InputSanitizer {
    
    private static final String SQL_INJECTION_PATTERN = "('.+(\\-\\-|;))|(.*(\\bOR\\b|\\bAND\\b).*)";
    
    public static String sanitizeSymbol(String symbol) {
        if (StringUtils.isBlank(symbol)) {
            return null;
        }
        // Remove any non-alphanumeric characters except dots
        return symbol.toUpperCase().replaceAll("[^A-Z0-9.]", "");
    }
    
    public static boolean containsSqlInjection(String input) {
        if (StringUtils.isBlank(input)) {
            return false;
        }
        return input.matches(SQL_INJECTION_PATTERN);
    }
}
```

## Testing

### Unit Tests

**File: `src/test/java/com/companyx/equity/validation/ValidationTest.java`**

```java
package com.companyx.equity.validation;

import com.companyx.equity.dto.PnLQueryRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ValidationTest {
    
    private Validator validator;
    
    @BeforeEach
    void setUp() {
        validator = Validation.buildDefaultValidatorFactory().getValidator();
    }
    
    @Test
    void shouldPassValidPnLRequest() {
        PnLQueryRequest request = new PnLQueryRequest(
                LocalDate.of(2020, 1, 1),
                LocalDate.of(2020, 12, 31)
        );
        
        Set<ConstraintViolation<PnLQueryRequest>> violations = validator.validate(request);
        assertTrue(violations.isEmpty());
    }
    
    @Test
    void shouldFailWhenToDateBeforeFromDate() {
        PnLQueryRequest request = new PnLQueryRequest(
                LocalDate.of(2020, 12, 31),
                LocalDate.of(2020, 1, 1)
        );
        
        Set<ConstraintViolation<PnLQueryRequest>> violations = validator.validate(request);
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream()
                .anyMatch(v -> v.getMessage().contains("after or equal")));
    }
    
    @Test
    void shouldFailWhenDateRangeExceeds5Years() {
        PnLQueryRequest request = new PnLQueryRequest(
                LocalDate.of(2015, 1, 1),
                LocalDate.of(2022, 1, 1)
        );
        
        Set<ConstraintViolation<PnLQueryRequest>> violations = validator.validate(request);
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream()
                .anyMatch(v -> v.getMessage().contains("5 years")));
    }
    
    @Test
    void shouldFailWhenDatesAreNull() {
        PnLQueryRequest request = new PnLQueryRequest(null, null);
        
        Set<ConstraintViolation<PnLQueryRequest>> violations = validator.validate(request);
        assertEquals(2, violations.size());
    }
}
```

### Integration Tests

**File: `src/test/java/com/companyx/equity/controller/ValidationIntegrationTest.java`**

```java
package com.companyx.equity.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class ValidationIntegrationTest {
    
    @Autowired
    private MockMvc mockMvc;
    
    @Test
    @WithMockUser
    void shouldReturn400ForInvalidDateRange() throws Exception {
        mockMvc.perform(get("/api/v1/pnl")
                .param("from", "2021-01-01")
                .param("to", "2020-01-01"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.validationErrors").isArray())
                .andExpect(jsonPath("$.correlationId").exists());
    }
    
    @Test
    @WithMockUser
    void shouldReturn400ForFutureDates() throws Exception {
        mockMvc.perform(get("/api/v1/pnl")
                .param("from", "2030-01-01")
                .param("to", "2031-01-01"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.validationErrors").isArray());
    }
    
    @Test
    @WithMockUser
    void shouldReturn400ForMissingRequiredParams() throws Exception {
        mockMvc.perform(get("/api/v1/pnl"))
                .andExpect(status().isBadRequest());
    }
}
```

## Acceptance Criteria

- [ ] All controller methods use `@Valid` annotation
- [ ] Request DTOs created with validation annotations
- [ ] Custom validators implemented for business rules
- [ ] Date range validation (max 5 years for PnL, 1 year for candles)
- [ ] Symbol format validation
- [ ] Proper error responses with validation details
- [ ] Correlation IDs in error responses
- [ ] Request size limits configured
- [ ] Input sanitization for symbols
- [ ] All validation tests passing
- [ ] Integration tests for validation scenarios

## Dependencies

- 01-dependency-upgrades.md (requires Spring Boot 3.x)
- 02-security-authentication.md (authentication context)

## Estimated Effort

- Create DTOs and validators: 1.5 days
- Update controllers: 1 day
- Update exception handler: 1 day
- Write tests: 1.5 days
- **Total: 5 days**

## References

- [Bean Validation Specification](https://beanvalidation.org/2.0/spec/)
- [Spring Validation](https://docs.spring.io/spring-framework/reference/core/validation/beanvalidation.html)
- [OWASP Input Validation](https://cheatsheetseries.owasp.org/cheatsheets/Input_Validation_Cheat_Sheet.html)
