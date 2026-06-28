package com.companyx.equity.controller;

import com.companyx.equity.dto.PnLResponseDto;
import com.companyx.equity.dto.TaxLotReportDto;
import com.companyx.equity.model.ClosedLot;
import com.companyx.equity.model.Position;
import com.companyx.equity.model.Transaction;
import com.companyx.equity.service.PnLService;
import com.companyx.equity.service.TaxLotService;
import com.fasterxml.jackson.core.JsonProcessingException;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.hateoas.EntityModel;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.text.ParseException;
import java.time.LocalDate;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "Transactions & P&L", description = "Portfolio transactions and profit/loss queries")
public class TransactionController {

    private final PnLService pnLService;
    private final TaxLotService taxLotService;

    @GetMapping("/pnl")
    public ResponseEntity<EntityModel<PnLResponseDto>> pnlBetween(
            Authentication authentication,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) throws JsonProcessingException {
        String uid = authentication.getName();
        log.info("PnL query for user: {} from {} to {}", uid, from, to);
        Date fromDate = java.sql.Date.valueOf(from);
        Date toDate = java.sql.Date.valueOf(to);
        Map<String, Position> positions = pnLService.getPositions(uid, fromDate, toDate);
        return ResponseEntity.ok(EntityModel.of(new PnLResponseDto(positions, from, to)));
    }

    @GetMapping("/transactions/{id}")
    public ResponseEntity<EntityModel<Transaction>> show(
            Authentication authentication,
            @PathVariable String id
    ) {
        String uid = authentication.getName();
        log.debug("Transaction query for user: {} id: {}", uid, id);
        return ResponseEntity.ok(EntityModel.of(pnLService.getTransactionById(uid, id)));
    }

    @GetMapping("/transactions")
    public ResponseEntity<List<Transaction>> findBetween(
            Authentication authentication,
            @RequestParam Optional<String> from,
            @RequestParam Optional<String> to
    ) throws ParseException {
        String uid = authentication.getName();
        log.debug("Transactions query for user: {} from {} to {}", uid, from, to);
        return ResponseEntity.ok(pnLService.getTransactionsByDates(uid, from, to));
    }

    @GetMapping("/pnl/tax-lots")
    public ResponseEntity<TaxLotReportDto> taxLots(
            Authentication authentication,
            @RequestParam String symbol,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        String uid = authentication.getName();
        log.info("Tax-lot query for user: {} symbol: {} from {} to {}", uid, symbol, from, to);

        List<Transaction> transactions = pnLService.getAllTransactionsBySymbol(uid, symbol);
        List<ClosedLot> closedLots = taxLotService.computeClosedLots(transactions);

        BigDecimal shortTerm = closedLots.stream()
                .filter(l -> l.getTerm() == ClosedLot.Term.SHORT && !l.isWashSale())
                .map(ClosedLot::getGainLoss).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal longTerm = closedLots.stream()
                .filter(l -> l.getTerm() == ClosedLot.Term.LONG && !l.isWashSale())
                .map(ClosedLot::getGainLoss).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal washSaleDisallowed = closedLots.stream()
                .map(ClosedLot::getDisallowedLoss).reduce(BigDecimal.ZERO, BigDecimal::add);

        return ResponseEntity.ok(TaxLotReportDto.builder()
                .symbol(symbol)
                .from(from)
                .to(to)
                .closedLots(closedLots)
                .shortTermGainLoss(shortTerm)
                .longTermGainLoss(longTerm)
                .totalGainLoss(shortTerm.add(longTerm))
                .washSaleDisallowedLoss(washSaleDisallowed)
                .disclaimer(TaxLotReportDto.DISCLAIMER)
                .build());
    }
}