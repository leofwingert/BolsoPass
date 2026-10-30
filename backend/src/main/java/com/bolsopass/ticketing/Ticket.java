package com.bolsopass.ticketing;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Instant;

/** Ingresso nominal emitido após o pagamento (RF-13). */
@Entity
@Table(name = "tickets", indexes = @Index(name = "ix_tickets_user", columnList = "userId"))
public class Ticket {
  public enum Status {
    ATIVO,
    UTILIZADO,
    TRANSFERIDO,
    CANCELADO
  }

  @Id private String id;

  @Column(nullable = false)
  private String orderId;

  @Column(nullable = false)
  private String eventId;

  @Column(nullable = false)
  private String userId;

  @Column(nullable = false, unique = true)
  private String seatId;

  private String seatLabel;
  private String sectorName;
  private String holderName;
  private String holderCpf;
  private String holderEmail;
  private String gate;

  @Enumerated(EnumType.STRING)
  private Status status = Status.ATIVO;

  @Column(nullable = false)
  private Instant issuedAt;

  /** Primeira entrada registrada (portão e horário), usada para barrar reuso/clonagem. */
  private Instant usedAt;

  private String usedGate;

  protected Ticket() {}

  public Ticket(
      String id,
      String orderId,
      String eventId,
      String userId,
      String seatId,
      String seatLabel,
      String sectorName,
      String gate,
      Instant issuedAt) {
    this.id = id;
    this.orderId = orderId;
    this.eventId = eventId;
    this.userId = userId;
    this.seatId = seatId;
    this.seatLabel = seatLabel;
    this.sectorName = sectorName;
    this.gate = gate;
    this.issuedAt = issuedAt;
  }

  public Ticket holder(String name, String cpf, String email) {
    this.holderName = name;
    this.holderCpf = cpf;
    this.holderEmail = email;
    return this;
  }

  /** Check-in na catraca. Retorna false se o ingresso já tinha sido usado (tentativa de reuso). */
  public boolean checkIn(Instant at, String gate) {
    if (status != Status.ATIVO) return false;
    this.status = Status.UTILIZADO;
    this.usedAt = at;
    this.usedGate = gate;
    return true;
  }

  public Instant getUsedAt() {
    return usedAt;
  }

  public String getUsedGate() {
    return usedGate;
  }

  public String getId() {
    return id;
  }

  public String getOrderId() {
    return orderId;
  }

  public String getEventId() {
    return eventId;
  }

  public String getUserId() {
    return userId;
  }

  public String getSeatId() {
    return seatId;
  }

  public String getSeatLabel() {
    return seatLabel;
  }

  public String getSectorName() {
    return sectorName;
  }

  public String getHolderName() {
    return holderName;
  }

  public String getHolderCpf() {
    return holderCpf;
  }

  public String getHolderEmail() {
    return holderEmail;
  }

  public String getGate() {
    return gate;
  }

  public Status getStatus() {
    return status;
  }

  public Instant getIssuedAt() {
    return issuedAt;
  }
}
