package com.bolsopass.outbox;

/**
 * Destino dos eventos do outbox: {@link KafkaEventPublisher} (at-least-once, acks=all) ou
 * {@link LoggingEventPublisher} no perfil dev. Ver {@link EventPublisherConfig}.
 */
public interface EventPublisher {
  /** Deve lançar exceção se o broker não confirmar (o evento fica PENDING e é reenviado). */
  void publish(OutboxEvent event);
}
