package com.bolsopass.domain;

import com.bolsopass.catalog.domain.EventCatalogItem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EventCatalogItemAvailabilityTest {

    private static final Instant T0 = Instant.parse("2026-11-14T20:00:00Z");

    private EventCatalogItem event;
    private EventCatalogItem.TicketLot lot1;
    private EventCatalogItem.TicketLot lot2;

    @BeforeEach
    void setUp() {
        event = new EventCatalogItem(
                UUID.randomUUID(), UUID.randomUUID(),
                "Festival Rock PUCRS 2026", "Estadio Central PUCRS",
                1000, T0, T0.plusSeconds(14400)
        );
        // lot1: [T0, T0+1h) | lot2: [T0+30min, T0+2h)
        lot1 = new EventCatalogItem.TicketLot(
                UUID.randomUUID(), "1o Lote Pista", 38500L, 600, T0, T0.plusSeconds(3600));
        lot2 = new EventCatalogItem.TicketLot(
                UUID.randomUUID(), "2o Lote Cadeira", 35000L, 300,
                T0.plusSeconds(1800), T0.plusSeconds(7200));
    }

    @Test
    @DisplayName("Capacidade restante: total do local menos a soma das cotas dos lotes")
    void shouldComputeRemainingCapacity() {
        assertThat(event.getRemainingCapacity()).isEqualTo(1000);

        event.addLot(lot1);
        assertThat(event.getRemainingCapacity()).isEqualTo(400);

        event.addLot(lot2);
        assertThat(event.getRemainingCapacity()).isEqualTo(100);
    }

    @Test
    @DisplayName("Lote a venda: inicio inclusivo e fim exclusivo")
    void shouldTreatLotWindowAsStartInclusiveEndExclusive() {
        assertThat(lot1.isOnSaleAt(T0.minusSeconds(1))).isFalse();
        assertThat(lot1.isOnSaleAt(T0)).isTrue();
        assertThat(lot1.isOnSaleAt(T0.plusSeconds(1800))).isTrue();
        assertThat(lot1.isOnSaleAt(T0.plusSeconds(3600))).isFalse();
        assertThat(lot1.isOnSaleAt(T0.plusSeconds(3601))).isFalse();
    }

    @Test
    @DisplayName("getLotsOnSaleAt retorna apenas os lotes vigentes no instante informado")
    void shouldReturnOnlyLotsOnSaleAtGivenInstant() {
        event.addLot(lot1);
        event.addLot(lot2);

        assertThat(event.getLotsOnSaleAt(T0.minusSeconds(1))).isEmpty();
        assertThat(event.getLotsOnSaleAt(T0)).containsExactly(lot1);
        assertThat(event.getLotsOnSaleAt(T0.plusSeconds(1800))).containsExactly(lot1, lot2);
        assertThat(event.getLotsOnSaleAt(T0.plusSeconds(3600))).containsExactly(lot2);
        assertThat(event.getLotsOnSaleAt(T0.plusSeconds(7200))).isEmpty();
    }

    @Test
    @DisplayName("isUpcoming: verdadeiro apenas antes do inicio do evento")
    void shouldIdentifyUpcomingEvent() {
        assertThat(event.isUpcoming(T0.minusSeconds(1))).isTrue();
        assertThat(event.isUpcoming(T0)).isFalse();
        assertThat(event.isUpcoming(T0.plusSeconds(60))).isFalse();
    }

    @Test
    @DisplayName("Instante nulo e rejeitado")
    void shouldRejectNullInstant() {
        assertThatThrownBy(() -> lot1.isOnSaleAt(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> event.getLotsOnSaleAt(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> event.isUpcoming(null)).isInstanceOf(NullPointerException.class);
    }
}