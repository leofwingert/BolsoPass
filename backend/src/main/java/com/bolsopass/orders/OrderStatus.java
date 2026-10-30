package com.bolsopass.orders;

import com.bolsopass.shared.DomainException;
import java.util.EnumMap;
import java.util.Map;

/** FSM do pedido, idêntica à tabela de "06 - Maquinas de Estados e Regras de Negocio". */
public enum OrderStatus {
  CRIADO,
  RESERVADO,
  AGUARDANDO_PAGAMENTO,
  PAGO,
  RECUSADO,
  EXPIRADO,
  CANCELADO;

  public enum Event {
    ASSENTO_BLOQUEADO,
    BLOQUEIO_FALHOU,
    SUBMETER_PAGAMENTO,
    PAGAMENTO_APROVADO,
    PAGAMENTO_RECUSADO,
    TIMEOUT_10_MINUTOS,
    DESISTENCIA
  }

  private static final Map<OrderStatus, Map<Event, OrderStatus>> TRANSITIONS =
      new EnumMap<>(OrderStatus.class);

  static {
    TRANSITIONS.put(
        CRIADO, Map.of(Event.ASSENTO_BLOQUEADO, RESERVADO, Event.BLOQUEIO_FALHOU, CANCELADO));
    TRANSITIONS.put(
        RESERVADO,
        Map.of(Event.SUBMETER_PAGAMENTO, AGUARDANDO_PAGAMENTO, Event.TIMEOUT_10_MINUTOS, EXPIRADO));
    TRANSITIONS.put(
        AGUARDANDO_PAGAMENTO,
        Map.of(
            Event.PAGAMENTO_APROVADO, PAGO,
            Event.PAGAMENTO_RECUSADO, RECUSADO,
            Event.TIMEOUT_10_MINUTOS, EXPIRADO));
    TRANSITIONS.put(
        RECUSADO, Map.of(Event.SUBMETER_PAGAMENTO, AGUARDANDO_PAGAMENTO, Event.DESISTENCIA, CANCELADO));
    TRANSITIONS.put(PAGO, Map.of());
    TRANSITIONS.put(EXPIRADO, Map.of());
    TRANSITIONS.put(CANCELADO, Map.of());
  }

  public boolean canApply(Event event) {
    return TRANSITIONS.get(this).containsKey(event);
  }

  public OrderStatus apply(Event event) {
    OrderStatus next = TRANSITIONS.get(this).get(event);
    if (next == null) throw new DomainException("Transição proibida: " + this + " --" + event + "-->");
    return next;
  }

  public boolean isTerminal() {
    return TRANSITIONS.get(this).isEmpty();
  }
}
