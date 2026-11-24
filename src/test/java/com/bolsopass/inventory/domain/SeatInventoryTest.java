package com.bolsopass.inventory.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SeatInventoryTest {

    private static final UUID EVENT_ID = UUID.fromString("3fa85f64-5717-4562-b3fc-2c963f66afa6");
    private static final Instant T0 = Instant.parse("2026-11-14T20:00:00Z");

    @Test
    @DisplayName("Zero Overselling: 200 threads simultaneas disputando o mesmo assento A-12 -> exatamente 1 sucesso e 199 conflitos (RNF-02 / US-05)")
    void shouldGuaranteeZeroOversellingUnderHighConcurrency() throws InterruptedException {
        SeatInventory inventory = new SeatInventory(EVENT_ID, Clock.fixed(T0, ZoneOffset.UTC));
        inventory.registerSeat("A-12", "Pista Premium", 48000L);

        int concurrentBuyers = 200;
        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        CountDownLatch readyLatch = new CountDownLatch(concurrentBuyers);
        CountDownLatch startGun = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(concurrentBuyers);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger conflictCount = new AtomicInteger(0);

        for (int i = 0; i < concurrentBuyers; i++) {
            final UUID buyerId = UUID.randomUUID();
            executor.submit(() -> {
                readyLatch.countDown();
                try {
                    startGun.await();
                    inventory.reserveSeat("A-12", buyerId);
                    successCount.incrementAndGet();
                } catch (SeatAlreadyReservedException ex) {
                    conflictCount.incrementAndGet();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        assertThat(readyLatch.await(5, TimeUnit.SECONDS)).isTrue();
        startGun.countDown(); // Disparo simultaneo de todas as threads
        assertThat(doneLatch.await(10, TimeUnit.SECONDS)).isTrue();
        executor.shutdownNow();

        // Exatamente 1 reserva criada e 199 rejeitadas com HTTP 409 Conflict (0% sobre-venda)
        assertThat(successCount.get()).isEqualTo(1);
        assertThat(conflictCount.get()).isEqualTo(concurrentBuyers - 1);
        assertThat(inventory.countByStatus(SeatStatus.RESERVADO_TEMPORARIO)).isEqualTo(1);
        assertThat(inventory.countByStatus(SeatStatus.DISPONIVEL)).isEqualTo(0);
    }

    @Test
    @DisplayName("TTL Estrito de 10 Minutos (600s): Expiracao exata em t=600s devolve assento para DISPONIVEL e publica ReservaExpiradaEvent (RNF-03 / US-06)")
    void shouldEnforceStrictTenMinutesTtlAndPublishExpirationEvent() {
        SeatInventory inventory = new SeatInventory(EVENT_ID, Clock.fixed(T0, ZoneOffset.UTC));
        inventory.registerSeat("B-04", "Cadeira Coberta", 35000L);

        UUID buyer1 = UUID.randomUUID();
        Reservation reservation = inventory.reserveSeat("B-04", buyer1);

        assertThat(reservation.getTtlSeconds()).isEqualTo(600L);
        assertThat(reservation.getExpiresAt()).isEqualTo(T0.plusSeconds(600));
        assertThat(reservation.getRemainingTtlSeconds(T0.plusSeconds(180))).isEqualTo(420L);
        assertThat(reservation.isExpiredAt(T0.plusSeconds(599))).isFalse();

        // Em t = 599s (1 segundo antes de expirar), nenhuma reserva e expirada
        inventory.setClock(Clock.fixed(T0.plusSeconds(599), ZoneOffset.UTC));
        assertThat(inventory.expireOverdueReservations()).isEmpty();
        assertThat(inventory.getSeatOrThrow("B-04").getStatus()).isEqualTo(SeatStatus.RESERVADO_TEMPORARIO);

        // Em t = 600s (exatamente 10 minutos), a reserva expira e o assento volta para DISPONIVEL
        Instant t600 = T0.plusSeconds(600);
        inventory.setClock(Clock.fixed(t600, ZoneOffset.UTC));
        List<ReservaExpiradaEvent> expiredEvents = inventory.expireOverdueReservations();

        assertThat(expiredEvents).hasSize(1);
        ReservaExpiradaEvent event = expiredEvents.get(0);
        assertThat(event.eventId()).isEqualTo(EVENT_ID);
        assertThat(event.seatId()).isEqualTo("B-04");
        assertThat(event.reservationId()).isEqualTo(reservation.getReservationId());
        assertThat(event.expiredAt()).isEqualTo(t600);

        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.EXPIRADO);
        assertThat(inventory.getSeatOrThrow("B-04").getStatus()).isEqualTo(SeatStatus.DISPONIVEL);

        // Novo comprador pode agora alocar o assento B-04 devolvido ao catalogo
        UUID buyer2 = UUID.randomUUID();
        Reservation newReservation = inventory.reserveSeat("B-04", buyer2);
        assertThat(newReservation.getStatus()).isEqualTo(ReservationStatus.RESERVADO_TEMPORARIO);
        assertThat(inventory.getSeatOrThrow("B-04").getReservedByUserId()).isEqualTo(buyer2);
    }

    @Test
    @DisplayName("Ciclo Completo do Assento: DISPONIVEL -> RESERVADO_TEMPORARIO -> VENDIDO -> UTILIZADO na catraca")
    void shouldTransitionSeatFromAvailableToSoldAndUtilizedAtGate() {
        SeatInventory inventory = new SeatInventory(EVENT_ID, Clock.fixed(T0, ZoneOffset.UTC));
        Seat seat = inventory.registerSeat("C-01", "Camarote VIP", 95000L);

        Reservation res = inventory.reserveSeat("C-01", UUID.randomUUID());
        assertThat(seat.getStatus()).isEqualTo(SeatStatus.RESERVADO_TEMPORARIO);

        inventory.setClock(Clock.fixed(T0.plusSeconds(120), ZoneOffset.UTC));
        inventory.confirmReservationPayment(res.getReservationId());

        assertThat(seat.getStatus()).isEqualTo(SeatStatus.VENDIDO);
        assertThat(res.getStatus()).isEqualTo(ReservationStatus.CONFIRMADA);

        // Assento VENDIDO nao pode ser liberado nem revendido
        assertThatThrownBy(seat::releaseToAvailable).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> inventory.reserveSeat("C-01", UUID.randomUUID()))
                .isInstanceOf(SeatAlreadyReservedException.class);

        // Bipagem na catraca transiciona para UTILIZADO
        seat.markUtilizedAtGate();
        assertThat(seat.getStatus()).isEqualTo(SeatStatus.UTILIZADO);
    }

    @Test
    @DisplayName("Cancelamento e expiracao na tentativa tardia de pagamento devolvem o assento imediatamente")
    void shouldReleaseSeatOnCancellationOrLatePaymentAttempt() {
        SeatInventory inventory = new SeatInventory(EVENT_ID, Clock.fixed(T0, ZoneOffset.UTC));
        inventory.registerSeat("D-05", "Arquibancada", 18000L);
        inventory.registerSeat("D-06", "Arquibancada", 18000L);

        // Cancelamento voluntario devolve D-05 para DISPONIVEL
        Reservation r1 = inventory.reserveSeat("D-05", UUID.randomUUID());
        inventory.cancelReservation(r1.getReservationId());
        assertThat(r1.getStatus()).isEqualTo(ReservationStatus.CANCELADA);
        assertThat(inventory.getSeatOrThrow("D-05").getStatus()).isEqualTo(SeatStatus.DISPONIVEL);

        // Tentativa de confirmar pagamento de D-06 apos 10 minutos falha e devolve D-06 para DISPONIVEL
        Reservation r2 = inventory.reserveSeat("D-06", UUID.randomUUID());
        inventory.setClock(Clock.fixed(T0.plus(Duration.ofMinutes(11)), ZoneOffset.UTC));

        assertThatThrownBy(() -> inventory.confirmReservationPayment(r2.getReservationId()))
                .isInstanceOf(ReservationExpiredException.class);
        assertThat(r2.getStatus()).isEqualTo(ReservationStatus.EXPIRADO);
        assertThat(inventory.getSeatOrThrow("D-06").getStatus()).isEqualTo(SeatStatus.DISPONIVEL);
        assertThat(inventory.getPublishedExpirationEvents()).hasSize(1);

        // Testes adicionais de invariantes de borda em Seat, Reservation e SeatInventory
        assertThat(inventory.getEventId()).isEqualTo(EVENT_ID);
        assertThat(inventory.getAllSeats()).hasSize(2);
        assertThat(inventory.findReservation(r2.getReservationId())).isPresent();
        assertThat(r2.getRemainingTtlSeconds(T0.plus(Duration.ofMinutes(11)))).isZero();
        assertThatThrownBy(() -> inventory.registerSeat("D-05", "Arquibancada", 18000L))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> inventory.getSeatOrThrow("INEXISTENTE"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> inventory.getReservationOrThrow(UUID.randomUUID()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> r1.expire(T0))
                .isInstanceOf(IllegalStateException.class);

        Seat s = inventory.getSeatOrThrow("D-05");
        assertThat(s.getEventId()).isEqualTo(EVENT_ID);
        assertThat(s.getSection()).isEqualTo("Arquibancada");
        assertThat(s.getPriceCents()).isEqualTo(18000L);
        assertThat(s.getVersion()).isGreaterThan(0L);
        assertThatThrownBy(s::markUtilizedAtGate).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> s.confirmSale(UUID.randomUUID(), T0)).isInstanceOf(IllegalStateException.class);
    }
}
