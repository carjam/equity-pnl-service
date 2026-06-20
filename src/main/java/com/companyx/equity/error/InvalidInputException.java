package com.companyx.equity.error;

public class InvalidInputException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public InvalidInputException(String errorMessage) {
        super(errorMessage);
    }
}
