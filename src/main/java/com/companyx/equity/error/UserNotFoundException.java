package com.companyx.equity.error;

public class UserNotFoundException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public UserNotFoundException(String uid) {
        super("User not found: " + uid);
    }
}
