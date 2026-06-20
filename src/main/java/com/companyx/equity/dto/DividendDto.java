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
public class DividendDto {
    private LocalDate exDate;
    private LocalDate payDate;
    private BigDecimal amount;
    private String currency;
    private String type;
}
