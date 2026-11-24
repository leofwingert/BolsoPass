package com.bolsopass.inventory.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Agregado de Reserva Temporaria com TTL estrito de 10 minutos (600 segundos).
 * Atende RNF-02, RNF-03, RF-06, RF-07 e US-05 / US-06.
 */
public class Reservation {

    public static final long STRICT_TTL_SECONDS = 600L;
    public static final Duration STRICT_TTL = Duration.ofSeconds(STRICT_TTL_SECONDS);

    private final UUID reservationId;
    private final UUID eventId;
    private final String seatId;
    private final UUID userId;
    private final Instant createdAt;
    private final Instant expiresAt;
    private ReservationStatus status;

    public Reservation(UUID reservationId, UUID eventId, String seatId, UUID userId, Instant createdAt) {
        this.reservationId = Objects.requireNonNull(reservationId, "reservationId cannot be null");
        this.eventId = Objects.requireNonNull(eventId, "eventId cannot be null");
        if (seatId == null || seatId.isBlank()) {
            throw new IllegalArgumentException("seatId cannot be blank");
        }
        this.seatId = seatId.trim();
        this.userId = Objects.requireNonNull(userId, "userId cannot be null");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt cannot be null");
        this.expiresAt = createdAt.plus(STRICT_TTL);
        this.status = ReservationStatus.RESERVADO_TEMPORARIO;
    }

    public boolean isExpiredAt(Instant now) {
        Objects.requireNonNull(now, "Timestamp cannot be null");
        return !now.isBefore(expiresAt) || status == ReservationStatus.EXPIRADO;
    }

    public long getRemainingTtlSeconds(Instant now) {
        Objects.requireNonNull(now, "Timestamp cannot be null");
        if (status != ReservationStatus.RESERVADO_TEMPORARIO || !now.isBefore(expiresAt)) {
            return 0L;
        }
        return Duration.between(now, expiresAt).getSeconds();
    }

    public ReservaExpiradaEvent expire(Instant now) {
        Objects.requireNonNull(now, "Timestamp cannot be null");
        if (status == ReservationStatus.CONFIRMADA) {
            throw new IllegalStateException("Confirmed reservation cannot expire");
        }
        if (status == ReservationStatus.CANCELADA) {
            throw new IllegalStateException("Cancelled reservation cannot expire");
        }
        this.status = ReservationStatus.EXPIRADO;
        return new ReservaExpiradaEvent(eventId, seatId, reservationId, now);
    }

    public void confirm(Instant now) {
        Objects.requireNonNull(now, "Timestamp cannot be null");
        if (status == ReservationStatus.EXPIRADO || !now.isBefore(expiresAt)) {
            this.status = ReservationStatus.EXPIRADO;
            throw new ReservationExpiredException(reservationId);
        }
        if (status != ReservationStatus.RESERVADO_TEMPORARIO) {
            throw new IllegalStateException("Cannot confirm reservation in status " + status);
        }
        this.status = ReservationStatus.CONFIRMADA;
    }

    public void cancel() {
        if (status == ReservationStatus.CONFIRMADA) {
            throw new IllegalStateException("Cannot cancel an already confirmed reservation");
        }
        this.status = ReservationStatus.CANCELADA;
    }

    public UUID getReservationId() {
        return reservationId;
    }

    public UUID getEventId() {
        return eventId;
    }

    public String getSeatId() {
        return seatId;
    }

    public UUID getUserId() {
        return userId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public long getTtlSeconds() {
        return STRICT_TTL_SECONDS;
    }

    public ReservationStatus getStatus() {
        return status;
    }
}

