package com.companyx.equity.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TotalReturnResponse {
    private String symbol;
    private Period period;
    private BigDecimal capitalGain;
    private BigDecimal dividendIncome;
    private BigDecimal totalReturn;
    /** Holding Period Return: totalReturn / beginningCostBasis. */
    private BigDecimal hpr;
    /** Annualized HPR using geometric compounding: (1 + HPR)^(365/days) − 1. */
    private BigDecimal annualizedReturn;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Period {
        private LocalDate from;
        private LocalDate to;
    }
}
