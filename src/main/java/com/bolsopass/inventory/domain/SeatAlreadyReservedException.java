package com.bolsopass.inventory.domain;

public class SeatAlreadyReservedException extends RuntimeException {
    private final String seatId;

    public SeatAlreadyReservedException(String seatId) {
        super("O assento " + seatId + " acabou de ser selecionado por outro cliente.");
        this.seatId = seatId;
    }

    public String getSeatId() {
        return seatId;
    }
}

