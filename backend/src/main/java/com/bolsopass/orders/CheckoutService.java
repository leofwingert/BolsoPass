package com.bolsopass.orders;

import com.bolsopass.audit.AuditLog;
import com.bolsopass.audit.AuditService;
import com.bolsopass.catalog.CatalogService;
import com.bolsopass.catalog.Event;
import com.bolsopass.catalog.Sector;
import com.bolsopass.inventory.Reservation;
import com.bolsopass.inventory.ReservationRepository;
import com.bolsopass.inventory.ReservationService;
import com.bolsopass.inventory.Seat;
import com.bolsopass.inventory.SeatRepository;
import com.bolsopass.orders.OrderViews.OrderView;
import com.bolsopass.orders.OrderViews.ReservationView;
import com.bolsopass.outbox.OutboxWriter;
import com.bolsopass.realtime.RealtimeHub;
import com.bolsopass.shared.AfterCommit;
import com.bolsopass.shared.ApiException;
import com.bolsopass.ticketing.TicketService;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/** Checkout com FSM, Idempotency-Key (RF-09) e Transactional Outbox (RF-10). */
@Service
public class CheckoutService {
  public record Holder(String seatId, String name, String cpf) {}

  public record Card(String number, String holderName, String expiry, String cvv) {}

  public record PayRequest(
      String reservationId, Order.PaymentMethod method, int installments, List<Holder> holders, Card card) {}

  private final OrderRepository orders;
  private final ReservationRepository reservations;
  private final ReservationService reservationService;
  private final SeatRepository seats;
  private final IdempotencyRepository idempotency;
  private final PaymentGateway gateway;
  private final TicketService tickets;
  private final CatalogService catalog;
  private final OutboxWriter outbox;
  private final RealtimeHub hub;
  private final OrderViews views;
  private final TransactionTemplate tx;
  private final AuditService audit;

  public CheckoutService(
      OrderRepository orders,
      ReservationRepository reservations,
      ReservationService reservationService,
      SeatRepository seats,
      IdempotencyRepository idempotency,
      PaymentGateway gateway,
      TicketService tickets,
      CatalogService catalog,
      OutboxWriter outbox,
      RealtimeHub hub,
      OrderViews views,
      TransactionTemplate tx,
      AuditService audit) {
    this.audit = audit;
    this.orders = orders;
    this.reservations = reservations;
    this.reservationService = reservationService;
    this.seats = seats;
    this.idempotency = idempotency;
    this.gateway = gateway;
    this.tickets = tickets;
    this.catalog = catalog;
    this.outbox = outbox;
    this.hub = hub;
    this.views = views;
    this.tx = tx;
  }

  public Map.Entry<ReservationView, OrderView> reservation(String userId, String reservationId) {
    return tx.execute(
        s -> {
          Reservation r = ownedReservation(userId, reservationId);
          Order o = orders.findById(r.getOrderId()).orElseThrow();
          return Map.entry(views.reservation(r, o.getStatus()), views.order(o));
        });
  }

  /**
   * Mesma chave = mesma resposta, sem cobrar de novo. Chave em processamento = 409.
   * O registro PROCESSING é gravado numa transação própria antes da cobrança.
   */
  public OrderView pay(String userId, PayRequest req, String idempotencyKey) {
    if (idempotencyKey == null || idempotencyKey.isBlank()) {
      throw ApiException.badRequest("Header Idempotency-Key é obrigatório");
    }
    IdempotencyRecord existing = idempotency.findById(idempotencyKey).orElse(null);
    if (existing != null) return replay(existing, userId);
    try {
      tx.executeWithoutResult(s -> idempotency.saveAndFlush(new IdempotencyRecord(idempotencyKey, userId, Instant.now())));
    } catch (DataIntegrityViolationException race) {
      return replay(idempotency.findById(idempotencyKey).orElseThrow(), userId);
    }

    try {
      return tx.execute(s -> process(userId, req, idempotencyKey));
    } catch (RuntimeException e) {
      // Falhou antes de concluir: libera a chave para o cliente tentar de novo.
      tx.executeWithoutResult(s -> idempotency.deleteById(idempotencyKey));
      throw e;
    }
  }

