package com.bolsopass.iam;

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
  private final SecureRandom random = new SecureRandom();
  private final Map<String, Failures> failures = new ConcurrentHashMap<>();

  private record Failures(int count, Instant lockedUntil) {}

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
      AppProperties props) {
    this.users = users;
    this.refreshTokens = refreshTokens;
    this.passwordEncoder = passwordEncoder;
    this.jwtEncoder = jwtEncoder;
    this.props = props;
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
            .filter(u -> u.getRoles().contains(Role.ROLE_OPERATOR))
            .filter(u -> passwordEncoder.matches(pin, u.getPasswordHash()))
            .orElse(null);
    if (user == null) {
      registerFailure(key);
      throw new ApiException(HttpStatus.UNAUTHORIZED, "Credencial ou PIN inválidos");
    }
    failures.remove(key);
    return issue(user);
  }

  /** Rotação do refresh token. Reuso de um token já rotacionado derruba todas as sessões. */
  @Transactional
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
