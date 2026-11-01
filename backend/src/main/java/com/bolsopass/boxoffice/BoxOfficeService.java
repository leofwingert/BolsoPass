package com.bolsopass.boxoffice;

import com.bolsopass.audit.AuditLog;
import com.bolsopass.audit.AuditService;
import com.bolsopass.catalog.CatalogService;
import com.bolsopass.iam.CurrentUser;
import com.bolsopass.iam.Role;
import com.bolsopass.iam.User;
import com.bolsopass.iam.UserRepository;
import com.bolsopass.inventory.ReservationService;
import com.bolsopass.orders.CheckoutService;
import com.bolsopass.orders.Cpf;
import com.bolsopass.orders.OrderViews.OrderView;
import com.bolsopass.orders.OrderViews.ReservationView;
import com.bolsopass.shared.ApiException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Venda no balcão da bilheteria. O operador compra em nome do cliente (identificado pelo CPF)
 * usando exatamente o mesmo fluxo do site: trava atômica, reserva de 10 min, limite por CPF,
 * pagamento idempotente, outbox e ingresso com QR na carteira do cliente.
 */
@Service
public class BoxOfficeService {
  /** Dados do cliente que o operador vê no balcão. */
  public record Customer(String id, String name, String phone, String cpf, String email, boolean newCustomer) {}

  public record NewCustomer(String cpf, String name, String phone, String email) {}

  private final UserRepository users;
  private final ReservationService reservations;
  private final CheckoutService checkout;
  private final CatalogService catalog;
  private final AuditService audit;
  private final PasswordEncoder encoder;

  public BoxOfficeService(
      UserRepository users, ReservationService reservations, CheckoutService checkout,
      CatalogService catalog, AuditService audit, PasswordEncoder encoder) {
    this.users = users;
    this.reservations = reservations;
    this.checkout = checkout;
    this.catalog = catalog;
    this.audit = audit;
    this.encoder = encoder;
  }

  private static String formatted(String digits) {
    return digits.substring(0, 3) + "." + digits.substring(3, 6) + "." + digits.substring(6, 9) + "-" + digits.substring(9);
  }

  private static Customer view(User u, boolean created) {
    return new Customer(u.getId(), u.getName(), u.getPhone(), Cpf.digits(u.getCpf()), maskEmail(u.getEmail()), created);
  }

  /** E-mail parcial no balcão (LGPD): an***@exemplo.com */
  static String maskEmail(String email) {
    if (email == null || !email.contains("@")) return null;
    String[] p = email.split("@", 2);
    return p[0].substring(0, Math.min(2, p[0].length())) + "***@" + p[1];
  }

  private User byCpf(String cpf) {
    if (!Cpf.isValid(cpf)) throw ApiException.badRequest("CPF inválido");
    String digits = Cpf.digits(cpf);
    return users.findByCpfIn(List.of(digits, formatted(digits))).stream()
        .filter(u -> u.getRoles().contains(Role.ROLE_CLIENT))
        .findFirst()
        .orElse(null);
  }

  /** Busca o cliente pelo CPF e devolve nome e telefone para confirmar com a pessoa no balcão. */
  @Transactional(readOnly = true)
  public Customer find(String cpf) {
    User u = byCpf(cpf);
    if (u == null) throw ApiException.notFound("Cliente com este CPF");
    return view(u, false);
  }

  /** Cadastro rápido de quem ainda não tem conta (o ingresso fica na carteira desse CPF). */
  @Transactional
  public Customer register(NewCustomer in) {
    if (byCpf(in.cpf()) != null) throw ApiException.conflict("Já existe cliente com este CPF");
    if (in.name() == null || in.name().trim().split("\\s+").length < 2) {
      throw ApiException.badRequest("Informe nome e sobrenome");
    }
    if (in.phone() == null || in.phone().replaceAll("\\D", "").length() < 10) {
      throw ApiException.badRequest("Informe um telefone com DDD");
    }
    String digits = Cpf.digits(in.cpf());
    String email = in.email() != null && in.email().contains("@")
        ? in.email().trim().toLowerCase()
        : "cpf" + digits + "@balcao.bolsopass.io";
    if (users.findByEmail(email).isPresent()) throw ApiException.conflict("E-mail já cadastrado");
    User u = users.save(new User("u-" + UUID.randomUUID(), in.name().trim(), email,
            encoder.encode(UUID.randomUUID().toString()), Set.of(Role.ROLE_CLIENT))
        .withContact(formatted(digits), in.phone().trim()));
    return view(u, true);
  }

  private User customer(String customerId) {
    User u = users.findById(customerId).orElseThrow(() -> ApiException.notFound("Cliente"));
    if (!u.getRoles().contains(Role.ROLE_CLIENT)) throw ApiException.badRequest("A venda é feita para um cliente");
    return u;
  }

  /** Trava os assentos escolhidos em nome do cliente (sem sala de espera: ele está no balcão). */
  @Transactional
  public Map<String, Object> reserve(String eventId, String customerId, List<String> seatIds) {
    customer(customerId);
    ReservationView r = reservations.reserve(customerId, eventId, seatIds, true);
    var full = checkout.reservation(customerId, r.id());
    return Map.of("reservation", full.getKey(), "order", full.getValue());
  }

  /** Cobra no cartão ou PIX. Mesma Idempotency-Key = mesmo pedido, sem cobrar duas vezes. */
  public OrderView pay(String customerId, CheckoutService.PayRequest req, String idempotencyKey) {
    customer(customerId);
    OrderView order = checkout.pay(customerId, req, idempotencyKey);
    if ("PAGO".equals(String.valueOf(order.status()))) {
      audit.log(order.event().id(), AuditLog.Kind.BILHETERIA, "Venda no balcão",
          "Pedido " + order.code() + " (" + order.items().size() + " ingresso(s)) vendido na bilheteria por "
              + (req.method() == null ? "" : req.method().name()) + ".",
          users.findById(CurrentUser.id()).map(u -> u.getBadge() != null ? u.getBadge() : u.getEmail()).orElse(CurrentUser.id()));
    }
    return order;
  }

  /** Desistência após pagamento recusado: assentos voltam à venda. */
  public void giveUp(String customerId, String reservationId) {
    customer(customerId);
    checkout.giveUp(customerId, reservationId);
  }

  @Transactional(readOnly = true)
  public CatalogService.SeatMapView seatMap(String eventId) {
    return catalog.seatMap(eventId);
  }
}
