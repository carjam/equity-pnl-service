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
public class SymbolChangesResponse {
    private String symbol;
    private List<SymbolChangeDto> symbolChanges;
    private int count;
}
