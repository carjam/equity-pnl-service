package com.companyx.equity.service;

import com.companyx.equity.TestDataBuilder;
import com.companyx.equity.dto.CandleDto;
import com.companyx.equity.dto.MarkDto;
import com.companyx.equity.model.Position;
import com.companyx.equity.model.Transaction;
import com.companyx.equity.model.TransactionType;
import com.companyx.equity.model.User;
import com.companyx.equity.model.corporateaction.Dividend;
import com.companyx.equity.model.corporateaction.DividendType;
import com.companyx.equity.model.corporateaction.Merger;
import com.companyx.equity.model.corporateaction.MergerType;
import com.companyx.equity.model.corporateaction.StockSplit;
import com.companyx.equity.provider.CorporateActionProvider;
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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * End-to-end tests wiring real corporate action services through {@link PnLService}.
 * Provider data mimics Finnhub responses without calling the external API.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Corporate Actions P&L End-to-End")
class CorporateActionsPnLEndToEndTest {

    private static final StockSplit AAPL_FOUR_FOR_ONE = StockSplit.builder()
            .symbol("AAPL")
            .date(LocalDate.of(2020, 8, 31))
            .fromFactor(1)
            .toFactor(4)
            .build();

    private static final Merger XYZ_STOCK_MERGER = Merger.builder()
            .symbol("XYZ")
            .acquirerSymbol("ABC")
            .date(LocalDate.of(2024, 6, 1))
            .type(MergerType.STOCK_FOR_STOCK)
            .exchangeRatio(new BigDecimal("0.8"))
            .build();

    private static final List<Dividend> KO_2024_QUARTERLY_DIVIDENDS = List.of(
            cashDividend("KO", LocalDate.of(2024, 3, 15), "0.25"),
            cashDividend("KO", LocalDate.of(2024, 6, 15), "0.25"),
            cashDividend("KO", LocalDate.of(2024, 9, 15), "0.25"),
            cashDividend("KO", LocalDate.of(2024, 12, 15), "0.25")
    );

    @Mock
    private CorporateActionProvider corporateActionProvider;

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
        stubProvider();

