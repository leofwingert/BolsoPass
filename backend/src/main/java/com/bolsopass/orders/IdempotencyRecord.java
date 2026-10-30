package com.bolsopass.orders;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * Idempotency-Key do checkout (RF-09). Guardado 24 h.
 * RF-09 cita Redis; a chave primária no banco dá a mesma garantia de "só um vence".
 */
@Entity
@Table(name = "idempotency_keys")
public class IdempotencyRecord {
  public enum State {
    PROCESSING,
    COMPLETED
  }

  @Id
  @Column(name = "idem_key")
  private String key;

  @Column(nullable = false)
  private String userId;

  @Enumerated(EnumType.STRING)
  private State state;

  private String orderId;

  @Column(nullable = false)
  private Instant createdAt;

  protected IdempotencyRecord() {}

  public IdempotencyRecord(String key, String userId, Instant createdAt) {
    this.key = key;
    this.userId = userId;
    this.state = State.PROCESSING;
    this.createdAt = createdAt;
  }

  public void complete(String orderId) {
    this.state = State.COMPLETED;
    this.orderId = orderId;
  }

  public String getUserId() {
    return userId;
  }

  public State getState() {
    return state;
  }

  public String getOrderId() {
    return orderId;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
