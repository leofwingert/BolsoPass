package com.bolsopass;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Testes pela API HTTP, como o front chama: autenticação (JWT + refresh com rotação, bloqueio),
 * RBAC, ingressos com QR assinado e a API da portaria (cache offline e envio idempotente).
 */
@SpringBootTest
@AutoConfigureMockMvc
class ApiIntegrationTest {
  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;

  private ResultActions postJson(String path, Object body, String token) throws Exception {
    var req = post(path).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
    if (token != null) req.header("Authorization", "Bearer " + token);
    return mvc.perform(req);
  }

  private ResultActions getAs(String path, String token) throws Exception {
    var req = get(path);
    if (token != null) req.header("Authorization", "Bearer " + token);
    return mvc.perform(req);
  }

  private JsonNode body(ResultActions r) throws Exception {
    return json.readTree(r.andReturn().getResponse().getContentAsString());
  }

  private JsonNode login(String email, String password) throws Exception {
    return body(postJson("/api/v1/auth/login", Map.of("email", email, "password", password), null).andExpect(status().isOk()));
  }

  private String operatorToken() throws Exception {
    return body(postJson("/api/v1/auth/operator/login", Map.of("badge", "OP-88219", "pin", "123456"), null)
            .andExpect(status().isOk()))
        .path("accessToken").asText();
  }

  @Test
  void loginDevolveTokensEPerfil() throws Exception {
    JsonNode s = login("sarah@bolsopass.io", "bolsopass");
    assertThat(s.path("accessToken").asText()).isNotBlank();
    assertThat(s.path("refreshToken").asText()).isNotBlank();
    assertThat(s.path("user").path("roles").toString()).contains("ROLE_CLIENT");
  }

  @Test
  void senhaErradaE401EBloqueiaDepoisDeCincoTentativas() throws Exception {
    Map<String, String> bad = Map.of("email", "ninguem@teste.io", "password", "errada");
    for (int i = 0; i < 5; i++) postJson("/api/v1/auth/login", bad, null).andExpect(status().isUnauthorized());
    int code = postJson("/api/v1/auth/login", bad, null).andReturn().getResponse().getStatus();
    assertThat(code).isNotEqualTo(200).isNotEqualTo(401);
  }

  @Test
  void corpoInvalidoE400() throws Exception {
    postJson("/api/v1/auth/login", Map.of("email", ""), null).andExpect(status().isBadRequest());
  }

  @Test
  void refreshGiraOTokenEReusoDerrubaASessao() throws Exception {
    String first = login("sarah@bolsopass.io", "bolsopass").path("refreshToken").asText();
    JsonNode rotated = body(postJson("/api/v1/auth/refresh", Map.of("refreshToken", first), null).andExpect(status().isOk()));
    String second = rotated.path("refreshToken").asText();
    assertThat(second).isNotEqualTo(first);

    // Reuso do token antigo: 401 e todas as sessões do usuário caem (inclusive a nova).
    postJson("/api/v1/auth/refresh", Map.of("refreshToken", first), null).andExpect(status().isUnauthorized());
    postJson("/api/v1/auth/refresh", Map.of("refreshToken", second), null).andExpect(status().isUnauthorized());
    postJson("/api/v1/auth/refresh", Map.of("refreshToken", "nao-existe"), null).andExpect(status().isUnauthorized());
  }

  @Test
  void logoutRevogaORefreshToken() throws Exception {
    String rt = login("sarah@bolsopass.io", "bolsopass").path("refreshToken").asText();
    postJson("/api/v1/auth/logout", Map.of("refreshToken", rt), null).andExpect(status().is2xxSuccessful());
    postJson("/api/v1/auth/refresh", Map.of("refreshToken", rt), null).andExpect(status().isUnauthorized());
  }

  @Test
  void operadorEntraComMatriculaEPin() throws Exception {
    assertThat(operatorToken()).isNotBlank();
    postJson("/api/v1/auth/operator/login", Map.of("badge", "OP-88219", "pin", "000000"), null).andExpect(status().isUnauthorized());
    // Cliente não é operador, mesmo com a senha certa.
    postJson("/api/v1/auth/operator/login", Map.of("badge", "sarah@bolsopass.io", "pin", "bolsopass"), null)
        .andExpect(status().isUnauthorized());
  }

