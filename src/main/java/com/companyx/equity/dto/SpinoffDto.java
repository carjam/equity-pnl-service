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
public class SpinoffDto {
    private LocalDate date;
    private String spunoffSymbol;
    private BigDecimal distributionRatio;
}
