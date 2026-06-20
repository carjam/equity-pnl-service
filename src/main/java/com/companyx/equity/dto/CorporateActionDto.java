package com.companyx.equity.dto;

import com.companyx.equity.model.corporateaction.CorporateActionType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CorporateActionDto {
    private CorporateActionType type;
    private LocalDate date;
    private Object details;
}
