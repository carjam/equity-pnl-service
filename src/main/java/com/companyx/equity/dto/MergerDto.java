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
public class MergerDto {
    private LocalDate date;
    private String acquirerSymbol;
    private String type;
    private BigDecimal exchangeRatio;
    private BigDecimal cashPerShare;
}
