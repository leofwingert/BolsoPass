package com.bolsopass.facial.domain.model;

import java.time.Instant;

/** Resposta da catraca: LIBERADO (nome e ingresso) ou NEGADO com o motivo. */
public record CheckinOutcome(
    Status status,
    DenialReason reason,
    String ticketId,
    String holderName,
    String seatLabel,
    String sectorName,
    Float similarity,
    Instant usedAt,
    String usedGate) {

  public enum Status {
    LIBERADO,
    NEGADO
  }

  public enum DenialReason {
    ROSTO_NAO_RECONHECIDO,
    INGRESSO_JA_UTILIZADO,
    INGRESSO_INVALIDO
  }

  public static CheckinOutcome notRecognized() {
    return new CheckinOutcome(Status.NEGADO, DenialReason.ROSTO_NAO_RECONHECIDO, null, null, null, null, null, null, null);
  }
}
