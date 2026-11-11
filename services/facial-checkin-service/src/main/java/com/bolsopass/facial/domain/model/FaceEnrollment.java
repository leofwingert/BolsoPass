package com.bolsopass.facial.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Rosto cadastrado para um ingresso. <b>A imagem nunca é guardada</b>: só o FaceId devolvido pelo
 * Rekognition, o ingresso, o evento e a data (minimização de dados, LGPD art. 6º III).
 */
@Entity
@Table(
    name = "face_enrollments",
    indexes = {
      @Index(name = "ix_face_user", columnList = "userId"),
      @Index(name = "ix_face_event", columnList = "eventId")
    })
public class FaceEnrollment {
  @Id private String id;

  @Column(nullable = false)
  private String faceId;

  @Column(nullable = false, unique = true)
  private String ticketId;

  @Column(nullable = false)
  private String eventId;

  @Column(nullable = false)
  private String userId;

  /** Para a retenção: os rostos do evento são apagados alguns dias depois. */
  private Instant eventStartsAt;

  @Column(nullable = false)
  private Instant createdAt;

  protected FaceEnrollment() {}

  public FaceEnrollment(String faceId, String ticketId, String eventId, String userId, Instant eventStartsAt, Instant now) {
    this.id = UUID.randomUUID().toString();
    this.faceId = faceId;
    this.ticketId = ticketId;
    this.eventId = eventId;
    this.userId = userId;
    this.eventStartsAt = eventStartsAt;
    this.createdAt = now;
  }

  public String getId() {
    return id;
  }

  public String getFaceId() {
    return faceId;
  }

  public String getTicketId() {
    return ticketId;
  }

  public String getEventId() {
    return eventId;
  }

  public String getUserId() {
    return userId;
  }

  public Instant getEventStartsAt() {
    return eventStartsAt;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
