package com.bolsopass.catalog;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.List;

/**
 * Setor do evento com seus lotes em sequência (RF-04, wizard do organizador).
 * O lote vigente é o primeiro que ainda não vendeu toda a sua cota: a virada é automática.
 */
@Entity
@Table(name = "sectors")
public class Sector {
  public enum Kind {
    NUMERADO,
    PISTA
  }

  public enum LotStatus {
    ATIVO,
    ESGOTADO,
    PROGRAMADO
  }

  /** Um lote da sequência. Preço fica gravado no item do pedido na hora da reserva. */
  @Embeddable
  public record LotTier(
      String name,
      long priceCents,
      Long originalPriceCents,
      int quantity,
      int limitPerCpf,
      int maxInstallments) {}

  /** Lote vigente e sua posição, calculados a partir dos assentos vendidos do setor. */
  public record ActiveLot(int index, LotTier tier, int soldInTier, LotTier next) {
    public int remaining() {
      return tier.quantity() - soldInTier;
    }
  }

  @Id private String id;

  @ManyToOne(optional = false)
  @JoinColumn(name = "event_id")
  private Event event;

  private int position;

  @Column(nullable = false)
  private String code;

  @Column(nullable = false)
  private String name;

  @Enumerated(EnumType.STRING)
  private Kind kind = Kind.NUMERADO;

  private String color;
  private int seatsPerRow;

  @Column(length = 500)
  private String description;

  private String gate;

  /** Esgotamento manual pelo organizador (ex.: setor fechado). */
  private boolean closed;

  @ElementCollection(fetch = FetchType.EAGER)
  @CollectionTable(name = "sector_lots", joinColumns = @JoinColumn(name = "sector_id"))
  @OrderColumn(name = "position")
  private List<LotTier> lots = new ArrayList<>();

  @ElementCollection
  @CollectionTable(name = "sector_perks", joinColumns = @JoinColumn(name = "sector_id"))
  @OrderColumn(name = "position")
  @Column(name = "perk")
  private List<String> perks = new ArrayList<>();

  protected Sector() {}

  public Sector(String code, String name, String color, int seatsPerRow) {
    this.code = code;
    this.name = name;
    this.color = color;
    this.seatsPerRow = seatsPerRow;
  }

  public Sector info(String description, String gate, List<String> perks) {
    this.description = description;
    this.gate = gate;
    this.perks = new ArrayList<>(perks);
    return this;
  }

  public Sector addLot(LotTier lot) {
    lots.add(lot);
    return this;
  }

  public Sector close() {
    this.closed = true;
    return this;
  }

  void attachTo(Event event, int position) {
    this.event = event;
    this.position = position;
    this.id = event.getId() + ":" + code;
  }

  /** Soma das cotas dos lotes (deve ser igual à capacidade: validado no wizard). */
  public int totalQuota() {
    return lots.stream().mapToInt(LotTier::quantity).sum();
  }

  /** Lote vigente para um setor com {@code sold} assentos vendidos; null = todos esgotados. */
  public ActiveLot activeLot(long sold) {
    if (closed) return null;
    long cumulative = 0;
    for (int i = 0; i < lots.size(); i++) {
      LotTier t = lots.get(i);
      if (sold < cumulative + t.quantity()) {
        LotTier next = i + 1 < lots.size() ? lots.get(i + 1) : null;
        return new ActiveLot(i, t, (int) (sold - cumulative), next);
      }
      cumulative += t.quantity();
    }
    return null;
  }

  /** Lote usado para preço/limites quando tudo esgotou (exibição). */
  public LotTier lastLot() {
    return lots.isEmpty() ? null : lots.get(lots.size() - 1);
  }

  public String getId() {
    return id;
  }

  public Event getEvent() {
    return event;
  }

  public String getCode() {
    return code;
  }

  public String getName() {
    return name;
  }

  public Kind getKind() {
    return kind;
  }

  public String getColor() {
    return color;
  }

  public int getSeatsPerRow() {
    return seatsPerRow;
  }

  public String getDescription() {
    return description;
  }

  public String getGate() {
    return gate;
  }

  public boolean isClosed() {
    return closed;
  }

  public List<LotTier> getLots() {
    return lots;
  }

  public List<String> getPerks() {
    return perks;
  }
}
