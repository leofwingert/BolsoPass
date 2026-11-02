package com.bolsopass;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bolsopass.audit.AuditLogRepository;
import com.bolsopass.ticketing.TicketRepository;
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

/** Venda no balcão: busca por CPF, cadastro rápido, lugares vagos, cartão/PIX e ingresso emitido. */
@SpringBootTest
@AutoConfigureMockMvc
class BoxOfficeIntegrationTest {
  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;
  @Autowired TicketRepository tickets;
  @Autowired AuditLogRepository logs;
  @Autowired com.bolsopass.ticketing.TicketService ticketService;

  private static final String BASE = "/api/v1/operator/box-office";

  private String token(boolean operator) throws Exception {
    var req = operator
        ? post("/api/v1/auth/operator/login").content("{\"badge\":\"OP-88219\",\"pin\":\"123456\"}")
        : post("/api/v1/auth/login").content("{\"email\":\"sarah@bolsopass.io\",\"password\":\"bolsopass\"}");
    return body(mvc.perform(req.contentType(MediaType.APPLICATION_JSON))).path("accessToken").asText();
  }

  private JsonNode body(ResultActions r) throws Exception {
    String s = r.andReturn().getResponse().getContentAsString();
    return s.isEmpty() ? json.nullNode() : json.readTree(s);
  }

