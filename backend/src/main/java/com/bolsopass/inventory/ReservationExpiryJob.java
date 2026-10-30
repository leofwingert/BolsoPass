package com.bolsopass.inventory;

import com.bolsopass.orders.Order;
import com.bolsopass.orders.OrderRepository;
import com.bolsopass.orders.OrderStatus;
import com.bolsopass.outbox.OutboxWriter;
import com.bolsopass.realtime.RealtimeHub;
import com.bolsopass.shared.AfterCommit;
import java.time.Instant;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Devolução automática ao fim dos 10 minutos (RF-07).
 * Pedido RESERVADO/AGUARDANDO_PAGAMENTO -> EXPIRADO, assento -> DISPONIVEL,
 * evento reservas.expiradas no outbox e aviso em tempo real para quem está no mapa.
 */
@Component
public class ReservationExpiryJob {
  private static final Logger log = LoggerFactory.getLogger(ReservationExpiryJob.class);

  private final ReservationRepository reservations;
  private final OrderRepository orders;
  private final ReservationService reservationService;
  private final OutboxWriter outbox;
  private final RealtimeHub hub;
  private final TransactionTemplate tx;

  public ReservationExpiryJob(
      ReservationRepository reservations,
      OrderRepository orders,
      ReservationService reservationService,
      OutboxWriter outbox,
      RealtimeHub hub,
      TransactionTemplate tx) {
    this.reservations = reservations;
    this.orders = orders;
    this.reservationService = reservationService;
    this.outbox = outbox;
    this.hub = hub;
    this.tx = tx;
  }

  @Scheduled(fixedDelay = 1000)
  public void expireDue() {
    for (String id : reservations.findExpiredOpenIds(Instant.now())) {
      try {
        tx.executeWithoutResult(s -> expire(id));
      } catch (RuntimeException e) {
        log.warn("Falha ao expirar reserva {}: {}", id, e.getMessage());
      }
    }
  }

  void expire(String reservationId) {
    Reservation r = reservations.findById(reservationId).orElse(null);
    if (r == null || r.isReleased()) return;
    // Mesmo lock do checkout: pagamento e expiração nunca se cruzam.
    Order order = orders.lockById(r.getOrderId()).orElseThrow();
    if (order.getStatus() == OrderStatus.PAGO) {
      r.close();
      return;
    }
    if (order.getStatus().canApply(OrderStatus.Event.TIMEOUT_10_MINUTOS)) {
      order.apply(OrderStatus.Event.TIMEOUT_10_MINUTOS);
      outbox.write(
          OutboxWriter.RESERVAS_EXPIRADAS,
          r.getEventId(),
          "ReservaExpiradaEvent",
          Map.of("reservationId", r.getId(), "orderId", order.getId(), "eventId", r.getEventId(), "seatIds", r.getSeatIds()));
      String orderId = order.getId();
      AfterCommit.run(
          () ->
              hub.publish(
                  RealtimeHub.order(orderId),
                  Map.of("type", "ORDER_STATUS", "orderId", orderId, "status", OrderStatus.EXPIRADO.name())));
    }
    reservationService.releaseSeats(r, "EXPIRADA");
  }
}
