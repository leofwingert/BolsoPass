package com.bolsopass.orders.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Controle de Idempotencia HTTP (24 horas) baseado no cabecalho Idempotency-Key (RF-09 / RNF-05 / US-10).
 */
public class IdempotencyStore<T> {

    public static final Duration RETENTION_WINDOW = Duration.ofHours(24);

    public enum EntryState {
        PROCESSING,
        COMPLETED
    }

    public record IdempotencyEntry<T>(
            String key,
            String requestFingerprint,
            EntryState state,
            T cachedResponse,
            Instant createdAt,
            Instant expiresAt
    ) {
        public boolean isExpiredAt(Instant now) {
            return !now.isBefore(expiresAt);
        }
    }

    private final Map<String, IdempotencyEntry<T>> entries = new ConcurrentHashMap<>();

    public synchronized Optional<T> tryBeginOrGetCached(String rawKey, String requestFingerprint, Instant now) {
        if (rawKey == null || rawKey.isBlank()) {
            throw new IllegalArgumentException("Idempotency-Key cannot be null or blank");
        }
        if (requestFingerprint == null || requestFingerprint.isBlank()) {
            throw new IllegalArgumentException("Request fingerprint cannot be blank");
        }
        Objects.requireNonNull(now, "Timestamp cannot be null");

        String key = rawKey.trim();
        IdempotencyEntry<T> existing = entries.get(key);
        if (existing != null && !existing.isExpiredAt(now)) {
            if (!existing.requestFingerprint().equals(requestFingerprint)) {
                throw new IdempotencyConflictException(key,
                        "Idempotency-Key reutilizada com payload divergente na janela de 24 horas");
            }
            if (existing.state() == EntryState.PROCESSING) {
                throw new IdempotencyConflictException(key,
                        "Transacao com esta Idempotency-Key ja esta em processamento (HTTP 409 Conflict)");
            }
            return Optional.of(existing.cachedResponse());
        }

        entries.put(key, new IdempotencyEntry<>(
                key,
                requestFingerprint,
                EntryState.PROCESSING,
                null,
                now,
                now.plus(RETENTION_WINDOW)
        ));
        return Optional.empty();
    }

    public synchronized void complete(String rawKey, String requestFingerprint, T response, Instant now) {
        Objects.requireNonNull(response, "Response cannot be null");
        String key = rawKey.trim();
        IdempotencyEntry<T> current = entries.get(key);
        Instant createdAt = current != null ? current.createdAt() : now;
        Instant expiresAt = current != null ? current.expiresAt() : now.plus(RETENTION_WINDOW);
        entries.put(key, new IdempotencyEntry<>(
                key,
                requestFingerprint,
                EntryState.COMPLETED,
                response,
                createdAt,
                expiresAt
        ));
    }

    public synchronized void abort(String rawKey) {
        if (rawKey != null) {
            entries.remove(rawKey.trim());
        }
    }
}

