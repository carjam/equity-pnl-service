package com.companyx.equity.dto;

import com.companyx.equity.model.Position;
import lombok.Getter;

import java.time.LocalDate;
import java.util.Map;

/**
 * API response for GET /pnl.
 *
 * Wraps positions with a methodology section so consumers know the calculation
 * basis and are not misled into using these figures for tax reporting. Cost basis
 * is calculated using Average Cost (AVCO), which is acceptable for performance
 * measurement but does not satisfy IRS requirements (IRC §1012 requires FIFO or
 * specific identification for equities unless the taxpayer has elected otherwise).
 */
@Getter
public class PnLResponseDto {

    public static final String BASIS = "AVCO";
    public static final String REPORTING_TYPE = "performance";
    public static final String DISCLAIMER =
            "Positions are calculated using Average Cost (AVCO) methodology for performance " +
            "tracking purposes only. This output is NOT suitable for tax reporting. " +
            "U.S. tax regulations (IRC §1012) require FIFO or specific identification " +
            "for equity cost basis reporting. Consult a qualified tax advisor for " +
            "tax-lot-level reporting.";

    private final Map<String, Position> positions;
    private final Methodology methodology;

    public PnLResponseDto(Map<String, Position> positions, LocalDate from, LocalDate to) {
        this.positions = positions;
        this.methodology = new Methodology(from, to);
    }

    @Getter
    public static class Methodology {
        private final String basis = BASIS;
        private final String reportingType = REPORTING_TYPE;
        private final String disclaimer = DISCLAIMER;
        private final Period period;

        Methodology(LocalDate from, LocalDate to) {
            this.period = new Period(from, to);
        }

        @Getter
        public static class Period {
            private final LocalDate from;
            private final LocalDate to;

            Period(LocalDate from, LocalDate to) {
                this.from = from;
                this.to = to;
            }
        }
    }
}
