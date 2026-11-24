package com.bolsopass.orders.domain;

import com.bolsopass.inventory.domain.Reservation;
import com.bolsopass.inventory.domain.ReservationExpiredException;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Agregado de Pedido (Order) governado pela OrderStateMachine (RF-08, RF-09, RF-10).
 */
public class Order {

    private final UUID orderId;
    private final UUID clientId;
    private final UUID eventId;
    private final String seatId;
    private final long amountCents;
    private final Instant createdAt;

    private UUID reservationId;
    private Instant reservationExpiresAt;
    private OrderStatus status;
    private String lastIdempotencyKey;
    private String paymentMethod;
    private int installments;
    private String declineReason;
    private Instant paidAt;
    private Instant updatedAt;

    public Order(UUID orderId, UUID clientId, UUID eventId, String seatId, long amountCents, Instant createdAt) {
        this.orderId = Objects.requireNonNull(orderId, "orderId cannot be null");
        this.clientId = Objects.requireNonNull(clientId, "clientId cannot be null");
        this.eventId = Objects.requireNonNull(eventId, "eventId cannot be null");
        if (seatId == null || seatId.isBlank()) {
            throw new IllegalArgumentException("seatId cannot be blank");
        }
        if (amountCents <= 0) {
            throw new IllegalArgumentException("amountCents must be positive");
        }
        this.seatId = seatId.trim();
        this.amountCents = amountCents;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt cannot be null");
        this.status = OrderStatus.CRIADO;
        this.updatedAt = createdAt;
        this.installments = 1;
    }

    public static Order createFromReservation(UUID orderId, Reservation reservation, long amountCents, Instant now) {
        Objects.requireNonNull(reservation, "reservation cannot be null");
        Order order = new Order(orderId, reservation.getUserId(), reservation.getEventId(),
                reservation.getSeatId(), amountCents, now);
        order.attachReservation(reservation.getReservationId(), reservation.getExpiresAt(), now);
        return order;
    }

    public void attachReservation(UUID reservationId, Instant expiresAt, Instant now) {
        Objects.requireNonNull(reservationId, "reservationId cannot be null");
        Objects.requireNonNull(expiresAt, "expiresAt cannot be null");
        Objects.requireNonNull(now, "now cannot be null");

        this.status = OrderStateMachine.transition(this.status, OrderStatus.RESERVADO);
        this.reservationId = reservationId;
        this.reservationExpiresAt = expiresAt;
        this.updatedAt = now;
    }

    public void submitPayment(String idempotencyKey, String paymentMethod, int installments, Instant now) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("Idempotency-Key header is mandatory");
        }
        if (paymentMethod == null || paymentMethod.isBlank()) {
            throw new IllegalArgumentException("Payment method is mandatory");
        }
        if (installments < 1 || installments > 12) {
            throw new IllegalArgumentException("Installments must be between 1 and 12");
        }
        Objects.requireNonNull(now, "now cannot be null");

        checkNotExpiredOrThrow(now);
        this.status = OrderStateMachine.transition(this.status, OrderStatus.AGUARDANDO_PAGAMENTO);
        this.lastIdempotencyKey = idempotencyKey.trim();
        this.paymentMethod = paymentMethod.trim();
        this.installments = installments;
        this.declineReason = null;
        this.updatedAt = now;
    }

    public PedidoPagoEvent confirmPayment(Instant now) {
        Objects.requireNonNull(now, "now cannot be null");
        if (this.status == OrderStatus.EXPIRADO) {
            throw new InvalidOrderStateTransitionException(OrderStatus.EXPIRADO, OrderStatus.PAGO,
                    "Um pedido no estado EXPIRADO jamais pode transicionar para PAGO");
        }
        checkNotExpiredOrThrow(now);
        this.status = OrderStateMachine.transition(this.status, OrderStatus.PAGO);
        this.paidAt = now;
        this.updatedAt = now;
        return new PedidoPagoEvent(orderId, clientId, eventId, seatId, amountCents, now);
    }

    public void declinePayment(String reason, Instant now) {
        Objects.requireNonNull(now, "now cannot be null");
        this.status = OrderStateMachine.transition(this.status, OrderStatus.RECUSADO);
        this.declineReason = (reason == null || reason.isBlank()) ? "PAYMENT_DECLINED" : reason.trim();
        this.updatedAt = now;
    }

    public void expire(Instant now) {
        Objects.requireNonNull(now, "now cannot be null");
        this.status = OrderStateMachine.transition(this.status, OrderStatus.EXPIRADO);
        this.updatedAt = now;
    }

    public void cancel(String reason, Instant now) {
        Objects.requireNonNull(now, "now cannot be null");
        this.status = OrderStateMachine.transition(this.status, OrderStatus.CANCELADO);
        this.declineReason = reason;
        this.updatedAt = now;
    }

    private void checkNotExpiredOrThrow(Instant now) {
        if (this.reservationExpiresAt != null && !now.isBefore(this.reservationExpiresAt)) {
            if (OrderStateMachine.canTransition(this.status, OrderStatus.EXPIRADO)) {
                this.status = OrderStateMachine.transition(this.status, OrderStatus.EXPIRADO);
                this.updatedAt = now;
            }
            throw new ReservationExpiredException(this.reservationId);
        }
    }

    public UUID getOrderId() {
        return orderId;
    }

    public UUID getClientId() {
        return clientId;
    }

    public UUID getEventId() {
        return eventId;
    }

    public String getSeatId() {
        return seatId;
    }

    public long getAmountCents() {
        return amountCents;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public UUID getReservationId() {
        return reservationId;
    }

    public Instant getReservationExpiresAt() {
        return reservationExpiresAt;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public String getLastIdempotencyKey() {
        return lastIdempotencyKey;
    }

    public String getPaymentMethod() {
        return paymentMethod;
    }

    public int getInstallments() {
        return installments;
    }

    public String getDeclineReason() {
        return declineReason;
    }

    public Instant getPaidAt() {
        return paidAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}

