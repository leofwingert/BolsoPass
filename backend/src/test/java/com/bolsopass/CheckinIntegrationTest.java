package com.bolsopass;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bolsopass.outbox.OutboxRepository;
import com.bolsopass.outbox.OutboxWriter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Peças do backend usadas pelo facial-checkin-service: JWKS, elegibilidade do titular para o
 * cadastro facial e o registro único da entrada (QR e FACIAL) com {@code checkin.realizado}.
 */
@SpringBootTest
@AutoConfigureMockMvc
class CheckinIntegrationTest {
  private static final String BOX = "/api/v1/operator/box-office";
  private static final String EVENT = "evt-neon";

  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;
  @Autowired OutboxRepository outbox;

  private ResultActions send(MockHttpServletRequestBuilder req, Object body, String token) throws Exception {
    if (token != null) req.header("Authorization", "Bearer " + token);
    if (body != null) req.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
    return mvc.perform(req);
  }

  private JsonNode body(ResultActions r) throws Exception {
    return json.readTree(r.andReturn().getResponse().getContentAsString());
  }

  private String token(String path, Map<String, String> credentials) throws Exception {
    return body(send(post(path), credentials, null).andExpect(status().isOk())).path("accessToken").asText();
  }

  private String operator() throws Exception {
    return token("/api/v1/auth/operator/login", Map.of("badge", "OP-88219", "pin", "123456"));
  }

  private String sarah() throws Exception {
    return token("/api/v1/auth/login", Map.of("email", "sarah@bolsopass.io", "password", "bolsopass"));
  }

  /** Cliente novo pelo cadastro público (CPF próprio: não esbarra no limite por CPF de outros testes). */
  private JsonNode newClient(String name, String cpf) throws Exception {
    Map<String, Object> req = new HashMap<>();
    req.put("name", name);
    req.put("email", "c" + cpf + "@teste.io");
    req.put("cpf", cpf);
    req.put("phone", "(51) 99876-5432");
    req.put("password", "senhaforte");
    req.put("acceptTerms", true);
    return body(send(post("/api/v1/auth/register"), req, null).andExpect(status().isCreated()));
  }

  /** Vende 2 ingressos no balcão para o cliente: o 1º para ele mesmo (titular), o 2º para um acompanhante. */
  private List<String> sell(String op, String customerId, String holderName, String holderCpf, String friendCpf) throws Exception {
    JsonNode map = body(send(get(BOX + "/events/" + EVENT + "/seats"), null, op));
    List<String> seats = new ArrayList<>();
    for (JsonNode s : map.path("seats")) {
      if (seats.size() < 2 && "DISPONIVEL".equals(s.path("status").asText()) && "PADRAO".equals(s.path("kind").asText())) {
        seats.add(s.path("id").asText());
      }
    }
    String reservationId = body(send(post(BOX + "/events/" + EVENT + "/reservations"),
            Map.of("customerId", customerId, "seatIds", seats), op))
        .path("reservation").path("id").asText();
    Map<String, Object> pay = new HashMap<>();
    pay.put("reservationId", reservationId);
    pay.put("method", "PIX");
    pay.put("installments", 1);
    pay.put("holders", List.of(
        Map.of("seatId", seats.get(0), "name", holderName, "cpf", holderCpf),
        Map.of("seatId", seats.get(1), "name", "Bia Torres Lima", "cpf", friendCpf)));
    JsonNode order = body(send(post(BOX + "/checkout").header("Idempotency-Key", UUID.randomUUID().toString()),
            Map.of("customerId", customerId, "payment", pay), op)
        .andExpect(status().isOk()));
    List<String> tickets = new ArrayList<>();
    for (JsonNode item : order.path("items")) tickets.add(item.path("ticketId").asText());
    return tickets;
  }

  @Test
  void jwksPublicaSoAChavePublica() throws Exception {
    send(get("/api/v1/auth/jwks"), null, null)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.keys[0].kty").value("RSA"))
        .andExpect(jsonPath("$.keys[0].kid").isNotEmpty())
        .andExpect(jsonPath("$.keys[0].d").doesNotExist()); // sem a parte privada
  }

