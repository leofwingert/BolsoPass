package com.bolsopass.iam.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Gerencia uma familia de Refresh Tokens no padrão de rotacao com deteccao de reuso (US-02 / RF-01).
 * Caso um Refresh Token ja rotacionado/revogado seja reapresentado, toda a familia de tokens
 * do usuario e imediatamente invalidada para conter sequestro de sessao.
 */
public class RefreshTokenFamily {

    public static final Duration REFRESH_TOKEN_TTL = Duration.ofDays(7);

    private final UUID familyId;
    private final UUID userId;
    private final Map<String, TokenNode> tokensByValue = new HashMap<>();
    private String activeTokenValue;
    private boolean familyCompromised;

    public record TokenNode(String tokenValue, Instant issuedAt, Instant expiresAt, boolean rotated, boolean revoked) {
    }

    public RefreshTokenFamily(UUID familyId, UUID userId, String initialTokenValue, Instant now) {
        this.familyId = Objects.requireNonNull(familyId, "Family ID cannot be null");
        this.userId = Objects.requireNonNull(userId, "User ID cannot be null");
        if (initialTokenValue == null || initialTokenValue.isBlank()) {
            throw new IllegalArgumentException("Initial refresh token value cannot be blank");
        }
        Objects.requireNonNull(now, "Timestamp cannot be null");
        TokenNode node = new TokenNode(initialTokenValue, now, now.plus(REFRESH_TOKEN_TTL), false, false);
        this.tokensByValue.put(initialTokenValue, node);
        this.activeTokenValue = initialTokenValue;
        this.familyCompromised = false;
    }

    public synchronized String rotate(String presentedTokenValue, String nextTokenValue, Instant now) {
        Objects.requireNonNull(presentedTokenValue, "Presented refresh token cannot be null");
        if (nextTokenValue == null || nextTokenValue.isBlank()) {
            throw new IllegalArgumentException("Next refresh token cannot be blank");
        }
        Objects.requireNonNull(now, "Timestamp cannot be null");

        if (familyCompromised) {
            throw new RefreshTokenReuseException("Token family has been revoked due to suspected token replay");
        }

        TokenNode node = tokensByValue.get(presentedTokenValue);
        if (node == null) {
            throw new InvalidCredentialsException("Unknown refresh token");
        }

        if (node.rotated() || node.revoked()) {
            // Reuso detectado! Revoga toda a familia imediatamente (US-02)
            revokeAll();
            this.familyCompromised = true;
            throw new RefreshTokenReuseException("Malicious reuse of previously rotated refresh token detected; entire token family invalidated");
        }

        if (!now.isBefore(node.expiresAt())) {
            throw new InvalidCredentialsException("Refresh token has expired");
        }

        tokensByValue.put(presentedTokenValue, new TokenNode(node.tokenValue(), node.issuedAt(), node.expiresAt(), true, true));
        TokenNode nextNode = new TokenNode(nextTokenValue, now, now.plus(REFRESH_TOKEN_TTL), false, false);
        tokensByValue.put(nextTokenValue, nextNode);
        this.activeTokenValue = nextTokenValue;
        return nextTokenValue;
    }

    public synchronized void revokeAll() {
        for (Map.Entry<String, TokenNode> entry : new HashMap<>(tokensByValue).entrySet()) {
            TokenNode current = entry.getValue();
            tokensByValue.put(entry.getKey(), new TokenNode(current.tokenValue(), current.issuedAt(), current.expiresAt(), current.rotated(), true));
        }
        this.activeTokenValue = null;
    }

    public UUID getFamilyId() {
        return familyId;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getActiveTokenValue() {
        return activeTokenValue;
    }

    public boolean isFamilyCompromised() {
        return familyCompromised;
    }
}

