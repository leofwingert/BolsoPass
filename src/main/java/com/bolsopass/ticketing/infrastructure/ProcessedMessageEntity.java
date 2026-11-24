package com.bolsopass.ticketing.infrastructure;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Tabela relacional `processed_messages` para deduplicacao mandatoria de consumidores Kafka (RF-12 / RNF-05 / US-12).
 */
@Entity
@Table(name = "processed_messages")
public class ProcessedMessageEntity {

    @Id
    @Column(name = "message_id", nullable = false, length = 128)
    private String messageId;

    @Column(name = "consumer_group", nullable = false, length = 128)
    private String consumerGroup;

    @Column(name = "processed_at", nullable = false)
    private Instant processedAt;

    protected ProcessedMessageEntity() {
    }

    public ProcessedMessageEntity(String messageId, String consumerGroup, Instant processedAt) {
        this.messageId = messageId;
        this.consumerGroup = consumerGroup;
        this.processedAt = processedAt;
    }

    public String getMessageId() {
        return messageId;
    }

    public String getConsumerGroup() {
        return consumerGroup;
    }

    public Instant getProcessedAt() {
        return processedAt;
    }
}