  @Test
  void soOTitularPodeCadastrarORosto() throws Exception {
    JsonNode session = newClient("Lara Nunes Prado", "27182818205");
    List<String> tickets = sell(operator(), session.path("user").path("id").asText(), "Lara Nunes Prado",
        "27182818205", "16180339805");
    String client = session.path("accessToken").asText();
    send(get("/api/v1/tickets/" + tickets.get(0) + "/biometric-eligibility"), null, client)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.holder").value(true))
        .andExpect(jsonPath("$.eventId").value(EVENT))
        .andExpect(jsonPath("$.eventStartsAt").isNotEmpty());
    // Ela comprou o do acompanhante, mas não é a titular: não consente pelo rosto de outra pessoa.
    send(get("/api/v1/tickets/" + tickets.get(1) + "/biometric-eligibility"), null, client)
        .andExpect(jsonPath("$.holder").value(false));
  }

  @Test
  void checkinFacialLiberaUmaVezEPublicaCheckinRealizado() throws Exception {
    String op = operator();
    String customerId = newClient("Caio Mendes Rocha", "14142135651").path("user").path("id").asText();
    String ticketId = sell(op, customerId, "Caio Mendes Rocha", "14142135651", "17320508052").get(0);
    long before = outbox.findAll().stream().filter(e -> e.getTopic().equals(OutboxWriter.CHECKIN_REALIZADO)).count();

    send(post("/api/v1/operator/events/" + EVENT + "/checkins"),
            Map.of("ticketId", ticketId, "method", "FACIAL", "gate", "Portão 01"), op)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("LIBERADO"))
        .andExpect(jsonPath("$.holderName").value("Caio Mendes Rocha"));

    var events = outbox.findAll().stream().filter(e -> e.getTopic().equals(OutboxWriter.CHECKIN_REALIZADO)).toList();
    assertThat(events).hasSize((int) before + 1);
    JsonNode payload = json.readTree(events.getLast().getPayload());
    assertThat(payload.path("metodo").asText()).isEqualTo("FACIAL");
    assertThat(payload.path("ticketId").asText()).isEqualTo(ticketId);
    assertThat(payload.path("operadorId").asText()).isEqualTo("u-operator");

    // Segunda entrada (pelo rosto ou pelo QR): já utilizado, com horário e portão da primeira.
    send(post("/api/v1/operator/events/" + EVENT + "/checkins"), Map.of("ticketId", ticketId, "method", "QR"), op)
        .andExpect(jsonPath("$.status").value("NEGADO"))
        .andExpect(jsonPath("$.reason").value("INGRESSO_JA_UTILIZADO"))
        .andExpect(jsonPath("$.usedGate").value("Portão 01"));
    // Ingresso de outro evento ou inexistente: inválido.
    send(post("/api/v1/operator/events/evt-metal/checkins"), Map.of("ticketId", ticketId, "method", "FACIAL"), op)
        .andExpect(jsonPath("$.reason").value("INGRESSO_INVALIDO"));
    send(post("/api/v1/operator/events/" + EVENT + "/checkins"), Map.of("ticketId", "TK-00000-Z9", "method", "FACIAL"), op)
        .andExpect(jsonPath("$.reason").value("INGRESSO_INVALIDO"));
  }

  @Test
  void soAPortariaRegistraEntrada() throws Exception {
    String seller = token("/api/v1/auth/operator/login", Map.of("badge", "VD-20001", "pin", "654321"));
    send(post("/api/v1/operator/events/" + EVENT + "/checkins"), Map.of("ticketId", "x", "method", "FACIAL"), seller)
        .andExpect(status().isForbidden());
    send(post("/api/v1/operator/events/" + EVENT + "/checkins"),
            Map.of("ticketId", "x", "method", "FACIAL", "idempotency", UUID.randomUUID().toString()), sarah())
        .andExpect(status().isForbidden());
  }
}
