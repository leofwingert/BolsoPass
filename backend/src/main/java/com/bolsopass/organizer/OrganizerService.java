package com.bolsopass.organizer;

import com.bolsopass.audit.AuditLog;
import com.bolsopass.audit.AuditLogRepository;
import com.bolsopass.audit.AuditService;
import com.bolsopass.catalog.CatalogService;
import com.bolsopass.catalog.Event;
import com.bolsopass.catalog.EventRepository;
import com.bolsopass.catalog.Sector;
import com.bolsopass.iam.CurrentUser;
import com.bolsopass.iam.UserRepository;
import com.bolsopass.inventory.ReservationRepository;
import com.bolsopass.inventory.Seat;
import com.bolsopass.inventory.SeatRepository;
import com.bolsopass.orders.Order;
import com.bolsopass.orders.OrderRepository;
import com.bolsopass.orders.OrderStatus;
import com.bolsopass.organizer.OrganizerViews.*;
import com.bolsopass.outbox.OutboxEvent;
import com.bolsopass.outbox.OutboxRepository;
import com.bolsopass.shared.ApiException;
import com.bolsopass.ticketing.Ticket;
import com.bolsopass.ticketing.TicketRepository;
import java.text.Normalizer;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Painel do organizador (RF-04, RF-16). ROLE_ORGANIZER vê e altera só os próprios eventos;
 * ROLE_ADMIN vê todos. Os números saem do banco transacional (TODO: telemetry-service/Kafka).
 */
@Service
@Transactional(readOnly = true)
public class OrganizerService {
  private static final ZoneId ZONE = ZoneId.of("America/Sao_Paulo");
  private static final String[] COLORS = {"#e11d48", "#1e293b", "#64748b", "#94a3b8", "#0f7a4a", "#7c3aed", "#d97706", "#0891b2"};

  private final EventRepository events;
  private final CatalogService catalog;
  private final SeatRepository seats;
  private final OrderRepository orders;
  private final ReservationRepository reservations;
  private final OutboxRepository outbox;
  private final AuditLogRepository logs;
  private final AuditService audit;
  private final UserRepository users;
  private final TicketRepository tickets;

  public OrganizerService(
      EventRepository events, CatalogService catalog, SeatRepository seats, OrderRepository orders,
      ReservationRepository reservations, OutboxRepository outbox, AuditLogRepository logs,
      AuditService audit, UserRepository users, TicketRepository tickets) {
    this.tickets = tickets;
    this.events = events;
    this.catalog = catalog;
    this.seats = seats;
    this.orders = orders;
    this.reservations = reservations;
    this.outbox = outbox;
    this.logs = logs;
    this.audit = audit;
    this.users = users;
  }

  // ---------------------------------------------------------------- acesso

  List<Event> visibleEvents() {
    if (CurrentUser.hasRole("ROLE_ADMIN")) return events.findAllByOrderByStartsAt();
    return events.findByOrganizerIdOrderByStartsAt(CurrentUser.id());
  }

  /** Evento que o usuário pode gerenciar. Organizador de outro evento recebe 403. */
  Event owned(String eventId) {
    Event e = catalog.find(eventId);
    if (!CurrentUser.hasRole("ROLE_ADMIN") && !CurrentUser.id().equals(e.getOrganizerId())) {
      throw ApiException.forbidden("Este evento pertence a outro organizador");
    }
    return e;
  }

  String actorEmail() {
    return actor();
  }

  private String actor() {
    return users.findById(CurrentUser.id()).map(u -> u.getEmail()).orElse(CurrentUser.id());
  }

  // ---------------------------------------------------------------- painel

