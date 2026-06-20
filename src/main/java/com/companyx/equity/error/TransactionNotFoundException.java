package com.companyx.equity.error;

public class TransactionNotFoundException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public TransactionNotFoundException(String uid, Integer transactionId) {
        super("Transaction not found for user: " + uid + ", transactionId: " + transactionId);
    }
}
