package com.bolsopass.facial.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * Consentimento para uso de dados biométricos (LGPD art. 11: dado sensível, consentimento
 * específico e destacado). Sem consentimento ativo, nenhum rosto é indexado.
 */
@Entity
@Table(name = "biometric_consents")
public class BiometricConsent {
  @Id private String userId;

  @Column(nullable = false)
  private boolean accepted;

  private String termVersion;
  private Instant acceptedAt;
  private Instant revokedAt;

  protected BiometricConsent() {}

  public BiometricConsent(String userId) {
    this.userId = userId;
  }

  public void accept(String termVersion, Instant now) {
    this.accepted = true;
    this.termVersion = termVersion;
    this.acceptedAt = now;
    this.revokedAt = null;
  }

  public void revoke(Instant now) {
    this.accepted = false;
    this.revokedAt = now;
  }

  /** Ativo só se aceito na versão atual do termo (termo novo pede novo aceite). */
  public boolean isActive(String currentTermVersion) {
    return accepted && currentTermVersion.equals(termVersion);
  }

  public String getUserId() {
    return userId;
  }

  public boolean isAccepted() {
    return accepted;
  }

  public String getTermVersion() {
    return termVersion;
  }

  public Instant getAcceptedAt() {
    return acceptedAt;
  }

  public Instant getRevokedAt() {
    return revokedAt;
  }
}
