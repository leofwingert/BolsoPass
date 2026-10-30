package com.bolsopass.outbox;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Linha da tabela outbox_events (RF-10, doc 06 seção 3). */
@Entity
@Table(name = "outbox_events", indexes = @Index(name = "ix_outbox_pending", columnList = "status,createdAt"))
public class OutboxEvent {
  public enum Status {
    PENDING,
    PUBLISHED
  }

  @Id private String id;

  @Column(nullable = false)
  private String topic;

  /** Chave de particionamento (event_id ou order_id, conforme a skill kafka-contracts). */
  @Column(nullable = false)
  private String messageKey;

  @Column(nullable = false)
  private String eventType;

  @Lob
  @Column(nullable = false)
  private String payload;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private Status status = Status.PENDING;

  @Column(nullable = false)
  private Instant createdAt;

  private Instant publishedAt;

  protected OutboxEvent() {}

  public OutboxEvent(String topic, String messageKey, String eventType, String payload, Instant now) {
    this.id = UUID.randomUUID().toString();
    this.topic = topic;
    this.messageKey = messageKey;
    this.eventType = eventType;
    this.payload = payload;
    this.createdAt = now;
  }

  public void published(Instant at) {
    this.status = Status.PUBLISHED;
    this.publishedAt = at;
  }

  public String getId() {
    return id;
  }

  public String getTopic() {
    return topic;
  }

  public String getMessageKey() {
    return messageKey;
  }

  public String getEventType() {
    return eventType;
  }

  public String getPayload() {
    return payload;
  }

  public Status getStatus() {
    return status;
  }
}
