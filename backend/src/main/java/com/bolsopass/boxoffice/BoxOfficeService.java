package com.bolsopass.boxoffice;

import com.bolsopass.audit.AuditLog;
import com.bolsopass.audit.AuditService;
import com.bolsopass.catalog.CatalogService;
import com.bolsopass.iam.AccountRules;
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
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
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
  public record Customer(
      String id, String name, String phone, String cpf, String email, boolean newCustomer,
      String activationCode, boolean pendingActivation) {}

  public record NewCustomer(String cpf, String name, String phone, String email) {}

  private final UserRepository users;
  private final ReservationService reservations;
  private final CheckoutService checkout;
  private final CatalogService catalog;
  private final AuditService audit;
  private final PasswordEncoder encoder;
  private final SecureRandom random = new SecureRandom();

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

  /** Código de ativação: entregue no balcão, vale 24 h e só ele permite assumir a conta no site. */
  static final Duration ACTIVATION_TTL = Duration.ofHours(24);

  /**
   * {@code activationCode} só vem preenchido logo após gerá-lo (o banco guarda apenas o hash).
   * {@code pendingActivation}: a conta ainda não foi assumida pelo dono no site.
   */
  private static Customer view(User u, boolean created, String activationCode) {
    return new Customer(
        u.getId(), u.getName(), u.getPhone(), Cpf.digits(u.getCpf()), maskEmail(u.getEmail()), created,
        activationCode, u.isCounterAccount());
  }

  /** Gera um código novo de 6 dígitos para a conta do balcão (o anterior deixa de valer). */
  private String newActivationCode(User u) {
    String code = "%06d".formatted(random.nextInt(1_000_000));
    u.markCounterAccount(encoder.encode(code), Instant.now().plus(ACTIVATION_TTL));
    return code;
  }

  /** E-mail parcial no balcão (LGPD): an***@exemplo.com */
  static String maskEmail(String email) {
    if (email == null || !email.contains("@")) return null;
    String[] p = email.split("@", 2);
    return p[0].substring(0, Math.min(2, p[0].length())) + "***@" + p[1];
  }

  /** Conta do CPF (cliente ou equipe). CPF inválido = 400. */
  private User byCpf(String cpf) {
    if (!Cpf.isValid(cpf)) throw ApiException.badRequest("CPF inválido");
    return users.findByCpf(cpf).orElse(null);
  }

  /** Busca o cliente pelo CPF e devolve nome e telefone para confirmar com a pessoa no balcão. */
  @Transactional(readOnly = true)
  public Customer find(String cpf) {
    User u = byCpf(cpf);
    if (u == null || !u.getRoles().contains(Role.ROLE_CLIENT)) throw ApiException.notFound("Cliente com este CPF");
    return view(u, false, null);
  }

  /** Cadastro rápido de quem ainda não tem conta (o ingresso fica na carteira desse CPF). */
  @Transactional
  public Customer register(NewCustomer in) {
    User existing = byCpf(in.cpf());
    if (existing != null) {
      // Um CPF, uma conta: CPF de alguém da equipe (conta promovida) não vira um segundo cadastro.
      throw ApiException.conflict(existing.getRoles().contains(Role.ROLE_CLIENT)
          ? "Já existe cliente com este CPF"
          : "Este CPF pertence a uma conta da equipe e não pode comprar como cliente");
    }
    String name = AccountRules.fullName(in.name());
    String phone = AccountRules.phone(in.phone());
    String digits = Cpf.digits(in.cpf());
    String email = in.email() != null && !in.email().isBlank()
        ? AccountRules.email(in.email())
        : "cpf" + digits + User.COUNTER_EMAIL_DOMAIN;
    if (users.findByEmail(email).isPresent()) throw ApiException.conflict("E-mail já cadastrado");
    User u = new User("u-" + UUID.randomUUID(), name, email, encoder.encode(UUID.randomUUID().toString()),
            Set.of(Role.ROLE_CLIENT))
        .withContact(Cpf.formatted(digits), phone);
    String code = newActivationCode(u);
    users.save(u);
    return view(u, true, code);
  }

  /**
   * Novo código de ativação para quem ainda não assumiu a conta (código vencido, perdido ou conta
   * criada antes de existir o código). O anterior deixa de valer.
   */
  @Transactional
  public Customer issueActivationCode(String customerId) {
    User u = customer(customerId);
    if (!u.isCounterAccount()) throw ApiException.conflict("Esta conta já foi ativada pelo cliente");
    String code = newActivationCode(u);
    audit.log(null, AuditLog.Kind.SEGURANCA, "Código de ativação gerado",
        "Novo código para a conta do CPF " + Cpf.mask(u.getCpf()) + ", gerado na bilheteria.", operatorBadge());
    return view(u, false, code);
  }

  private String operatorBadge() {
    return users.findById(CurrentUser.id()).map(u -> u.getBadge() != null ? u.getBadge() : u.getEmail()).orElse(CurrentUser.id());
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
          operatorBadge());
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
