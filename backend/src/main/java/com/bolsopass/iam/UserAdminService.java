package com.bolsopass.iam;

import com.bolsopass.audit.AuditLog;
import com.bolsopass.audit.AuditService;
import com.bolsopass.orders.Cpf;
import com.bolsopass.shared.ApiException;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Gestão de usuários pela administração (RF-02, RF-03).
 *
 * <ul>
 *   <li>Cada conta tem <b>um perfil só</b>: promover troca o perfil (quem vira equipe deixa de
 *       comprar como cliente; os ingressos já comprados continuam na conta).
 *   <li>Bilheteria e Venda recebem matrícula + PIN gerados aqui; o PIN só aparece uma vez.
 *   <li>O admin não muda o próprio perfil e o sistema nunca fica sem admin.
 *   <li>Toda criação e troca de perfil vai para o registro de auditoria.
 * </ul>
 */
@Service
public class UserAdminService {
  private static final ZoneId ZONE = ZoneId.of("America/Sao_Paulo");

  /** Perfis que a administração atribui (um por conta). */
  public enum Profile {
    CLIENT("Cliente", EnumSet.of(Role.ROLE_CLIENT), null),
    ADMIN("Admin", EnumSet.of(Role.ROLE_ORGANIZER, Role.ROLE_ADMIN), null),
    OPERATOR("Bilheteria", EnumSet.of(Role.ROLE_OPERATOR), "OP"),
    SELLER("Venda", EnumSet.of(Role.ROLE_SELLER), "VD");

    final String label;
    final Set<Role> roles;
    final String badgePrefix;

    Profile(String label, Set<Role> roles, String badgePrefix) {
      this.label = label;
      this.roles = roles;
      this.badgePrefix = badgePrefix;
    }

    boolean staff() {
      return badgePrefix != null;
    }

    static Profile of(User u) {
      Set<Role> r = u.getRoles();
      if (r.contains(Role.ROLE_ADMIN) || r.contains(Role.ROLE_ORGANIZER)) return ADMIN;
      if (r.contains(Role.ROLE_OPERATOR)) return OPERATOR;
      if (r.contains(Role.ROLE_SELLER)) return SELLER;
      return CLIENT;
    }
  }

  public record UserView(
      String id, String name, String email, String cpf, String phone, Profile profile, String profileLabel,
      String badge, Instant createdAt, boolean counterAccount) {}

  public record UserPage(List<UserView> items, int total, int page, int size) {}

  public record NewUser(String name, String email, String cpf, String phone, String password, Profile profile) {}

  /** Matrícula e PIN gerados: mostrados ao admin uma única vez. */
  public record Credentials(String badge, String pin) {}

  public record UserResult(UserView user, Credentials credentials) {}

  private final UserRepository users;
  private final RefreshTokenRepository refreshTokens;
  private final PasswordEncoder encoder;
  private final AuditService audit;
  private final SecureRandom random = new SecureRandom();

  public UserAdminService(
      UserRepository users, RefreshTokenRepository refreshTokens, PasswordEncoder encoder, AuditService audit) {
    this.users = users;
    this.refreshTokens = refreshTokens;
    this.encoder = encoder;
    this.audit = audit;
  }

  /** Lista com filtros: busca (nome, e-mail, CPF ou matrícula), perfil e período de cadastro. */
  @Transactional(readOnly = true)
  public UserPage list(String q, Profile profile, LocalDate from, LocalDate to, int page, int size) {
    String term = q == null ? "" : q.trim().toLowerCase(Locale.ROOT);
    String digits = Cpf.digits(term);
    Instant start = from == null ? null : from.atStartOfDay(ZONE).toInstant();
    Instant end = to == null ? null : to.plusDays(1).atStartOfDay(ZONE).toInstant();
    List<User> all =
        users.findAll().stream()
            .filter(u -> profile == null || Profile.of(u) == profile)
            .filter(u -> term.isEmpty() || matches(u, term, digits))
            .filter(u -> start == null || (u.getCreatedAt() != null && !u.getCreatedAt().isBefore(start)))
            .filter(u -> end == null || (u.getCreatedAt() != null && u.getCreatedAt().isBefore(end)))
            .sorted(Comparator.comparing(User::getCreatedAt, Comparator.nullsFirst(Comparator.naturalOrder()))
                .reversed()
                .thenComparing(User::getName))
            .toList();
    int safeSize = Math.min(Math.max(size, 1), 100);
    int safePage = Math.max(page, 0);
    List<UserView> items =
        all.stream().skip((long) safePage * safeSize).limit(safeSize).map(UserAdminService::view).toList();
    return new UserPage(items, all.size(), safePage, safeSize);
  }

