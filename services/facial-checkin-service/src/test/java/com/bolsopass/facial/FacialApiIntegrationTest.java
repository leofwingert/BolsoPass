package com.bolsopass.facial;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bolsopass.facial.application.RetentionJob;
import com.bolsopass.facial.domain.model.CheckinOutcome;
import com.bolsopass.facial.domain.model.FaceEnrollment;
import com.bolsopass.facial.domain.model.FacialException;
import com.bolsopass.facial.domain.port.EnrollmentRepository;
import com.bolsopass.facial.domain.port.FaceRecognition;
import com.bolsopass.facial.domain.port.Ticketing;
import com.bolsopass.facial.infrastructure.rekognition.SimulatedFaceRecognition;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * API do facial-checkin-service de ponta a ponta, com o Rekognition simulado e o backend de
 * ingressos substituído por um dublê (os contratos HTTP dele são testados em HttpTicketingTest).
 */
@SpringBootTest
@AutoConfigureMockMvc
class FacialApiIntegrationTest {
  private static final String EVENT = "evt-neon";
  private static final Instant STARTS = Instant.now().plus(Duration.ofDays(10));

  @Autowired MockMvc mvc;
  @Autowired FaceRecognition faces;
  @Autowired EnrollmentRepository enrollments;
  @Autowired RetentionJob retention;
  @MockitoBean Ticketing ticketing;

  static byte[] face(int seed) {
    byte[] b = new byte[2048];
    Arrays.fill(b, (byte) seed);
    return b;
  }

  static MockMultipartFile image(byte[] bytes) {
    return new MockMultipartFile("image", "selfie.jpg", MediaType.IMAGE_JPEG_VALUE, bytes);
  }

  static RequestPostProcessor client(String userId) {
    return jwt().jwt(j -> j.subject(userId).tokenValue("token-" + userId))
        .authorities(new SimpleGrantedAuthority("ROLE_CLIENT"));
  }

  static RequestPostProcessor operator() {
    return jwt().jwt(j -> j.subject("u-operator").tokenValue("token-operador"))
        .authorities(new SimpleGrantedAuthority("ROLE_OPERATOR"));
  }

  private void acceptTerm(String userId) throws Exception {
    mvc.perform(post("/api/v1/facial/consent").with(client(userId)).contentType(MediaType.APPLICATION_JSON)
            .content("{\"termVersion\":\"biometria-v1\",\"accepted\":true}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.active").value(true));
  }

  private void holderOf(String ticketId, String userId) {
    when(ticketing.eligibility(eq(ticketId), eq("token-" + userId)))
        .thenReturn(new Ticketing.Eligibility(ticketId, EVENT, "Neon", STARTS, "Ana Souza", true, "ATIVO"));
  }

  private ResultActions enroll(String userId, String ticketId, byte[] bytes) throws Exception {
    return mvc.perform(multipart("/api/v1/facial/enrollments").file(image(bytes)).param("ticketId", ticketId)
        .with(client(userId)));
  }

  private ResultActions checkin(byte[] bytes) throws Exception {
    return mvc.perform(multipart("/api/v1/facial/events/" + EVENT + "/checkins").file(image(bytes))
        .param("gate", "Portão 01").with(operator()));
  }

  @Test
  void consentimentoAceitarComAVersaoAtual() throws Exception {
    String user = "u-" + UUID.randomUUID();
    mvc.perform(get("/api/v1/facial/consent").with(client(user)))
        .andExpect(jsonPath("$.active").value(false))
        .andExpect(jsonPath("$.currentTermVersion").value("biometria-v1"));
    mvc.perform(post("/api/v1/facial/consent").with(client(user)).contentType(MediaType.APPLICATION_JSON)
            .content("{\"termVersion\":\"biometria-v0\",\"accepted\":true}"))
        .andExpect(status().isConflict());
    mvc.perform(post("/api/v1/facial/consent").with(client(user)).contentType(MediaType.APPLICATION_JSON)
            .content("{\"termVersion\":\"biometria-v1\",\"accepted\":false}"))
        .andExpect(status().isBadRequest());
    acceptTerm(user);
    mvc.perform(get("/api/v1/facial/consent").with(client(user)))
        .andExpect(jsonPath("$.active").value(true))
        .andExpect(jsonPath("$.acceptedAt").isNotEmpty());
  }

