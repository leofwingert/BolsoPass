package com.bolsopass.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.kafka.core.KafkaTemplate;

/**
 * Publica a linha do outbox no Kafka (doc 03 do Pacote 2) e só retorna depois do ACK do broker
 * (acks=all). Qualquer falha vira exceção: o relay deixa o evento PENDING e tenta de novo.
 *
 * <p>O {@code eventId} do envelope é o id da linha do outbox, então um reenvio carrega o mesmo
 * id e o consumidor descarta a duplicata (processed_messages).
 */
public class KafkaEventPublisher implements EventPublisher {
  static final String PRODUCER = "bolsopass-api";
  static final String EVENT_VERSION = "1.0";

  private final KafkaTemplate<String, String> kafka;
  private final ObjectMapper json;
  private final Duration ackTimeout;

  public KafkaEventPublisher(KafkaTemplate<String, String> kafka, ObjectMapper json, Duration ackTimeout) {
    this.kafka = kafka;
    this.json = json;
    this.ackTimeout = ackTimeout;
  }

  @Override
  public void publish(OutboxEvent event) {
    ProducerRecord<String, String> record =
        new ProducerRecord<>(event.getTopic(), event.getMessageKey(), envelope(event));
    header(record, "event-id", event.getId());
    header(record, "event-type", event.getEventType());
    header(record, "content-type", "application/json");
    try {
      kafka.send(record).get(ackTimeout.toMillis(), TimeUnit.MILLISECONDS);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Publicação interrompida", e);
    } catch (ExecutionException | TimeoutException e) {
      throw new IllegalStateException("Kafka não confirmou o evento " + event.getId(), e);
    }
  }

  /** Envelope padrão do doc 03: eventId, eventType, eventVersion, occurredAt, producer, aggregateId, payload. */
  String envelope(OutboxEvent event) {
    try {
      ObjectNode root = json.createObjectNode();
      root.put("eventId", event.getId());
      root.put("eventType", event.getEventType());
      root.put("eventVersion", EVENT_VERSION);
      root.put("occurredAt", event.getCreatedAt().toString());
      root.put("producer", PRODUCER);
      root.put("aggregateId", event.getMessageKey());
      root.set("payload", json.readTree(event.getPayload()));
      return json.writeValueAsString(root);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Payload inválido no outbox " + event.getId(), e);
    }
  }

  private static void header(ProducerRecord<String, String> record, String name, String value) {
    record.headers().add(name, value.getBytes(StandardCharsets.UTF_8));
  }
}
