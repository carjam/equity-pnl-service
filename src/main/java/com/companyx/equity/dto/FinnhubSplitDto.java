package com.companyx.equity.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for Finnhub stock split API response
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class FinnhubSplitDto {
    
    private String symbol;
    
    private String date; // Effective date (yyyy-MM-dd)
    
    private Integer fromFactor;
    
    private Integer toFactor;
}
