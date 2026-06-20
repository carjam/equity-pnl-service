package com.companyx.equity.error;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import lombok.extern.slf4j.Slf4j;

import java.text.ParseException;

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice
@Slf4j
public class RestExceptionHandler {
    @ExceptionHandler(value = ResponseVerificationException.class)
    protected ResponseEntity<Object> exception(ResponseVerificationException e) {
        String msg = "Remote resource interaction error. " + e.getMessage();
        log.error(msg);
        return new ResponseEntity<>(msg, HttpStatus.EXPECTATION_FAILED);
    }

    @ExceptionHandler(value = JsonProcessingException.class)
    protected ResponseEntity<Object> exception(JsonProcessingException e) {
        String msg = e.getMessage();
        log.error(msg);
        return new ResponseEntity<>(msg, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(value = ParseException.class)
    protected ResponseEntity<Object> exception(ParseException e) {
        String msg = e.getMessage();
        log.error(msg);
        return new ResponseEntity<>(msg, HttpStatus.EXPECTATION_FAILED);
    }

    @ExceptionHandler(value = UnexpectedValueException.class)
    protected ResponseEntity<Object> exception(UnexpectedValueException e) {
        String msg = e.getMessage();
        log.error(msg);
        return new ResponseEntity<>(msg, HttpStatus.EXPECTATION_FAILED);
    }

    @ExceptionHandler(value = VendorConnectivityException.class)
    protected ResponseEntity<Object> exception(VendorConnectivityException e) {
        String msg = e.getMessage();
        log.error(msg);
        return new ResponseEntity<>(msg, HttpStatus.EXPECTATION_FAILED);
    }
}
