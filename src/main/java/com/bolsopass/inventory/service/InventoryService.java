package com.bolsopass.inventory.service;

import com.bolsopass.inventory.domain.ReservaExpiradaEvent;
import com.bolsopass.inventory.domain.Reservation;
import com.bolsopass.inventory.domain.Seat;
import com.bolsopass.inventory.domain.SeatInventory;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class InventoryService {

    public static final UUID DEFAULT_EVENT_ID = UUID.fromString("3fa85f64-5717-4562-b3fc-2c963f66afa6");

    private final Map<UUID, SeatInventory> inventoriesByEvent = new ConcurrentHashMap<>();
    private final Map<UUID, Reservation> reservationsIndex = new ConcurrentHashMap<>();
    private Clock clock = Clock.systemUTC();

    public InventoryService() {
        SeatInventory defaultInventory = getOrCreateInventory(DEFAULT_EVENT_ID);
        for (int i = 1; i <= 20; i++) {
            defaultInventory.registerSeat("A-" + i, "Pista Premium", 38500L);
            defaultInventory.registerSeat("B-" + i, "Cadeira Coberta", 35000L);
        }
    }

    public void setClock(Clock clock) {
        this.clock = clock;
        inventoriesByEvent.values().forEach(inv -> inv.setClock(clock));
    }

    public SeatInventory getOrCreateInventory(UUID eventId) {
        return inventoriesByEvent.computeIfAbsent(eventId, id -> new SeatInventory(id, clock));
    }

    public Seat registerSeat(UUID eventId, String seatId, String section, long priceCents) {
        return getOrCreateInventory(eventId).registerSeat(seatId, section, priceCents);
    }

    public Reservation allocateSeat(UUID eventId, String seatId, UUID userId) {
        SeatInventory inventory = getOrCreateInventory(eventId);
        try {
            inventory.getSeatOrThrow(seatId);
        } catch (IllegalArgumentException ex) {
            inventory.registerSeat(seatId, "Setor Geral", 38500L);
        }
        Reservation reservation = inventory.reserveSeat(seatId, userId);
        reservationsIndex.put(reservation.getReservationId(), reservation);
        return reservation;
    }

    public Reservation getReservationOrThrow(UUID reservationId) {
        Reservation reservation = reservationsIndex.get(reservationId);
        if (reservation == null) {
            throw new IllegalArgumentException("Reservation not found: " + reservationId);
        }
        return reservation;
    }

    public void confirmReservation(UUID reservationId) {
        Reservation reservation = getReservationOrThrow(reservationId);
        getOrCreateInventory(reservation.getEventId()).confirmReservationPayment(reservationId);
    }

    public List<ReservaExpiradaEvent> expireAllOverdue() {
        return inventoriesByEvent.values().stream()
                .flatMap(inv -> inv.expireOverdueReservations().stream())
                .toList();
    }
}

