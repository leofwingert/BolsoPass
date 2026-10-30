package com.bolsopass.inventory;

import com.bolsopass.shared.DomainException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.util.EnumMap;
import java.util.Map;

/**
 * Assento físico com identificador imutável (RF-04) e ciclo de vida da seção 2 do doc 06:
 * DISPONIVEL -> BLOQUEADO_TEMPORARIO -> VENDIDO -> UTILIZADO.
 */
@Entity
@Table(name = "seats", indexes = @Index(name = "ix_seats_event", columnList = "eventId"))
public class Seat {
  public enum Status {
    DISPONIVEL,
    BLOQUEADO_TEMPORARIO,
    VENDIDO,
    UTILIZADO
  }

  public enum Kind {
    PADRAO,
    PCD,
    BLOQUEADO_PRODUCAO,
    OBSTRUCAO
  }

  enum Event {
    TRAVA_ADQUIRIDA,
    PEDIDO_PAGO,
    TIMEOUT_OU_CANCELAMENTO,
    CHECKIN
  }

  private static final Map<Status, Map<Event, Status>> TRANSITIONS = new EnumMap<>(Status.class);

  static {
    TRANSITIONS.put(Status.DISPONIVEL, Map.of(Event.TRAVA_ADQUIRIDA, Status.BLOQUEADO_TEMPORARIO));
    TRANSITIONS.put(
        Status.BLOQUEADO_TEMPORARIO,
        Map.of(
            Event.PEDIDO_PAGO, Status.VENDIDO,
            Event.TIMEOUT_OU_CANCELAMENTO, Status.DISPONIVEL));
    TRANSITIONS.put(Status.VENDIDO, Map.of(Event.CHECKIN, Status.UTILIZADO));
    TRANSITIONS.put(Status.UTILIZADO, Map.of());
  }

  @Id private String id;

  @Column(nullable = false)
  private String eventId;

  @Column(nullable = false)
  private String sectorId;

  private String rowLabel;
  private int number;
  private String label;

  @Enumerated(EnumType.STRING)
  private Kind kind = Kind.PADRAO;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private Status status = Status.DISPONIVEL;

  /** Id da reserva que segura a trava temporária. */
  private String heldBy;

  protected Seat() {}

  public Seat(String eventId, String sectorId, String sectorCode, int number, int perRow, Kind kind) {
    this.eventId = eventId;
    this.sectorId = sectorId;
    this.number = number;
    this.label = sectorCode + String.format("%02d", number);
    this.id = eventId + ":" + sectorCode + number;
    this.rowLabel = sectorCode + ((number - 1) / perRow + 1);
    this.kind = kind;
  }

  public boolean isSelectable() {
    return status == Status.DISPONIVEL && (kind == Kind.PADRAO || kind == Kind.PCD);
  }

  public void lock(String reservationId) {
    if (!isSelectable()) throw new DomainException("Assento " + label + " indisponível");
    status = next(Event.TRAVA_ADQUIRIDA);
    heldBy = reservationId;
  }

  public void release(String reservationId) {
    if (status == Status.BLOQUEADO_TEMPORARIO && reservationId.equals(heldBy)) {
      status = next(Event.TIMEOUT_OU_CANCELAMENTO);
      heldBy = null;
    }
  }

  public void sell(String reservationId) {
    if (!reservationId.equals(heldBy)) {
      throw new DomainException("Assento " + label + " não está travado por esta reserva");
    }
    status = next(Event.PEDIDO_PAGO);
    heldBy = null;
  }

  /** Usado pelo seed para montar vendas antigas. */
  public void markSold() {
    status = Status.VENDIDO;
    heldBy = null;
  }

  public void markUsed() {
    status = Status.UTILIZADO;
  }

  private Status next(Event event) {
    Status to = TRANSITIONS.get(status).get(event);
    if (to == null) throw new DomainException("Transição proibida do assento: " + status + " --" + event);
    return to;
  }

  public String getId() {
    return id;
  }

  public String getEventId() {
    return eventId;
  }

  public String getSectorId() {
    return sectorId;
  }

  public String getRowLabel() {
    return rowLabel;
  }

  public int getNumber() {
    return number;
  }

  public String getLabel() {
    return label;
  }

  public Kind getKind() {
    return kind;
  }

  public Status getStatus() {
    return status;
  }

  public String getHeldBy() {
    return heldBy;
  }
}