        CorporateActionService         corporateActionService = new CorporateActionService(
                corporateActionProvider,
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
    @DisplayName("AAPL 4:1 split produces break-even unrealized at post-split market price")
    void aaplSplitEndToEnd() throws JsonProcessingException {
        when(userRepository.findByUid("test-user")).thenReturn(Optional.of(testUser));
        when(transactionRepository.findAllBefore(anyLong(), any(Date.class))).thenReturn(List.of(
                TestDataBuilder.createDepositTransaction(testUser,
                        TestDataBuilder.createTransactionType(3, TransactionType.DEPOSIT),
                        LocalDateTime.of(2020, 1, 1, 10, 0), 50_000.0),
                TestDataBuilder.createBuyTransaction(testUser,
                        TestDataBuilder.createTransactionType(1, TransactionType.BUY),
                        "AAPL", LocalDateTime.of(2020, 1, 15, 10, 0), 100, 20_000.0)
        ));
        when(transactionRepository.findAllBetween(anyLong(), any(Date.class), any(Date.class)))
                .thenReturn(List.of());
        when(transactionRepository.findEarliestByUserAndSymbol(1L, "AAPL"))
                .thenReturn(Optional.of(java.sql.Timestamp.valueOf(LocalDateTime.of(2020, 1, 15, 10, 0))));

        MarkDto mark = new MarkDto();
        mark.setCurrentPrice(new BigDecimal("50.00"));
        when(finhubRepository.getMark("AAPL")).thenReturn(mark);

        Date start = java.sql.Date.valueOf("2020-01-01");
        Date end = java.sql.Date.valueOf(LocalDate.now());

        Map<String, Position> result = pnLService.getPositions("test-user", start, end);
        Position aapl = result.get("AAPL");

        assertNotNull(aapl);
        assertEquals(BigInteger.valueOf(400), aapl.getQuantity(),
                "100 shares should become 400 after the 4:1 split");
        assertEquals(0, new BigDecimal("0.00").compareTo(aapl.getUnrealized()),
                "400 shares @ $50 with $20,000 basis should be break-even");
        assertEquals(0, new BigDecimal("-20000.00").compareTo(aapl.getValue()),
                "Total cost basis should be unchanged by the split");
    }

    @Test
    @DisplayName("KO quarterly dividends add to realized P&L while unrealized reflects price move")
    void koDividendEndToEnd() throws JsonProcessingException {
        when(userRepository.findByUid("test-user")).thenReturn(Optional.of(testUser));
        when(transactionRepository.findAllBefore(anyLong(), any(Date.class))).thenReturn(List.of(
                TestDataBuilder.createDepositTransaction(testUser,
                        TestDataBuilder.createTransactionType(3, TransactionType.DEPOSIT),
                        LocalDateTime.of(2024, 1, 1, 10, 0), 10_000.0),
                TestDataBuilder.createBuyTransaction(testUser,
                        TestDataBuilder.createTransactionType(1, TransactionType.BUY),
                        "KO", LocalDateTime.of(2024, 1, 15, 10, 0), 100, 6_000.0)
        ));
        when(transactionRepository.findAllBetween(anyLong(), any(Date.class), any(Date.class)))
                .thenReturn(List.of());
        when(transactionRepository.findEarliestByUserAndSymbol(1L, "KO"))
                .thenReturn(Optional.of(java.sql.Timestamp.valueOf(LocalDateTime.of(2024, 1, 15, 10, 0))));

        CandleDto candle = new CandleDto();
        candle.setClose(List.of(new BigDecimal("63.00")));
        candle.setStatus("ok");
        when(finhubRepository.getCandle(eq("KO"), any(Date.class), any(Date.class))).thenReturn(candle);

        Date start = java.sql.Date.valueOf("2024-01-01");
        Date end = java.sql.Date.valueOf("2024-12-31");

        Map<String, Position> result = pnLService.getPositions("test-user", start, end);
        Position ko = result.get("KO");

        assertNotNull(ko);
        assertEquals(BigInteger.valueOf(100), ko.getQuantity());
        assertEquals(0, new BigDecimal("100.00").compareTo(ko.getRealized()),
                "Four $0.25 dividends on 100 shares = $100 income");
        assertEquals(0, new BigDecimal("300.00").compareTo(ko.getUnrealized()),
                "Price move from $60 to $63 on 100 shares = $300 unrealized gain");
    }

    @Test
    @DisplayName("Stock-for-stock merger retitles position under acquirer symbol")
    void stockMergerEndToEnd() throws JsonProcessingException {
        when(userRepository.findByUid("test-user")).thenReturn(Optional.of(testUser));
        when(transactionRepository.findAllBefore(anyLong(), any(Date.class))).thenReturn(List.of(
                TestDataBuilder.createDepositTransaction(testUser,
                        TestDataBuilder.createTransactionType(3, TransactionType.DEPOSIT),
                        LocalDateTime.of(2024, 1, 1, 10, 0), 10_000.0),
                TestDataBuilder.createBuyTransaction(testUser,
                        TestDataBuilder.createTransactionType(1, TransactionType.BUY),
                        "XYZ", LocalDateTime.of(2024, 1, 15, 10, 0), 100, 5_000.0)
        ));
        when(transactionRepository.findAllBetween(anyLong(), any(Date.class), any(Date.class)))
                .thenReturn(List.of());
        when(transactionRepository.findEarliestByUserAndSymbol(1L, "XYZ"))
                .thenReturn(Optional.of(java.sql.Timestamp.valueOf(LocalDateTime.of(2024, 1, 15, 10, 0))));

        MarkDto mark = new MarkDto();
        mark.setCurrentPrice(new BigDecimal("62.50"));
        when(finhubRepository.getMark("ABC")).thenReturn(mark);

        Date start = java.sql.Date.valueOf("2024-01-01");
        Date end = java.sql.Date.valueOf(LocalDate.now());

        Map<String, Position> result = pnLService.getPositions("test-user", start, end);

        assertNull(result.get("XYZ"), "Original symbol should be replaced after merger");
        Position abc = result.get("ABC");
        assertNotNull(abc);
        assertEquals(BigInteger.valueOf(80), abc.getQuantity());
        assertEquals(0, new BigDecimal("0.00").compareTo(abc.getUnrealized()),
                "80 shares @ $62.50 with $5,000 basis should be break-even");
    }

    private void stubProvider() {
        lenient().when(corporateActionProvider.getName()).thenReturn("TEST");

        lenient().when(corporateActionProvider.getStockSplits(anyString(), any(), any()))
                .thenAnswer(invocation -> filterSplitsByDate(
                        invocation.getArgument(1),
                        invocation.getArgument(2),
                        List.of(AAPL_FOUR_FOR_ONE)
                ));

        lenient().when(corporateActionProvider.getDividends(anyString(), any(), any()))
                .thenAnswer(invocation -> {
                    String symbol = invocation.getArgument(0);
                    LocalDate from = invocation.getArgument(1);
                    LocalDate to = invocation.getArgument(2);
                    if (!"KO".equals(symbol)) {
                        return List.of();
                    }
                    return filterDividendsByDate(from, to, KO_2024_QUARTERLY_DIVIDENDS);
                });

        lenient().when(corporateActionProvider.getMergers(anyString(), any(), any()))
                .thenAnswer(invocation -> {
                    String symbol = invocation.getArgument(0);
                    if (!"XYZ".equals(symbol)) {
                        return List.of();
                    }
                    return filterMergersByDate(
                            invocation.getArgument(1),
                            invocation.getArgument(2),
                            List.of(XYZ_STOCK_MERGER)
                    );
                });
    }

    private static List<Merger> filterMergersByDate(LocalDate from, LocalDate to, List<Merger> mergers) {
        return mergers.stream()
                .filter(merger -> !merger.getDate().isBefore(from) && !merger.getDate().isAfter(to))
                .collect(Collectors.toList());
    }

    private static List<StockSplit> filterSplitsByDate(LocalDate from, LocalDate to, List<StockSplit> splits) {
        return splits.stream()
                .filter(split -> !split.getDate().isBefore(from) && !split.getDate().isAfter(to))
                .collect(Collectors.toList());
    }

    private static List<Dividend> filterDividendsByDate(LocalDate from, LocalDate to, List<Dividend> dividends) {
        return dividends.stream()
                .filter(dividend -> !dividend.getExDate().isBefore(from) && !dividend.getExDate().isAfter(to))
                .collect(Collectors.toList());
    }

    private static Dividend cashDividend(String symbol, LocalDate exDate, String amount) {
        return Dividend.builder()
                .symbol(symbol)
                .exDate(exDate)
                .amount(new BigDecimal(amount))
                .type(DividendType.CASH)
                .build();
    }
}
