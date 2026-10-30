package com.bolsopass.orders;

import com.bolsopass.catalog.CatalogService;
import com.bolsopass.catalog.CatalogService.EventSummaryView;
import com.bolsopass.inventory.Reservation;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Component;

/** Conversão para o formato JSON do front (Order, Reservation em src/api/types.ts). */
@Component
public class OrderViews {
  private final CatalogService catalog;

  public OrderViews(CatalogService catalog) {
    this.catalog = catalog;
  }

  public record ItemView(
      String seatId, String seatLabel, String sectorName, long priceCents, String holderName, String holderCpf, String ticketId) {}

  public record TotalsView(long subtotalCents, long feeCents, long totalCents) {}

  public record PaymentView(Order.PaymentMethod method, String last4, int installments, Instant approvedAt) {}

  public record OrderView(
      String id,
      String code,
      String reservationId,
      EventSummaryView event,
      OrderStatus status,
      List<ItemView> items,
      TotalsView totals,
      PaymentView payment,
      Instant createdAt,
      Instant paidAt,
      String declineReason) {}

  public record ReservationView(
      String id,
      String eventId,
      List<String> seatIds,
      String orderId,
      OrderStatus status,
      Instant createdAt,
      Instant expiresAt,
      Instant serverNow) {}

  public OrderView order(Order o) {
    return new OrderView(
        o.getId(),
        o.getCode(),
        o.getReservationId(),
        catalog.summary(catalog.find(o.getEventId())),
        o.getStatus(),
        o.getItems().stream()
            .map(
                i ->
                    new ItemView(
                        i.getSeatId(), i.getSeatLabel(), i.getSectorName(), i.getPriceCents(),
                        i.getHolderName(), i.getHolderCpf(), i.getTicketId()))
            .toList(),
        new TotalsView(o.getSubtotalCents(), o.getFeeCents(), o.getTotalCents()),
        o.getPaymentMethod() == null
            ? null
            : new PaymentView(o.getPaymentMethod(), o.getCardLast4(), o.getInstallments(), o.getPaidAt()),
        o.getCreatedAt(),
        o.getPaidAt(),
        o.getDeclineReason());
  }

  public ReservationView reservation(Reservation r, OrderStatus status) {
    return new ReservationView(
        r.getId(), r.getEventId(), List.copyOf(r.getSeatIds()), r.getOrderId(), status,
        r.getCreatedAt(), r.getExpiresAt(), Instant.now());
  }
}
