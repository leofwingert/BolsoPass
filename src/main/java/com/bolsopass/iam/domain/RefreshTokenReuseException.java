package com.bolsopass.iam.domain;

public class RefreshTokenReuseException extends RuntimeException {
    public RefreshTokenReuseException(String message) {
        super(message);
    }
}