  public Dashboard dashboard() {
    List<Event> mine = visibleEvents();
    Set<String> ids = mine.stream().map(Event::getId).collect(Collectors.toSet());
    List<Order> paid = ids.isEmpty() ? List.of() : orders.findByEventIdInAndStatus(ids, OrderStatus.PAGO);

    Instant now = Instant.now();
    LocalDate today = LocalDate.now(ZONE);
    Instant startToday = today.atStartOfDay(ZONE).toInstant();
    Instant startYesterday = today.minusDays(1).atStartOfDay(ZONE).toInstant();
    Instant startMonth = today.withDayOfMonth(1).atStartOfDay(ZONE).toInstant();

    long ticketsToday = paid.stream().filter(o -> !o.getPaidAt().isBefore(startToday)).mapToLong(o -> o.getItems().size()).sum();
    long ticketsYesterday =
        paid.stream()
            .filter(o -> !o.getPaidAt().isBefore(startYesterday) && o.getPaidAt().isBefore(startToday))
            .mapToLong(o -> o.getItems().size())
            .sum();
    int growth = ticketsYesterday == 0 ? (ticketsToday > 0 ? 100 : 0) : (int) Math.round((ticketsToday - ticketsYesterday) * 100.0 / ticketsYesterday);

    List<DayRevenue> weekly = new ArrayList<>();
    for (int i = 6; i >= 0; i--) {
      LocalDate d = today.minusDays(i);
      Instant from = d.atStartOfDay(ZONE).toInstant();
      Instant to = d.plusDays(1).atStartOfDay(ZONE).toInstant();
      long cents = paid.stream().filter(o -> !o.getPaidAt().isBefore(from) && o.getPaidAt().isBefore(to)).mapToLong(Order::getTotalCents).sum();
      weekly.add(new DayRevenue(dayLabel(d.getDayOfWeek()), cents));
    }

    long reservedToday = ids.isEmpty() ? 0 : reservations.countByEventIdInAndCreatedAtAfter(ids, startToday);
    long paidFromToday =
        paid.stream().filter(o -> !o.getCreatedAt().isBefore(startToday)).count();
    long expiredToday = ids.isEmpty() ? 0 : orders.countByEventIdInAndStatusAndCreatedAtAfter(ids, OrderStatus.EXPIRADO, startToday);

    Health health =
        new Health(
            ids.isEmpty() ? 0 : reservations.countByEventIdInAndCreatedAtAfter(ids, now.minus(Duration.ofMinutes(1))),
            ids.isEmpty() ? 0 : seats.countByEventIdInAndStatus(ids, Seat.Status.BLOQUEADO_TEMPORARIO),
            oversellingIncidents(paid),
            expiredToday,
            reservedToday == 0 ? 100 : (int) Math.round(paidFromToday * 100.0 / reservedToday),
            outbox.countByStatus(OutboxEvent.Status.PENDING));

    return new Dashboard(
        mine.stream().filter(Event::isOnSale).count(),
        mine.stream().filter(e -> e.getCreatedAt() != null && !e.getCreatedAt().isBefore(startMonth)).count(),
        ticketsToday,
        growth,
        paid.stream().filter(o -> !o.getPaidAt().isBefore(startToday)).mapToLong(Order::getTotalCents).sum(),
        paid.stream().mapToLong(Order::getTotalCents).sum(),
        paid.stream().mapToLong(Order::getSubtotalCents).sum(),
        10,
        weekly,
        critical(mine, now),
        lotTurn(mine),
        health);
  }

  /** Mesmo assento em dois pedidos pagos = sobre-venda. Deve ser sempre 0. */
  private static long oversellingIncidents(List<Order> paid) {
    Map<String, Long> bySeat =
        paid.stream().flatMap(o -> o.getItems().stream()).collect(Collectors.groupingBy(Order.Item::getSeatId, Collectors.counting()));
    return bySeat.values().stream().filter(c -> c > 1).count();
  }

  private Critical critical(List<Event> mine, Instant now) {
    return mine.stream()
        .filter(Event::isOnSale)
        .map(e -> Map.entry(e, catalog.summary(e)))
        .filter(x -> x.getValue().capacity() > 0)
        .max(Comparator.comparingDouble(x -> x.getValue().sold() / (double) x.getValue().capacity()))
        .map(x -> new Critical(
            x.getKey().getId(), x.getKey().getTitle(), x.getKey().getVenue(),
            Math.max(0, Duration.between(now, x.getKey().getStartsAt()).toDays()),
            (int) Math.round(x.getValue().sold() * 100.0 / x.getValue().capacity())))
        .orElse(null);
  }

  /** Setor cujo lote vigente está mais perto de virar (e que tem próximo lote). */
  private LotTurn lotTurn(List<Event> mine) {
    LotTurn best = null;
    for (Event e : mine) {
      if (!e.isOnSale()) continue;
      for (Sector s : e.getSectors()) {
        Sector.ActiveLot a = catalog.activeLot(s);
        if (a == null || a.next() == null) continue;
        if (best == null || a.remaining() < best.remaining()) {
          best = new LotTurn(e.getId(), e.getTitle(), s.getName(), a.tier().name(), a.remaining(),
              a.tier().priceCents(), a.next().priceCents());
        }
      }
    }
    return best;
  }

  private static String dayLabel(DayOfWeek d) {
    String s = d.getDisplayName(TextStyle.SHORT, Locale.forLanguageTag("pt-BR")).replace(".", "");
    return Character.toUpperCase(s.charAt(0)) + s.substring(1);
  }

  // ---------------------------------------------------------------- listas e detalhe

