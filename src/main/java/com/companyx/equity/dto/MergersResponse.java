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
public class MergersResponse {
    private String symbol;
    private List<MergerDto> mergers;
    private int count;
}
