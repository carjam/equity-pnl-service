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

import java.text.ParseException;
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

    @ExceptionHandler(UserNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleUserNotFound(
            UserNotFoundException ex,
            HttpServletRequest request
    ) {
        String correlationId = UUID.randomUUID().toString();
        
        ErrorResponse errorResponse = ErrorResponse.builder()
                .timestamp(Instant.now())
                .status(HttpStatus.NOT_FOUND.value())
                .error("User Not Found")
                .message(ex.getMessage())
                .path(request.getRequestURI())
                .correlationId(correlationId)
                .build();

        log.warn("User not found [{}]: {}", correlationId, ex.getMessage());
        
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorResponse);
    }

    @ExceptionHandler(TransactionNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleTransactionNotFound(
            TransactionNotFoundException ex,
            HttpServletRequest request
    ) {
        String correlationId = UUID.randomUUID().toString();
        
        ErrorResponse errorResponse = ErrorResponse.builder()
                .timestamp(Instant.now())
                .status(HttpStatus.NOT_FOUND.value())
                .error("Transaction Not Found")
                .message(ex.getMessage())
                .path(request.getRequestURI())
                .correlationId(correlationId)
                .build();

        log.warn("Transaction not found [{}]: {}", correlationId, ex.getMessage());
        
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorResponse);
    }

    @ExceptionHandler(InvalidInputException.class)
    public ResponseEntity<ErrorResponse> handleInvalidInput(
            InvalidInputException ex,
            HttpServletRequest request
    ) {
        String correlationId = UUID.randomUUID().toString();
        
        ErrorResponse errorResponse = ErrorResponse.builder()
                .timestamp(Instant.now())
                .status(HttpStatus.BAD_REQUEST.value())
                .error("Invalid Input")
                .message(ex.getMessage())
                .path(request.getRequestURI())
                .correlationId(correlationId)
                .build();

        log.warn("Invalid input [{}]: {}", correlationId, ex.getMessage());
        
        return ResponseEntity.badRequest().body(errorResponse);
    }

    @ExceptionHandler(JsonProcessingException.class)
    public ResponseEntity<ErrorResponse> handleJsonProcessing(
            JsonProcessingException ex,
            HttpServletRequest request
    ) {
        String correlationId = UUID.randomUUID().toString();
        
        ErrorResponse errorResponse = ErrorResponse.builder()
                .timestamp(Instant.now())
                .status(HttpStatus.BAD_REQUEST.value())
                .error("JSON Processing Error")
                .message("Invalid JSON format")
                .path(request.getRequestURI())
                .correlationId(correlationId)
                .build();

        log.error("JSON processing error [{}]: {}", correlationId, ex.getMessage());
        
        return ResponseEntity.badRequest().body(errorResponse);
    }

    @ExceptionHandler(ParseException.class)
    public ResponseEntity<ErrorResponse> handleParseException(
            ParseException ex,
            HttpServletRequest request
    ) {
        String correlationId = UUID.randomUUID().toString();
        
        ErrorResponse errorResponse = ErrorResponse.builder()
                .timestamp(Instant.now())
                .status(HttpStatus.BAD_REQUEST.value())
                .error("Parse Error")
                .message("Unable to parse input")
                .path(request.getRequestURI())
                .correlationId(correlationId)
                .build();

        log.error("Parse error [{}]: {}", correlationId, ex.getMessage());
        
        return ResponseEntity.badRequest().body(errorResponse);
    }

    private String getFieldName(ConstraintViolation<?> violation) {
        String propertyPath = violation.getPropertyPath().toString();
        String[] parts = propertyPath.split("\\.");
        return parts.length > 0 ? parts[parts.length - 1] : propertyPath;
    }
}
