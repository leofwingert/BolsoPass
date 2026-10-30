package com.bolsopass.audit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Trilha de auditoria das ações do organizador e do sistema (RF-03, mockup "Logs de Auditoria"). */
@Entity
@Table(name = "audit_logs", indexes = @Index(name = "ix_audit_event", columnList = "eventId,at"))
public class AuditLog {
  public enum Kind {
    EVENTO,
    LOTE,
    BILHETERIA,
    SEGURANCA
  }

  @Id private String id;
  private String eventId;

  @Column(nullable = false)
  private Instant at;

  @Enumerated(EnumType.STRING)
  private Kind kind;

  private String title;

  @Column(length = 500)
  private String description;

  /** E-mail do usuário ou nome do processo automático. */
  private String actor;

  protected AuditLog() {}

  public AuditLog(String eventId, Kind kind, String title, String description, String actor) {
    this.id = UUID.randomUUID().toString();
    this.eventId = eventId;
    this.at = Instant.now();
    this.kind = kind;
    this.title = title;
    this.description = description;
    this.actor = actor;
  }

  /** Só para dados de exemplo: registra a ação numa data passada. */
  public AuditLog at(Instant when) {
    this.at = when;
    return this;
  }

  public String getId() {
    return id;
  }

  public String getEventId() {
    return eventId;
  }

  public Instant getAt() {
    return at;
  }

  public Kind getKind() {
    return kind;
  }

  public String getTitle() {
    return title;
  }

  public String getDescription() {
    return description;
  }

  public String getActor() {
    return actor;
  }
}
