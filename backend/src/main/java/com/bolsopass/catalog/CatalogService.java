package com.bolsopass.catalog;

import com.bolsopass.iam.CurrentUser;
import com.bolsopass.inventory.Reservation;
import com.bolsopass.inventory.ReservationRepository;
import com.bolsopass.inventory.Seat;
import com.bolsopass.inventory.SeatRepository;
import com.bolsopass.realtime.RealtimeHub;
import com.bolsopass.shared.ApiException;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class CatalogService {
  private static final Set<Event.Status> PUBLIC_STATUSES =
      Set.of(Event.Status.EM_VENDAS, Event.Status.ESGOTADO);

  private final EventRepository events;
  private final SeatRepository seats;
  private final ReservationRepository reservations;
  private final RealtimeHub hub;

  public CatalogService(
      EventRepository events, SeatRepository seats, ReservationRepository reservations, RealtimeHub hub) {
    this.events = events;
    this.seats = seats;
    this.reservations = reservations;
    this.hub = hub;
  }

  // ---------- views (mesmo formato de src/api/types.ts do front) ----------

  public record EventSummaryView(
      String id,
      String title,
      String venue,
      String city,
      Instant startsAt,
      Instant endsAt,
      Event.Category category,
      Event.Status status,
      long fromPriceCents,
      long capacity,
      long sold,
      int hue,
      String highlight,
      boolean hasVirtualQueue,
      boolean numberedSeats) {}

  public record SectorView(
      String id, String name, Sector.Kind kind, long capacity, long priceCents, String color, List<String> rows) {}

  /** Lote vigente de cada setor (o público só vê o lote atual e o preço do próximo). */
  public record LotView(
      String id,
      String sectorId,
      String name,
      String description,
      long priceCents,
      Long originalPriceCents,
      long quantity,
      long sold,
      int limitPerCpf,
      int maxInstallments,
      Sector.LotStatus status,
      List<String> perks,
      String gate,
      Long nextPriceCents) {}

  public record EventDetailView(
      String id,
      String title,
      String venue,
      String city,
      Instant startsAt,
      Instant endsAt,
      Event.Category category,
      Event.Status status,
      long fromPriceCents,
      long capacity,
      long sold,
      int hue,
      String highlight,
      boolean hasVirtualQueue,
      boolean numberedSeats,
      String description,
      String address,
      String ageRating,
      List<SectorView> sectors,
      List<LotView> lots,
      List<Event.LineupItem> lineup,
      Instant nextLotAt) {}

  public record SeatView(
      String id, String sectorId, String row, int number, String label, Seat.Kind kind, Seat.Status status, String heldBy) {}

  public record SeatMapView(String eventId, List<SectorView> sectors, List<SeatView> seats, int liveBuyers) {}

  // ---------- consultas ----------

  public List<EventSummaryView> list(String q, String city, Event.Category category) {
    String needle = q == null ? null : q.toLowerCase(Locale.ROOT).trim();
    return events.findByStatusInOrderByStartsAt(PUBLIC_STATUSES).stream()
        .filter(e -> category == null || e.getCategory() == category)
        .filter(e -> city == null || city.isBlank() || city.equals(e.getCity()))
        .filter(
            e ->
                needle == null
                    || needle.isEmpty()
                    || (e.getTitle() + " " + e.getVenue() + " " + e.getCity())
                        .toLowerCase(Locale.ROOT)
                        .contains(needle))
        .map(this::summary)
        .toList();
  }

  public EventDetailView detail(String id) {
    Event e = find(id);
    List<Seat> all = seats.findByEventIdOrderBySectorIdAscNumberAsc(id);
    EventSummaryView s = summary(e, all);
    return new EventDetailView(
        s.id(), s.title(), s.venue(), s.city(), s.startsAt(), s.endsAt(), s.category(), s.status(),
        s.fromPriceCents(), s.capacity(), s.sold(), s.hue(), s.highlight(), s.hasVirtualQueue(),
        s.numberedSeats(), e.getDescription(), e.getAddress(), e.getAgeRating(),
        e.getSectors().stream().map(sec -> sectorView(sec, all)).toList(),
        e.getSectors().stream().map(sec -> lotView(sec, all)).toList(),
        List.copyOf(e.getLineup()),
        e.getNextLotAt());
  }

  /** Mapa de assentos. "heldBy" só aparece para as travas do próprio usuário. */
  public SeatMapView seatMap(String eventId) {
    Event e = find(eventId);
    List<Seat> all = seats.findByEventIdOrderBySectorIdAscNumberAsc(eventId);
    String userId = CurrentUser.idOrNull();
    Set<String> mine =
        userId == null
            ? Set.of()
            : reservations.findOpen(userId, eventId, Instant.now()).stream()
                .map(Reservation::getId)
                .collect(Collectors.toSet());
    List<SeatView> seatViews =
        all.stream()
            .map(
                st ->
                    new SeatView(
                        st.getId(), st.getSectorId(), st.getRowLabel(), st.getNumber(), st.getLabel(),
                        st.getKind(), st.getStatus(),
                        st.getHeldBy() != null && mine.contains(st.getHeldBy()) ? st.getHeldBy() : null))
            .toList();
    return new SeatMapView(
        eventId,
        e.getSectors().stream().map(sec -> sectorView(sec, all)).toList(),
        seatViews,
        hub.subscriberCount(RealtimeHub.eventSeats(eventId)));
  }

  public Event find(String id) {
    return events.findById(id).orElseThrow(() -> ApiException.notFound("Evento"));
  }

  public EventSummaryView summary(Event e) {
    return summary(e, seats.findByEventIdOrderBySectorIdAscNumberAsc(e.getId()));
  }

  private EventSummaryView summary(Event e, List<Seat> all) {
    long capacity = all.stream().filter(s -> s.getKind() != Seat.Kind.OBSTRUCAO).count();
    long sold = all.stream().filter(CatalogService::isSold).count();
    long from =
        e.getSectors().stream()
            .map(sec -> sec.activeLot(soldIn(sec, all)))
            .filter(java.util.Objects::nonNull)
            .mapToLong(a -> a.tier().priceCents())
            .min()
            .orElse(0);
    return new EventSummaryView(
        e.getId(), e.getTitle(), e.getVenue(), e.getCity(), e.getStartsAt(), e.getEndsAt(),
        e.getCategory(), e.getStatus(), from, capacity, sold, e.getHue(), e.getHighlight(),
        e.isHasVirtualQueue(), e.getSectors().stream().anyMatch(s -> s.getKind() == Sector.Kind.NUMERADO));
  }

  private SectorView sectorView(Sector sec, List<Seat> all) {
    List<Seat> mine = all.stream().filter(s -> s.getSectorId().equals(sec.getId())).toList();
    Set<String> rows =
        mine.stream().map(Seat::getRowLabel).collect(Collectors.toCollection(LinkedHashSet::new));
    Sector.LotTier price = currentOrLast(sec, soldIn(sec, all));
    return new SectorView(
        sec.getId(), sec.getName(), sec.getKind(), mine.size(), price == null ? 0 : price.priceCents(),
        sec.getColor(), List.copyOf(rows));
  }

  private LotView lotView(Sector sec, List<Seat> all) {
    long sold = soldIn(sec, all);
    Sector.ActiveLot active = sec.activeLot(sold);
    long free = all.stream()
        .filter(s -> s.getSectorId().equals(sec.getId()) && s.isSelectable())
        .count();
    if (active == null || free == 0) {
      Sector.LotTier last = sec.lastLot();
      return new LotView(
          sec.getId() + ":lot", sec.getId(), last == null ? "Lote único" : last.name(), sec.getDescription(),
          last == null ? 0 : last.priceCents(), null, last == null ? 0 : last.quantity(),
          last == null ? 0 : last.quantity(), last == null ? 1 : last.limitPerCpf(),
          last == null ? 1 : last.maxInstallments(), Sector.LotStatus.ESGOTADO,
          List.copyOf(sec.getPerks()), sec.getGate(), null);
    }
    Sector.LotTier t = active.tier();
    return new LotView(
        sec.getId() + ":lot:" + active.index(), sec.getId(),
        t.name() + " de " + String.format("%02d", sec.getLots().size()), sec.getDescription(),
        t.priceCents(), t.originalPriceCents(), t.quantity(), active.soldInTier(), t.limitPerCpf(),
        t.maxInstallments(), Sector.LotStatus.ATIVO, List.copyOf(sec.getPerks()), sec.getGate(),
        active.next() == null ? null : active.next().priceCents());
  }

  /** Lote vigente (ou o último, se tudo esgotou) de um setor, consultando o inventário. */
  public Sector.LotTier currentLot(Sector sec) {
    long sold = seats.countBySectorIdAndStatusIn(sec.getId(), SOLD);
    return currentOrLast(sec, sold);
  }

  public Sector.ActiveLot activeLot(Sector sec) {
    return sec.activeLot(seats.countBySectorIdAndStatusIn(sec.getId(), SOLD));
  }

  private static Sector.LotTier currentOrLast(Sector sec, long sold) {
    Sector.ActiveLot a = sec.activeLot(sold);
    return a != null ? a.tier() : sec.lastLot();
  }

  private static long soldIn(Sector sec, List<Seat> all) {
    return all.stream().filter(s -> s.getSectorId().equals(sec.getId()) && isSold(s)).count();
  }

  public static final java.util.Set<Seat.Status> SOLD =
      java.util.EnumSet.of(Seat.Status.VENDIDO, Seat.Status.UTILIZADO);

  private static boolean isSold(Seat s) {
    return s.getStatus() == Seat.Status.VENDIDO || s.getStatus() == Seat.Status.UTILIZADO;
  }

  /** Índice de setores por id (usado por reservas e pedidos). */
  public Map<String, Sector> sectorsById(Event e) {
    return e.getSectors().stream().collect(Collectors.toMap(Sector::getId, Function.identity()));
  }
}
