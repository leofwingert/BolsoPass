package com.bolsopass.facial.infrastructure.ticketing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bolsopass.facial.application.FacialProperties;
import com.bolsopass.facial.domain.model.CheckinOutcome;
import com.bolsopass.facial.domain.model.FacialException;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

/** Contrato HTTP com o backend de ingressos, contra um servidor HTTP de verdade (sem mocks). */
class HttpTicketingTest {
  private HttpServer server;
  private final AtomicReference<String> lastAuth = new AtomicReference<>();
  private final AtomicReference<String> lastBody = new AtomicReference<>();

  @BeforeEach
  void start() throws IOException {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    route("/api/v1/tickets/TK-1/biometric-eligibility", 200,
        "{\"ticketId\":\"TK-1\",\"eventId\":\"evt-neon\",\"eventTitle\":\"Neon\",\"eventStartsAt\":\"2026-12-02T00:00:00Z\","
            + "\"holderName\":\"Ana Souza\",\"holder\":true,\"status\":\"ATIVO\"}");
    route("/api/v1/tickets/TK-2/biometric-eligibility", 403, "{\"message\":\"Ingresso não pertence a você\"}");
    route("/api/v1/tickets/TK-3/biometric-eligibility", 500, "erro");
    route("/api/v1/operator/events/evt-neon/checkins", 200,
        "{\"status\":\"NEGADO\",\"reason\":\"INGRESSO_JA_UTILIZADO\",\"ticketId\":\"TK-1\",\"holderName\":\"Ana Souza\","
            + "\"seatLabel\":\"A12\",\"sectorName\":\"Pista\",\"usedAt\":\"2026-12-02T00:10:00Z\",\"usedGate\":\"Portão 01\"}");
    server.start();
  }

  @AfterEach
  void stop() {
    server.stop(0);
  }

  private void route(String path, int status, String body) {
    server.createContext(path, ex -> {
      lastAuth.set(ex.getRequestHeaders().getFirst("Authorization"));
      lastBody.set(new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
      byte[] out = body.getBytes(StandardCharsets.UTF_8);
      ex.getResponseHeaders().add("Content-Type", status == 500 ? "text/plain" : "application/json");
      ex.sendResponseHeaders(status, out.length);
      ex.getResponseBody().write(out);
      ex.close();
    });
  }

  private HttpTicketing client(String baseUrl) {
    var props = new FacialProperties("biometria-v1", 90, 7, new FacialProperties.Ticketing(baseUrl), null);
    return new HttpTicketing(RestClient.builder(), props);
  }

  private String base() {
    return "http://127.0.0.1:" + server.getAddress().getPort() + "/api/v1";
  }

  @Test
  void elegibilidadeRepassaOTokenELeOsDados() {
    var e = client(base()).eligibility("TK-1", "abc");
    assertThat(lastAuth.get()).isEqualTo("Bearer abc");
    assertThat(e.holder()).isTrue();
    assertThat(e.active()).isTrue();
    assertThat(e.eventId()).isEqualTo("evt-neon");
    assertThat(e.eventStartsAt()).isNotNull();
  }

  @Test
  void checkinEnviaMetodoFacialELeOMotivo() {
    CheckinOutcome o = client(base()).registerCheckin("evt-neon", "TK-1", "Portão 01", 97.3f, "op");
    assertThat(lastBody.get()).contains("\"method\":\"FACIAL\"").contains("\"ticketId\":\"TK-1\"");
    assertThat(o.status()).isEqualTo(CheckinOutcome.Status.NEGADO);
    assertThat(o.reason()).isEqualTo(CheckinOutcome.DenialReason.INGRESSO_JA_UTILIZADO);
    assertThat(o.similarity()).isEqualTo(97.3f);
    assertThat(o.usedGate()).isEqualTo("Portão 01");
  }

  @Test
  void errosDoBackendViramRegrasDoDominio() {
    assertThatThrownBy(() -> client(base()).eligibility("TK-2", "abc"))
        .isInstanceOf(FacialException.class)
        .hasMessage("Ingresso não pertence a você")
        .extracting(e -> ((FacialException) e).status()).isEqualTo(403);
    assertThatThrownBy(() -> client(base()).eligibility("TK-3", "abc"))
        .extracting(e -> ((FacialException) e).status()).isEqualTo(503);
    // Backend desligado.
    assertThatThrownBy(() -> client("http://127.0.0.1:1/api/v1").eligibility("TK-1", "abc"))
        .extracting(e -> ((FacialException) e).status()).isEqualTo(503);
  }
}