  private OrderView replay(IdempotencyRecord rec, String userId) {
    if (!rec.getUserId().equals(userId)) throw ApiException.forbidden("Chave de outro usuário");
    if (rec.getState() == IdempotencyRecord.State.PROCESSING) {
      throw ApiException.conflict("Pagamento já está em processamento");
    }
    return tx.execute(s -> views.order(orders.findById(rec.getOrderId()).orElseThrow()));
  }

  private OrderView process(String userId, PayRequest req, String idempotencyKey) {
    Reservation r = ownedReservation(userId, req.reservationId());
    Order order = orders.lockById(r.getOrderId()).orElseThrow();
    Instant now = Instant.now();

    if (r.isExpired(now)) {
      throw new ApiException(HttpStatus.GONE, "Sua reserva expirou e os assentos voltaram ao catálogo.");
    }
    if (!order.getStatus().canApply(OrderStatus.Event.SUBMETER_PAGAMENTO)) {
      throw ApiException.conflict("Pedido em estado " + order.getStatus() + " não aceita pagamento");
    }
    validate(req, order);

    req.holders().forEach(h -> order.assignHolder(h.seatId(), h.name().trim(), Cpf.digits(h.cpf())));
    String last4 = req.card() == null ? null : lastFour(req.card().number());
    order.paymentAttempt(req.method(), last4, Math.max(1, req.installments()));
    order.apply(OrderStatus.Event.SUBMETER_PAGAMENTO);

    PaymentGateway.Result result = gateway.charge(idempotencyKey, req.method(), last4, order.getTotalCents());
    if (result.approved()) {
      order.approved(now);
      Map<String, Sector> sectors = catalog.sectorsById(catalog.find(order.getEventId()));
      Map<String, Integer> lotBefore = lotIndexes(order, sectors);
      for (Seat seat : seats.lockAll(r.getSeatIds())) seat.sell(r.getId());
      seats.flush();
      auditLotTurns(order, sectors, lotBefore);
      r.close();
      tickets.issue(order);
      // Mesma transação ACID: pedido PAGO + evento no outbox.
      outbox.write(
          OutboxWriter.PEDIDOS_PAGOS,
          order.getId(),
          "PedidoPagoEvent",
          Map.of("orderId", order.getId(), "eventId", order.getEventId(), "userId", userId,
              "totalCents", order.getTotalCents(), "paidAt", now.toString()));
      String eventId = order.getEventId();
      List<String> seatIds = List.copyOf(r.getSeatIds());
      AfterCommit.run(
          () ->
              hub.publish(
                  RealtimeHub.eventSeats(eventId),
                  Map.of("type", "SEAT_SOLD", "eventId", eventId, "seatIds", seatIds)));
    } else {
      order.declined(result.reason());
    }
    idempotency.findById(idempotencyKey).orElseThrow().complete(order.getId());
    publishStatus(order);
    return views.order(order);
  }

  /** Desistência após recusa: RECUSADO -> CANCELADO e assentos de volta ao catálogo. */
  public void giveUp(String userId, String reservationId) {
    tx.executeWithoutResult(
        s -> {
          Reservation r = ownedReservation(userId, reservationId);
          Order order = orders.lockById(r.getOrderId()).orElseThrow();
          order.apply(OrderStatus.Event.DESISTENCIA);
          outbox.write(
              OutboxWriter.PEDIDOS_CANCELADOS,
              order.getId(),
              "PedidoCanceladoEvent",
              Map.of("orderId", order.getId(), "eventId", order.getEventId(), "seatIds", r.getSeatIds()));
          reservationService.releaseSeats(r, "CANCELADA");
          publishStatus(order);
        });
  }

