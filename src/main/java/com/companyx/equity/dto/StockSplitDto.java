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
public class StockSplitDto {
    private LocalDate date;
    private int fromFactor;
    private int toFactor;
    private BigDecimal splitRatio;
    private String type;
}
