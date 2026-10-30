package com.bolsopass.ticketing;

import com.bolsopass.catalog.CatalogService;
import com.bolsopass.catalog.CatalogService.EventSummaryView;
import com.bolsopass.catalog.Sector;
import com.bolsopass.iam.UserRepository;
import com.bolsopass.orders.Order;
import com.bolsopass.shared.ApiException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Emissão de ingressos (RF-13). Hoje roda na transação do pagamento; quando o
 * ticketing-service for separado, passa a consumir pedidos.pagos (com processed_messages).
 */
@Service
public class TicketService {
  private final TicketRepository tickets;
  private final CatalogService catalog;
  private final UserRepository users;
  private final QrSigner signer;

  public TicketService(TicketRepository tickets, CatalogService catalog, UserRepository users, QrSigner signer) {
    this.tickets = tickets;
    this.catalog = catalog;
    this.users = users;
    this.signer = signer;
  }

  public record TicketView(
      String id,
      String orderId,
      EventSummaryView event,
      String seatLabel,
      String sectorName,
      String holderName,
      String holderEmail,
      String gate,
      Ticket.Status status,
      Instant issuedAt) {}

  public record QrView(String qr, long window) {}

  @Transactional
  public void issue(Order order) {
    Map<String, Sector> sectors = catalog.sectorsById(catalog.find(order.getEventId()));
    String email = users.findById(order.getUserId()).map(u -> u.getEmail()).orElse(null);
    for (Order.Item item : order.getItems()) {
      // Código do pedido (5 dígitos, único) + assento: único em toda a plataforma.
      String ticketId = "TK-" + order.getCode().substring(4, 9) + "-" + item.getSeatLabel();
      Sector sec = sectors.get(item.getSectorId());
      tickets.save(
          new Ticket(
                  ticketId, order.getId(), order.getEventId(), order.getUserId(), item.getSeatId(),
                  item.getSeatLabel(), item.getSectorName(), sec == null ? null : sec.getGate(), order.getPaidAt())
              .holder(item.getHolderName(), item.getHolderCpf(), email));
      order.assignTicket(item.getSeatId(), ticketId);
    }
  }

  @Transactional(readOnly = true)
  public List<TicketView> mine(String userId) {
    return tickets.findByUserIdOrderByIssuedAtDesc(userId).stream().map(this::view).toList();
  }

  @Transactional(readOnly = true)
  public TicketView get(String userId, String ticketId) {
    return view(owned(userId, ticketId));
  }

  /** QR que muda a cada 15 s: um print de tela vence rápido. */
  @Transactional(readOnly = true)
  public QrView qr(String userId, String ticketId) {
    Ticket t = owned(userId, ticketId);
    if (t.getStatus() != Ticket.Status.ATIVO) throw ApiException.conflict("Ingresso não está ativo");
    long window = QrSigner.window(System.currentTimeMillis());
    return new QrView(signer.sign(t, window), window);
  }

  private Ticket owned(String userId, String ticketId) {
    Ticket t = tickets.findById(ticketId).orElseThrow(() -> ApiException.notFound("Ingresso"));
    // Doc 07: só o titular do pedido vê o ingresso.
    if (!t.getUserId().equals(userId)) throw ApiException.forbidden("Ingresso não pertence a você");
    return t;
  }

  private TicketView view(Ticket t) {
    return new TicketView(
        t.getId(), t.getOrderId(), catalog.summary(catalog.find(t.getEventId())), t.getSeatLabel(),
        t.getSectorName(), t.getHolderName(), t.getHolderEmail(), t.getGate(), t.getStatus(), t.getIssuedAt());
  }
}
