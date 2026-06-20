package com.companyx.equity.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DelistingsResponse {
    private String symbol;
    private List<DelistingDto> delistings;
    private int count;
}
