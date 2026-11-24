package com.bolsopass.orders.domain;

public enum OrderStatus {
    CRIADO,
    RESERVADO,
    AGUARDANDO_PAGAMENTO,
    PAGO,
    RECUSADO,
    EXPIRADO,
    CANCELADO;

    public boolean isTerminal() {
        return this == PAGO || this == EXPIRADO || this == CANCELADO;
    }
}

