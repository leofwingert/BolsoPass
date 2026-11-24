package com.bolsopass.orders.infrastructure;

import com.bolsopass.orders.domain.OrderStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "orders")
public class OrderEntity {

    @Id
    private UUID id;

    @Column(name = "client_id", nullable = false)
    private UUID clientId;

    @Column(name = "event_id", nullable = false)
    private UUID eventId;

    @Column(name = "reservation_id", nullable = false)
    private UUID reservationId;

    @Column(name = "seat_id", nullable = false, length = 32)
    private String seatId;

    @Column(name = "amount_cents", nullable = false)
    private long amountCents;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private OrderStatus status;

    @Column(name = "idempotency_key", length = 128)
    private String idempotencyKey;

    @Column(name = "paid_at")
    private Instant paidAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected OrderEntity() {
    }

    public OrderEntity(
            UUID id,
            UUID clientId,
            UUID eventId,
            UUID reservationId,
            String seatId,
            long amountCents,
            OrderStatus status,
            String idempotencyKey,
            Instant paidAt,
            Instant createdAt,
            Instant updatedAt
    ) {
        this.id = id;
        this.clientId = clientId;
        this.eventId = eventId;
        this.reservationId = reservationId;
        this.seatId = seatId;
        this.amountCents = amountCents;
        this.status = status;
        this.idempotencyKey = idempotencyKey;
        this.paidAt = paidAt;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getClientId() {
        return clientId;
    }

    public UUID getEventId() {
        return eventId;
    }

    public UUID getReservationId() {
        return reservationId;
    }

    public String getSeatId() {
        return seatId;
    }

    public long getAmountCents() {
        return amountCents;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public void setStatus(OrderStatus status) {
        this.status = status;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public Instant getPaidAt() {
        return paidAt;
    }

    public void setPaidAt(Instant paidAt) {
        this.paidAt = paidAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}