  public List<EventRow> list() {
    List<Event> mine = visibleEvents();
    Set<String> ids = mine.stream().map(Event::getId).collect(Collectors.toSet());
    Map<String, List<Order>> paidByEvent =
        (ids.isEmpty() ? List.<Order>of() : orders.findByEventIdInAndStatus(ids, OrderStatus.PAGO))
            .stream().collect(Collectors.groupingBy(Order::getEventId));
    return mine.stream()
        .map(e -> {
          List<Order> paid = paidByEvent.getOrDefault(e.getId(), List.of());
          long revenue = paid.stream().mapToLong(Order::getSubtotalCents).sum();
          long tickets = paid.stream().mapToLong(o -> o.getItems().size()).sum();
          var summary = catalog.summary(e);
          return new EventRow(summary, revenue, tickets, tickets == 0 ? 0 : revenue / tickets, note(e, summary.capacity() - summary.sold()));
        })
        .toList();
  }

  private static String note(Event e, long remaining) {
    return switch (e.getStatus()) {
      case RASCUNHO -> "Rascunho: publique para abrir as vendas";
      case AGENDADO -> "Abertura programada";
      case PAUSADO -> "Vendas pausadas";
      case ENCERRADO -> "Evento encerrado";
      case CANCELADO -> "Cancelado";
      default -> remaining <= 0 ? "Capacidade esgotada" : remaining + " ingressos disponíveis";
    };
  }

  public OrganizerViews.EventDetail detail(String eventId) {
    Event e = owned(eventId);
    List<Order> paid = orders.findByEventIdInAndStatus(Set.of(eventId), OrderStatus.PAGO);
    List<Seat> all = seats.findByEventIdOrderBySectorIdAscNumberAsc(eventId);
    Instant now = Instant.now();

    List<Long> last6h = new ArrayList<>();
    for (int h = 5; h >= 0; h--) {
      Instant from = now.minus(Duration.ofHours(h + 1));
      Instant to = now.minus(Duration.ofHours(h));
      last6h.add(paid.stream().filter(o -> o.getPaidAt().isAfter(from) && !o.getPaidAt().isAfter(to)).mapToLong(o -> o.getItems().size()).sum());
    }

    // Entradas já registradas pela portaria, por setor.
    Map<String, Long> checkedIn = tickets.findByEventId(eventId).stream()
        .filter(t -> t.getUsedAt() != null)
        .collect(java.util.stream.Collectors.groupingBy(Ticket::getSectorName, java.util.stream.Collectors.counting()));

    List<SectorSummary> sectors = new ArrayList<>();
    for (Sector s : e.getSectors()) {
      List<Seat> mine = all.stream().filter(x -> x.getSectorId().equals(s.getId())).toList();
      long sold = mine.stream().filter(x -> CatalogService.SOLD.contains(x.getStatus())).count();
      long locked = mine.stream().filter(x -> x.getStatus() == Seat.Status.BLOQUEADO_TEMPORARIO).count();
      Sector.ActiveLot active = s.activeLot(sold);
      List<LotSummary> lots = new ArrayList<>();
      long before = 0;
      for (int i = 0; i < s.getLots().size(); i++) {
        Sector.LotTier t = s.getLots().get(i);
        int soldInTier = (int) Math.max(0, Math.min(t.quantity(), sold - before));
        String status = soldInTier >= t.quantity() ? "ESGOTADO" : active != null && active.index() == i ? "ATIVO" : "PROGRAMADO";
        if (s.isClosed() && !"ESGOTADO".equals(status)) status = "FECHADO";
        lots.add(new LotSummary(t.name(), t.priceCents(), t.quantity(), soldInTier, status));
        before += t.quantity();
      }
      sectors.add(new SectorSummary(s.getId(), s.getName(), mine.size(), sold, locked, s.isClosed(), lots,
          s.getGate(), checkedIn.getOrDefault(s.getName(), 0L)));
    }

    Instant startToday = LocalDate.now(ZONE).atStartOfDay(ZONE).toInstant();
    return new OrganizerViews.EventDetail(
        catalog.detail(eventId),
        paid.stream().mapToLong(Order::getTotalCents).sum(),
        paid.stream().mapToLong(Order::getSubtotalCents).sum(),
        paid.stream().mapToLong(o -> o.getItems().size()).sum(),
        all.stream().filter(x -> x.getStatus() == Seat.Status.BLOQUEADO_TEMPORARIO).count(),
        orders.countByEventIdInAndStatusAndCreatedAtAfter(Set.of(eventId), OrderStatus.EXPIRADO, startToday),
        last6h,
        sectors,
        logs.findTop20ByEventIdOrderByAtDesc(eventId).stream()
            .map(l -> new LogView(l.getId(), l.getAt(), l.getKind(), l.getTitle(), l.getDescription(), l.getActor()))
            .toList());
  }