  @Test
  void rbacSemTokenE401PerfilErradoE403() throws Exception {
    getAs("/api/v1/tickets/me", null).andExpect(status().isUnauthorized());
    String client = login("sarah@bolsopass.io", "bolsopass").path("accessToken").asText();
    getAs("/api/v1/organizer/dashboard", client).andExpect(status().isForbidden());
    getAs("/api/v1/operator/events", client).andExpect(status().isForbidden());
    getAs("/api/v1/tickets/me", operatorToken()).andExpect(status().isForbidden());
    getAs("/api/v1/tickets/me", "token.invalido").andExpect(status().isUnauthorized());
  }

  @Test
  void clienteVeIngressosEQrAssinado() throws Exception {
    String client = login("sarah@bolsopass.io", "bolsopass").path("accessToken").asText();
    JsonNode tickets = body(getAs("/api/v1/tickets/me", client).andExpect(status().isOk()));
    assertThat(tickets.size()).isPositive();
    String id = null;
    for (JsonNode t : tickets) if ("ATIVO".equals(t.path("status").asText())) id = t.path("id").asText();
    assertThat(id).isNotNull();

    getAs("/api/v1/tickets/" + id, client).andExpect(status().isOk()).andExpect(jsonPath("$.id").value(id));
    String qr = body(getAs("/api/v1/tickets/" + id + "/qr", client).andExpect(status().isOk())).path("qr").asText();
    assertThat(qr).startsWith("BP1.").matches("BP1\\.[\\w-]+\\.[\\w-]+");
    getAs("/api/v1/tickets/nao-existe", client).andExpect(status().isNotFound());

    // Ingresso de outra pessoa não aparece para quem não é dono.
    String admin = login("admin@bolsopass.io", "bolsopass").path("accessToken").asText();
    int other = getAs("/api/v1/tickets/" + id, admin).andReturn().getResponse().getStatus();
    assertThat(other).isIn(403, 404);
  }

  @Test
  void chavePublicaDoQrEPublica() throws Exception {
    getAs("/api/v1/keys/qr", null).andExpect(status().isOk()).andExpect(jsonPath("$.crv").value("P-256"));
  }

  @Test
  void portariaBaixaACacheEEnviaEntradasSemContarDuasVezes() throws Exception {
    String op = operatorToken();
    JsonNode events = body(getAs("/api/v1/operator/events", op).andExpect(status().isOk()));
    assertThat(events.size()).isPositive();
    assertThat(events.get(0).path("gates").size()).isPositive();

    JsonNode cache = body(getAs("/api/v1/operator/events/evt-metal/cache", op).andExpect(status().isOk()));
    assertThat(cache.path("publicKeyJwk").path("crv").asText()).isEqualTo("P-256");
    String ticketId = null;
    for (var it = cache.path("tickets").fieldNames(); it.hasNext(); ) {
      String tid = it.next();
      if (!cache.path("used").has(tid)) ticketId = tid;
    }
    assertThat(ticketId).isNotNull();

    var scan = Map.of("scans", List.of(Map.of("id", "s-1", "status", "VALIDO", "ticketId", ticketId,
        "at", "2026-10-30T20:00:00Z", "gate", "Portão 01")));
    postJson("/api/v1/operator/events/evt-metal/scans", scan, op).andExpect(jsonPath("$.accepted").value(1));
    postJson("/api/v1/operator/events/evt-metal/scans", scan, op)
        .andExpect(jsonPath("$.accepted").value(0))
        .andExpect(jsonPath("$.duplicates").value(1));

    JsonNode after = body(getAs("/api/v1/operator/events/evt-metal/cache", op));
    assertThat(after.path("used").has(ticketId)).isTrue();
    getAs("/api/v1/operator/events/nao-existe/cache", op).andExpect(status().isNotFound());
  }
}
