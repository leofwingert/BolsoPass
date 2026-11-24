package com.bolsopass.ticketing.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ProcessedMessageRepository extends JpaRepository<ProcessedMessageEntity, String> {
    boolean existsByMessageIdAndConsumerGroup(String messageId, String consumerGroup);
}

