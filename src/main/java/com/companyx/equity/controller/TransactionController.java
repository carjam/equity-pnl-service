package com.companyx.equity.controller;

import com.companyx.equity.model.Position;
import com.companyx.equity.model.Transaction;
import com.companyx.equity.service.PnLService;
import com.fasterxml.jackson.core.JsonProcessingException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.hateoas.EntityModel;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

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
public class TransactionController {
    
    private final PnLService pnLService;

    @GetMapping("/pnl")
    public ResponseEntity<EntityModel<Map<String, Position>>> pnlBetween(
            Authentication authentication,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) throws JsonProcessingException {
        String uid = authentication.getName();
        log.info("PnL query for user: {} from {} to {}", uid, from, to);
        // Convert LocalDate to Date for service compatibility
        Date fromDate = java.sql.Date.valueOf(from);
        Date toDate = java.sql.Date.valueOf(to);
        return ResponseEntity.ok(EntityModel.of(pnLService.getPositions(uid, fromDate, toDate)));
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
}