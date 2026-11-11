package com.bolsopass.management;

import com.bolsopass.catalog.CatalogService;
import com.bolsopass.catalog.Event;
import com.bolsopass.catalog.EventRepository;
import com.bolsopass.iam.CurrentUser;
import com.bolsopass.ticketing.QrSigner;
import com.bolsopass.ticketing.Ticket;
import com.bolsopass.ticketing.TicketRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Portaria / bilheteria (ROLE_OPERATOR). Os endpoints do organizador ficam em
 * {@link com.bolsopass.organizer.OrganizerController}.
 */
@RestController
@RequestMapping("/api/v1/operator")
@PreAuthorize("hasRole('OPERATOR')")
public class ManagementController {
  /** Eventos que a portaria pode operar. */
  private static final Set<Event.Status> OPERABLE =
      Set.of(Event.Status.EM_VENDAS, Event.Status.PAUSADO, Event.Status.ESGOTADO);

  private final EventRepository events;
  private final CatalogService catalog;
  private final TicketRepository tickets;
  private final QrSigner signer;
  private final CheckinService checkins;

  public ManagementController(
      EventRepository events, CatalogService catalog, TicketRepository tickets, QrSigner signer,
      CheckinService checkins) {
    this.events = events;
    this.catalog = catalog;
    this.tickets = tickets;
    this.signer = signer;
    this.checkins = checkins;
  }

  public record CheckinRequest(String ticketId, CheckinService.Method method, String gate) {}

  /**
   * Entrada online, decidida na hora pelo servidor. Usada pelo facial-checkin-service (método
   * FACIAL), que repassa o token do operador. Mesma regra e mesmo evento do QR.
   */
  @PostMapping("/events/{eventId}/checkins")
  @Transactional
  CheckinService.Result checkin(@PathVariable String eventId, @RequestBody CheckinRequest body) {
    catalog.find(eventId);
    CheckinService.Method method = body.method() == null ? CheckinService.Method.QR : body.method();
    return checkins.register(eventId, body.ticketId(), method, body.gate(), CurrentUser.id(), Instant.now());
  }

  // Lista de eventos também serve ao terminal de venda (escolher o evento a vender).
  @PreAuthorize("hasAnyRole('OPERATOR', 'SELLER')")
  @GetMapping("/events")
  @Transactional(readOnly = true)
  List<Map<String, Object>> assignedEvents() {
    Instant now = Instant.now();
    return events.findByStatusInOrderByStartsAt(OPERABLE).stream()
        .filter(e -> e.getStartsAt().isAfter(now.minus(Duration.ofHours(12))))
        .map(e -> assignedEvent(e, now))
        .toList();
  }

  /** Base para validar offline (RF-13): chave pública + ingressos válidos + entradas já feitas. */
  @GetMapping("/events/{eventId}/cache")
  @Transactional(readOnly = true)
  Map<String, Object> cache(@PathVariable String eventId) {
    catalog.find(eventId);
    Map<String, Object> ticketMap = new HashMap<>();
    Map<String, Object> used = new HashMap<>();
    for (Ticket t : tickets.findByEventId(eventId)) {
      if (t.getStatus() == Ticket.Status.CANCELADO) continue;
      ticketMap.put(t.getId(), Map.of(
          "holderName", value(t.getHolderName()),
          "holderCpf", value(t.getHolderCpf()),
          "sectorName", value(t.getSectorName()),
          "categoryName", "Inteira"));
      if (t.getUsedAt() != null) {
        used.put(t.getId(), Map.of("at", t.getUsedAt().toString(), "gate", value(t.getUsedGate())));
      }
    }
    return Map.of(
        "eventId", eventId,
        "publicKeyJwk", signer.publicJwk(),
        "syncedAt", Instant.now().toString(),
        "tickets", ticketMap,
        "used", used);
  }

  /**
   * Leituras do posto (inclusive as feitas offline). Idempotente: a mesma entrada enviada de
   * novo não conta duas vezes, e a primeira entrada de um ingresso é a que vale.
   */
  @PostMapping("/events/{eventId}/scans")
  @Transactional
  Map<String, Integer> syncScans(@PathVariable String eventId, @RequestBody Map<String, List<Map<String, Object>>> body) {
    catalog.find(eventId);
    int accepted = 0;
    int duplicates = 0;
    for (Map<String, Object> scan : body.getOrDefault("scans", List.of())) {
      if (!"VALIDO".equals(scan.get("status")) || !(scan.get("ticketId") instanceof String ticketId)) continue;
      CheckinService.Result r = checkins.register(eventId, ticketId, CheckinService.Method.QR,
          scan.get("gate") instanceof String g ? g : "", CurrentUser.id(), parse(scan.get("at")));
      if (r.status() == CheckinService.Status.LIBERADO) accepted++;
      else if (r.reason() == CheckinService.Reason.INGRESSO_JA_UTILIZADO) duplicates++;
    }
    return Map.of("accepted", accepted, "duplicates", duplicates);
  }

  private Map<String, Object> assignedEvent(Event event, Instant now) {
    var summary = catalog.summary(event);
    List<Ticket> issued = tickets.findByEventId(event.getId());
    long used = issued.stream().filter(t -> t.getUsedAt() != null).count();
    List<Map<String, Object>> gates = new ArrayList<>();
    event.getSectors().stream().map(s -> s.getGate()).distinct().forEach(g ->
        gates.add(Map.of("id", g, "name", g, "turnstiles", "Catracas do setor", "description", "Leitor QR", "icon", "door")));
    return Map.of(
        "event", summary,
        "checkedInPct", issued.isEmpty() ? 0 : Math.round(used * 100.0 / issued.size()),
        // Portões abrem 2 horas antes do início.
        "gatesOpen", !now.isBefore(event.getStartsAt().minus(Duration.ofHours(2))),
        "gates", gates);
  }

  private static Instant parse(Object at) {
    try {
      return at instanceof String s ? Instant.parse(s) : Instant.now();
    } catch (RuntimeException e) {
      return Instant.now();
    }
  }

  private static String value(String text) {
    return text == null ? "" : text;
  }
}
