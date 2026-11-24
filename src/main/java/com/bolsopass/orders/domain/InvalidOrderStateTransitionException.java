package com.bolsopass.orders.domain;

public class InvalidOrderStateTransitionException extends IllegalStateException {

    private final OrderStatus fromStatus;
    private final OrderStatus toStatus;

    public InvalidOrderStateTransitionException(OrderStatus fromStatus, OrderStatus toStatus, String detail) {
        super("Transicao de estado invalida na FSM do Pedido: " + fromStatus + " -> " + toStatus + " (" + detail + ")");
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
    }

    public OrderStatus getFromStatus() {
        return fromStatus;
    }

    public OrderStatus getToStatus() {
        return toStatus;
    }
}

