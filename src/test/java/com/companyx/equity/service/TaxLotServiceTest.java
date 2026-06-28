package com.companyx.equity.service;

import com.companyx.equity.model.ClosedLot;
import com.companyx.equity.model.Transaction;
import com.companyx.equity.model.TransactionType;
import com.companyx.equity.model.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for FIFO lot matching (Issue 8: STCG/LTCG) and wash-sale detection (Issue 7).
 */
class TaxLotServiceTest {

    private TaxLotService service;
    private User testUser;
    private TransactionType buyType;
    private TransactionType sellType;

    @BeforeEach
    void setUp() {
        service = new TaxLotService();
        testUser = new User();
        testUser.setId(1L);
        buyType = new TransactionType(1L, TransactionType.BUY);
        sellType = new TransactionType(2L, TransactionType.SALE);
    }

    // ─── STCG / LTCG classification (Issue 8) ────────────────────────────────

    @Test
    void shouldProduceShortTermGainWhenHeldLessThan366Days() {
        // Buy 100 AAPL at $100 on Jan 1; sell 100 at $120 on Dec 31 (364 days) → SHORT gain
        Transaction buy = buy("AAPL", LocalDate.of(2023, 1, 1), 100, 10000);
        Transaction sell = sell("AAPL", LocalDate.of(2023, 12, 31), 100, 12000);

        List<ClosedLot> lots = service.computeClosedLots(List.of(buy, sell));

        assertEquals(1, lots.size());
        ClosedLot lot = lots.get(0);
        assertEquals(ClosedLot.Term.SHORT, lot.getTerm(), "364-day hold is short-term");
        assertEquals(0, new BigDecimal("2000.00").compareTo(lot.getGainLoss()),
                "100 × ($120 − $100) = $2,000 gain");
        assertFalse(lot.isWashSale());
    }

    @Test
    void shouldProduceLongTermGainWhenHeldMoreThan365Days() {
        // Buy 100 AAPL at $100 on Jan 1 2022; sell 100 at $150 on Jan 2 2023 (366 days) → LONG gain
        Transaction buy = buy("AAPL", LocalDate.of(2022, 1, 1), 100, 10000);
        Transaction sell = sell("AAPL", LocalDate.of(2023, 1, 2), 100, 15000);

        List<ClosedLot> lots = service.computeClosedLots(List.of(buy, sell));

        assertEquals(1, lots.size());
        assertEquals(ClosedLot.Term.LONG, lots.get(0).getTerm(), "366-day hold is long-term");
        assertEquals(0, new BigDecimal("5000.00").compareTo(lots.get(0).getGainLoss()));
    }

    @Test
    void shouldMatchOldestLotFirstFifo() {
        // Buy 50 at $10 (Jan), buy 50 at $20 (Jun), sell 50 (Dec)
        // FIFO → sells the Jan lot first → lower gain than LIFO
        Transaction buy1 = buy("XYZ", LocalDate.of(2023, 1, 1), 50, 500);   // $10/share
        Transaction buy2 = buy("XYZ", LocalDate.of(2023, 6, 1), 50, 1000);  // $20/share
        Transaction sell1 = sell("XYZ", LocalDate.of(2023, 12, 1), 50, 1500); // $30/share

        List<ClosedLot> lots = service.computeClosedLots(List.of(buy1, buy2, sell1));

        assertEquals(1, lots.size());
        ClosedLot lot = lots.get(0);
        assertEquals(LocalDate.of(2023, 1, 1), lot.getAcquiredDate(), "FIFO: oldest lot sold first");
        assertEquals(0, new BigDecimal("1000.00").compareTo(lot.getGainLoss()),
                "50 × ($30 − $10) = $1,000 FIFO gain (not $500 LIFO gain)");
    }

    @Test
    void shouldSplitAcrossMultipleLotsWhenSellExceedsSingleLot() {
        // Buy 60 at $10; buy 60 at $20; sell 80 → consumes entire first lot + 20 from second
        Transaction buy1 = buy("MSFT", LocalDate.of(2022, 1, 1), 60, 600);   // $10/share
        Transaction buy2 = buy("MSFT", LocalDate.of(2022, 6, 1), 60, 1200);  // $20/share
        Transaction sell1 = sell("MSFT", LocalDate.of(2023, 1, 10), 80, 2400); // $30/share

        List<ClosedLot> lots = service.computeClosedLots(List.of(buy1, buy2, sell1));

        assertEquals(2, lots.size(), "Sell spans 2 lots");
        ClosedLot lot1 = lots.get(0); // 60 from Jan 2022 lot
        ClosedLot lot2 = lots.get(1); // 20 from Jun 2022 lot
        assertEquals(0, BigDecimal.valueOf(60).compareTo(lot1.getQuantity()));
        assertEquals(0, BigDecimal.valueOf(20).compareTo(lot2.getQuantity()));
        // lot1 gain: 60 × ($30 - $10) = $1200
        assertEquals(0, new BigDecimal("1200.00").compareTo(lot1.getGainLoss()));
        // lot2 gain: 20 × ($30 - $20) = $200
        assertEquals(0, new BigDecimal("200.00").compareTo(lot2.getGainLoss()));
    }

