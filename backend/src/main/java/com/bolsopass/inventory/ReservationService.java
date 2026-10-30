package com.bolsopass.inventory;

import com.bolsopass.catalog.CatalogService;
import com.bolsopass.catalog.Event;
import com.bolsopass.catalog.Sector;
import com.bolsopass.catalog.VirtualQueue;
import com.bolsopass.orders.Order;
import com.bolsopass.orders.OrderRepository;
import com.bolsopass.orders.OrderStatus;
import com.bolsopass.orders.OrderViews;
import com.bolsopass.orders.OrderViews.ReservationView;
import com.bolsopass.outbox.OutboxWriter;
import com.bolsopass.realtime.RealtimeHub;
import com.bolsopass.shared.AfterCommit;
import com.bolsopass.shared.ApiException;
import com.bolsopass.shared.AppProperties;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Alocação atômica de assentos (RF-06) e devolução por timeout (RF-07).
 * Invariante: nunca dois compradores com a mesma trava (zero overselling).
 */
@Service
public class ReservationService {
  private final SeatRepository seats;
  private final ReservationRepository reservations;
  private final OrderRepository orders;
  private final CatalogService catalog;
  private final VirtualQueue queue;
  private final OutboxWriter outbox;
  private final RealtimeHub hub;
  private final OrderViews views;
  private final AppProperties props;
  private final SecureRandom random = new SecureRandom();

  public ReservationService(
      SeatRepository seats,
      ReservationRepository reservations,
      OrderRepository orders,
      CatalogService catalog,
      VirtualQueue queue,
      OutboxWriter outbox,
      RealtimeHub hub,
      OrderViews views,
      AppProperties props) {
    this.seats = seats;
    this.reservations = reservations;
    this.orders = orders;
    this.catalog = catalog;
    this.queue = queue;
    this.outbox = outbox;
    this.hub = hub;
    this.views = views;
    this.props = props;
  }

  @Transactional
  public ReservationView reserve(String userId, String eventId, List<String> requestedSeatIds) {
    List<String> seatIds = new ArrayList<>(new LinkedHashSet<>(requestedSeatIds));
    if (seatIds.isEmpty()) throw ApiException.badRequest("Selecione ao menos um assento");

    Event event = catalog.find(eventId);
    if (!event.isOnSale()) throw ApiException.conflict("As vendas deste evento não estão abertas");
    if (event.isHasVirtualQueue() && !queue.isAdmitted(eventId, userId)) {
      throw ApiException.conflict("Aguarde sua vez na sala de espera virtual");
    }

    Instant now = Instant.now();
    // Uma reserva aberta por comprador e evento: evita monopolizar assentos.
    if (!reservations.findOpen(userId, eventId, now).isEmpty()) {
      throw ApiException.conflict("Você já tem uma reserva ativa para este evento");
    }

    // SELECT ... FOR UPDATE: a partir daqui ninguém mais mexe nesses assentos até o commit.
    List<Seat> locked = seats.lockAll(seatIds);
    if (locked.size() != seatIds.size()
        || locked.stream().anyMatch(s -> !s.getEventId().equals(eventId))) {
      throw ApiException.badRequest("Assento inexistente para este evento");
    }
    List<String> taken = locked.stream().filter(s -> !s.isSelectable()).map(Seat::getId).toList();
    if (!taken.isEmpty()) {
      throw new ApiException(HttpStatus.CONFLICT, "Assento já reservado por outro comprador", taken);
    }

    Map<String, Sector> sectors = catalog.sectorsById(event);
    // Preço e limite vêm do lote vigente de cada setor neste instante.
    Map<String, Sector.LotTier> lots = new HashMap<>();
    for (Seat s : locked) {
      lots.computeIfAbsent(s.getSectorId(), id -> {
        Sector.ActiveLot active = catalog.activeLot(sectors.get(id));
        if (active == null) throw ApiException.conflict("Setor " + sectors.get(id).getName() + " esgotado");
        return active.tier();
      });
    }
    enforceCpfLimit(userId, eventId, locked, sectors, lots);

    String reservationId = "res-" + UUID.randomUUID();
    String orderId = "ord-" + UUID.randomUUID();
    locked.forEach(s -> s.lock(reservationId));

    Order order = new Order(orderId, newCode(), userId, eventId, now);
    order.attachReservation(
        reservationId,
        locked.stream()
            .map(s -> {
              Sector sec = sectors.get(s.getSectorId());
              return new Order.Item(
                  s.getId(), s.getLabel(), sec.getId(), sec.getName(), lots.get(sec.getId()).priceCents());
            })
            .toList(),
        props.serviceFeeRate());
    order.apply(OrderStatus.Event.ASSENTO_BLOQUEADO);
    orders.save(order);

    Reservation reservation =
        reservations.save(
            new Reservation(reservationId, eventId, userId, orderId, seatIds, now, now.plus(props.reservationTtl())));

    outbox.write(
        OutboxWriter.RESERVAS_CRIADAS,
        eventId,
        "ReservaCriadaEvent",
        Map.of("reservationId", reservationId, "orderId", orderId, "eventId", eventId, "seatIds", seatIds,
            "expiresAt", reservation.getExpiresAt().toString()));
    AfterCommit.run(
        () ->
            hub.publish(
                RealtimeHub.eventSeats(eventId),
                Map.of("type", "SEAT_LOCKED", "eventId", eventId, "seatIds", seatIds, "reservationId", reservationId)));
    return views.reservation(reservation, order.getStatus());
  }

  /**
   * Fim da trava (timeout, desistência). Chamado com o pedido já travado pelo chamador.
   * A trava some aos 10 min mesmo que o pedido esteja RECUSADO.
   */
  @Transactional
  public void releaseSeats(Reservation reservation, String reason) {
    List<Seat> held = seats.lockHeldBy(reservation.getId());
    held.forEach(s -> s.release(reservation.getId()));
    reservation.close();
    List<String> ids = held.stream().map(Seat::getId).toList();
    if (ids.isEmpty()) return;
    String eventId = reservation.getEventId();
    AfterCommit.run(
        () ->
            hub.publish(
                RealtimeHub.eventSeats(eventId),
                Map.of("type", "SEAT_RELEASED", "eventId", eventId, "seatIds", ids, "reason", reason)));
  }

  /** Limite de ingressos por CPF do lote (wizard: coluna "Limite CPF"). */
  private void enforceCpfLimit(
      String userId, String eventId, List<Seat> requested, Map<String, Sector> sectors,
      Map<String, Sector.LotTier> lots) {
    Map<String, Integer> already = new HashMap<>();
    for (Order o :
        orders.findByUserIdAndEventIdAndStatusIn(
            userId, eventId, EnumSet.of(OrderStatus.PAGO, OrderStatus.RESERVADO, OrderStatus.AGUARDANDO_PAGAMENTO))) {
      for (Order.Item i : o.getItems()) already.merge(i.getSectorId(), 1, Integer::sum);
    }
    Map<String, Integer> wanted = new HashMap<>();
    requested.forEach(s -> wanted.merge(s.getSectorId(), 1, Integer::sum));
    wanted.forEach(
        (sectorId, count) -> {
          Sector sec = sectors.get(sectorId);
          int limit = lots.get(sectorId).limitPerCpf();
          int total = count + already.getOrDefault(sectorId, 0);
          if (total > limit) {
            throw ApiException.conflict("Limite de " + limit + " ingressos por CPF em " + sec.getName());
          }
        });
  }

  private String newCode() {
    return "PLS-" + (10_000 + random.nextInt(89_999)) + "-BR";
  }
}
