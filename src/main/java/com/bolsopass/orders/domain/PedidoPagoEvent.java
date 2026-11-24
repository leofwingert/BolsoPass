package com.bolsopass.orders.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Evento de dominio publicado via Transactional Outbox no topico `pedidos.pagos` (AsyncAPI 2.6).
 */
public record PedidoPagoEvent(
        UUID orderId,
        UUID clientId,
        UUID eventId,
        String seatId,
        long amountCents,
        Instant paidAt
) {
    public PedidoPagoEvent {
        Objects.requireNonNull(orderId, "order_id cannot be null");
        Objects.requireNonNull(clientId, "client_id cannot be null");
        Objects.requireNonNull(eventId, "event_id cannot be null");
        if (seatId == null || seatId.isBlank()) {
            throw new IllegalArgumentException("seat_id cannot be blank");
        }
        if (amountCents <= 0) {
            throw new IllegalArgumentException("amount_cents must be positive");
        }
        Objects.requireNonNull(paidAt, "paid_at cannot be null");
    }
}

