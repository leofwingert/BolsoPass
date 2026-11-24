package com.bolsopass.iam.service;

import com.bolsopass.iam.domain.AccountRateLimitedException;
import com.bolsopass.iam.domain.AuditEvent;
import com.bolsopass.iam.domain.InvalidCredentialsException;
import com.bolsopass.iam.domain.RefreshTokenFamily;
import com.bolsopass.iam.domain.Role;
import com.bolsopass.iam.domain.UserAccount;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
public class IamService {

    public static final long ACCESS_TOKEN_TTL_SECONDS = 900L; // 15 minutes (US-01 / RF-01)

    private final KeyPair rsaKeyPair;
    private final Map<String, UserAccount> usersByEmail = new ConcurrentHashMap<>();
    private final Map<UUID, RefreshTokenFamily> familiesById = new ConcurrentHashMap<>();
    private final Map<String, UUID> tokenToFamilyId = new ConcurrentHashMap<>();
    private final List<AuditEvent> auditTrail = new CopyOnWriteArrayList<>();
    private Clock clock = Clock.systemUTC();

    public record TokenPairResponse(
            String accessToken,
            String refreshToken,
            long expiresIn,
            List<String> roles
    ) {
    }

    public IamService() {
        try {
            KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
            kpg.initialize(2048);
            this.rsaKeyPair = kpg.generateKeyPair();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to initialize RSA key pair for JWT signing", e);
        }
        seedDefaultPersonas();
    }

    private void seedDefaultPersonas() {
        registerUser(UUID.fromString("11111111-1111-1111-1111-111111111111"),
                "gabriel@bolsopass.com", "123.456.789-00", "Gabriel S Silva",
                " Pass123!".trim(), EnumSet.of(Role.ROLE_CLIENT));
        registerUser(UUID.fromString("22222222-2222-2222-2222-222222222222"),
                "beatriz@bolsopass.com", "234.567.890-11", "Beatriz Organizadora",
                "Org123!", EnumSet.of(Role.ROLE_ORGANIZER));
        registerUser(UUID.fromString("33333333-3333-3333-3333-333333333333"),
                "marcos@bolsopass.com", "345.678.901-22", "Marcos Operador",
                "Op123!", EnumSet.of(Role.ROLE_OPERATOR));
        registerUser(UUID.fromString("44444444-4444-4444-4444-444444444444"),
                "ana@bolsopass.com", "456.789.012-33", "Ana SRE Admin",
                "Admin123!", EnumSet.of(Role.ROLE_ADMIN));
    }

    public void setClock(Clock clock) {
        this.clock = clock;
    }

    public UserAccount registerUser(UUID id, String email, String cpf, String fullName, String rawPassword, Set<Role> roles) {
        UserAccount account = new UserAccount(id, email, cpf, fullName, hashPassword(rawPassword), roles);
        usersByEmail.put(account.getEmail(), account);
        return account;
    }

    public TokenPairResponse login(String email, String rawPassword) {
        Instant now = clock.instant();
        UserAccount user = usersByEmail.get(email == null ? "" : email.trim().toLowerCase());
        if (user == null) {
            recordAudit(null, "AUTH_LOGIN_FAILED", "auth/login", "Unknown email: " + email);
            throw new InvalidCredentialsException("Invalid email or password");
        }
        try {
            user.authenticate(hashPassword(rawPassword), now);
        } catch (AccountRateLimitedException ex) {
            recordAudit(user.getId(), "AUTH_RATE_LIMITED", "auth/login", ex.getMessage());
            throw ex;
        } catch (InvalidCredentialsException ex) {
            recordAudit(user.getId(), "AUTH_LOGIN_FAILED", "auth/login", ex.getMessage());
            throw ex;
        }

        String accessToken = issueAccessToken(user, now);
        String refreshToken = "rt_" + UUID.randomUUID();
        UUID familyId = UUID.randomUUID();
        RefreshTokenFamily family = new RefreshTokenFamily(familyId, user.getId(), refreshToken, now);
        familiesById.put(familyId, family);
        tokenToFamilyId.put(refreshToken, familyId);

        recordAudit(user.getId(), "AUTH_LOGIN_SUCCESS", "auth/login", "Issued JWT and RefreshToken family " + familyId);
        List<String> roleNames = user.getRoles().stream().map(Enum::name).sorted().toList();
        return new TokenPairResponse(accessToken, refreshToken, ACCESS_TOKEN_TTL_SECONDS, roleNames);
    }

    public TokenPairResponse refresh(String presentedRefreshToken) {
        Instant now = clock.instant();
        UUID familyId = tokenToFamilyId.get(presentedRefreshToken == null ? "" : presentedRefreshToken);
        if (familyId == null) {
            throw new InvalidCredentialsException("Unknown refresh token");
        }
        RefreshTokenFamily family = familiesById.get(familyId);
        String nextRefreshToken = "rt_" + UUID.randomUUID();
        family.rotate(presentedRefreshToken, nextRefreshToken, now);
        tokenToFamilyId.put(nextRefreshToken, familyId);

        UserAccount user = usersByEmail.values().stream()
                .filter(u -> u.getId().equals(family.getUserId()))
                .findFirst()
                .orElseThrow(() -> new InvalidCredentialsException("User no longer active"));

        String accessToken = issueAccessToken(user, now);
        recordAudit(user.getId(), "AUTH_TOKEN_ROTATED", "auth/refresh", "Rotated refresh token in family " + familyId);
        List<String> roleNames = user.getRoles().stream().map(Enum::name).sorted().toList();
        return new TokenPairResponse(accessToken, nextRefreshToken, ACCESS_TOKEN_TTL_SECONDS, roleNames);
    }

    public void logout(String refreshToken) {
        UUID familyId = tokenToFamilyId.get(refreshToken);
        if (familyId != null) {
            RefreshTokenFamily family = familiesById.get(familyId);
            if (family != null) {
                family.revokeAll();
                recordAudit(family.getUserId(), "AUTH_LOGOUT", "auth/logout", "Revoked refresh token family " + familyId);
            }
        }
    }

    public Claims validateAccessToken(String jwt) {
        return Jwts.parser()
                .verifyWith(rsaKeyPair.getPublic())
                .clock(() -> Date.from(clock.instant()))
                .build()
                .parseSignedClaims(jwt)
                .getPayload();
    }

    private String issueAccessToken(UserAccount user, Instant now) {
        Instant exp = now.plusSeconds(ACCESS_TOKEN_TTL_SECONDS);
        List<String> roles = user.getRoles().stream().map(Enum::name).sorted().toList();
        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(user.getId().toString())
                .claim("email", user.getEmail())
                .claim("roles", roles)
                .issuedAt(Date.from(now))
                .expiration(Date.from(exp))
                .signWith(rsaKeyPair.getPrivate(), Jwts.SIG.RS256)
                .compact();
    }

    private void recordAudit(UUID userId, String action, String resource, String details) {
        auditTrail.add(new AuditEvent(UUID.randomUUID(), userId, action, resource, details, clock.instant()));
    }

    public List<AuditEvent> getAuditTrail() {
        return List.copyOf(auditTrail);
    }

    private static String hashPassword(String rawPassword) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest((rawPassword == null ? "" : rawPassword).getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(digest);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}

