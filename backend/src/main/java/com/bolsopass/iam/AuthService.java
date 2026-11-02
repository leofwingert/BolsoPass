package com.bolsopass.iam;

import com.bolsopass.audit.AuditLog;
import com.bolsopass.audit.AuditService;
import com.bolsopass.orders.Cpf;
import com.bolsopass.shared.ApiException;
import com.bolsopass.shared.AppProperties;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {
  /** RF-03: tentativas erradas seguidas bloqueiam o login por um tempo. */
  private static final int MAX_FAILURES = 5;

  private static final Duration LOCKOUT = Duration.ofMinutes(15);

  private final UserRepository users;
  private final RefreshTokenRepository refreshTokens;
  private final PasswordEncoder passwordEncoder;
  private final JwtEncoder jwtEncoder;
  private final AppProperties props;
  private final AuditService audit;
  private final SecureRandom random = new SecureRandom();
  private final Map<String, Failures> failures = new ConcurrentHashMap<>();

  private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
  static final int MIN_PASSWORD = 8;

  private record Failures(int count, Instant lockedUntil) {}

  /** Cadastro público: sempre cria um Cliente (não existe campo de perfil). */
  public record RegisterRequest(String name, String email, String cpf, String phone, String password, boolean acceptTerms) {}

  public record SessionDto(UserDto user, String accessToken, Instant expiresAt, String refreshToken) {}

  public record UserDto(
      String id, String name, String email, List<Role> roles, String cpf, String phone, String badge) {
    static UserDto of(User u) {
      return new UserDto(
          u.getId(),
          u.getName(),
          u.getEmail(),
          u.getRoles().stream().sorted().toList(),
          u.getCpf(),
          u.getPhone(),
          u.getBadge());
    }
  }

  public AuthService(
      UserRepository users,
      RefreshTokenRepository refreshTokens,
      PasswordEncoder passwordEncoder,
      JwtEncoder jwtEncoder,
      AppProperties props,
      AuditService audit) {
    this.users = users;
    this.refreshTokens = refreshTokens;
    this.passwordEncoder = passwordEncoder;
    this.jwtEncoder = jwtEncoder;
    this.props = props;
    this.audit = audit;
  }

  @Transactional
  public SessionDto login(String email, String password) {
    String key = "email:" + email.trim().toLowerCase();
    ensureNotLocked(key);
    User user =
        users
            .findByEmail(email.trim().toLowerCase())
            .filter(u -> passwordEncoder.matches(password, u.getPasswordHash()))
            .orElse(null);
    if (user == null) {
      registerFailure(key);
      throw new ApiException(HttpStatus.UNAUTHORIZED, "E-mail ou senha incorretos");
    }
    failures.remove(key);
    return issue(user);
  }

  @Transactional
  public SessionDto loginOperator(String badge, String pin) {
    String key = "badge:" + badge.trim().toUpperCase();
    ensureNotLocked(key);
    User user =
        users
            .findByBadge(badge.trim().toUpperCase())
            .or(() -> users.findByEmail(badge.trim().toLowerCase()))
            .filter(u -> u.getRoles().contains(Role.ROLE_OPERATOR) || u.getRoles().contains(Role.ROLE_SELLER))
            .filter(u -> passwordEncoder.matches(pin, pinOf(u)))
            .orElse(null);
    if (user == null) {
      registerFailure(key);
      throw new ApiException(HttpStatus.UNAUTHORIZED, "Credencial ou PIN inválidos");
    }
    failures.remove(key);
    return issue(user);
  }

  /**
   * Cadastro público (RF-03). Sempre cria {@code ROLE_CLIENT}: os perfis de equipe só são dados
   * pela administração. Se o CPF é de uma conta criada no balcão, o dono assume essa conta e passa
   * a ver os ingressos já comprados lá.
   */
  @Transactional
  public SessionDto register(RegisterRequest in) {
    String name = in.name() == null ? "" : in.name().trim().replaceAll("\\s+", " ");
    String email = in.email() == null ? "" : in.email().trim().toLowerCase();
    String phone = in.phone() == null ? "" : in.phone().trim();
    if (name.split(" ").length < 2) throw ApiException.badRequest("Informe nome e sobrenome");
    if (!EMAIL.matcher(email).matches()) throw ApiException.badRequest("E-mail inválido");
    if (!Cpf.isValid(in.cpf())) throw ApiException.badRequest("CPF inválido");
    if (phone.replaceAll("\\D", "").length() < 10) throw ApiException.badRequest("Informe um telefone com DDD");
    if (in.password() == null || in.password().length() < MIN_PASSWORD) {
      throw ApiException.badRequest("A senha precisa ter pelo menos " + MIN_PASSWORD + " caracteres");
    }
    if (!in.acceptTerms()) throw ApiException.badRequest("Aceite os termos de uso para criar a conta");

    String cpf = Cpf.formatted(Cpf.digits(in.cpf()));
    User owner = users.findByCpfIn(List.of(Cpf.digits(in.cpf()), cpf)).stream().findFirst().orElse(null);
    users.findByEmail(email)
        .filter(u -> owner == null || !u.getId().equals(owner.getId()))
        .ifPresent(u -> { throw ApiException.conflict("E-mail já cadastrado"); });

    User user;
    if (owner != null) {
      if (!owner.isCounterAccount()) throw ApiException.conflict("CPF já cadastrado. Entre com seu e-mail e senha.");
      owner.claim(name, email, passwordEncoder.encode(in.password()), phone);
      user = owner;
      audit.log(null, AuditLog.Kind.SEGURANCA, "Conta do balcão assumida",
          "O dono do CPF " + maskCpf(cpf) + " assumiu a conta criada na bilheteria (" + email + ").", email);
    } else {
      user = users.save(new User("u-" + UUID.randomUUID(), name, email, passwordEncoder.encode(in.password()),
              Set.of(Role.ROLE_CLIENT))
          .withContact(cpf, phone));
      audit.log(null, AuditLog.Kind.SEGURANCA, "Conta criada", "Cadastro público de cliente (" + email + ").", email);
    }
    return issue(user);
  }

  /** PIN da bilheteria. Contas antigas (antes do campo próprio) guardavam o PIN como senha. */
  private static String pinOf(User u) {
    return u.getPinHash() != null ? u.getPinHash() : u.getPasswordHash();
  }

  /** CPF parcial (LGPD): ***.982.247-** */
  public static String maskCpf(String cpf) {
    if (cpf == null) return null;
    String d = Cpf.digits(cpf);
    return d.length() != 11 ? cpf : "***." + d.substring(3, 6) + "." + d.substring(6, 9) + "-**";
  }

  /**
   * Rotação do refresh token. Reuso de um token já rotacionado derruba todas as sessões.
   * noRollbackFor: a revogação precisa ser gravada mesmo respondendo 401 (senão o erro desfaz).
   */
  @Transactional(noRollbackFor = ApiException.class)
  public SessionDto refresh(String refreshToken) {
    RefreshToken stored =
        refreshTokens
            .findById(hash(refreshToken))
            .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Sessão inválida"));
    if (stored.isRevoked()) {
      refreshTokens.revokeAllForUser(stored.getUserId());
      throw new ApiException(HttpStatus.UNAUTHORIZED, "Sessão encerrada por segurança");
    }
    if (stored.isExpired(Instant.now())) {
      throw new ApiException(HttpStatus.UNAUTHORIZED, "Sessão expirada");
    }
    stored.revoke();
    User user = users.findById(stored.getUserId()).orElseThrow(() -> ApiException.notFound("Usuário"));
    return issue(user);
  }

  @Transactional
  public void logout(String refreshToken) {
    if (refreshToken != null) refreshTokens.findById(hash(refreshToken)).ifPresent(RefreshToken::revoke);
  }

  private SessionDto issue(User user) {
    Instant now = Instant.now();
    Instant exp = now.plus(props.jwt().accessTtl());
    JwtClaimsSet claims =
        JwtClaimsSet.builder()
            .issuer("bolsopass")
            .subject(user.getId())
            .issuedAt(now)
            .expiresAt(exp)
            .claim("name", user.getName())
            .claim("email", user.getEmail())
            .claim("roles", user.getRoles().stream().map(Enum::name).sorted().toList())
            .build();
    String access =
        jwtEncoder
            .encode(JwtEncoderParameters.from(JwsHeader.with(SignatureAlgorithm.RS256).build(), claims))
            .getTokenValue();

    byte[] bytes = new byte[32];
    random.nextBytes(bytes);
    String refresh = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    refreshTokens.save(new RefreshToken(hash(refresh), user.getId(), now.plus(props.jwt().refreshTtl())));
    return new SessionDto(UserDto.of(user), access, exp, refresh);
  }

  private void ensureNotLocked(String key) {
    Failures f = failures.get(key);
    if (f != null && f.lockedUntil() != null && Instant.now().isBefore(f.lockedUntil())) {
      throw new ApiException(
          HttpStatus.TOO_MANY_REQUESTS, "Muitas tentativas. Tente novamente em alguns minutos.");
    }
  }

  private void registerFailure(String key) {
    failures.compute(
        key,
        (k, f) -> {
          int count = (f == null || f.lockedUntil() != null ? 0 : f.count()) + 1;
          return new Failures(count, count >= MAX_FAILURES ? Instant.now().plus(LOCKOUT) : null);
        });
  }

  private static String hash(String token) {
    try {
      byte[] digest =
          MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(digest);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }
}
