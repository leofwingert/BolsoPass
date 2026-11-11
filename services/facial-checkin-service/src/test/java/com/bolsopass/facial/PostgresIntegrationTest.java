package com.bolsopass.facial;

import static org.assertj.core.api.Assertions.assertThat;

import com.bolsopass.facial.domain.model.BiometricConsent;
import com.bolsopass.facial.domain.model.FaceEnrollment;
import com.bolsopass.facial.domain.port.ConsentRepository;
import com.bolsopass.facial.domain.port.EnrollmentRepository;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * PostgreSQL real (Testcontainers, RNF-07): as tabelas ficam no schema próprio "facial" (banco por
 * serviço) e não existe coluna para a imagem. Sem Docker na máquina, a classe é pulada.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(properties = "spring.profiles.active=postgres")
@DirtiesContext
class PostgresIntegrationTest {
  @Container @ServiceConnection
  static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

  @Autowired ConsentRepository consents;
  @Autowired EnrollmentRepository enrollments;
  @Autowired JdbcTemplate jdbc;

  @Test
  void gravaNoSchemaFacialSemColunaDeImagem() {
    BiometricConsent c = new BiometricConsent("u-pg");
    c.accept("biometria-v1", Instant.now());
    consents.save(c);
    enrollments.save(new FaceEnrollment("face-1", "TK-PG-1", "evt-neon", "u-pg", Instant.now(), Instant.now()));

    assertThat(consents.findById("u-pg")).get().extracting(BiometricConsent::isAccepted).isEqualTo(true);
    assertThat(enrollments.findByTicketId("TK-PG-1")).isPresent();
    var columns = jdbc.queryForList(
        "select column_name from information_schema.columns where table_schema = 'facial' and table_name = 'face_enrollments'",
        String.class);
    assertThat(columns).contains("face_id", "ticket_id", "event_id", "user_id", "created_at");
    assertThat(columns).noneMatch(col -> col.contains("image") || col.contains("photo") || col.contains("selfie"));
  }
}
