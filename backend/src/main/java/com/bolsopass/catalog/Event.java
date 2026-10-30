package com.bolsopass.catalog;

import jakarta.persistence.CascadeType;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "events")
public class Event {
  public enum Status {
    RASCUNHO,
    AGENDADO,
    EM_VENDAS,
    PAUSADO,
    ESGOTADO,
    ENCERRADO,
    CANCELADO
  }

  public enum Category {
    FESTIVAIS,
    ELETRONICA,
    ROCK,
    SERTANEJO_POP,
    TEATRO,
    ESPORTES
  }

  @Embeddable
  public record LineupItem(String stage, String artists, @Column(length = 500) String description) {}

  @Id private String id;

  @Column(nullable = false)
  private String title;

  private String venue;
  private String city;
  private String address;
  private String ageRating;

  @Column(length = 1000)
  private String description;

  @Column(nullable = false)
  private Instant startsAt;

  private Instant endsAt;
  private Instant nextLotAt;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private Status status;

  @Enumerated(EnumType.STRING)
  private Category category;

  /** Matiz do placeholder de capa no front. */
  private int hue;

  private String highlight;
  private boolean hasVirtualQueue;

  /** Organizador dono do evento (ROLE_ORGANIZER só vê os próprios; ROLE_ADMIN vê todos). */
  private String organizerId;

  private Instant createdAt;

  @OneToMany(mappedBy = "event", cascade = CascadeType.ALL, orphanRemoval = true)
  @OrderBy("position")
  private List<Sector> sectors = new ArrayList<>();

  @ElementCollection
  @CollectionTable(name = "event_lineup", joinColumns = @JoinColumn(name = "event_id"))
  @OrderColumn(name = "position")
  private List<LineupItem> lineup = new ArrayList<>();

  protected Event() {}

  public Event(String id, String title, Category category, Instant startsAt, Status status) {
    this.id = id;
    this.title = title;
    this.category = category;
    this.startsAt = startsAt;
    this.status = status;
    this.createdAt = Instant.now();
  }

  public Event ownedBy(String organizerId) {
    this.organizerId = organizerId;
    return this;
  }

  // --- ciclo de vida controlado pelo organizador ---

  /** Rascunho -> em vendas. */
  public void publish() {
    if (status != Status.RASCUNHO && status != Status.AGENDADO) {
      throw new com.bolsopass.shared.DomainException("Só rascunhos podem ser publicados (status atual: " + status + ")");
    }
    status = Status.EM_VENDAS;
  }

  /** "Pausar vendas imediatamente" (mockup Admin): novas reservas param; pedidos em andamento seguem. */
  public void pauseSales() {
    if (status != Status.EM_VENDAS) throw new com.bolsopass.shared.DomainException("Só é possível pausar um evento em vendas");
    status = Status.PAUSADO;
  }

  public void resumeSales() {
    if (status != Status.PAUSADO) throw new com.bolsopass.shared.DomainException("O evento não está pausado");
    status = Status.EM_VENDAS;
  }

  public Event at(String venue, String city, String address) {
    this.venue = venue;
    this.city = city;
    this.address = address;
    return this;
  }

  public Event details(String description, String ageRating, int hue, String highlight, boolean queue) {
    this.description = description;
    this.ageRating = ageRating;
    this.hue = hue;
    this.highlight = highlight;
    this.hasVirtualQueue = queue;
    return this;
  }

  public Event schedule(Instant endsAt, Instant nextLotAt) {
    this.endsAt = endsAt;
    this.nextLotAt = nextLotAt;
    return this;
  }

  public Sector addSector(Sector sector) {
    sector.attachTo(this, sectors.size());
    sectors.add(sector);
    return sector;
  }

  public void addLineup(String stage, String artists, String description) {
    lineup.add(new LineupItem(stage, artists, description));
  }

  public boolean isOnSale() {
    return status == Status.EM_VENDAS;
  }

  public String getId() {
    return id;
  }

  public String getTitle() {
    return title;
  }

  public String getVenue() {
    return venue;
  }

  public String getCity() {
    return city;
  }

  public String getAddress() {
    return address;
  }

  public String getAgeRating() {
    return ageRating;
  }

  public String getDescription() {
    return description;
  }

  public Instant getStartsAt() {
    return startsAt;
  }

  public Instant getEndsAt() {
    return endsAt;
  }

  public Instant getNextLotAt() {
    return nextLotAt;
  }

  public Status getStatus() {
    return status;
  }

  public Category getCategory() {
    return category;
  }

  public int getHue() {
    return hue;
  }

  public String getHighlight() {
    return highlight;
  }

  public boolean isHasVirtualQueue() {
    return hasVirtualQueue;
  }

  public String getOrganizerId() {
    return organizerId;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public List<Sector> getSectors() {
    return sectors;
  }

  public List<LineupItem> getLineup() {
    return lineup;
  }
}
