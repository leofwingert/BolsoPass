package com.bolsopass.inventory.domain;

import java.util.UUID;

public class ReservationExpiredException extends RuntimeException {
    private final UUID reservationId;

    public ReservationExpiredException(UUID reservationId) {
        super("O tempo de 10 minutos para este checkout expirou. O assento foi devolvido.");
        this.reservationId = reservationId;
    }

    public UUID getReservationId() {
        return reservationId;
    }
}

