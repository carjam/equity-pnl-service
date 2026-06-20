package com.companyx.equity.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for Finnhub dividend API response
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class FinnhubDividendDto {
    
    private String symbol;
    
    private String date; // Pay date (yyyy-MM-dd)
    
    private Double amount;
    
    private Double adjustedAmount;
    
    private String currency;
    
    @JsonProperty("declarationDate")
    private String declarationDate; // yyyy-MM-dd
    
    @JsonProperty("exDividendDate")
    private String exDividendDate; // yyyy-MM-dd
    
    private String recordDate; // yyyy-MM-dd
    
    private String payDate; // yyyy-MM-dd
    
    private Integer frequency;
}
