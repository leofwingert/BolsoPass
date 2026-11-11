package com.bolsopass.facial.infrastructure.ticketing;

import com.bolsopass.facial.application.FacialProperties;
import com.bolsopass.facial.domain.model.CheckinOutcome;
import com.bolsopass.facial.domain.model.FacialException;
import com.bolsopass.facial.domain.port.Ticketing;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/**
 * Chama o backend dos ingressos repassando o token de quem está usando o app (cliente no cadastro,
 * operador na portaria): o backend aplica as mesmas permissões de sempre.
 */
@Component
public class HttpTicketing implements Ticketing {
  private final RestClient http;

  public HttpTicketing(RestClient.Builder builder, FacialProperties props) {
    SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
    // Catraca não pode esperar: 2 s para conectar, 5 s para responder.
    factory.setConnectTimeout(Duration.ofSeconds(2));
    factory.setReadTimeout(Duration.ofSeconds(5));
    this.http = builder.baseUrl(props.ticketing().baseUrl()).requestFactory(factory).build();
  }

  @Override
  public Eligibility eligibility(String ticketId, String bearerToken) {
    JsonNode n = call(() -> http.get()
        .uri("/tickets/{id}/biometric-eligibility", ticketId)
        .header(HttpHeaders.AUTHORIZATION, "Bearer " + bearerToken)
        .retrieve()
        .body(JsonNode.class));
    return new Eligibility(
        n.path("ticketId").asText(), n.path("eventId").asText(), n.path("eventTitle").asText(null),
        n.hasNonNull("eventStartsAt") ? Instant.parse(n.path("eventStartsAt").asText()) : null,
        n.path("holderName").asText(null), n.path("holder").asBoolean(false), n.path("status").asText(null));
  }

  @Override
  public CheckinOutcome registerCheckin(String eventId, String ticketId, String gate, float similarity, String bearerToken) {
    JsonNode n = call(() -> http.post()
        .uri("/operator/events/{eventId}/checkins", eventId)
        .header(HttpHeaders.AUTHORIZATION, "Bearer " + bearerToken)
        .contentType(MediaType.APPLICATION_JSON)
        .body(Map.of("ticketId", ticketId, "method", "FACIAL", "gate", gate == null ? "" : gate))
        .retrieve()
        .body(JsonNode.class));
    CheckinOutcome.Status status = CheckinOutcome.Status.valueOf(n.path("status").asText());
    CheckinOutcome.DenialReason reason = n.hasNonNull("reason")
        ? CheckinOutcome.DenialReason.valueOf(n.path("reason").asText())
        : null;
    return new CheckinOutcome(status, reason, n.path("ticketId").asText(ticketId), n.path("holderName").asText(null),
        n.path("seatLabel").asText(null), n.path("sectorName").asText(null), similarity,
        n.hasNonNull("usedAt") ? Instant.parse(n.path("usedAt").asText()) : null, n.path("usedGate").asText(null));
  }

  private interface Call {
    JsonNode run();
  }

  /** Traduz as respostas de erro do backend para as regras do domínio. */
  private static JsonNode call(Call call) {
    try {
      JsonNode body = call.run();
      if (body == null) throw FacialException.unavailable("Resposta vazia do serviço de ingressos");
      return body;
    } catch (RestClientResponseException e) {
      String message = message(e);
      int code = e.getStatusCode().value();
      if (code == 401 || code == 403 || code == 404 || code == 409) throw new FacialException(code, message);
      throw FacialException.unavailable("Serviço de ingressos indisponível. Use o QR Code.");
    } catch (ResourceAccessException e) {
      throw FacialException.unavailable("Serviço de ingressos indisponível. Use o QR Code.");
    }
  }

  private static String message(RestClientResponseException e) {
    try {
      JsonNode body = e.getResponseBodyAs(JsonNode.class);
      if (body != null && body.hasNonNull("message")) return body.path("message").asText();
    } catch (RuntimeException ignored) {
      // corpo não é JSON
    }
    return e.getStatusText();
  }
}
