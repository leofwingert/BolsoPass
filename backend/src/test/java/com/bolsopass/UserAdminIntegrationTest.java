package com.bolsopass;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bolsopass.audit.AuditLogRepository;
import com.bolsopass.iam.Role;
import com.bolsopass.iam.User;
import com.bolsopass.iam.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Cadastro público (sempre Cliente) e gestão de usuários pela administração (RF-02, RF-03):
 * listar com filtros, cadastrar, promover/rebaixar com matrícula + PIN e as travas de segurança.
 */
@SpringBootTest
@AutoConfigureMockMvc
class UserAdminIntegrationTest {
  private static final String USERS = "/api/v1/admin/users";

  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;
  @Autowired AuditLogRepository logs;
  @Autowired UserRepository users;

  private ResultActions send(MockHttpServletRequestBuilder req, Object body, String token) throws Exception {
    if (token != null) req.header("Authorization", "Bearer " + token);
    if (body != null) req.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
    return mvc.perform(req);
  }

  private JsonNode body(ResultActions r) throws Exception {
    return json.readTree(r.andReturn().getResponse().getContentAsString());
  }

  private String login(String email, String password) throws Exception {
    return body(send(post("/api/v1/auth/login"), Map.of("email", email, "password", password), null)
            .andExpect(status().isOk()))
        .path("accessToken").asText();
  }

  private String operatorLogin(String badge, String pin) throws Exception {
    return body(send(post("/api/v1/auth/operator/login"), Map.of("badge", badge, "pin", pin), null)
            .andExpect(status().isOk()))
        .path("accessToken").asText();
  }

  private static Map<String, Object> signup(String name, String email, String cpf, String password) {
    return signup(name, email, cpf, password, null);
  }

  private static Map<String, Object> signup(
      String name, String email, String cpf, String password, String activationCode) {
    Map<String, Object> m = new HashMap<>();
    m.put("name", name);
    m.put("email", email);
    m.put("cpf", cpf);
    m.put("phone", "(51) 99876-5432");
    m.put("password", password);
    m.put("acceptTerms", true);
    if (activationCode != null) m.put("activationCode", activationCode);
    return m;
  }

  @Test
  void cadastroPublicoSempreCriaClienteEJaEntraLogado() throws Exception {
    Map<String, Object> req = signup("Paula Mendes Costa", "Paula@Teste.io", "246.813.579-28", "senhaforte");
    req.put("roles", new String[] {"ROLE_ADMIN"}); // tentativa de escolher o perfil: ignorada
    send(post("/api/v1/auth/register"), req, null)
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.user.email").value("paula@teste.io"))
        .andExpect(jsonPath("$.user.roles.length()").value(1))
        .andExpect(jsonPath("$.user.roles[0]").value("ROLE_CLIENT"))
        .andExpect(jsonPath("$.user.cpf").value("246.813.579-28"))
        .andExpect(jsonPath("$.accessToken").isNotEmpty());

    // Cliente não entra na administração nem na bilheteria.
    String token = login("paula@teste.io", "senhaforte");
    send(get(USERS), null, token).andExpect(status().isForbidden());
    send(get("/api/v1/organizer/events"), null, token).andExpect(status().isForbidden());
    send(get("/api/v1/operator/box-office/customers?cpf=52998224725"), null, token).andExpect(status().isForbidden());
  }

  @Test
  void cadastroPublicoValidaDadosEDuplicados() throws Exception {
    send(post("/api/v1/auth/register"), signup("Ana Souza", "sarah@bolsopass.io", "135.792.468-28", "senhaforte"), null)
        .andExpect(status().isConflict()); // e-mail em uso
    send(post("/api/v1/auth/register"), signup("Ana Souza", "ana.dup@teste.io", "529.982.247-25", "senhaforte"), null)
        .andExpect(status().isConflict()); // CPF da Sarah (conta normal)
    send(post("/api/v1/auth/register"), signup("Ana Souza", "ana1@teste.io", "123.456.789-00", "senhaforte"), null)
        .andExpect(status().isBadRequest()); // CPF inválido
    send(post("/api/v1/auth/register"), signup("Ana Souza", "ana2@teste.io", "135.792.468-28", "curta"), null)
        .andExpect(status().isBadRequest()); // senha curta
    send(post("/api/v1/auth/register"), signup("Ana", "ana3@teste.io", "135.792.468-28", "senhaforte"), null)
        .andExpect(status().isBadRequest()); // sem sobrenome
    Map<String, Object> noTerms = signup("Ana Souza", "ana4@teste.io", "135.792.468-28", "senhaforte");
    noTerms.put("acceptTerms", false);
    send(post("/api/v1/auth/register"), noTerms, null).andExpect(status().isBadRequest());
  }

