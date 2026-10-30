package com.bolsopass.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Grava o evento na MESMA transação da mudança de estado (MANDATORY falha se chamado fora
 * de uma transação). Assim nunca existe pagamento sem evento, nem evento sem pagamento.
 */
@Component
public class OutboxWriter {
  /** Tópicos da skill kafka-contracts. */
  public static final String RESERVAS_CRIADAS = "reservas.criadas";

  public static final String RESERVAS_EXPIRADAS = "reservas.expiradas";
  public static final String PEDIDOS_PAGOS = "pedidos.pagos";
  public static final String PEDIDOS_CANCELADOS = "pedidos.cancelados";

  private final OutboxRepository repo;
  private final ObjectMapper json;

  public OutboxWriter(OutboxRepository repo, ObjectMapper json) {
    this.repo = repo;
    this.json = json;
  }

  @Transactional(propagation = Propagation.MANDATORY)
  public void write(String topic, String key, String eventType, Object payload) {
    try {
      repo.save(new OutboxEvent(topic, key, eventType, json.writeValueAsString(payload), Instant.now()));
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Payload de evento inválido", e);
    }
  }
}
