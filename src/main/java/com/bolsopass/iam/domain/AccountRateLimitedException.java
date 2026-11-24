package com.bolsopass.iam.domain;

public class AccountRateLimitedException extends RuntimeException {
    public AccountRateLimitedException(String message) {
        super(message);
    }
}

