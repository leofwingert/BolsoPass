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

  /**
   * Carteira: ingressos que a pessoa comprou + ingressos em que ela é a titular pelo CPF
   * (venda no balcão, ou um amigo que comprou para ela). O CPF vinculado é o que vale na entrada.
   */
  @Transactional(readOnly = true)
  public List<TicketView> mine(String userId) {
    Map<String, Ticket> all = new java.util.LinkedHashMap<>();
    tickets.findByUserIdOrderByIssuedAtDesc(userId).forEach(t -> all.put(t.getId(), t));
    String cpf = holderCpf(userId);
    if (cpf != null) tickets.findByHolderCpfOrderByIssuedAtDesc(cpf).forEach(t -> all.putIfAbsent(t.getId(), t));
    return all.values().stream()
        .sorted(java.util.Comparator.comparing(Ticket::getIssuedAt).reversed())
        .map(this::view)
        .toList();
  }

  /** CPF da conta, só dígitos (como fica gravado no ingresso). */
  private String holderCpf(String userId) {
    return users.findById(userId)
        .map(u -> u.getCpf())
        .filter(c -> c != null && !c.isBlank())
        .map(c -> c.replaceAll("\\D", ""))
        .orElse(null);
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

  /**
   * Dados para o cadastro facial (facial-checkin-service). {@code holder} = a conta é a titular
   * (CPF da conta = CPF do ingresso): pela LGPD, só a própria pessoa consente com o uso do rosto.
   */
  public record BiometricEligibility(
      String ticketId, String eventId, String eventTitle, Instant eventStartsAt, String holderName, boolean holder,
      Ticket.Status status) {}

  @Transactional(readOnly = true)
  public BiometricEligibility eligibility(String userId, String ticketId) {
    Ticket t = owned(userId, ticketId);
    String cpf = holderCpf(userId);
    var event = catalog.find(t.getEventId());
    return new BiometricEligibility(t.getId(), t.getEventId(), event.getTitle(), event.getStartsAt(),
        t.getHolderName(), cpf != null && cpf.equals(t.getHolderCpf()), t.getStatus());
  }

  private Ticket owned(String userId, String ticketId) {
    Ticket t = tickets.findById(ticketId).orElseThrow(() -> ApiException.notFound("Ingresso"));
    // Doc 07: só quem comprou ou a pessoa titular (CPF vinculado ao ingresso) vê o ingresso.
    boolean buyer = t.getUserId().equals(userId);
    boolean holder = t.getHolderCpf() != null && t.getHolderCpf().equals(holderCpf(userId));
    if (!buyer && !holder) throw ApiException.forbidden("Ingresso não pertence a você");
    return t;
  }

  private TicketView view(Ticket t) {
    return new TicketView(
        t.getId(), t.getOrderId(), catalog.summary(catalog.find(t.getEventId())), t.getSeatLabel(),
        t.getSectorName(), t.getHolderName(), t.getHolderEmail(), t.getGate(), t.getStatus(), t.getIssuedAt());
  }
}
