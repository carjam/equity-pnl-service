package com.companyx.equity.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TotalReturnResponse {
    private String symbol;
    private BigDecimal capitalGain;
    private BigDecimal dividendIncome;
    private BigDecimal totalReturn;
    private BigDecimal totalReturnPct;
}
