package com.bolsopass.orders;

import static com.bolsopass.orders.OrderStatus.Event.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bolsopass.shared.DomainException;
import java.util.EnumSet;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.api.Test;

class OrderStatusTest {

  @ParameterizedTest
  @CsvSource({
    "CRIADO,ASSENTO_BLOQUEADO,RESERVADO",
    "CRIADO,BLOQUEIO_FALHOU,CANCELADO",
    "RESERVADO,SUBMETER_PAGAMENTO,AGUARDANDO_PAGAMENTO",
    "RESERVADO,TIMEOUT_10_MINUTOS,EXPIRADO",
    "AGUARDANDO_PAGAMENTO,PAGAMENTO_APROVADO,PAGO",
    "AGUARDANDO_PAGAMENTO,PAGAMENTO_RECUSADO,RECUSADO",
    "AGUARDANDO_PAGAMENTO,TIMEOUT_10_MINUTOS,EXPIRADO",
    "RECUSADO,SUBMETER_PAGAMENTO,AGUARDANDO_PAGAMENTO",
    "RECUSADO,DESISTENCIA,CANCELADO"
  })
  void transicoesValidas(OrderStatus from, OrderStatus.Event event, OrderStatus to) {
    assertThat(from.apply(event)).isEqualTo(to);
  }

  @ParameterizedTest
  @EnumSource(OrderStatus.Event.class)
  void pagoEhImutavel(OrderStatus.Event event) {
    assertThatThrownBy(() -> OrderStatus.PAGO.apply(event)).isInstanceOf(DomainException.class);
  }

  @Test
  void expiradoNuncaViraPago() {
    assertThat(OrderStatus.EXPIRADO.canApply(PAGAMENTO_APROVADO)).isFalse();
    assertThatThrownBy(() -> OrderStatus.EXPIRADO.apply(PAGAMENTO_APROVADO))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void reservadoNaoPodeSerCanceladoDireto() {
    assertThat(OrderStatus.RESERVADO.canApply(DESISTENCIA)).isFalse();
  }

  @Test
  void terminais() {
    EnumSet<OrderStatus> terminal = EnumSet.noneOf(OrderStatus.class);
    for (OrderStatus s : OrderStatus.values()) if (s.isTerminal()) terminal.add(s);
    assertThat(terminal).containsExactlyInAnyOrder(OrderStatus.PAGO, OrderStatus.EXPIRADO, OrderStatus.CANCELADO);
  }
}
