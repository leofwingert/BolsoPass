package com.bolsopass.organizer;

import com.bolsopass.audit.AuditLog;
import com.bolsopass.audit.AuditService;
import com.bolsopass.catalog.Event;
import com.bolsopass.catalog.Sector;
import com.bolsopass.iam.User;
import com.bolsopass.iam.UserRepository;
import com.bolsopass.inventory.Seat;
import com.bolsopass.inventory.SeatRepository;
import com.bolsopass.orders.Order;
import com.bolsopass.orders.OrderRepository;
import com.bolsopass.orders.OrderStatus;
import com.bolsopass.shared.ApiException;
import com.bolsopass.ticketing.Ticket;
import com.bolsopass.ticketing.TicketRepository;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Mapa de assentos do setor para o organizador e "para quem vendi" de cada assento.
 * Dados pessoais só do dono do evento (ou admin), e toda consulta fica na auditoria (LGPD).
 */
@Service
public class SeatSalesService {
  public record SeatView(String id, String label, String row, int number, String status, String kind) {}

  public record SectorSeats(String sectorId, String sectorName, String gate, List<SeatView> seats) {}

  public record Person(String name, String email, String phone, String cpf) {}

  public record SeatSale(
      String seatId, String seatLabel, String sectorName, String seatStatus,
      String orderCode, Instant paidAt, String paymentMethod, String cardLast4, int installments,
      long priceCents, long orderTotalCents, int orderSeats,
      Person buyer, Person holder,
      String ticketId, Instant checkedInAt, String checkedInGate) {}

  private final OrganizerService organizer;
  private final SeatRepository seats;
  private final OrderRepository orders;
  private final TicketRepository tickets;
  private final UserRepository users;
  private final AuditService audit;

  public SeatSalesService(
      OrganizerService organizer, SeatRepository seats, OrderRepository orders, TicketRepository tickets,
      UserRepository users, AuditService audit) {
    this.organizer = organizer;
    this.seats = seats;
    this.orders = orders;
    this.tickets = tickets;
    this.users = users;
    this.audit = audit;
  }

  @Transactional(readOnly = true)
  public SectorSeats sectorSeats(String eventId, String sectorId) {
    Event e = organizer.owned(eventId);
    Sector sector = e.getSectors().stream().filter(s -> s.getId().equals(sectorId)).findFirst()
        .orElseThrow(() -> ApiException.notFound("Setor"));
    List<SeatView> list = seats.findByEventIdOrderBySectorIdAscNumberAsc(eventId).stream()
        .filter(s -> s.getSectorId().equals(sectorId))
        .map(s -> new SeatView(s.getId(), s.getLabel(), s.getRowLabel(), s.getNumber(), s.getStatus().name(), s.getKind().name()))
        .toList();
    return new SectorSeats(sector.getId(), sector.getName(), sector.getGate(), list);
  }

  @Transactional
  public SeatSale seatSale(String eventId, String seatId) {
    organizer.owned(eventId);
    Seat seat = seats.findByEventIdOrderBySectorIdAscNumberAsc(eventId).stream()
        .filter(s -> s.getId().equals(seatId)).findFirst()
        .orElseThrow(() -> ApiException.notFound("Assento"));
    if (seat.getStatus() != Seat.Status.VENDIDO && seat.getStatus() != Seat.Status.UTILIZADO) {
      throw ApiException.conflict("Este assento não foi vendido");
    }
    Order order = orders.findByEventIdInAndStatus(Set.of(eventId), OrderStatus.PAGO).stream()
        .filter(o -> o.getItems().stream().anyMatch(i -> i.getSeatId().equals(seatId)))
        .findFirst()
        .orElseThrow(() -> ApiException.notFound("Pedido deste assento"));
    Order.Item item = order.getItems().stream().filter(i -> i.getSeatId().equals(seatId)).findFirst().orElseThrow();
    User buyer = users.findById(order.getUserId()).orElse(null);
    Ticket ticket = tickets.findByEventId(eventId).stream()
        .filter(t -> seatId.equals(t.getSeatId())).findFirst().orElse(null);

    audit.log(eventId, AuditLog.Kind.SEGURANCA, "Consulta de dados do comprador",
        "Dados do comprador do assento " + seat.getLabel() + " (pedido " + order.getCode() + ") foram visualizados.",
        organizer.actorEmail());

    return new SeatSale(
        seat.getId(), seat.getLabel(), item.getSectorName(), seat.getStatus().name(),
        order.getCode(), order.getPaidAt(), order.getPaymentMethod() == null ? null : order.getPaymentMethod().name(),
        order.getCardLast4(), order.getInstallments(),
        item.getPriceCents(), order.getTotalCents(), order.getItems().size(),
        buyer == null ? null : new Person(buyer.getName(), buyer.getEmail(), buyer.getPhone(), buyer.getCpf()),
        new Person(item.getHolderName(), ticket == null ? null : ticket.getHolderEmail(), null, item.getHolderCpf()),
        ticket == null ? item.getTicketId() : ticket.getId(),
        ticket == null ? null : ticket.getUsedAt(),
        ticket == null ? null : ticket.getUsedGate());
  }
}
