package com.bolsopass.inventory.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Agregado de Assento Fisico Numerado (Seat).
 * Implementa exclusao mutua e ciclo de vida simetrico com a FSM do Pedido:
 * DISPONIVEL -> RESERVADO_TEMPORARIO -> VENDIDO -> UTILIZADO
 *            <- (Timeout 10m ou Cancelamento)
 */
public class Seat {

    private final UUID eventId;
    private final String seatId;
    private final String section;
    private final long priceCents;
    private SeatStatus status;
    private UUID currentReservationId;
    private UUID reservedByUserId;
    private Instant reservedUntil;
    private long version;

    public Seat(UUID eventId, String seatId, String section, long priceCents) {
        this.eventId = Objects.requireNonNull(eventId, "eventId cannot be null");
        if (seatId == null || seatId.isBlank()) {
            throw new IllegalArgumentException("seatId cannot be blank");
        }
        if (section == null || section.isBlank()) {
            throw new IllegalArgumentException("section cannot be blank");
        }
        if (priceCents <= 0) {
            throw new IllegalArgumentException("priceCents must be positive");
        }
        this.seatId = seatId.trim();
        this.section = section.trim();
        this.priceCents = priceCents;
        this.status = SeatStatus.DISPONIVEL;
        this.version = 0L;
    }

    public synchronized Reservation allocateAtomically(UUID reservationId, UUID userId, Instant now) {
        Objects.requireNonNull(reservationId, "reservationId cannot be null");
        Objects.requireNonNull(userId, "userId cannot be null");
        Objects.requireNonNull(now, "now cannot be null");

        if (this.status == SeatStatus.RESERVADO_TEMPORARIO && this.reservedUntil != null && !now.isBefore(this.reservedUntil)) {
            // Auto-devolucao caso o TTL estrito de 10 minutos ja tenha expirado
            releaseToAvailable();
        }

        if (this.status != SeatStatus.DISPONIVEL) {
            throw new SeatAlreadyReservedException(this.seatId);
        }

        Reservation reservation = new Reservation(reservationId, this.eventId, this.seatId, userId, now);
        this.status = SeatStatus.RESERVADO_TEMPORARIO;
        this.currentReservationId = reservationId;
        this.reservedByUserId = userId;
        this.reservedUntil = reservation.getExpiresAt();
        this.version++;
        return reservation;
    }

    public synchronized void confirmSale(UUID reservationId, Instant now) {
        Objects.requireNonNull(reservationId, "reservationId cannot be null");
        Objects.requireNonNull(now, "now cannot be null");

        if (this.status == SeatStatus.VENDIDO || this.status == SeatStatus.UTILIZADO) {
            throw new IllegalStateException("Seat " + seatId + " is already sold");
        }
        if (this.status != SeatStatus.RESERVADO_TEMPORARIO || !reservationId.equals(this.currentReservationId)) {
            throw new IllegalStateException("Seat " + seatId + " is not reserved by reservation " + reservationId);
        }
        if (this.reservedUntil != null && !now.isBefore(this.reservedUntil)) {
            releaseToAvailable();
            throw new ReservationExpiredException(reservationId);
        }

        this.status = SeatStatus.VENDIDO;
        this.reservedUntil = null;
        this.version++;
    }

    public synchronized void releaseToAvailable() {
        if (this.status == SeatStatus.VENDIDO || this.status == SeatStatus.UTILIZADO) {
            throw new IllegalStateException("Cannot release an already sold seat " + seatId);
        }
        this.status = SeatStatus.DISPONIVEL;
        this.currentReservationId = null;
        this.reservedByUserId = null;
        this.reservedUntil = null;
        this.version++;
    }

    public synchronized void markUtilizedAtGate() {
        if (this.status != SeatStatus.VENDIDO) {
            throw new IllegalStateException("Only a VENDIDO seat can be marked as UTILIZADO at the gate (current: " + status + ")");
        }
        this.status = SeatStatus.UTILIZADO;
        this.version++;
    }

    public UUID getEventId() {
        return eventId;
    }

    public String getSeatId() {
        return seatId;
    }

    public String getSection() {
        return section;
    }

    public long getPriceCents() {
        return priceCents;
    }

    public synchronized SeatStatus getStatus() {
        return status;
    }

    public synchronized UUID getCurrentReservationId() {
        return currentReservationId;
    }

    public synchronized UUID getReservedByUserId() {
        return reservedByUserId;
    }

    public synchronized Instant getReservedUntil() {
        return reservedUntil;
    }

    public synchronized long getVersion() {
        return version;
    }
}

