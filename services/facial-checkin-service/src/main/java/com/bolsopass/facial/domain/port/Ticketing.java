package com.bolsopass.facial.domain.port;

import com.bolsopass.facial.domain.model.CheckinOutcome;
import java.time.Instant;

/**
 * Porta para o backend dono dos ingressos. Ele é a fonte única da verdade de "ingresso já usado":
 * QR e rosto passam pela mesma regra, e é ele que grava {@code checkin.realizado} no Outbox, na
 * mesma transação da entrada (sem Dual-Write). As chamadas usam o token de quem está operando.
 */
public interface Ticketing {

  /** Dados do ingresso para o cadastro facial; {@code holder} = o usuário é o titular. */
  Eligibility eligibility(String ticketId, String bearerToken);

  /** Registra a entrada (método FACIAL) e devolve LIBERADO ou NEGADO com o motivo. */
  CheckinOutcome registerCheckin(String eventId, String ticketId, String gate, float similarity, String bearerToken);

  record Eligibility(
      String ticketId, String eventId, String eventTitle, Instant eventStartsAt, String holderName, boolean holder,
      String status) {

    public boolean active() {
      return "ATIVO".equals(status);
    }
  }
}
