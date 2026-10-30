package com.bolsopass.iam;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * Refresh token (RF-01). Guardamos só o hash SHA-256, nunca o token em claro.
 * RF-01 cita Redis; a tabela cumpre o mesmo papel até o Redis entrar no compose.
 */
@Entity
@Table(name = "refresh_tokens")
public class RefreshToken {
  @Id private String tokenHash;

  @Column(nullable = false)
  private String userId;

  @Column(nullable = false)
  private Instant expiresAt;

  private boolean revoked;

  protected RefreshToken() {}

  public RefreshToken(String tokenHash, String userId, Instant expiresAt) {
    this.tokenHash = tokenHash;
    this.userId = userId;
    this.expiresAt = expiresAt;
  }

  public String getUserId() {
    return userId;
  }

  public boolean isRevoked() {
    return revoked;
  }

  public boolean isExpired(Instant now) {
    return now.isAfter(expiresAt);
  }

  public void revoke() {
    this.revoked = true;
  }
}
