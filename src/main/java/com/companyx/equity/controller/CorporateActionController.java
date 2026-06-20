package com.companyx.equity.controller;

import com.companyx.equity.dto.*;
import com.companyx.equity.model.Position;
import com.companyx.equity.model.corporateaction.*;
import com.companyx.equity.provider.CorporateActionProviderFactory;
import com.companyx.equity.service.CorporateActionService;
import com.companyx.equity.service.PnLService;
import com.fasterxml.jackson.core.JsonProcessingException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class CorporateActionController {

    private final CorporateActionService corporateActionService;
    private final CorporateActionProviderFactory providerFactory;
    private final PnLService pnLService;

    @GetMapping("/corporate-actions/providers")
    public ResponseEntity<CorporateActionProvidersResponse> listProviders() {
        return ResponseEntity.ok(CorporateActionProvidersResponse.builder()
                .providers(providerFactory.getActiveProviderNames())
                .build());
    }

    @GetMapping("/corporate-actions")
    public ResponseEntity<CorporateActionsResponse> listCorporateActions(
            @RequestParam String symbol,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) CorporateActionType type
    ) {
        log.info("Corporate actions query for {} from {} to {}", symbol, from, to);

        List<CorporateActionDto> actions = new ArrayList<>();

        if (type == null || type.isDividend()) {
            corporateActionService.getDividends(symbol, from, to).stream()
                    .map(this::toCorporateActionDto)
                    .forEach(actions::add);
        }

        if (type == null || type.isSplit()) {
            corporateActionService.getStockSplits(symbol, from, to).stream()
                    .map(this::toCorporateActionDto)
                    .forEach(actions::add);
        }

        if (includesType(type, CorporateActionType.MERGER)) {
            corporateActionService.getMergers(symbol, from, to).stream()
                    .map(this::toCorporateActionDto)
                    .forEach(actions::add);
        }

        if (includesType(type, CorporateActionType.SPINOFF)) {
            corporateActionService.getSpinoffs(symbol, from, to).stream()
                    .map(this::toCorporateActionDto)
                    .forEach(actions::add);
        }

        if (includesType(type, CorporateActionType.SYMBOL_CHANGE)) {
            corporateActionService.getSymbolChanges(symbol, from, to).stream()
                    .map(this::toCorporateActionDto)
                    .forEach(actions::add);
        }

        if (includesType(type, CorporateActionType.DELISTING)) {
            corporateActionService.getDelistings(symbol, from, to).stream()
                    .map(this::toCorporateActionDto)
                    .forEach(actions::add);
        }

        actions.sort(Comparator.comparing(CorporateActionDto::getDate));

        return ResponseEntity.ok(CorporateActionsResponse.builder()
                .symbol(symbol)
                .actions(actions)
                .build());
    }

    @GetMapping("/corporate-actions/dividends")
    public ResponseEntity<DividendsResponse> getDividends(
            @RequestParam String symbol,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        List<Dividend> dividends = corporateActionService.getDividends(symbol, from, to);
        List<DividendDto> dtoList = dividends.stream().map(this::toDividendDto).toList();

        BigDecimal total = dividends.stream()
                .filter(d -> d.getType() == DividendType.CASH)
                .map(Dividend::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return ResponseEntity.ok(DividendsResponse.builder()
                .symbol(symbol)
                .dividends(dtoList)
                .totalAmount(total)
                .count(dtoList.size())
                .build());
    }

    @GetMapping("/corporate-actions/splits")
    public ResponseEntity<SplitsResponse> getSplits(
            @RequestParam String symbol,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        List<StockSplitDto> splits = corporateActionService.getStockSplits(symbol, from, to).stream()
                .map(this::toSplitDto)
                .toList();

        return ResponseEntity.ok(SplitsResponse.builder()
                .symbol(symbol)
                .splits(splits)
                .build());
    }

    @GetMapping("/corporate-actions/mergers")
    public ResponseEntity<MergersResponse> getMergers(
            @RequestParam String symbol,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        List<MergerDto> mergers = corporateActionService.getMergers(symbol, from, to).stream()
                .map(this::toMergerDto)
                .toList();

        return ResponseEntity.ok(MergersResponse.builder()
                .symbol(symbol)
                .mergers(mergers)
                .count(mergers.size())
                .build());
    }

    @GetMapping("/corporate-actions/spinoffs")
    public ResponseEntity<SpinoffsResponse> getSpinoffs(
            @RequestParam String symbol,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        List<SpinoffDto> spinoffs = corporateActionService.getSpinoffs(symbol, from, to).stream()
                .map(this::toSpinoffDto)
                .toList();

        return ResponseEntity.ok(SpinoffsResponse.builder()
                .symbol(symbol)
                .spinoffs(spinoffs)
                .count(spinoffs.size())
                .build());
    }

    @GetMapping("/corporate-actions/symbol-changes")
    public ResponseEntity<SymbolChangesResponse> getSymbolChanges(
            @RequestParam String symbol,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        List<SymbolChangeDto> changes = corporateActionService.getSymbolChanges(symbol, from, to).stream()
                .map(this::toSymbolChangeDto)
                .toList();

        return ResponseEntity.ok(SymbolChangesResponse.builder()
                .symbol(symbol)
                .symbolChanges(changes)
                .count(changes.size())
                .build());
    }

    @GetMapping("/corporate-actions/delistings")
    public ResponseEntity<DelistingsResponse> getDelistings(
            @RequestParam String symbol,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        List<DelistingDto> delistings = corporateActionService.getDelistings(symbol, from, to).stream()
                .map(this::toDelistingDto)
                .toList();

        return ResponseEntity.ok(DelistingsResponse.builder()
                .symbol(symbol)
                .delistings(delistings)
                .count(delistings.size())
                .build());
    }

    @GetMapping("/pnl/total-return")
    public ResponseEntity<TotalReturnResponse> getTotalReturn(
            Authentication authentication,
            @RequestParam String symbol,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) throws JsonProcessingException {
        String uid = authentication.getName();
        Date fromDate = java.sql.Date.valueOf(from);
        Date toDate = java.sql.Date.valueOf(to);

        Map<String, Position> positions = pnLService.getPositions(uid, fromDate, toDate);
        Position position = positions.get(symbol);

        if (position == null) {
            return ResponseEntity.ok(TotalReturnResponse.builder()
                    .symbol(symbol)
                    .capitalGain(BigDecimal.ZERO)
                    .dividendIncome(BigDecimal.ZERO)
                    .totalReturn(BigDecimal.ZERO)
                    .totalReturnPct(BigDecimal.ZERO)
                    .build());
        }

        BigDecimal dividendIncome = corporateActionService.calculateDividendIncome(
                position.getQuantity(), symbol, from, to);
        BigDecimal totalReturn = position.getRealized().add(position.getUnrealized());
        BigDecimal capitalGain = totalReturn.subtract(dividendIncome);
        BigDecimal basis = position.getValue().abs();
        BigDecimal totalReturnPct = basis.compareTo(BigDecimal.ZERO) == 0
                ? BigDecimal.ZERO
                : totalReturn.divide(basis, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100));

        return ResponseEntity.ok(TotalReturnResponse.builder()
                .symbol(symbol)
                .capitalGain(capitalGain)
                .dividendIncome(dividendIncome)
                .totalReturn(totalReturn)
                .totalReturnPct(totalReturnPct)
                .build());
    }

    private boolean includesType(CorporateActionType filter, CorporateActionType target) {
        return filter == null || filter == target;
    }

    private CorporateActionDto toCorporateActionDto(Dividend dividend) {
        return CorporateActionDto.builder()
                .type(dividend.getActionType())
                .date(dividend.getDate())
                .details(Map.of(
                        "amount", dividend.getAmount(),
                        "currency", dividend.getCurrency(),
                        "dividendType", dividend.getType().name()
                ))
                .build();
    }

    private CorporateActionDto toCorporateActionDto(StockSplit split) {
        return CorporateActionDto.builder()
                .type(split.getActionType())
                .date(split.getDate())
                .details(Map.of(
                        "fromFactor", split.getFromFactor(),
                        "toFactor", split.getToFactor(),
                        "splitRatio", split.getSplitRatio()
                ))
                .build();
    }

    private CorporateActionDto toCorporateActionDto(Merger merger) {
        return CorporateActionDto.builder()
                .type(merger.getActionType())
                .date(merger.getDate())
                .details(Map.of(
                        "acquirerSymbol", merger.getAcquirerSymbol() != null ? merger.getAcquirerSymbol() : "",
                        "mergerType", merger.getType().name(),
                        "exchangeRatio", merger.getExchangeRatio(),
                        "cashPerShare", merger.getCashPerShare()
                ))
                .build();
    }

    private CorporateActionDto toCorporateActionDto(Spinoff spinoff) {
        return CorporateActionDto.builder()
                .type(spinoff.getActionType())
                .date(spinoff.getDate())
                .details(Map.of(
                        "spunoffSymbol", spinoff.getSpunoffSymbol(),
                        "distributionRatio", spinoff.getDistributionRatio()
                ))
                .build();
    }

    private CorporateActionDto toCorporateActionDto(SymbolChange symbolChange) {
        return CorporateActionDto.builder()
                .type(symbolChange.getActionType())
                .date(symbolChange.getDate())
                .details(Map.of(
                        "oldSymbol", symbolChange.getOldSymbol(),
                        "newSymbol", symbolChange.getNewSymbol()
                ))
                .build();
    }

    private CorporateActionDto toCorporateActionDto(Delisting delisting) {
        return CorporateActionDto.builder()
                .type(delisting.getActionType())
                .date(delisting.getDate())
                .details(Map.of())
                .build();
    }

    private DividendDto toDividendDto(Dividend dividend) {
        return DividendDto.builder()
                .exDate(dividend.getExDate())
                .payDate(dividend.getPayDate())
                .amount(dividend.getAmount())
                .currency(dividend.getCurrency())
                .type(dividend.getType().name())
                .build();
    }

    private StockSplitDto toSplitDto(StockSplit split) {
        return StockSplitDto.builder()
                .date(split.getDate())
                .fromFactor(split.getFromFactor())
                .toFactor(split.getToFactor())
                .splitRatio(split.getSplitRatio())
                .type(split.getActionType().name())
                .build();
    }

    private MergerDto toMergerDto(Merger merger) {
        return MergerDto.builder()
                .date(merger.getDate())
                .acquirerSymbol(merger.getAcquirerSymbol())
                .type(merger.getType().name())
                .exchangeRatio(merger.getExchangeRatio())
                .cashPerShare(merger.getCashPerShare())
                .build();
    }

    private SpinoffDto toSpinoffDto(Spinoff spinoff) {
        return SpinoffDto.builder()
                .date(spinoff.getDate())
                .spunoffSymbol(spinoff.getSpunoffSymbol())
                .distributionRatio(spinoff.getDistributionRatio())
                .build();
    }

    private SymbolChangeDto toSymbolChangeDto(SymbolChange symbolChange) {
        return SymbolChangeDto.builder()
                .date(symbolChange.getDate())
                .oldSymbol(symbolChange.getOldSymbol())
                .newSymbol(symbolChange.getNewSymbol())
                .build();
    }

    private DelistingDto toDelistingDto(Delisting delisting) {
        return DelistingDto.builder()
                .date(delisting.getDate())
                .build();
    }
}
