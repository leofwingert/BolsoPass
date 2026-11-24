package com.bolsopass.orders.service;

import com.bolsopass.orders.infrastructure.OutboxEventEntity;
import com.bolsopass.orders.infrastructure.OutboxEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Relay Worker desacoplado do Transactional Outbox Pattern (RF-10 / RNF-04 / US-09).
 * Seleciona eventos pendentes usando `FOR UPDATE SKIP LOCKED`, publica no Apache Kafka
 * e atualiza `status = 'PUBLISHED'` somente apos o ACK do broker.
 */
@Component
public class OutboxRelayWorker {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelayWorker.class);

    private final OutboxEventRepository outboxEventRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private volatile boolean brokerAvailable = true;

    public OutboxRelayWorker(OutboxEventRepository outboxEventRepository, KafkaTemplate<String, String> kafkaTemplate) {
        this.outboxEventRepository = outboxEventRepository;
        this.kafkaTemplate = kafkaTemplate;
    }

    public void setBrokerAvailable(boolean brokerAvailable) {
        this.brokerAvailable = brokerAvailable;
    }

    public boolean isBrokerAvailable() {
        return brokerAvailable;
    }

    @Transactional
    public int relayPendingEvents() {
        if (!brokerAvailable) {
            log.warn("Kafka broker is marked unavailable; skipping outbox relay cycle (events remain safely PENDING in PostgreSQL)");
            return 0;
        }

        List<OutboxEventEntity> pendingBatch = outboxEventRepository.findPendingBatchForUpdateSkipLocked();
        int publishedCount = 0;

        for (OutboxEventEntity event : pendingBatch) {
            try {
                kafkaTemplate.send(event.getTopic(), event.getAggregateId(), event.getPayload())
                        .get(10, TimeUnit.SECONDS);
                event.markPublished(Instant.now());
                outboxEventRepository.save(event);
                publishedCount++;
            } catch (Exception ex) {
                log.warn("Failed to publish outbox event {} to Kafka topic {}: {}. Will retry on next cycle.",
                        event.getId(), event.getTopic(), ex.getMessage());
                break;
            }
        }
        return publishedCount;
    }
}

