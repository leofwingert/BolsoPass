package com.bolsopass.orders.domain;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Maquina de Estados Finitos (FSM) formal do Pedido (RF-08 / US-08).
 * Transicoes validas:
 * - CRIADO -> RESERVADO, CANCELADO
 * - RESERVADO -> AGUARDANDO_PAGAMENTO, EXPIRADO, CANCELADO
 * - AGUARDANDO_PAGAMENTO -> PAGO, RECUSADO, EXPIRADO
 * - RECUSADO -> AGUARDANDO_PAGAMENTO, EXPIRADO, CANCELADO
 * - PAGO -> (Estado Terminal Imutavel: nenhuma transicao permitida!)
 * - EXPIRADO -> (Estado Terminal: jamais pode transicionar para PAGO ou outro estado!)
 * - CANCELADO -> (Estado Terminal: nenhuma transicao permitida!)
 */
public final class OrderStateMachine {

    private static final Map<OrderStatus, Set<OrderStatus>> ALLOWED_TRANSITIONS = new EnumMap<>(OrderStatus.class);

    static {
        ALLOWED_TRANSITIONS.put(OrderStatus.CRIADO,
                EnumSet.of(OrderStatus.RESERVADO, OrderStatus.CANCELADO));
        ALLOWED_TRANSITIONS.put(OrderStatus.RESERVADO,
                EnumSet.of(OrderStatus.AGUARDANDO_PAGAMENTO, OrderStatus.EXPIRADO, OrderStatus.CANCELADO));
        ALLOWED_TRANSITIONS.put(OrderStatus.AGUARDANDO_PAGAMENTO,
                EnumSet.of(OrderStatus.PAGO, OrderStatus.RECUSADO, OrderStatus.EXPIRADO));
        ALLOWED_TRANSITIONS.put(OrderStatus.RECUSADO,
                EnumSet.of(OrderStatus.AGUARDANDO_PAGAMENTO, OrderStatus.EXPIRADO, OrderStatus.CANCELADO));
        ALLOWED_TRANSITIONS.put(OrderStatus.PAGO, EnumSet.noneOf(OrderStatus.class));
        ALLOWED_TRANSITIONS.put(OrderStatus.EXPIRADO, EnumSet.noneOf(OrderStatus.class));
        ALLOWED_TRANSITIONS.put(OrderStatus.CANCELADO, EnumSet.noneOf(OrderStatus.class));
    }

    private OrderStateMachine() {
    }

    public static boolean canTransition(OrderStatus current, OrderStatus target) {
        if (current == null || target == null) {
            return false;
        }
        Set<OrderStatus> allowed = ALLOWED_TRANSITIONS.getOrDefault(current, EnumSet.noneOf(OrderStatus.class));
        return allowed.contains(target);
    }

    public static OrderStatus transition(OrderStatus current, OrderStatus target) {
        Objects.requireNonNull(current, "Current order status cannot be null");
        Objects.requireNonNull(target, "Target order status cannot be null");

        if (current == OrderStatus.PAGO) {
            throw new InvalidOrderStateTransitionException(current, target,
                    "O estado PAGO e terminal e imutavel");
        }
        if (current == OrderStatus.EXPIRADO) {
            throw new InvalidOrderStateTransitionException(current, target,
                    "Um pedido EXPIRADO jamais pode transicionar para " + target);
        }
        if (current == OrderStatus.CANCELADO) {
            throw new InvalidOrderStateTransitionException(current, target,
                    "Um pedido CANCELADO e terminal e nao aceita novas transicoes");
        }
        if (!canTransition(current, target)) {
            throw new InvalidOrderStateTransitionException(current, target,
                    "Transicao nao permitida pelas regras de dominio do BolsoPass");
        }
        return target;
    }

    public static Set<OrderStatus> allowedTargetsFrom(OrderStatus current) {
        Objects.requireNonNull(current, "Current order status cannot be null");
        return EnumSet.copyOf(ALLOWED_TRANSITIONS.getOrDefault(current, EnumSet.noneOf(OrderStatus.class)));
    }
}