    @Test
    void shouldReturnEmptyListWhenNoSales() {
        Transaction buy = buy("AAPL", LocalDate.of(2023, 1, 1), 100, 10000);
        List<ClosedLot> lots = service.computeClosedLots(List.of(buy));
        assertTrue(lots.isEmpty(), "No sales → no closed lots");
    }

    @Test
    void shouldReturnEmptyListForEmptyTransactions() {
        assertTrue(service.computeClosedLots(List.of()).isEmpty());
    }

    // ─── Wash-sale detection (Issue 7) ────────────────────────────────────────

    @Test
    void shouldFlagWashSaleWhenReplacementBoughtWithin30DaysAfterLossSale() {
        // Sell at a loss on Day 0; buy same security on Day 15 → wash sale
        Transaction buy1 = buy("ENPH", LocalDate.of(2023, 1, 1), 100, 10000); // $100/share
        Transaction sell1 = sell("ENPH", LocalDate.of(2023, 3, 1), 100, 8000); // $80/share → $2,000 loss
        Transaction buy2 = buy("ENPH", LocalDate.of(2023, 3, 15), 100, 8200);  // Day +14 replacement

        List<ClosedLot> lots = service.computeClosedLots(List.of(buy1, sell1, buy2));

        assertEquals(1, lots.size());
        ClosedLot lot = lots.get(0);
        assertTrue(lot.isWashSale(), "Re-buy within 30 days should trigger wash sale");
        assertEquals(0, new BigDecimal("2000.00").compareTo(lot.getDisallowedLoss()),
                "Entire $2,000 loss should be disallowed");
    }

    @Test
    void shouldFlagWashSaleWhenReplacementBoughtWithin30DaysBeforeLossSale() {
        // Buy replacement 20 days before the loss sale → wash sale
        Transaction buy1 = buy("ENPH", LocalDate.of(2023, 1, 1), 100, 10000);  // $100/share
        Transaction buy2 = buy("ENPH", LocalDate.of(2023, 2, 8), 100, 8100);   // Day -21 replacement
        Transaction sell1 = sell("ENPH", LocalDate.of(2023, 3, 1), 100, 8000); // Day 0: $80/share → loss

        List<ClosedLot> lots = service.computeClosedLots(List.of(buy1, buy2, sell1));

        // The loss sale is on the first lot (FIFO); buy2 is within 30 days before
        ClosedLot lossSale = lots.stream()
                .filter(l -> l.getSoldDate().equals(LocalDate.of(2023, 3, 1)))
                .findFirst()
                .orElseThrow();
        assertTrue(lossSale.isWashSale(), "Pre-sale replacement within 30 days triggers wash sale");
    }

    @Test
    void shouldNotFlagWashSaleWhenReplacementIsOutside30DayWindow() {
        // Loss sale on Day 0; next buy is Day 45 → outside window → NOT a wash sale
        Transaction buy1 = buy("ENPH", LocalDate.of(2023, 1, 1), 100, 10000);
        Transaction sell1 = sell("ENPH", LocalDate.of(2023, 3, 1), 100, 8000); // loss
        Transaction buy2 = buy("ENPH", LocalDate.of(2023, 4, 15), 100, 8200);  // Day +45

        List<ClosedLot> lots = service.computeClosedLots(List.of(buy1, sell1, buy2));

        assertEquals(1, lots.size());
        assertFalse(lots.get(0).isWashSale(), "45-day gap is outside the 30-day wash sale window");
    }

    @Test
    void shouldNotFlagWashSaleForGainingSales() {
        // Sale at a gain is never a wash sale, regardless of subsequent purchases
        Transaction buy1 = buy("AAPL", LocalDate.of(2023, 1, 1), 100, 10000);
        Transaction sell1 = sell("AAPL", LocalDate.of(2023, 3, 1), 100, 12000); // gain
        Transaction buy2 = buy("AAPL", LocalDate.of(2023, 3, 10), 100, 11000);  // Day +9

        List<ClosedLot> lots = service.computeClosedLots(List.of(buy1, sell1, buy2));

        assertEquals(1, lots.size());
        assertFalse(lots.get(0).isWashSale(), "Gains are never wash sales");
        assertEquals(0, BigDecimal.ZERO.compareTo(lots.get(0).getDisallowedLoss()));
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private Transaction buy(String symbol, LocalDate date, long qty, long totalCost) {
        return new Transaction(null,
                Timestamp.valueOf(date.atTime(12, 0)),
                symbol,
                BigDecimal.valueOf(qty),
                BigDecimal.valueOf(totalCost),
                testUser,
                buyType);
    }

    private Transaction sell(String symbol, LocalDate date, long qty, long totalProceeds) {
        return new Transaction(null,
                Timestamp.valueOf(date.atTime(12, 0)),
                symbol,
                BigDecimal.valueOf(qty),
                BigDecimal.valueOf(totalProceeds),
                testUser,
                sellType);
    }
}
