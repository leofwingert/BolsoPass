package com.bolsopass.facial.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.bolsopass.facial.domain.model.BiometricConsent;
import com.bolsopass.facial.domain.model.CheckinOutcome;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class BiometricConsentTest {
  private static final Instant NOW = Instant.parse("2026-11-09T12:00:00Z");

  @Test
  void semAceiteNaoEstaAtivo() {
    assertThat(new BiometricConsent("u-1").isActive("biometria-v1")).isFalse();
  }

  @Test
  void aceiteValeSoParaAVersaoAtualDoTermo() {
    BiometricConsent c = new BiometricConsent("u-1");
    c.accept("biometria-v1", NOW);
    assertThat(c.isActive("biometria-v1")).isTrue();
    assertThat(c.getAcceptedAt()).isEqualTo(NOW);
    // Termo novo: precisa aceitar de novo.
    assertThat(c.isActive("biometria-v2")).isFalse();
  }

  @Test
  void revogarDesativaEGuardaAData() {
    BiometricConsent c = new BiometricConsent("u-1");
    c.accept("biometria-v1", NOW);
    c.revoke(NOW.plusSeconds(60));
    assertThat(c.isActive("biometria-v1")).isFalse();
    assertThat(c.getRevokedAt()).isEqualTo(NOW.plusSeconds(60));
    // Aceitar de novo limpa a revogação.
    c.accept("biometria-v1", NOW.plusSeconds(120));
    assertThat(c.isActive("biometria-v1")).isTrue();
    assertThat(c.getRevokedAt()).isNull();
  }

  @Test
  void rostoNaoReconhecidoENegadoSemIngresso() {
    CheckinOutcome o = CheckinOutcome.notRecognized();
    assertThat(o.status()).isEqualTo(CheckinOutcome.Status.NEGADO);
    assertThat(o.reason()).isEqualTo(CheckinOutcome.DenialReason.ROSTO_NAO_RECONHECIDO);
    assertThat(o.ticketId()).isNull();
  }
}
