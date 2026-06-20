package com.companyx.equity.service;

import com.companyx.equity.TestDataBuilder;
import com.companyx.equity.dto.CandleDto;
import com.companyx.equity.model.Position;
import com.companyx.equity.model.TransactionType;
import com.companyx.equity.model.User;
import com.companyx.equity.provider.FixtureCorporateActionData;
import com.companyx.equity.provider.FixtureCorporateActionProvider;
import com.companyx.equity.repository.FinhubRepository;
import com.companyx.equity.repository.TransactionRepository;
import com.companyx.equity.repository.UserRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * End-to-end P&amp;L tests using {@link FixtureCorporateActionProvider} with SEC-documented
 * real-world scenarios (Disney/Fox merger, eBay/PayPal spinoff).
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Real-World Corporate Actions P&L End-to-End")
class RealWorldCorporateActionsPnLEndToEndTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private FinhubRepository finhubRepository;

    private PnLService pnLService;

    private User testUser;

    @BeforeEach
    void setUp() {
        FixtureCorporateActionProvider fixtureProvider = new FixtureCorporateActionProvider();

        CorporateActionService corporateActionService = new CorporateActionService(
                fixtureProvider,
                new SplitAdjustmentService(),
                new DividendService(),
                new MergerService(),
                new SpinoffService(),
                new SymbolMappingService(),
                new DelistingService()
        );
        pnLService = new PnLService(
                userRepository,
                transactionRepository,
                finhubRepository,
                corporateActionService
        );

        testUser = TestDataBuilder.createTestUser("test-user", "password");
    }

    @Test
    @DisplayName("FOX→DIS stock merger (0.4517 ratio, March 2019) transfers cost basis to DIS")
    void foxDisMergerEndToEnd() throws JsonProcessingException {
        when(userRepository.findByUid("test-user")).thenReturn(Optional.of(testUser));
        when(transactionRepository.findAllBefore(anyInt(), any(Date.class))).thenReturn(List.of(
                TestDataBuilder.createDepositTransaction(testUser,
                        TestDataBuilder.createTransactionType(3, TransactionType.DEPOSIT),
                        LocalDateTime.of(2019, 1, 1, 10, 0), 50_000.0),
                TestDataBuilder.createBuyTransaction(testUser,
                        TestDataBuilder.createTransactionType(1, TransactionType.BUY),
                        "FOX", LocalDateTime.of(2019, 1, 15, 10, 0), 100, 5_000.0)
        ));
        when(transactionRepository.findAllBetween(anyInt(), any(Date.class), any(Date.class)))
                .thenReturn(List.of());
        when(transactionRepository.findEarliestByUserAndSymbol(1, "FOX"))
                .thenReturn(Optional.of(java.sql.Timestamp.valueOf(LocalDateTime.of(2019, 1, 15, 10, 0))));

        // 100 FOX × 0.4517 = 45.17 → 45 DIS shares (rounded)
        Date end = java.sql.Date.valueOf("2019-12-31");
        stubEndPrice("DIS", end, new BigDecimal("114.18"));

        Date start = java.sql.Date.valueOf("2019-01-01");

        Map<String, Position> result = pnLService.getPositions("test-user", start, end);

        assertNull(result.get("FOX"), "FOX should be replaced by DIS after merger");
        Position dis = result.get("DIS");
        assertNotNull(dis);
        assertEquals(BigInteger.valueOf(45), dis.getQuantity(),
                "100 FOX shares × 0.4517 exchange ratio → 45 DIS shares");
        assertEquals(0, new BigDecimal("-5000.00").compareTo(dis.getValue()),
                "Total cost basis should transfer unchanged from FOX to DIS");
    }

    @Test
    @DisplayName("EBAY→PYPL spinoff (1:1, July 2015) allocates basis per Form 8937 FMV")
    void ebayPyplSpinoffEndToEnd() throws JsonProcessingException {
        when(userRepository.findByUid("test-user")).thenReturn(Optional.of(testUser));
        when(transactionRepository.findAllBefore(anyInt(), any(Date.class))).thenReturn(List.of(
                TestDataBuilder.createDepositTransaction(testUser,
                        TestDataBuilder.createTransactionType(3, TransactionType.DEPOSIT),
                        LocalDateTime.of(2015, 1, 1, 10, 0), 100_000.0),
                TestDataBuilder.createBuyTransaction(testUser,
                        TestDataBuilder.createTransactionType(1, TransactionType.BUY),
                        "EBAY", LocalDateTime.of(2015, 1, 15, 10, 0), 100, 50_000.0)
        ));
        when(transactionRepository.findAllBetween(anyInt(), any(Date.class), any(Date.class)))
                .thenReturn(List.of());
        when(transactionRepository.findEarliestByUserAndSymbol(1, "EBAY"))
                .thenReturn(Optional.of(java.sql.Timestamp.valueOf(LocalDateTime.of(2015, 1, 15, 10, 0))));

        LocalDate distributionDate = FixtureCorporateActionData.EBAY_PYPL_SPINOFF.getDate();
        stubDistributionPrices(distributionDate);

        BigDecimal totalBasis = new BigDecimal("-50000.00");
        BigDecimal expectedEbayBasis = totalBasis.multiply(FixtureCorporateActionData.EBAY_PYPL_PARENT_BASIS_FRACTION)
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal expectedPyplBasis = totalBasis.multiply(FixtureCorporateActionData.EBAY_PYPL_SPINOFF_BASIS_FRACTION)
                .setScale(2, RoundingMode.HALF_UP);

        Date end = java.sql.Date.valueOf("2015-12-31");
        stubEndPrice("EBAY", end, expectedEbayBasis.abs().divide(new BigDecimal("100"), 6, RoundingMode.HALF_UP));
        stubEndPrice("PYPL", end, expectedPyplBasis.abs().divide(new BigDecimal("100"), 6, RoundingMode.HALF_UP));

        Date start = java.sql.Date.valueOf("2015-01-01");

        Map<String, Position> result = pnLService.getPositions("test-user", start, end);

        Position ebay = result.get("EBAY");
        Position pypl = result.get("PYPL");
        assertNotNull(ebay);
        assertNotNull(pypl);

        assertEquals(BigInteger.valueOf(100), ebay.getQuantity());
        assertEquals(BigInteger.valueOf(100), pypl.getQuantity(),
                "1:1 distribution — one PYPL share per EBAY share");

        assertEquals(0, expectedEbayBasis.compareTo(ebay.getValue()),
                "EBAY basis should be 39.2706% of original per Form 8937");
        assertEquals(0, expectedPyplBasis.compareTo(pypl.getValue()),
                "PYPL basis should be 60.7294% of original per Form 8937");
        assertEquals(0, totalBasis.compareTo(ebay.getValue().add(pypl.getValue())),
                "Parent + spinoff basis should equal original aggregate basis");

        assertEquals(0, new BigDecimal("0.00").compareTo(ebay.getUnrealized()),
                "EBAY unrealized should be break-even at allocated basis per share");
        assertEquals(0, new BigDecimal("0.00").compareTo(pypl.getUnrealized()),
                "PYPL unrealized should be break-even at allocated basis per share");
    }

    private void stubDistributionPrices(LocalDate distributionDate) throws JsonProcessingException {
        Date sqlDate = java.sql.Date.valueOf(distributionDate);

        CandleDto ebayCandle = new CandleDto();
        ebayCandle.setClose(List.of(FixtureCorporateActionData.EBAY_PYPL_DISTRIBUTION_EBAY_PRICE));
        ebayCandle.setStatus("ok");
        when(finhubRepository.getCandle(eq("EBAY"), eq(sqlDate), eq(sqlDate))).thenReturn(ebayCandle);

        CandleDto pyplCandle = new CandleDto();
        pyplCandle.setClose(List.of(FixtureCorporateActionData.EBAY_PYPL_DISTRIBUTION_PYPL_PRICE));
        pyplCandle.setStatus("ok");
        when(finhubRepository.getCandle(eq("PYPL"), eq(sqlDate), eq(sqlDate))).thenReturn(pyplCandle);
    }

    private void stubEndPrice(String symbol, Date end, BigDecimal price) throws JsonProcessingException {
        CandleDto candle = new CandleDto();
        candle.setClose(List.of(price));
        candle.setStatus("ok");
        when(finhubRepository.getCandle(eq(symbol), eq(end), eq(end))).thenReturn(candle);
    }
}
