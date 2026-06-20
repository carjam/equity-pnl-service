package com.companyx.equity.error;

import com.companyx.equity.dto.ErrorResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.text.ParseException;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

/**
 * Tests for RestExceptionHandler
 */
@ExtendWith(MockitoExtension.class)
public class RestExceptionHandlerTest {
    
    @InjectMocks
    private RestExceptionHandler exceptionHandler;
    
    @Mock
    private HttpServletRequest request;
    
    @Mock
    private BindingResult bindingResult;
    
    @BeforeEach
    public void setup() {
        when(request.getRequestURI()).thenReturn("/api/v1/test");
    }
    
    @Test
    public void testHandleValidationException() {
        FieldError fieldError = new FieldError("object", "field", "rejected", false, 
                null, null, "Field is invalid");
        when(bindingResult.getFieldErrors()).thenReturn(Arrays.asList(fieldError));
        
        MethodArgumentNotValidException ex = new MethodArgumentNotValidException(null, bindingResult);
        
        ResponseEntity<ErrorResponse> response = exceptionHandler.handleValidationException(ex, request);
        
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(HttpStatus.BAD_REQUEST.value(), response.getBody().getStatus());
        assertEquals("Validation Failed", response.getBody().getError());
        assertNotNull(response.getBody().getCorrelationId());
        assertNotNull(response.getBody().getValidationErrors());
        assertEquals(1, response.getBody().getValidationErrors().size());
    }
    
    @Test
    public void testHandleTypeMismatch() {
        MethodArgumentTypeMismatchException ex = new MethodArgumentTypeMismatchException(
                "invalid", String.class, "paramName", null, null);
        
        ResponseEntity<ErrorResponse> response = exceptionHandler.handleTypeMismatch(ex, request);
        
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("Type Mismatch", response.getBody().getError());
        assertTrue(response.getBody().getMessage().contains("paramName"));
    }
    
    @Test
    public void testHandleIllegalArgument() {
        IllegalArgumentException ex = new IllegalArgumentException("Invalid input");
        
        ResponseEntity<ErrorResponse> response = exceptionHandler.handleIllegalArgument(ex, request);
        
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("Invalid Argument", response.getBody().getError());
        assertEquals("Invalid input", response.getBody().getMessage());
    }
    
    @Test
    public void testHandleResponseVerificationException() {
        ResponseVerificationException ex = new ResponseVerificationException("Verification failed");
        
        ResponseEntity<ErrorResponse> response = exceptionHandler.handleResponseVerification(ex, request);
        
        assertEquals(HttpStatus.BAD_GATEWAY, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("External Service Error", response.getBody().getError());
    }
    
    @Test
    public void testHandleVendorConnectivityException() {
        VendorConnectivityException ex = new VendorConnectivityException("Cannot connect");
        
        ResponseEntity<ErrorResponse> response = exceptionHandler.handleVendorConnectivity(ex, request);
        
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("Service Unavailable", response.getBody().getError());
    }
    
    @Test
    public void testHandleUnexpectedValueException() {
        UnexpectedValueException ex = new UnexpectedValueException("Unexpected value");
        
        ResponseEntity<ErrorResponse> response = exceptionHandler.handleUnexpectedValue(ex, request);
        
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("Internal Server Error", response.getBody().getError());
    }
    
    @Test
    public void testHandleJsonProcessingException() {
        JsonProcessingException ex = new JsonProcessingException("JSON error") {};
        
        ResponseEntity<ErrorResponse> response = exceptionHandler.handleJsonProcessing(ex, request);
        
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("JSON Processing Error", response.getBody().getError());
    }
    
    @Test
    public void testHandleParseException() {
        ParseException ex = new ParseException("Parse error", 0);
        
        ResponseEntity<ErrorResponse> response = exceptionHandler.handleParseException(ex, request);
        
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("Parse Error", response.getBody().getError());
    }
    
    @Test
    public void testHandleUserNotFoundException() {
        UserNotFoundException ex = new UserNotFoundException("test-user");
        
        ResponseEntity<ErrorResponse> response = exceptionHandler.handleUserNotFound(ex, request);
        
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("User Not Found", response.getBody().getError());
        assertTrue(response.getBody().getMessage().contains("User not found"));
        assertNotNull(response.getBody().getCorrelationId());
    }
    
    @Test
    public void testHandleTransactionNotFoundException() {
        TransactionNotFoundException ex = new TransactionNotFoundException("test-user", 123L);
        
        ResponseEntity<ErrorResponse> response = exceptionHandler.handleTransactionNotFound(ex, request);
        
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("Transaction Not Found", response.getBody().getError());
        assertTrue(response.getBody().getMessage().contains("Transaction not found"));
        assertNotNull(response.getBody().getCorrelationId());
    }
    
    @Test
    public void testHandleInvalidInputException() {
        InvalidInputException ex = new InvalidInputException("Invalid date range");
        
        ResponseEntity<ErrorResponse> response = exceptionHandler.handleInvalidInput(ex, request);
        
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("Invalid Input", response.getBody().getError());
        assertEquals("Invalid date range", response.getBody().getMessage());
        assertNotNull(response.getBody().getCorrelationId());
    }
}
