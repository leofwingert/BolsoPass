package com.bolsopass.orders.domain;

import com.bolsopass.inventory.domain.Reservation;
import com.bolsopass.inventory.domain.ReservationExpiredException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderStateMachineTest {

    private static final Instant T0 = Instant.parse("2026-11-14T20:00:00Z");
    private static final UUID CLIENT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID EVENT_ID = UUID.fromString("3fa85f64-5717-4562-b3fc-2c963f66afa6");

    @Test
    @DisplayName("FSM: Caminho feliz completo CRIADO -> RESERVADO -> AGUARDANDO_PAGAMENTO -> PAGO")
    void shouldTransitionThroughHappyPathToPaid() {
        UUID reservationId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        Reservation reservation = new Reservation(reservationId, EVENT_ID, "A-12", CLIENT_ID, T0);

        Order order = Order.createFromReservation(orderId, reservation, 38500L, T0);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.RESERVADO);
        assertThat(order.getReservationId()).isEqualTo(reservationId);
        assertThat(order.getReservationExpiresAt()).isEqualTo(T0.plusSeconds(600));

        Instant tSubmit = T0.plusSeconds(120);
        order.submitPayment("idem-key-001", "CREDIT_CARD", 2, tSubmit);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.AGUARDANDO_PAGAMENTO);
        assertThat(order.getLastIdempotencyKey()).isEqualTo("idem-key-001");
        assertThat(order.getPaymentMethod()).isEqualTo("CREDIT_CARD");
        assertThat(order.getInstallments()).isEqualTo(2);

        Instant tPay = T0.plusSeconds(125);
        PedidoPagoEvent event = order.confirmPayment(tPay);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAGO);
        assertThat(order.getStatus().isTerminal()).isTrue();
        assertThat(order.getPaidAt()).isEqualTo(tPay);
        assertThat(order.getUpdatedAt()).isEqualTo(tPay);

        assertThat(event.orderId()).isEqualTo(orderId);
        assertThat(event.clientId()).isEqualTo(CLIENT_ID);
        assertThat(event.eventId()).isEqualTo(EVENT_ID);
        assertThat(event.seatId()).isEqualTo("A-12");
        assertThat(event.amountCents()).isEqualTo(38500L);
        assertThat(event.paidAt()).isEqualTo(tPay);
    }

    @Test
    @DisplayName("FSM: Estado PAGO e terminal e imutavel - qualquer tentativa de transicao lanca excecao")
    void shouldRejectAnyTransitionFromTerminalPaidState() {
        Reservation reservation = new Reservation(UUID.randomUUID(), EVENT_ID, "B-04", CLIENT_ID, T0);
        Order order = Order.createFromReservation(UUID.randomUUID(), reservation, 35000L, T0);
        order.submitPayment("idem-key-002", "PIX", 1, T0.plusSeconds(10));
        order.confirmPayment(T0.plusSeconds(15));

        for (OrderStatus target : OrderStatus.values()) {
            assertThat(OrderStateMachine.canTransition(OrderStatus.PAGO, target)).isFalse();
            assertThatThrownBy(() -> OrderStateMachine.transition(OrderStatus.PAGO, target))
                    .isInstanceOf(InvalidOrderStateTransitionException.class)
                    .hasMessageContaining("PAGO");
        }

        assertThatThrownBy(() -> order.cancel("Tentativa de estorno apos PAGO", T0.plusSeconds(30)))
                .isInstanceOf(InvalidOrderStateTransitionException.class);
        assertThatThrownBy(() -> order.expire(T0.plusSeconds(600)))
                .isInstanceOf(InvalidOrderStateTransitionException.class);
        assertThatThrownBy(() -> order.declinePayment("Decline tardio", T0.plusSeconds(30)))
                .isInstanceOf(InvalidOrderStateTransitionException.class);
    }

    @Test
    @DisplayName("FSM: Pedido EXPIRADO jamais pode transicionar para PAGO (Invariante US-08)")
    void shouldNeverAllowExpiredOrderToTransitionToPaid() {
        Reservation reservation = new Reservation(UUID.randomUUID(), EVENT_ID, "A-01", CLIENT_ID, T0);
        Order order = Order.createFromReservation(UUID.randomUUID(), reservation, 38500L, T0);
        order.expire(T0.plusSeconds(600));

        assertThat(order.getStatus()).isEqualTo(OrderStatus.EXPIRADO);
        assertThat(order.getStatus().isTerminal()).isTrue();

        assertThatThrownBy(() -> order.confirmPayment(T0.plusSeconds(601)))
                .isInstanceOf(InvalidOrderStateTransitionException.class)
                .satisfies(ex -> {
                    InvalidOrderStateTransitionException fsmEx = (InvalidOrderStateTransitionException) ex;
                    assertThat(fsmEx.getFromStatus()).isEqualTo(OrderStatus.EXPIRADO);
                    assertThat(fsmEx.getToStatus()).isEqualTo(OrderStatus.PAGO);
                });
    }

    @Test
    @DisplayName("FSM: Tentativa de confirmar pagamento apos estourar TTL de 10 minutos transiciona para EXPIRADO e lanca excecao")
    void shouldExpireAndRejectPaymentWhenTenMinutesTtlExceeded() {
        Reservation reservation = new Reservation(UUID.randomUUID(), EVENT_ID, "A-05", CLIENT_ID, T0);
        Order order = Order.createFromReservation(UUID.randomUUID(), reservation, 38500L, T0);
        order.submitPayment("idem-key-003", "CREDIT_CARD", 1, T0.plusSeconds(550));

        Instant atTenMinutes = T0.plusSeconds(600);
        assertThatThrownBy(() -> order.confirmPayment(atTenMinutes))
                .isInstanceOf(ReservationExpiredException.class);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.EXPIRADO);
    }

    @Test
    @DisplayName("FSM: Recuperacao de pagamento RECUSADO com novo cartao dentro da janela de 10 minutos (US-11)")
    void shouldAllowRetryFromDeclinedWithinTtlWindow() {
        Reservation reservation = new Reservation(UUID.randomUUID(), EVENT_ID, "C-10", CLIENT_ID, T0);
        Order order = Order.createFromReservation(UUID.randomUUID(), reservation, 48000L, T0);

        order.submitPayment("idem-attempt-1", "CREDIT_CARD", 1, T0.plusSeconds(60));
        order.declinePayment("INSUFFICIENT_FUNDS", T0.plusSeconds(65));
        assertThat(order.getStatus()).isEqualTo(OrderStatus.RECUSADO);
        assertThat(order.getDeclineReason()).isEqualTo("INSUFFICIENT_FUNDS");

        // Nova tentativa dentro dos 10 minutos restantes (4 minutos decorridos)
        order.submitPayment("idem-attempt-2", "CREDIT_CARD", 3, T0.plusSeconds(240));
        assertThat(order.getStatus()).isEqualTo(OrderStatus.AGUARDANDO_PAGAMENTO);
        assertThat(order.getDeclineReason()).isNull();

        order.confirmPayment(T0.plusSeconds(245));
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAGO);
    }

    @Test
    @DisplayName("FSM: Cancelamento explicito a partir de CRIADO, RESERVADO ou RECUSADO e bloqueio pos-CANCELADO")
    void shouldSupportValidCancellationsAndBlockTransitionsAfterCancelled() {
        Order createdOrder = new Order(UUID.randomUUID(), CLIENT_ID, EVENT_ID, "D-01", 25000L, T0);
        assertThat(createdOrder.getStatus()).isEqualTo(OrderStatus.CRIADO);
        assertThat(OrderStateMachine.allowedTargetsFrom(OrderStatus.CRIADO))
                .containsExactlyInAnyOrder(OrderStatus.RESERVADO, OrderStatus.CANCELADO);

        createdOrder.cancel("Falha na alocacao de assento", T0.plusSeconds(1));
        assertThat(createdOrder.getStatus()).isEqualTo(OrderStatus.CANCELADO);
        assertThat(createdOrder.getStatus().isTerminal()).isTrue();

        assertThatThrownBy(() -> createdOrder.attachReservation(UUID.randomUUID(), T0.plusSeconds(600), T0.plusSeconds(2)))
                .isInstanceOf(InvalidOrderStateTransitionException.class);

        // Cancelamento a partir de RECUSADO
        Reservation res = new Reservation(UUID.randomUUID(), EVENT_ID, "D-02", CLIENT_ID, T0);
        Order declinedOrder = Order.createFromReservation(UUID.randomUUID(), res, 25000L, T0);
        declinedOrder.submitPayment("idem-d2", "CREDIT_CARD", 1, T0.plusSeconds(20));
        declinedOrder.declinePayment(null, T0.plusSeconds(22));
        assertThat(declinedOrder.getDeclineReason()).isEqualTo("PAYMENT_DECLINED");

        declinedOrder.cancel("Cliente desistiu da compra", T0.plusSeconds(50));
        assertThat(declinedOrder.getStatus()).isEqualTo(OrderStatus.CANCELADO);
    }

    @Test
    @DisplayName("FSM: Rejeicao de saltos ilegais (ex: CRIADO -> PAGO ou RESERVADO -> PAGO sem AGUARDANDO_PAGAMENTO)")
    void shouldRejectIllegalDirectStateJumps() {
        assertThat(OrderStateMachine.canTransition(null, OrderStatus.PAGO)).isFalse();
        assertThat(OrderStateMachine.canTransition(OrderStatus.CRIADO, null)).isFalse();

        assertThatThrownBy(() -> OrderStateMachine.transition(OrderStatus.CRIADO, OrderStatus.PAGO))
                .isInstanceOf(InvalidOrderStateTransitionException.class);
        assertThatThrownBy(() -> OrderStateMachine.transition(OrderStatus.RESERVADO, OrderStatus.PAGO))
                .isInstanceOf(InvalidOrderStateTransitionException.class);
    }

    @Test
    @DisplayName("IdempotencyStore: Deduplicacao em janela de 24h e deteccao de conflito de payload (US-10 / RNF-05)")
    void shouldDeduplicateWithin24HoursAndDetectPayloadConflict() {
        IdempotencyStore<String> store = new IdempotencyStore<>();
        String key = "7b9e8312-d9f2-4b2a-a9e1-e63d865cfa99";
        String fingerprint = "res_1:CREDIT_CARD:1";

        // 1a chamada inicia processamento
        Optional<String> first = store.tryBeginOrGetCached(key, fingerprint, T0);
        assertThat(first).isEmpty();

        // Chamada concorrente enquanto ainda esta PROCESSING deve lancar 409 Conflict
        assertThatThrownBy(() -> store.tryBeginOrGetCached(key, fingerprint, T0.plusSeconds(1)))
                .isInstanceOf(IdempotencyConflictException.class)
                .satisfies(ex -> assertThat(((IdempotencyConflictException) ex).getIdempotencyKey()).isEqualTo(key));

        // Conclui processamento e armazena resposta
        store.complete(key, fingerprint, "ORDER_PAID_200_OK", T0.plusSeconds(2));

        // 2a e 3a chamadas dentro de 24h retornam exatamente a resposta em cache
        Optional<String> cached = store.tryBeginOrGetCached(key, fingerprint, T0.plus(Duration.ofHours(12)));
        assertThat(cached).contains("ORDER_PAID_200_OK");

        // Reuso da mesma chave com payload diferente dentro de 24h deve lancar IdempotencyConflictException
        assertThatThrownBy(() -> store.tryBeginOrGetCached(key, "res_2:PIX:1", T0.plus(Duration.ofHours(12))))
                .isInstanceOf(IdempotencyConflictException.class);

        // Apos 24 horas + 1 segundo, a chave expira e permite novo inicio
        Optional<String> after24h = store.tryBeginOrGetCached(key, fingerprint, T0.plus(Duration.ofHours(24).plusSeconds(1)));
        assertThat(after24h).isEmpty();
        store.abort(key);

        assertThatThrownBy(() -> store.tryBeginOrGetCached(" ", fingerprint, T0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> store.tryBeginOrGetCached(key, " ", T0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Order(UUID.randomUUID(), CLIENT_ID, EVENT_ID, " ", 100L, T0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Order(UUID.randomUUID(), CLIENT_ID, EVENT_ID, "A-1", 0L, T0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PedidoPagoEvent(UUID.randomUUID(), CLIENT_ID, EVENT_ID, " ", 100L, T0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PedidoPagoEvent(UUID.randomUUID(), CLIENT_ID, EVENT_ID, "A-1", 0L, T0))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
