package com.bolsopass.inventory.domain;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Coordenador de Inventario de Assentos (Aggregate Root / Domain Service).
 * Garante:
 * 1. Exclusao Mutua e Zero Sobre-venda (RNF-02)
 * 2. Invariante de Conservacao de Estoque: disponiveis + reservados + vendidos + utilizados == capacidade (RF-06)
 * 3. Expiracao Estrita de 10 minutos (600s) com devolucao atomica e emissao de ReservaExpiradaEvent (RNF-03 / RF-07)
 */
public class SeatInventory {

    private final UUID eventId;
    private final Map<String, Seat> seatsByCode = new ConcurrentHashMap<>();
    private final Map<UUID, Reservation> reservationsById = new ConcurrentHashMap<>();
    private final List<ReservaExpiradaEvent> publishedExpirationEvents = new CopyOnWriteArrayList<>();
    private Clock clock;

    public SeatInventory(UUID eventId, Clock clock) {
        this.eventId = Objects.requireNonNull(eventId, "eventId cannot be null");
        this.clock = Objects.requireNonNull(clock, "clock cannot be null");
    }

    public SeatInventory(UUID eventId) {
        this(eventId, Clock.systemUTC());
    }

    public void setClock(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock cannot be null");
    }

    public Seat registerSeat(String seatId, String section, long priceCents) {
        Seat seat = new Seat(eventId, seatId, section, priceCents);
        Seat existing = seatsByCode.putIfAbsent(seat.getSeatId(), seat);
        if (existing != null) {
            throw new IllegalArgumentException("Seat already registered: " + seatId);
        }
        return seat;
    }

    public Reservation reserveSeat(String seatId, UUID userId) {
        Objects.requireNonNull(userId, "userId cannot be null");
        Seat seat = getSeatOrThrow(seatId);
        Instant now = clock.instant();
        UUID reservationId = UUID.randomUUID();
        synchronized (seat) {
            if (seat.getStatus() == SeatStatus.RESERVADO_TEMPORARIO
                    && seat.getCurrentReservationId() != null
                    && seat.getReservedUntil() != null
                    && !now.isBefore(seat.getReservedUntil())) {
                Reservation previous = reservationsById.get(seat.getCurrentReservationId());
                if (previous != null && previous.getStatus() == ReservationStatus.RESERVADO_TEMPORARIO) {
                    publishedExpirationEvents.add(previous.expire(now));
                }
                seat.releaseToAvailable();
            }
            Reservation reservation = seat.allocateAtomically(reservationId, userId, now);
            reservationsById.put(reservationId, reservation);
            assertConservationInvariant();
            return reservation;
        }
    }

    public void confirmReservationPayment(UUID reservationId) {
        Objects.requireNonNull(reservationId, "reservationId cannot be null");
        Reservation reservation = getReservationOrThrow(reservationId);
        Seat seat = getSeatOrThrow(reservation.getSeatId());
        Instant now = clock.instant();
        synchronized (seat) {
            if (reservation.isExpiredAt(now)) {
                if (reservation.getStatus() == ReservationStatus.RESERVADO_TEMPORARIO) {
                    publishedExpirationEvents.add(reservation.expire(now));
                }
                if (seat.getStatus() == SeatStatus.RESERVADO_TEMPORARIO
                        && reservationId.equals(seat.getCurrentReservationId())) {
                    seat.releaseToAvailable();
                }
                throw new ReservationExpiredException(reservationId);
            }
            reservation.confirm(now);
            seat.confirmSale(reservationId, now);
            assertConservationInvariant();
        }
    }

    public void cancelReservation(UUID reservationId) {
        Objects.requireNonNull(reservationId, "reservationId cannot be null");
        Reservation reservation = getReservationOrThrow(reservationId);
        Seat seat = getSeatOrThrow(reservation.getSeatId());
        synchronized (seat) {
            reservation.cancel();
            if (seat.getStatus() == SeatStatus.RESERVADO_TEMPORARIO
                    && reservationId.equals(seat.getCurrentReservationId())) {
                seat.releaseToAvailable();
            }
            assertConservationInvariant();
        }
    }

    /**
     * Varre reservas cujo TTL de 10 minutos (600s) expirou, devolve o assento para DISPONIVEL
     * e emite ReservaExpiradaEvent para o topico `reservas.expiradas`.
     */
    public List<ReservaExpiradaEvent> expireOverdueReservations() {
        Instant now = clock.instant();
        List<ReservaExpiradaEvent> newlyExpired = new ArrayList<>();
        for (Reservation reservation : reservationsById.values()) {
            if (reservation.getStatus() == ReservationStatus.RESERVADO_TEMPORARIO && reservation.isExpiredAt(now)) {
                Seat seat = seatsByCode.get(reservation.getSeatId());
                if (seat != null) {
                    synchronized (seat) {
                        if (reservation.getStatus() == ReservationStatus.RESERVADO_TEMPORARIO && reservation.isExpiredAt(now)) {
                            ReservaExpiradaEvent event = reservation.expire(now);
                            if (seat.getStatus() == SeatStatus.RESERVADO_TEMPORARIO
                                    && reservation.getReservationId().equals(seat.getCurrentReservationId())) {
                                seat.releaseToAvailable();
                            }
                            newlyExpired.add(event);
                            publishedExpirationEvents.add(event);
                        }
                    }
                }
            }
        }
        assertConservationInvariant();
        return newlyExpired;
    }

    public void assertConservationInvariant() {
        int total = seatsByCode.size();
        long accounted = seatsByCode.values().stream()
                .map(Seat::getStatus)
                .filter(s -> s == SeatStatus.DISPONIVEL
                        || s == SeatStatus.RESERVADO_TEMPORARIO
                        || s == SeatStatus.VENDIDO
                        || s == SeatStatus.UTILIZADO)
                .count();
        if (total != accounted) {
            throw new IllegalStateException("Inventory conservation invariant violated: total=" + total + ", accounted=" + accounted);
        }
    }

    public Seat getSeatOrThrow(String seatId) {
        Seat seat = seatsByCode.get(seatId == null ? "" : seatId.trim());
        if (seat == null) {
            throw new IllegalArgumentException("Seat not found: " + seatId);
        }
        return seat;
    }

    public Reservation getReservationOrThrow(UUID reservationId) {
        Reservation reservation = reservationsById.get(reservationId);
        if (reservation == null) {
            throw new IllegalArgumentException("Reservation not found: " + reservationId);
        }
        return reservation;
    }

    public Optional<Reservation> findReservation(UUID reservationId) {
        return Optional.ofNullable(reservationsById.get(reservationId));
    }

    public Collection<Seat> getAllSeats() {
        return List.copyOf(seatsByCode.values());
    }

    public long countByStatus(SeatStatus status) {
        return seatsByCode.values().stream().filter(s -> s.getStatus() == status).count();
    }

    public List<ReservaExpiradaEvent> getPublishedExpirationEvents() {
        return List.copyOf(publishedExpirationEvents);
    }

    public UUID getEventId() {
        return eventId;
    }
}

