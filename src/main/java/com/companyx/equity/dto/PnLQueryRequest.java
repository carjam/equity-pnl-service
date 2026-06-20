package com.companyx.equity.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

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
            return true;
        }
        return !to.isBefore(from);
    }
    
    @AssertTrue(message = "Date range cannot exceed 5 years")
    public boolean isReasonableDateRange() {
        if (from == null || to == null) {
            return true;
        }
        return ChronoUnit.YEARS.between(from, to) <= 5;
    }
}
