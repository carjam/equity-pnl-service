package com.companyx.equity.service;

import com.companyx.equity.model.Position;
import com.companyx.equity.model.User;
import com.companyx.equity.model.corporateaction.Delisting;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class DelistingServiceTest {

    private DelistingService service;
    private User testUser;

    @BeforeEach
    void setUp() {
        service = new DelistingService();
        testUser = new User();
        testUser.setId(1L);
    }

    // ─── Worthless delisting (existing behaviour) ─────────────────────────────

    @Test
    void shouldRecognizeFullLossWhenDelistedWorthless() {
        // 100 shares with $3,000 cost basis, delisted worthless → $3,000 realized loss
        Position position = createPosition("SPCE", 100, new BigDecimal("-3000.00"));
        Delisting delisting = Delisting.builder()
                .symbol("SPCE")
                .date(LocalDate.of(2024, 6, 1))
                .build();

        Position result = service.applyDelisting(position, delisting);

        assertEquals(0, new BigDecimal("-3000.000000").compareTo(result.getRealized()),
                "Full basis should be recognized as a realized loss for worthless delisting");
        assertEquals(0, BigDecimal.ZERO.compareTo(result.getQuantity()),
                "Quantity should be zero after delisting");
        assertEquals(0, BigDecimal.ZERO.compareTo(result.getValue().stripTrailingZeros()),
                "Cost basis should be zero after delisting");
    }

    @Test
    void shouldReturnUnchangedPositionWhenQuantityIsZero() {
        Position position = createPosition("SPCE", 0, BigDecimal.ZERO);
        Delisting delisting = Delisting.builder()
                .symbol("SPCE")
                .date(LocalDate.of(2024, 6, 1))
                .build();

        Position result = service.applyDelisting(position, delisting);

        assertEquals(0, BigDecimal.ZERO.compareTo(result.getQuantity()));
    }

    // ─── Cash-per-share delisting (new behaviour) ─────────────────────────────

    @Test
    void shouldComputeProceedsWhenCashPerShareProvided() {
        // 100 shares at $30 cost basis; going-private at $54.20/share
        // Proceeds = 100 × $54.20 = $5,420; basis = −$3,000; realized gain = $2,420
        Position position = createPosition("TWTR", 100, new BigDecimal("-3000.00"));
        Delisting delisting = Delisting.builder()
                .symbol("TWTR")
                .date(LocalDate.of(2022, 10, 27))
                .cashPerShare(new BigDecimal("54.20"))
                .build();

        Position result = service.applyDelisting(position, delisting);

        assertEquals(0, new BigDecimal("2420.000000").compareTo(result.getRealized()),
                "100 × $54.20 − $3,000 basis = $2,420 realized gain");
        assertEquals(0, BigDecimal.ZERO.compareTo(result.getQuantity()),
                "Quantity should be zero after cash-out delisting");
        assertEquals(0, BigDecimal.ZERO.compareTo(result.getValue().stripTrailingZeros()),
                "Cost basis should be zero after cash-out delisting");
    }

    @Test
    void shouldHandlePartialRecovery() {
        // 200 shares with $10,000 basis; delisted at $20/share partial recovery
        // Proceeds = 200 × $20 = $4,000; realized loss = $4,000 − $10,000 = −$6,000
        Position position = createPosition("XYZ", 200, new BigDecimal("-10000.00"));
        Delisting delisting = Delisting.builder()
                .symbol("XYZ")
                .date(LocalDate.of(2023, 3, 15))
                .cashPerShare(new BigDecimal("20.00"))
                .build();

        Position result = service.applyDelisting(position, delisting);

        assertEquals(0, new BigDecimal("-6000.000000").compareTo(result.getRealized()),
                "200 × $20 = $4,000 proceeds; $4,000 − $10,000 basis = −$6,000 realized loss");
    }

    @Test
    void shouldTreatZeroCashPerShareAsWorthless() {
        // Explicit cashPerShare of 0 must behave the same as a worthless delisting
        Position position = createPosition("ENRN", 100, new BigDecimal("-5000.00"));
        Delisting delisting = Delisting.builder()
                .symbol("ENRN")
                .date(LocalDate.of(2001, 12, 2))
                .cashPerShare(BigDecimal.ZERO)
                .build();

        Position result = service.applyDelisting(position, delisting);

        assertEquals(0, new BigDecimal("-5000.000000").compareTo(result.getRealized()),
                "cashPerShare=0 is equivalent to worthless delisting");
    }

    @Test
    void shouldAccumulateExistingRealizedGainOnDelisting() {
        // Position already has $500 realized gain from prior sales; delisted worthless
        Position position = createPosition("FAIL", 50, new BigDecimal("-1000.00"));
        position.setRealized(new BigDecimal("500.00"));
        Delisting delisting = Delisting.builder()
                .symbol("FAIL")
                .date(LocalDate.of(2024, 1, 5))
                .build();

        Position result = service.applyDelisting(position, delisting);

        // Pre-existing $500 + realized loss of −$1,000 = −$500
        assertEquals(0, new BigDecimal("-500.000000").compareTo(result.getRealized()),
                "Existing realized P&L should be preserved and combined with delisting loss");
    }

    @Test
    void shouldThrowWhenPositionIsNull() {
        Delisting delisting = Delisting.builder()
                .symbol("XYZ")
                .date(LocalDate.of(2024, 1, 1))
                .build();
        assertThrows(IllegalArgumentException.class, () -> service.applyDelisting(null, delisting));
    }

    @Test
    void shouldThrowWhenDelistingIsNull() {
        Position position = createPosition("XYZ", 100, new BigDecimal("-1000.00"));
        assertThrows(IllegalArgumentException.class, () -> service.applyDelisting(position, null));
    }

    private Position createPosition(String symbol, long quantity, BigDecimal value) {
        Position position = new Position();
        position.setSymbol(symbol);
        position.setQuantity(BigDecimal.valueOf(quantity));
        position.setValue(value);
        position.setTimestamp(Timestamp.valueOf(LocalDateTime.now()));
        position.setUser(testUser);
        position.setRealized(BigDecimal.ZERO);
        position.setUnrealized(BigDecimal.ZERO);
        return position;
    }
}
