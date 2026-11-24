package com.bolsopass.orders.service;

import com.bolsopass.inventory.domain.Reservation;
import com.bolsopass.inventory.service.InventoryService;
import com.bolsopass.orders.domain.IdempotencyStore;
import com.bolsopass.orders.domain.Order;
import com.bolsopass.orders.domain.OrderStatus;
import com.bolsopass.orders.domain.PedidoPagoEvent;
import com.bolsopass.orders.infrastructure.OrderEntity;
import com.bolsopass.orders.infrastructure.OrderJpaRepository;
import com.bolsopass.orders.infrastructure.OutboxEventEntity;
import com.bolsopass.orders.infrastructure.OutboxEventRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class CheckoutService {

    public static final String TOPIC_PEDIDOS_PAGOS = "pedidos.pagos";

    private final InventoryService inventoryService;
    private final PaymentGatewaySimulator paymentGatewaySimulator;
    private final OrderJpaRepository orderJpaRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;
    private final IdempotencyStore<CheckoutResponse> idempotencyStore = new IdempotencyStore<>();
    private Clock clock = Clock.systemUTC();

    public record CheckoutCommand(
            String idempotencyKey,
            UUID reservationId,
            String paymentMethod,
            int installments,
            String paymentToken
    ) {
    }

    public record CheckoutResponse(
            UUID orderId,
            UUID reservationId,
            OrderStatus status,
            long amountCents,
            Instant paidAt,
            String declineReason
    ) {
    }

    public CheckoutService(
            InventoryService inventoryService,
            PaymentGatewaySimulator paymentGatewaySimulator,
            OrderJpaRepository orderJpaRepository,
            OutboxEventRepository outboxEventRepository,
            ObjectMapper objectMapper
    ) {
        this.inventoryService = inventoryService;
        this.paymentGatewaySimulator = paymentGatewaySimulator;
        this.orderJpaRepository = orderJpaRepository;
        this.outboxEventRepository = outboxEventRepository;
        this.objectMapper = objectMapper;
    }

    public void setClock(Clock clock) {
        this.clock = clock;
    }

    /**
     * Executa o checkout transacional:
     * 1. Valida e deduplica pelo cabecalho Idempotency-Key (janela de 24h).
     * 2. Verifica se o TTL de 10 minutos da reserva nao expirou.
     * 3. Executa a FSM do Pedido (CRIADO -> RESERVADO -> AGUARDANDO_PAGAMENTO -> PAGO | RECUSADO).
     * 4. Em uma UNICA transacao ACID relacional (@Transactional), persiste o pedido em `orders`
     *    e o evento `PedidoPagoEvent` em `outbox_events` com status `PENDING` (Transactional Outbox Pattern).
     */
    @Transactional
    public CheckoutResponse processCheckout(CheckoutCommand command) {
        if (command.idempotencyKey() == null || command.idempotencyKey().isBlank()) {
            throw new IllegalArgumentException("Cabecalho Idempotency-Key e obrigatorio");
        }
        Instant now = clock.instant();
        String fingerprint = command.reservationId() + ":" + command.paymentMethod() + ":" + command.installments();

        Optional<CheckoutResponse> cached = idempotencyStore.tryBeginOrGetCached(command.idempotencyKey(), fingerprint, now);
        if (cached.isPresent()) {
            return cached.get();
        }

        try {
            Reservation reservation = inventoryService.getReservationOrThrow(command.reservationId());
            long amountCents = 38500L;
            UUID orderId = UUID.randomUUID();

            Order order = Order.createFromReservation(orderId, reservation, amountCents, now);
            order.submitPayment(
                    command.idempotencyKey(),
                    command.paymentMethod() == null ? "CREDIT_CARD" : command.paymentMethod(),
                    command.installments() <= 0 ? 1 : command.installments(),
                    now
            );

            PaymentGatewaySimulator.GatewayAuthorizationResult gatewayResult = paymentGatewaySimulator.charge(
                    orderId, amountCents, order.getPaymentMethod(), command.paymentToken()
            );

            if (!gatewayResult.approved()) {
                order.declinePayment(gatewayResult.declineReason(), now);
                saveOrderEntity(order, now);
                CheckoutResponse declinedResponse = new CheckoutResponse(
                        orderId, reservation.getReservationId(), order.getStatus(), amountCents, null, order.getDeclineReason()
                );
                idempotencyStore.complete(command.idempotencyKey(), fingerprint, declinedResponse, now);
                return declinedResponse;
            }

            // Confirma na reserva/assento e na FSM do Pedido
            inventoryService.confirmReservation(reservation.getReservationId());
            PedidoPagoEvent paidEvent = order.confirmPayment(now);

            // Escrita Atomica na Mesma Transacao ACID Relacional: Orders + OutboxEvents
            saveOrderEntity(order, now);
            saveOutboxEvent(paidEvent, now);

            CheckoutResponse response = new CheckoutResponse(
                    order.getOrderId(),
                    reservation.getReservationId(),
                    order.getStatus(),
                    order.getAmountCents(),
                    order.getPaidAt(),
                    null
            );
            idempotencyStore.complete(command.idempotencyKey(), fingerprint, response, now);
            return response;
        } catch (RuntimeException ex) {
            idempotencyStore.abort(command.idempotencyKey());
            throw ex;
        }
    }

    private void saveOrderEntity(Order order, Instant now) {
        OrderEntity entity = new OrderEntity(
                order.getOrderId(),
                order.getClientId(),
                order.getEventId(),
                order.getReservationId(),
                order.getSeatId(),
                order.getAmountCents(),
                order.getStatus(),
                order.getLastIdempotencyKey(),
                order.getPaidAt(),
                order.getCreatedAt(),
                now
        );
        orderJpaRepository.save(entity);
    }

    private void saveOutboxEvent(PedidoPagoEvent event, Instant now) {
        try {
            String payloadJson = objectMapper.writeValueAsString(Map.of(
                    "order_id", event.orderId().toString(),
                    "client_id", event.clientId().toString(),
                    "event_id", event.eventId().toString(),
                    "seat_id", event.seatId(),
                    "amount_cents", event.amountCents(),
                    "paid_at", event.paidAt().toString()
            ));
            OutboxEventEntity outbox = new OutboxEventEntity(
                    UUID.randomUUID(),
                    "Order",
                    event.orderId().toString(),
                    TOPIC_PEDIDOS_PAGOS,
                    "PedidoPagoEvent",
                    payloadJson,
                    "PENDING",
                    now
            );
            outboxEventRepository.save(outbox);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize PedidoPagoEvent for Outbox", e);
        }
    }
}

