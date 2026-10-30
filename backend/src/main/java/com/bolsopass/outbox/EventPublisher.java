package com.bolsopass.outbox;

/**
 * Destino dos eventos do outbox. A implementação Kafka (spring-kafka, At-Least-Once)
 * entra na parte de mensageria; até lá os eventos vão para o log.
 */
public interface EventPublisher {
  /** Deve lançar exceção se o broker não confirmar (o evento fica PENDING e é reenviado). */
  void publish(OutboxEvent event);
}
