package com.bolsopass.inventory;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** Trava temporária de assentos com TTL estrito de 10 minutos. */
@Entity
@Table(name = "reservations", indexes = @Index(name = "ix_res_open", columnList = "released,expiresAt"))
public class Reservation {
  @Id private String id;

  @Column(nullable = false)
  private String eventId;

  @Column(nullable = false)
  private String userId;

  @Column(nullable = false)
  private String orderId;

  @ElementCollection(fetch = FetchType.EAGER)
  @CollectionTable(name = "reservation_seats", joinColumns = @JoinColumn(name = "reservation_id"))
  @OrderColumn(name = "position")
  @Column(name = "seat_id")
  private List<String> seatIds = new ArrayList<>();

  @Column(nullable = false)
  private Instant createdAt;

  @Column(nullable = false)
  private Instant expiresAt;

  /** true quando a trava já foi encerrada (pago, expirado ou desistência). */
  private boolean released;

  protected Reservation() {}

  public Reservation(
      String id, String eventId, String userId, String orderId, List<String> seatIds, Instant createdAt, Instant expiresAt) {
    this.id = id;
    this.eventId = eventId;
    this.userId = userId;
    this.orderId = orderId;
    this.seatIds = new ArrayList<>(seatIds);
    this.createdAt = createdAt;
    this.expiresAt = expiresAt;
  }

  public boolean isExpired(Instant now) {
    return !now.isBefore(expiresAt);
  }

  public void close() {
    released = true;
  }

  public String getId() {
    return id;
  }

  public String getEventId() {
    return eventId;
  }

  public String getUserId() {
    return userId;
  }

  public String getOrderId() {
    return orderId;
  }

  public List<String> getSeatIds() {
    return seatIds;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getExpiresAt() {
    return expiresAt;
  }

  public boolean isReleased() {
    return released;
  }
}
