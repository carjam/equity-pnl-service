package com.companyx.equity.dto;

import com.companyx.equity.model.ClosedLot;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * API response for GET /pnl/tax-lots.
 *
 * Provides FIFO lot matching with STCG/LTCG classification (IRC §1222) and
 * wash-sale flagging (IRC §1091). This is informational only — the service
 * uses AVCO for performance P&L and this output does not constitute tax advice.
 */
@Getter
@Builder
public class TaxLotReportDto {

    public static final String DISCLAIMER =
            "Tax-lot data is computed using FIFO and is provided for informational purposes only. " +
            "Consult a qualified tax advisor. Wash-sale disallowed losses are flagged but not " +
            "automatically carried forward to replacement-lot basis in this system.";

    private final String symbol;
    private final LocalDate from;
    private final LocalDate to;
    private final List<ClosedLot> closedLots;
    private final BigDecimal shortTermGainLoss;
    private final BigDecimal longTermGainLoss;
    private final BigDecimal totalGainLoss;
    private final BigDecimal washSaleDisallowedLoss;
    private final String disclaimer;
}