  @Test
  void cadastroExigeConsentimentoTitularEFotoValida() throws Exception {
    String user = "u-" + UUID.randomUUID();
    String ticket = "TK-" + UUID.randomUUID();
    holderOf(ticket, user);

    enroll(user, ticket, face(1)).andExpect(status().isForbidden()); // sem consentimento
    acceptTerm(user);

    mvc.perform(multipart("/api/v1/facial/enrollments")
            .file(new MockMultipartFile("image", "x.gif", "image/gif", face(1)))
            .param("ticketId", ticket).with(client(user)))
        .andExpect(status().isBadRequest()); // formato
    enroll(user, ticket, new byte[10]).andExpect(status().isUnprocessableEntity()); // sem rosto
    byte[] multi = face(4);
    System.arraycopy("MULTI".getBytes(StandardCharsets.US_ASCII), 0, multi, 0, 5);
    enroll(user, ticket, multi).andExpect(status().isUnprocessableEntity()); // mais de um rosto

    // Acompanhante: quem comprou não consente pelo rosto de outra pessoa.
    String friendTicket = "TK-" + UUID.randomUUID();
    when(ticketing.eligibility(eq(friendTicket), anyString()))
        .thenReturn(new Ticketing.Eligibility(friendTicket, EVENT, "Neon", STARTS, "Bia Lima", false, "ATIVO"));
    enroll(user, friendTicket, face(1)).andExpect(status().isForbidden());

    // Ingresso já usado ou cancelado.
    String usedTicket = "TK-" + UUID.randomUUID();
    when(ticketing.eligibility(eq(usedTicket), anyString()))
        .thenReturn(new Ticketing.Eligibility(usedTicket, EVENT, "Neon", STARTS, "Ana Souza", true, "UTILIZADO"));
    enroll(user, usedTicket, face(1)).andExpect(status().isConflict());

    // Evento que já passou.
    String oldTicket = "TK-" + UUID.randomUUID();
    when(ticketing.eligibility(eq(oldTicket), anyString())).thenReturn(new Ticketing.Eligibility(
        oldTicket, EVENT, "Neon", Instant.now().minus(Duration.ofDays(2)), "Ana Souza", true, "ATIVO"));
    enroll(user, oldTicket, face(1)).andExpect(status().isConflict());

    enroll(user, ticket, face(1))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.ticketId").value(ticket))
        .andExpect(jsonPath("$.eventId").value(EVENT));
    mvc.perform(get("/api/v1/facial/enrollments").with(client(user)))
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].ticketId").value(ticket));
    // Só FaceId, ingresso, evento e data: a imagem não é guardada.
    FaceEnrollment saved = enrollments.findByTicketId(ticket).orElseThrow();
    assertThat(saved.getFaceId()).isNotBlank();
  }

  @Test
  void checkinLiberaQuemCadastrouENegaRostoDesconhecido() throws Exception {
    String user = "u-" + UUID.randomUUID();
    String ticket = "TK-" + UUID.randomUUID();
    holderOf(ticket, user);
    acceptTerm(user);
    enroll(user, ticket, face(7)).andExpect(status().isCreated());

    when(ticketing.registerCheckin(eq(EVENT), eq(ticket), eq("Portão 01"), anyFloat(), eq("token-operador")))
        .thenReturn(new CheckinOutcome(CheckinOutcome.Status.LIBERADO, null, ticket, "Ana Souza", "A12", "Pista",
            99.5f, Instant.now(), "Portão 01"));
    checkin(face(7))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("LIBERADO"))
        .andExpect(jsonPath("$.holderName").value("Ana Souza"))
        .andExpect(jsonPath("$.ticketId").value(ticket));

    // Rosto que ninguém cadastrou: negado, e o backend nem é chamado.
    checkin(face(8))
        .andExpect(jsonPath("$.status").value("NEGADO"))
        .andExpect(jsonPath("$.reason").value("ROSTO_NAO_RECONHECIDO"));
    verify(ticketing, never()).registerCheckin(anyString(), eq("TK-nada"), anyString(), anyFloat(), anyString());

    // Segunda entrada: o backend responde que já foi usado.
    when(ticketing.registerCheckin(eq(EVENT), eq(ticket), anyString(), anyFloat(), anyString()))
        .thenReturn(new CheckinOutcome(CheckinOutcome.Status.NEGADO, CheckinOutcome.DenialReason.INGRESSO_JA_UTILIZADO,
            ticket, "Ana Souza", "A12", "Pista", 99.5f, Instant.now(), "Portão 01"));
    checkin(face(7)).andExpect(jsonPath("$.reason").value("INGRESSO_JA_UTILIZADO"));

    // Backend fora do ar: 503 com orientação de usar o QR.
    when(ticketing.registerCheckin(anyString(), anyString(), anyString(), anyFloat(), anyString()))
        .thenThrow(FacialException.unavailable("Serviço de ingressos indisponível. Use o QR Code."));
    checkin(face(7)).andExpect(status().isServiceUnavailable());
  }

  @Test
  void revogarApagaOsRostosEOCheckinDeixaDeReconhecer() throws Exception {
    String user = "u-" + UUID.randomUUID();
    String ticket = "TK-" + UUID.randomUUID();
    holderOf(ticket, user);
    acceptTerm(user);
    enroll(user, ticket, face(9)).andExpect(status().isCreated());
    int before = ((SimulatedFaceRecognition) faces).size(EVENT);

    mvc.perform(delete("/api/v1/facial/consent").with(client(user)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.active").value(false))
        .andExpect(jsonPath("$.revokedAt").isNotEmpty());
    assertThat(((SimulatedFaceRecognition) faces).size(EVENT)).isEqualTo(before - 1);
    assertThat(enrollments.findByTicketId(ticket)).isEmpty();
    checkin(face(9)).andExpect(jsonPath("$.reason").value("ROSTO_NAO_RECONHECIDO"));
    // Sem consentimento, não cadastra de novo.
    enroll(user, ticket, face(9)).andExpect(status().isForbidden());
  }

  @Test
  void recadastroTrocaORostoERemoverApaga() throws Exception {
    String user = "u-" + UUID.randomUUID();
    String ticket = "TK-" + UUID.randomUUID();
    holderOf(ticket, user);
    acceptTerm(user);
    enroll(user, ticket, face(20)).andExpect(status().isCreated());
    enroll(user, ticket, face(21)).andExpect(status().isCreated());
    when(ticketing.registerCheckin(anyString(), eq(ticket), anyString(), anyFloat(), anyString()))
        .thenReturn(new CheckinOutcome(CheckinOutcome.Status.LIBERADO, null, ticket, "Ana", null, null, 99f, null, null));
    checkin(face(20)).andExpect(jsonPath("$.reason").value("ROSTO_NAO_RECONHECIDO")); // a foto antiga não vale
    checkin(face(21)).andExpect(jsonPath("$.status").value("LIBERADO"));

    mvc.perform(delete("/api/v1/facial/enrollments/" + ticket).with(client("u-outro"))).andExpect(status().isNotFound());
    mvc.perform(delete("/api/v1/facial/enrollments/" + ticket).with(client(user))).andExpect(status().isNoContent());
    checkin(face(21)).andExpect(jsonPath("$.reason").value("ROSTO_NAO_RECONHECIDO"));
  }

  @Test
  void retencaoApagaRostosDeEventosQueJaPassaram() throws Exception {
    String oldEvent = "evt-antigo-" + UUID.randomUUID();
    faces.ensureCollection(oldEvent);
    String faceId = faces.indexFace(oldEvent, "TK-velho", face(30));
    enrollments.save(new FaceEnrollment(faceId, "TK-velho-" + UUID.randomUUID(), oldEvent, "u-x",
        Instant.now().minus(Duration.ofDays(30)), Instant.now().minus(Duration.ofDays(31))));

    assertThat(retention.purgeFinishedEvents()).isGreaterThanOrEqualTo(1);
    assertThat(((SimulatedFaceRecognition) faces).size(oldEvent)).isZero();
    assertThat(enrollments.findByEventStartsAtBefore(Instant.now().minus(Duration.ofDays(7)))).isEmpty();
  }

  @Test
  void permissoesPorPerfil() throws Exception {
    // Cliente não faz leitura na portaria; operador não cadastra rosto; sem login, 401.
    mvc.perform(multipart("/api/v1/facial/events/" + EVENT + "/checkins").file(image(face(1))).with(client("u-1")))
        .andExpect(status().isForbidden());
    mvc.perform(multipart("/api/v1/facial/enrollments").file(image(face(1))).param("ticketId", "TK-1").with(operator()))
        .andExpect(status().isForbidden());
    mvc.perform(get("/api/v1/facial/consent")).andExpect(status().isUnauthorized());
    mvc.perform(multipart("/api/v1/facial/events/" + EVENT + "/checkins").with(operator()))
        .andExpect(status().isBadRequest()); // sem foto
    verify(ticketing, never()).eligibility(eq("TK-1"), any());
  }
}
