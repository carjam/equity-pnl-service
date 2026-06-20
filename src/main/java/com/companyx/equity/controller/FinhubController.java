package com.companyx.equity.controller;

import com.companyx.equity.dto.CandleDto;
import com.companyx.equity.dto.MarkDto;
import com.companyx.equity.model.Transaction;
import com.companyx.equity.repository.FinhubRepository;
import com.companyx.equity.repository.TransactionRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.hateoas.EntityModel;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.sql.Timestamp;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Slf4j
@RestController
@RequiredArgsConstructor
public class FinhubController {

    private final FinhubRepository finhubRepository;

    @GetMapping(value = "/Mark/{symbol}", produces = MediaType.APPLICATION_JSON_VALUE)
    public EntityModel<MarkDto> mark(@PathVariable String symbol) throws JsonProcessingException {
        MarkDto result = finhubRepository.getMark(symbol);
        log.info(new Timestamp(System.currentTimeMillis()) + " "
                + this.getClass() + ":"
                + new Throwable().getStackTrace()[0].getMethodName()
                + "\n" + result
        );
        return EntityModel.of(result);
    }

    @GetMapping(value = "/Candle/{symbol}", produces = MediaType.APPLICATION_JSON_VALUE)
    public EntityModel<CandleDto> candle(@PathVariable String symbol, @RequestParam String from, @RequestParam String to)
            throws JsonProcessingException, ParseException {
        Date fromDate = new SimpleDateFormat("yyyy-MM-dd").parse(from);
        Date toDate = new SimpleDateFormat("yyyy-MM-dd").parse(to);
        CandleDto result = finhubRepository.getCandle(symbol, fromDate, toDate);
        log.info(new Timestamp(System.currentTimeMillis()) + " "
                + this.getClass() + ":"
                + new Throwable().getStackTrace()[0].getMethodName()
                + "\n" + result
        );
        return EntityModel.of(result);
    }
}