package com.bolsopass.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.kafka.core.KafkaTemplate;

/**
 * Escolhe para onde o relay do outbox publica:
 *
 * <ul>
 *   <li>{@code bolsopass.kafka.enabled=true} (docker-compose e testes com Testcontainers): Kafka;
 *   <li>senão (perfil dev, sem Docker): só o log, como antes.
 * </ul>
 */
@Configuration
public class EventPublisherConfig {

  @Bean
  @ConditionalOnProperty(name = "bolsopass.kafka.enabled", havingValue = "true")
  EventPublisher kafkaEventPublisher(
      KafkaTemplate<String, String> kafka,
      ObjectMapper json,
      @Value("${bolsopass.kafka.ack-timeout:5s}") Duration ackTimeout) {
    return new KafkaEventPublisher(kafka, json, ackTimeout);
  }

  /** Cria os tópicos na subida (doc 03: partições pela chave do agregado). */
  @Bean
  @ConditionalOnProperty(name = "bolsopass.kafka.enabled", havingValue = "true")
  KafkaAdmin.NewTopics bolsopassTopics(@Value("${bolsopass.kafka.replicas:1}") int replicas) {
    return new KafkaAdmin.NewTopics(
        TopicBuilder.name(OutboxWriter.RESERVAS_CRIADAS).partitions(12).replicas(replicas).build(),
        TopicBuilder.name(OutboxWriter.RESERVAS_EXPIRADAS).partitions(12).replicas(replicas).build(),
        TopicBuilder.name(OutboxWriter.PEDIDOS_PAGOS).partitions(12).replicas(replicas).build(),
        TopicBuilder.name(OutboxWriter.PEDIDOS_CANCELADOS).partitions(6).replicas(replicas).build(),
        TopicBuilder.name(OutboxWriter.CHECKIN_REALIZADO).partitions(6).replicas(replicas).build());
  }

  @Bean
  @ConditionalOnMissingBean(EventPublisher.class)
  EventPublisher loggingEventPublisher() {
    return new LoggingEventPublisher();
  }
}
