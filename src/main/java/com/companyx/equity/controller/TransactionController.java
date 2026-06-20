package com.companyx.equity.controller;

import com.companyx.equity.model.Position;
import com.companyx.equity.model.Transaction;
import com.companyx.equity.service.PnLService;
import com.fasterxml.jackson.core.JsonProcessingException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.hateoas.EntityModel;
import org.springframework.web.bind.annotation.*;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@RestController
public class TransactionController {
    @Autowired
    PnLService pnLService;

    @GetMapping("/pnl")
    public EntityModel<Map<String, Position>> pnlBetween(@RequestParam String uid, @RequestParam String from, @RequestParam String to)
            throws ParseException, JsonProcessingException {
        Date fromDate = new SimpleDateFormat("yyyy-MM-dd").parse(from);
        Date toDate = new SimpleDateFormat("yyyy-MM-dd").parse(to);
        return EntityModel.of(pnLService.getPositions(uid, fromDate, toDate));
    }

    @GetMapping("/Transaction/{id}")
    public EntityModel<Transaction> show(@RequestParam String uid, @PathVariable String id) {
        return EntityModel.of(pnLService.getTransactionById(uid, id));
    }

    @GetMapping("/Transaction")
    public List<Transaction> findBetween(@RequestParam String uid
            , @RequestParam Optional<String> from
            , @RequestParam Optional<String> to)
            throws ParseException {
        return pnLService.getTransactionsByDates(uid, from, to);
    }
}