  /** Índice do lote vigente por setor do pedido (-1 = esgotado). */
  private Map<String, Integer> lotIndexes(Order order, Map<String, Sector> sectors) {
    Map<String, Integer> out = new java.util.HashMap<>();
    for (Order.Item i : order.getItems()) {
      out.computeIfAbsent(i.getSectorId(), id -> {
        Sector.ActiveLot a = catalog.activeLot(sectors.get(id));
        return a == null ? -1 : a.index();
      });
    }
    return out;
  }

  /** Virada automática de lote: registra no log de auditoria quando a venda esgota um lote. */
  private void auditLotTurns(Order order, Map<String, Sector> sectors, Map<String, Integer> before) {
    lotIndexes(order, sectors).forEach((sectorId, after) -> {
      int prev = before.get(sectorId);
      if (after.equals(prev)) return;
      Sector sec = sectors.get(sectorId);
      String old = sec.getLots().get(prev).name();
      String desc = after < 0
          ? old + " esgotado. Setor " + sec.getName() + " sem lotes disponíveis."
          : old + " esgotado. " + sec.getLots().get(after).name() + " ativado por "
              + String.format("R$ %.2f", sec.getLots().get(after).priceCents() / 100.0) + ".";
      audit.log(order.getEventId(), AuditLog.Kind.LOTE, "Virada automática de lote", desc, AuditService.SYSTEM);
    });
  }

  private void validate(PayRequest req, Order order) {
    if (req.method() == null) throw ApiException.badRequest("Forma de pagamento obrigatória");
    Set<String> orderSeats = new HashSet<>();
    order.getItems().forEach(i -> orderSeats.add(i.getSeatId()));
    if (req.holders() == null || req.holders().size() != orderSeats.size()) {
      throw ApiException.badRequest("Informe o titular de cada ingresso");
    }
    Set<String> cpfs = new HashSet<>();
    for (Holder h : req.holders()) {
      if (!orderSeats.contains(h.seatId())) throw ApiException.badRequest("Assento fora do pedido");
      if (h.name() == null || h.name().trim().split("\\s+").length < 2) {
        throw ApiException.badRequest("Informe nome e sobrenome de cada titular");
      }
      if (!Cpf.isValid(h.cpf())) throw ApiException.badRequest("CPF inválido: " + h.cpf());
      if (!cpfs.add(Cpf.digits(h.cpf()))) throw ApiException.badRequest("CPF repetido no mesmo pedido");
    }
    Event event = catalog.find(order.getEventId());
    Map<String, Sector> sectors = catalog.sectorsById(event);
    int maxInstallments =
        order.getItems().stream()
            .mapToInt(i -> {
              Sector.LotTier lot = catalog.currentLot(sectors.get(i.getSectorId()));
              return lot == null ? 1 : lot.maxInstallments();
            })
            .min()
            .orElse(1);
    if (req.method() == Order.PaymentMethod.CREDIT_CARD) {
      if (req.card() == null || lastFour(req.card().number()) == null) {
        throw ApiException.badRequest("Dados do cartão incompletos");
      }
      if (req.installments() > maxInstallments) {
        throw ApiException.badRequest("Máximo de " + maxInstallments + " parcelas");
      }
    }
  }

  private Reservation ownedReservation(String userId, String reservationId) {
    Reservation r = reservations.findById(reservationId).orElseThrow(() -> ApiException.notFound("Reserva"));
    if (!r.getUserId().equals(userId)) throw ApiException.forbidden("Reserva não pertence a você");
    return r;
  }

  private void publishStatus(Order order) {
    String id = order.getId();
    String status = order.getStatus().name();
    AfterCommit.run(
        () -> hub.publish(RealtimeHub.order(id), Map.of("type", "ORDER_STATUS", "orderId", id, "status", status)));
  }

  private static String lastFour(String cardNumber) {
    String d = Cpf.digits(cardNumber);
    return d.length() >= 13 && d.length() <= 19 ? d.substring(d.length() - 4) : null;
  }

  /** Chaves de idempotência valem 24 h (RF-09). */
  @Scheduled(fixedDelay = 3_600_000)
  public void purgeOldKeys() {
    idempotency.deleteOlderThan(Instant.now().minus(Duration.ofHours(24)));
  }
}
