package com.bolsopass.inventory.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Evento de dominio publicado no topico Kafka `reservas.expiradas` (AsyncAPI 2.6).
 */
public record ReservaExpiradaEvent(
        UUID eventId,
        String seatId,
        UUID reservationId,
        Instant expiredAt
) {
    public ReservaExpiradaEvent {
        Objects.requireNonNull(eventId, "event_id cannot be null");
        if (seatId == null || seatId.isBlank()) {
            throw new IllegalArgumentException("seat_id cannot be blank");
        }
        Objects.requireNonNull(reservationId, "reservation_id cannot be null");
        Objects.requireNonNull(expiredAt, "expired_at cannot be null");
    }
}