  private ResultActions call(String method, String path, Object payload, String token, String key) throws Exception {
    var req = (method.equals("GET") ? get(path) : post(path)).header("Authorization", "Bearer " + token);
    if (key != null) req.header("Idempotency-Key", key);
    if (payload != null) req.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(payload));
    return mvc.perform(req);
  }

  private List<String> freeSeats(String op, String eventId, int n) throws Exception {
    JsonNode map = body(call("GET", BASE + "/events/" + eventId + "/seats", null, op, null).andExpect(status().isOk()));
    List<String> out = new ArrayList<>();
    for (JsonNode s : map.path("seats")) {
      if (out.size() < n && "DISPONIVEL".equals(s.path("status").asText()) && "PADRAO".equals(s.path("kind").asText())) {
        out.add(s.path("id").asText());
      }
    }
    return out;
  }

  private Map<String, Object> payment(String reservationId, String method, String seatId, String name, String cpf, String card) {
    Map<String, Object> p = new HashMap<>();
    p.put("reservationId", reservationId);
    p.put("method", method);
    p.put("installments", 1);
    p.put("holders", List.of(Map.of("seatId", seatId, "name", name, "cpf", cpf)));
    if (card != null) p.put("card", Map.of("number", card, "holderName", name.toUpperCase(), "expiry", "12/30", "cvv", "123"));
    return p;
  }

  @Test
  void buscaClientePeloCpfETrazNomeETelefone() throws Exception {
    String op = token(true);
    call("GET", BASE + "/customers?cpf=529.982.247-25", null, op, null)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("Sarah Jensen Oliveira"))
        .andExpect(jsonPath("$.phone").value("(11) 98844-3210"))
        .andExpect(jsonPath("$.email").value("sa***@bolsopass.io"));
    // Sem máscara também encontra.
    call("GET", BASE + "/customers?cpf=52998224725", null, op, null).andExpect(status().isOk());
    call("GET", BASE + "/customers?cpf=123.456.789-00", null, op, null).andExpect(status().isBadRequest());
    call("GET", BASE + "/customers?cpf=111.444.777-35", null, op, null).andExpect(status().isNotFound());
  }

  @Test
  void reservaOnlinePendenteDoClienteNaoBloqueiaAVendaNoBalcao() throws Exception {
    String op = token(true);
    String client = token(false);
    List<String> seats = freeSeats(op, "evt-metal", 2);
    // A Sarah reservou pelo site e não pagou (a reserva segue ativa por 10 min).
    call("POST", "/api/v1/reservations", Map.of("eventId", "evt-metal", "seatIds", List.of(seats.get(0))), client, null)
        .andExpect(status().isCreated());
    // Online, uma segunda reserva dela no mesmo evento continua barrada.
    call("POST", "/api/v1/reservations", Map.of("eventId", "evt-metal", "seatIds", List.of(seats.get(1))), client, null)
        .andExpect(status().isConflict());

    // No balcão, com ela presente, o operador vende normalmente.
    JsonNode reserved = body(call("POST", BASE + "/events/evt-metal/reservations",
            Map.of("customerId", "u-sarah", "seatIds", List.of(seats.get(1))), op, null)
        .andExpect(status().isCreated()));
    String reservationId = reserved.path("reservation").path("id").asText();
    call("POST", BASE + "/checkout",
            Map.of("customerId", "u-sarah",
                "payment", payment(reservationId, "PIX", seats.get(1), "Sarah Jensen Oliveira", "529.982.247-25", null)),
            op, UUID.randomUUID().toString())
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("PAGO"));
  }

  @Test
  void clienteNaoUsaABilheteria() throws Exception {
    call("GET", BASE + "/customers?cpf=529.982.247-25", null, token(false), null).andExpect(status().isForbidden());
  }

  @Test
  void cadastraNovoClienteEVendeComPixNumEventoComFilaVirtual() throws Exception {
    String op = token(true);
    JsonNode customer = body(call("POST", BASE + "/customers",
            Map.of("cpf", "153.509.460-56", "name", "Rita de Cássia Moura", "phone", "(51) 99876-1234"), op, null)
        .andExpect(status().isCreated()));
    assertThat(customer.path("newCustomer").asBoolean()).isTrue();
    String customerId = customer.path("id").asText();
    call("POST", BASE + "/customers", Map.of("cpf", "15350946056", "name", "Outra Pessoa", "phone", "(51) 90000-0000"), op, null)
        .andExpect(status().isConflict());

    // Festival Synthwave tem sala de espera virtual: no balcão a pessoa está presente e não passa por ela.
    String seat = freeSeats(op, "evt-synthwave", 1).get(0);
    JsonNode reserved = body(call("POST", BASE + "/events/evt-synthwave/reservations",
            Map.of("customerId", customerId, "seatIds", List.of(seat)), op, null)
        .andExpect(status().isCreated()));
    String reservationId = reserved.path("reservation").path("id").asText();
    assertThat(reserved.path("order").path("totals").path("totalCents").asLong()).isPositive();

    String key = UUID.randomUUID().toString();
    var pay = Map.of("customerId", customerId, "payment", payment(reservationId, "PIX", seat, "Rita de Cássia Moura", "153.509.460-56", null));
    JsonNode order = body(call("POST", BASE + "/checkout", pay, op, key).andExpect(status().isOk()));
    assertThat(order.path("status").asText()).isEqualTo("PAGO");

    // Mesma chave: mesmo pedido, sem cobrar de novo.
    JsonNode again = body(call("POST", BASE + "/checkout", pay, op, key).andExpect(status().isOk()));
    assertThat(again.path("id").asText()).isEqualTo(order.path("id").asText());

    assertThat(tickets.findByEventId("evt-synthwave")).anyMatch(t -> seat.equals(t.getSeatId()) && t.getHolderName().equals("Rita de Cássia Moura"));
    assertThat(logs.findAll()).anyMatch(l -> l.getTitle().equals("Venda no balcão"));

    // O assento vendido não está mais vago.
    assertThat(freeSeats(op, "evt-synthwave", 500)).doesNotContain(seat);
  }

  private String sellerToken() throws Exception {
    return body(mvc.perform(post("/api/v1/auth/operator/login").contentType(MediaType.APPLICATION_JSON)
            .content("{\"badge\":\"VD-20001\",\"pin\":\"654321\"}")))
        .path("accessToken").asText();
  }

  @Test
  void terminalDeVendaVendeEmDinheiroMasNaoLeQr() throws Exception {
    String seller = sellerToken();
    assertThat(seller).isNotBlank();
    // Só vende: base de ingressos e envio de leituras são da portaria.
    call("GET", "/api/v1/operator/events/evt-neon/cache", null, seller, null).andExpect(status().isForbidden());
    call("GET", "/api/v1/operator/events", null, seller, null).andExpect(status().isOk());

    JsonNode buyer = body(call("POST", BASE + "/customers",
        Map.of("cpf", "529.071.700-58", "name", "Lia Prado Moura", "phone", "(11) 97777-1111"), seller, null));
    JsonNode friend = body(call("POST", BASE + "/customers",
        Map.of("cpf", "817.385.770-90", "name", "Davi Nunes Rocha", "phone", "(11) 96666-2222"), seller, null));
    List<String> seats = freeSeats(seller, "evt-neon", 2);
    String reservationId = body(call("POST", BASE + "/events/evt-neon/reservations",
            Map.of("customerId", buyer.path("id").asText(), "seatIds", seats), seller, null))
        .path("reservation").path("id").asText();

    Map<String, Object> payment = new HashMap<>();
    payment.put("reservationId", reservationId);
    payment.put("method", "CASH");
    payment.put("installments", 1);
    payment.put("holders", List.of(
        Map.of("seatId", seats.get(0), "name", "Lia Prado Moura", "cpf", "529.071.700-58"),
        Map.of("seatId", seats.get(1), "name", "Davi Nunes Rocha", "cpf", "817.385.770-90")));
    JsonNode order = body(call("POST", BASE + "/checkout", Map.of("customerId", buyer.path("id").asText(), "payment", payment),
        seller, UUID.randomUUID().toString()));
    assertThat(order.path("status").asText()).isEqualTo("PAGO");
    assertThat(order.path("payment").path("method").asText()).isEqualTo("CASH");

    // O CPF vinculado ao ingresso leva o ingresso para a conta do titular, mesmo sem ter pago.
    var friendWallet = ticketService.mine(friend.path("id").asText());
    assertThat(friendWallet).anyMatch(t -> t.holderName().equals("Davi Nunes Rocha"));
    assertThat(friendWallet).noneMatch(t -> t.holderName().equals("Lia Prado Moura"));
  }

  @Test
  void adminNaoUsaABilheteriaEClienteNaoPagaEmDinheiroOnline() throws Exception {
    String admin = body(mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
            .content("{\"email\":\"admin@bolsopass.io\",\"password\":\"bolsopass\"}")))
        .path("accessToken").asText();
    call("GET", BASE + "/customers?cpf=529.982.247-25", null, admin, null).andExpect(status().isForbidden());
    call("GET", "/api/v1/operator/events", null, admin, null).andExpect(status().isForbidden());

    call("POST", "/api/v1/checkout", Map.of("reservationId", "x", "method", "CASH", "installments", 1, "holders", List.of()),
        token(false), UUID.randomUUID().toString())
        .andExpect(status().isBadRequest());
  }

  @Test
  void cartaoRecusadoPermiteDesistirEOAssentoVoltaAVenda() throws Exception {
    String op = token(true);
    JsonNode customer = body(call("POST", BASE + "/customers",
            Map.of("cpf", "714.602.380-01", "name", "Bruno Teles Araujo", "phone", "(41) 98888-7777"), op, null));
    String customerId = customer.path("id").asText();
    String seat = freeSeats(op, "evt-metal", 1).get(0);
    String reservationId = body(call("POST", BASE + "/events/evt-metal/reservations",
            Map.of("customerId", customerId, "seatIds", List.of(seat)), op, null))
        .path("reservation").path("id").asText();

    var pay = Map.of("customerId", customerId,
        "payment", payment(reservationId, "CREDIT_CARD", seat, "Bruno Teles Araujo", "714.602.380-01", "4111111111110000"));
    JsonNode declined = body(call("POST", BASE + "/checkout", pay, op, UUID.randomUUID().toString()));
    assertThat(declined.path("status").asText()).isEqualTo("RECUSADO");

    call("POST", BASE + "/reservations/" + reservationId + "/give-up", Map.of("customerId", customerId), op, null)
        .andExpect(status().isNoContent());
    assertThat(freeSeats(op, "evt-metal", 500)).contains(seat);
  }
}
