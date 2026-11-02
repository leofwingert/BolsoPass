package com.bolsopass.outbox;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Publicador do perfil dev (sem Docker): só escreve o evento no log. Ver {@link EventPublisherConfig}. */
public class LoggingEventPublisher implements EventPublisher {
  private static final Logger log = LoggerFactory.getLogger(LoggingEventPublisher.class);

  @Override
  public void publish(OutboxEvent event) {
    log.info(
        "[outbox] {} key={} type={} payload={}",
        event.getTopic(),
        event.getMessageKey(),
        event.getEventType(),
        event.getPayload());
  }
}