  @Test
  void cpfCadastradoNoBalcaoEAssumidoPeloDonoNoCadastro() throws Exception {
    String op = operatorLogin("OP-88219", "123456");
    JsonNode created = body(send(post("/api/v1/operator/box-office/customers"),
                Map.of("cpf", "864.209.753-10", "name", "Rui Barbosa Lima", "phone", "(51) 98888-1111"), op)
            .andExpect(status().isCreated()));
    String counterId = created.path("id").asText();
    JsonNode session = body(send(post("/api/v1/auth/register"),
                signup("Rui Barbosa Lima", "rui@teste.io", "86420975310", "senhaforte",
                    created.path("activationCode").asText()), null)
            .andExpect(status().isCreated()));
    // Mesma conta (os ingressos comprados no balcão continuam nela), agora com e-mail e senha.
    assertThat(session.path("user").path("id").asText()).isEqualTo(counterId);
    login("rui@teste.io", "senhaforte");
    // Uma segunda tentativa com o mesmo CPF já não assume nada.
    send(post("/api/v1/auth/register"), signup("Rui Barbosa Lima", "rui2@teste.io", "86420975310", "senhaforte"), null)
        .andExpect(status().isConflict());
  }

  @Test
  void contaDoBalcaoAntigaSemMarcaTambemPodeSerAssumida() throws Exception {
    // Criada no balcão antes da coluna counter_account existir: marca nula, e-mail automático do balcão.
    users.save(new User("u-legado-balcao", "Ivo Pereira Lopes", "cpf31415926590" + User.COUNTER_EMAIL_DOMAIN,
            "x", Set.of(Role.ROLE_CLIENT))
        .withContact("314.159.265-90", "(51) 97777-0000"));

    // Só o CPF não basta (sem código, ou com código errado): ninguém toma a conta de outra pessoa.
    send(post("/api/v1/auth/register"),
                signup("Ivo Pereira Lopes", "ivo@teste.io", "314.159.265-90", "senhaforte"), null)
            .andExpect(status().isConflict());
    send(post("/api/v1/auth/register"),
                signup("Ivo Pereira Lopes", "ivo@teste.io", "314.159.265-90", "senhaforte", "000000"), null)
            .andExpect(status().isConflict());

    // O operador gera um código novo no balcão e entrega à pessoa.
    String op = operatorLogin("OP-88219", "123456");
    String code = body(send(post("/api/v1/operator/box-office/customers/u-legado-balcao/activation-code"), null, op)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.pendingActivation").value(true)))
        .path("activationCode").asText();
    assertThat(code).matches("\\d{6}");

    JsonNode session = body(send(post("/api/v1/auth/register"),
                signup("Ivo Pereira Lopes", "ivo@teste.io", "314.159.265-90", "senhaforte", code), null)
            .andExpect(status().isCreated()));
    assertThat(session.path("user").path("id").asText()).isEqualTo("u-legado-balcao");

    // Conta já ativada: o balcão não gera mais código e a busca mostra que não está pendente.
    send(post("/api/v1/operator/box-office/customers/u-legado-balcao/activation-code"), null, op)
        .andExpect(status().isConflict());
    send(get("/api/v1/operator/box-office/customers?cpf=31415926590"), null, op)
        .andExpect(jsonPath("$.pendingActivation").value(false))
        .andExpect(jsonPath("$.activationCode").doesNotExist());
  }

  @Test
  void soAdminVeAListaComFiltros() throws Exception {
    send(get(USERS), null, operatorLogin("OP-88219", "123456")).andExpect(status().isForbidden());
    send(get(USERS), null, null).andExpect(status().isUnauthorized());

    String admin = login("admin@bolsopass.io", "bolsopass");
    send(get(USERS + "?q=sarah"), null, admin)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items[0].email").value("sarah@bolsopass.io"))
        .andExpect(jsonPath("$.items[0].cpf").value("***.982.247-**"))
        .andExpect(jsonPath("$.items[0].profile").value("CLIENT"));
    send(get(USERS + "?q=529.982"), null, admin).andExpect(jsonPath("$.items[0].id").value("u-sarah"));
    send(get(USERS + "?profile=SELLER"), null, admin)
        .andExpect(jsonPath("$.items[?(@.badge == 'VD-20001')]").exists())
        .andExpect(jsonPath("$.items[?(@.profile != 'SELLER')]").doesNotExist());
    send(get(USERS + "?profile=CLIENT&size=2"), null, admin)
        .andExpect(jsonPath("$.items.length()").value(2))
        .andExpect(jsonPath("$.size").value(2));
  }

  @Test
  void promoverParaBilheteriaGeraMatriculaEPinERebaixarTiraOAcesso() throws Exception {
    String admin = login("admin@bolsopass.io", "bolsopass");
    String id = body(send(post("/api/v1/auth/register"),
                signup("Lia Prado Nunes", "lia@teste.io", "975.318.642-82", "senhaforte"), null))
        .path("user").path("id").asText();
    String clientRefresh = body(send(post("/api/v1/auth/login"),
            Map.of("email", "lia@teste.io", "password", "senhaforte"), null))
        .path("refreshToken").asText();

    JsonNode promoted = body(send(patch(USERS + "/" + id + "/profile"), Map.of("profile", "OPERATOR"), admin)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.user.profile").value("OPERATOR")));
    String badge = promoted.path("credentials").path("badge").asText();
    String pin = promoted.path("credentials").path("pin").asText();
    assertThat(badge).matches("OP-\\d{5}");
    assertThat(pin).matches("\\d{6}");

    // Entra na bilheteria com a matrícula e o PIN; a senha do e-mail continua a mesma.
    String staff = operatorLogin(badge, pin);
    send(get("/api/v1/operator/events"), null, staff).andExpect(status().isOk());
    login("lia@teste.io", "senhaforte");
    // Um perfil por conta: virou equipe, não compra mais como cliente.
    send(post("/api/v1/reservations"), Map.of("eventId", "evt-neon", "seatIds", new String[] {"evt-neon:A9"}), staff)
        .andExpect(status().isForbidden());
    // A sessão antiga (de cliente) caiu.
    send(post("/api/v1/auth/refresh"), Map.of("refreshToken", clientRefresh), null).andExpect(status().isUnauthorized());

    send(patch(USERS + "/" + id + "/profile"), Map.of("profile", "CLIENT"), admin)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.user.badge").doesNotExist())
        .andExpect(jsonPath("$.credentials").doesNotExist());
    send(post("/api/v1/auth/operator/login"), Map.of("badge", badge, "pin", pin), null)
        .andExpect(status().isUnauthorized());

    assertThat(logs.findAll()).anySatisfy(l -> assertThat(l.getTitle()).isEqualTo("Perfil alterado"));
  }

  @Test
  void adminCadastraUsuarioComPerfilENaoMudaOProprioPerfil() throws Exception {
    String admin = login("admin@bolsopass.io", "bolsopass");
    Map<String, Object> seller = new HashMap<>();
    seller.put("name", "Téo Ramos Dias");
    seller.put("email", "teo@bolsopass.io");
    seller.put("profile", "SELLER");
    JsonNode created = body(send(post(USERS), seller, admin)
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.user.profile").value("SELLER")));
    String sellerToken = operatorLogin(created.path("credentials").path("badge").asText(),
        created.path("credentials").path("pin").asText());
    send(get("/api/v1/operator/events"), null, sellerToken).andExpect(status().isOk());

    send(post(USERS), seller, admin).andExpect(status().isConflict()); // e-mail repetido
    Map<String, Object> client = new HashMap<>(seller);
    client.put("email", "sem.cpf@bolsopass.io");
    client.put("profile", "CLIENT");
    client.put("password", "senhaforte");
    send(post(USERS), client, admin).andExpect(status().isBadRequest()); // cliente precisa de CPF

    send(patch(USERS + "/u-admin/profile"), Map.of("profile", "CLIENT"), admin).andExpect(status().isBadRequest());
    send(patch(USERS + "/u-sarah/profile"), Map.of("profile", "CLIENT"), admin).andExpect(status().isBadRequest());
  }
}
