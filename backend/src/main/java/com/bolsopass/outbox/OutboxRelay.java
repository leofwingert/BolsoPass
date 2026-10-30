package com.bolsopass.outbox;

import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Outbox Relay Worker: lê PENDING, publica e marca PUBLISHED só depois do ACK. */
@Component
public class OutboxRelay {
  private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);

  private final OutboxRepository repo;
  private final EventPublisher publisher;

  public OutboxRelay(OutboxRepository repo, EventPublisher publisher) {
    this.repo = repo;
    this.publisher = publisher;
  }

  @Scheduled(fixedDelay = 500)
  @Transactional
  public void relay() {
    for (OutboxEvent event : repo.lockPendingBatch(PageRequest.of(0, 100))) {
      try {
        publisher.publish(event);
        event.published(Instant.now());
      } catch (RuntimeException e) {
        // Broker fora: o evento continua PENDING e sai na próxima rodada.
        log.warn("Falha ao publicar {} ({}): {}", event.getId(), event.getTopic(), e.getMessage());
        return;
      }
    }
  }
}