  private static boolean matches(User u, String term, String digits) {
    if (u.getName().toLowerCase(Locale.ROOT).contains(term)) return true;
    if (u.getEmail().contains(term)) return true;
    if (u.getBadge() != null && u.getBadge().toLowerCase(Locale.ROOT).contains(term)) return true;
    return digits.length() >= 3 && u.getCpf() != null && Cpf.digits(u.getCpf()).contains(digits);
  }

  /** Cadastro pela administração, já com o perfil escolhido. */
  @Transactional
  public UserResult create(NewUser in, String actorId) {
    Profile profile = in.profile() == null ? Profile.CLIENT : in.profile();
    String name = AccountRules.fullName(in.name());
    String email = AccountRules.email(in.email());
    if (users.findByEmail(email).isPresent()) throw ApiException.conflict("E-mail já cadastrado");

    String cpf = null;
    if (in.cpf() != null && !in.cpf().isBlank()) {
      if (!Cpf.isValid(in.cpf())) throw ApiException.badRequest("CPF inválido");
      cpf = Cpf.formatted(Cpf.digits(in.cpf()));
      if (users.findByCpf(cpf).isPresent()) throw ApiException.conflict("CPF já cadastrado");
    } else if (profile == Profile.CLIENT) {
      throw ApiException.badRequest("CPF é obrigatório para clientes");
    }
    String phone = in.phone() == null || in.phone().isBlank() ? null : in.phone().trim();

    // Cliente e admin entram com e-mail e senha; bilheteria e venda, com matrícula e PIN.
    String passwordHash = encoder.encode(profile.staff() ? UUID.randomUUID().toString() : AccountRules.password(in.password()));

    User u = new User("u-" + UUID.randomUUID(), name, email, passwordHash, profile.roles).withContact(cpf, phone);
    Credentials creds = null;
    if (profile.staff()) {
      creds = newCredentials(profile);
      u.withBadge(creds.badge()).withPin(encoder.encode(creds.pin()));
    }
    users.save(u);
    audit.log(null, AuditLog.Kind.SEGURANCA, "Usuário cadastrado",
        name + " (" + email + ") cadastrado como " + profile.label
            + (creds == null ? "" : ", matrícula " + creds.badge()) + ".",
        actor(actorId));
    return new UserResult(view(u), creds);
  }

  /** Promove ou rebaixa: troca o perfil da conta (um perfil por conta). */
  @Transactional
  public UserResult changeProfile(String userId, Profile next, String actorId) {
    if (next == null) throw ApiException.badRequest("Escolha o novo perfil");
    User u = users.findById(userId).orElseThrow(() -> ApiException.notFound("Usuário"));
    if (u.getId().equals(actorId)) throw ApiException.badRequest("Você não pode alterar o próprio perfil");
    Profile current = Profile.of(u);
    if (current == next) throw ApiException.badRequest("O usuário já tem esse perfil");
    if (current == Profile.ADMIN && countAdmins() <= 1) {
      throw ApiException.conflict("O sistema precisa de pelo menos um admin");
    }

    Credentials creds = next.staff() ? newCredentials(next) : null;
    u.changeRoles(next.roles, creds == null ? null : creds.badge(), creds == null ? null : encoder.encode(creds.pin()));
    // Sessões abertas caem: o novo perfil vale no próximo login.
    refreshTokens.revokeAllForUser(u.getId());
    audit.log(null, AuditLog.Kind.SEGURANCA, "Perfil alterado",
        u.getName() + " (" + u.getEmail() + "): " + current.label + " → " + next.label
            + (creds == null ? "" : ", matrícula " + creds.badge()) + ".",
        actor(actorId));
    return new UserResult(view(u), creds);
  }

  private long countAdmins() {
    return users.findAll().stream().filter(x -> Profile.of(x) == Profile.ADMIN).count();
  }

  /** Matrícula única com o prefixo do perfil (OP-12345 / VD-12345) e PIN de 6 dígitos. */
  private Credentials newCredentials(Profile profile) {
    String badge;
    do {
      badge = profile.badgePrefix + "-" + (10000 + random.nextInt(90000));
    } while (users.findByBadge(badge).isPresent());
    return new Credentials(badge, String.format("%06d", random.nextInt(1_000_000)));
  }

  private String actor(String actorId) {
    return users.findById(actorId).map(User::getEmail).orElse(actorId);
  }

  static UserView view(User u) {
    Profile p = Profile.of(u);
    return new UserView(u.getId(), u.getName(), u.getEmail(), Cpf.mask(u.getCpf()), u.getPhone(), p,
        p.label, u.getBadge(), u.getCreatedAt(), u.isCounterAccount());
  }
}
