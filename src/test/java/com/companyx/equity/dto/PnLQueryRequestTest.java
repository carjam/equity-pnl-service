package com.companyx.equity.dto;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for PnLQueryRequest DTO validation
 */
public class PnLQueryRequestTest {
    
    @Test
    public void testValidDateRange() {
        PnLQueryRequest request = new PnLQueryRequest(
                LocalDate.of(2024, 1, 1),
                LocalDate.of(2024, 1, 31)
        );
        
        assertTrue(request.isValidDateRange());
        assertTrue(request.isReasonableDateRange());
    }
    
    @Test
    public void testInvalidDateRange_ToBeforeFrom() {
        PnLQueryRequest request = new PnLQueryRequest(
                LocalDate.of(2024, 1, 31),
                LocalDate.of(2024, 1, 1)
        );
        
        assertFalse(request.isValidDateRange());
    }
    
    @Test
    public void testEqualDates() {
        LocalDate date = LocalDate.of(2024, 1, 15);
        PnLQueryRequest request = new PnLQueryRequest(date, date);
        
        assertTrue(request.isValidDateRange());
    }
    
    @Test
    public void testUnreasonableDateRange_MoreThan5Years() {
        PnLQueryRequest request = new PnLQueryRequest(
                LocalDate.of(2019, 1, 1),
                LocalDate.of(2025, 1, 1)
        );
        
        assertFalse(request.isReasonableDateRange());
    }
    
    @Test
    public void testExactly5YearRange() {
        PnLQueryRequest request = new PnLQueryRequest(
                LocalDate.of(2020, 1, 1),
                LocalDate.of(2025, 1, 1)
        );
        
        assertTrue(request.isReasonableDateRange());
    }
    
    @Test
    public void testNullDates() {
        PnLQueryRequest request = new PnLQueryRequest(null, null);
        
        // Validation methods should return true when dates are null
        assertTrue(request.isValidDateRange());
        assertTrue(request.isReasonableDateRange());
    }
    
    @Test
    public void testPartiallyNullDates() {
        PnLQueryRequest request1 = new PnLQueryRequest(
                LocalDate.of(2024, 1, 1),
                null
        );
        
        assertTrue(request1.isValidDateRange());
        assertTrue(request1.isReasonableDateRange());
        
        PnLQueryRequest request2 = new PnLQueryRequest(
                null,
                LocalDate.of(2024, 1, 31)
        );
        
        assertTrue(request2.isValidDateRange());
        assertTrue(request2.isReasonableDateRange());
    }
}
