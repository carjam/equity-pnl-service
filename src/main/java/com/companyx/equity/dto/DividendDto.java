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
    /** True = IRC §1(h)(11) qualified; false = ordinary; null = undetermined. */
    private Boolean qualified;
}
