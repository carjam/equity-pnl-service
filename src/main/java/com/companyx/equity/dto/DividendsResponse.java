package com.companyx.equity.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DividendsResponse {
    private String symbol;
    private List<DividendDto> dividends;
    private BigDecimal totalAmount;
    private int count;
}
