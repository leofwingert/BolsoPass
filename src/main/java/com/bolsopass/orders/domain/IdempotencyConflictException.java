package com.bolsopass.orders.domain;

public class IdempotencyConflictException extends RuntimeException {
    private final String idempotencyKey;

    public IdempotencyConflictException(String idempotencyKey, String message) {
        super(message);
        this.idempotencyKey = idempotencyKey;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }
}