  // ---------------------------------------------------------------- comandos

  /** Wizard: cria evento, setores, lotes e o mapa físico de assentos numa única transação. */
  @Transactional
  public String create(CreateEventRequest req) {
    Map<Integer, Map<Integer, Seat.Kind>> blocked = new HashMap<>();
    for (CreateEventRequest.BlockedSeat b : req.blockedSeats() == null ? List.<CreateEventRequest.BlockedSeat>of() : req.blockedSeats()) {
      if (b.sector() >= req.sectors().size() || b.number() > req.sectors().get(b.sector()).capacity()) {
        throw ApiException.badRequest("Assento marcado fora do mapa");
      }
      blocked.computeIfAbsent(b.sector(), k -> new HashMap<>()).put(b.number(), b.kind());
    }

    String id = "evt-" + slug(req.title()) + "-" + UUID.randomUUID().toString().substring(0, 4);
    Event e =
        new Event(id, req.title().trim(), req.category(), req.startsAt(), Event.Status.RASCUNHO)
            .ownedBy(CurrentUser.id())
            .at(req.venue().trim(), req.city().trim(), req.address() == null ? req.venue() : req.address())
            .details(req.description(), req.ageRating() == null ? "Livre" : req.ageRating(), Math.floorMod(id.hashCode(), 360), null, req.hasVirtualQueue());

    for (int i = 0; i < req.sectors().size(); i++) {
      CreateEventRequest.SectorInput in = req.sectors().get(i);
      Map<Integer, Seat.Kind> marks = blocked.getOrDefault(i, Map.of());
      long unsellable = marks.values().stream().filter(k -> k == Seat.Kind.OBSTRUCAO || k == Seat.Kind.BLOQUEADO_PRODUCAO).count();
      long sellable = in.capacity() - unsellable;
      int quota = in.lots().stream().mapToInt(CreateEventRequest.LotInput::quantity).sum();
      // Invariante de conservação: as cotas dos lotes fecham exatamente com os assentos vendáveis.
      if (quota != sellable) {
        throw ApiException.badRequest(
            "Setor " + in.name() + ": soma dos lotes (" + quota + ") difere dos assentos vendáveis (" + sellable + ")");
      }
      Sector s = new Sector(String.valueOf((char) ('A' + i)), in.name().trim(), COLORS[i % COLORS.length], in.seatsPerRow())
          .info(in.description(), in.gate() == null || in.gate().isBlank() ? "Portão principal" : in.gate(), List.of());
      in.lots().forEach(l -> s.addLot(new Sector.LotTier(l.name().trim(), l.priceCents(), null, l.quantity(), l.limitPerCpf(), l.maxInstallments())));
      e.addSector(s);
    }
    events.save(e);

    List<Seat> map = new ArrayList<>();
    for (int i = 0; i < e.getSectors().size(); i++) {
      Sector s = e.getSectors().get(i);
      Map<Integer, Seat.Kind> marks = blocked.getOrDefault(i, Map.of());
      for (int n = 1; n <= req.sectors().get(i).capacity(); n++) {
        map.add(new Seat(e.getId(), s.getId(), s.getCode(), n, s.getSeatsPerRow(), marks.getOrDefault(n, Seat.Kind.PADRAO)));
      }
    }
    seats.saveAll(map);

    audit.log(id, AuditLog.Kind.EVENTO, "Evento criado",
        e.getSectors().size() + " setores, " + map.size() + " assentos e "
            + e.getSectors().stream().mapToInt(s -> s.getLots().size()).sum() + " lotes configurados.", actor());
    if (req.publish()) publish(id);
    return id;
  }

  @Transactional
  public void publish(String eventId) {
    Event e = owned(eventId);
    e.publish();
    audit.log(eventId, AuditLog.Kind.BILHETERIA, "Bilheteria aberta", "Evento publicado e vendas liberadas.", actor());
  }

  @Transactional
  public void pause(String eventId) {
    Event e = owned(eventId);
    e.pauseSales();
    audit.log(eventId, AuditLog.Kind.BILHETERIA, "Vendas pausadas",
        "Novas reservas bloqueadas. Pedidos em andamento seguem até o fim do prazo de 10 minutos.", actor());
  }

  @Transactional
  public void resume(String eventId) {
    Event e = owned(eventId);
    e.resumeSales();
    audit.log(eventId, AuditLog.Kind.BILHETERIA, "Vendas retomadas", "Reservas liberadas novamente.", actor());
  }

  private static String slug(String title) {
    String s = Normalizer.normalize(title, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
    s = s.replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
    return s.length() > 24 ? s.substring(0, 24) : s;
  }
}
