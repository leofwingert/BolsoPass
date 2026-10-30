package com.bolsopass.outbox;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnMissingBean(value = EventPublisher.class, ignored = LoggingEventPublisher.class)
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
