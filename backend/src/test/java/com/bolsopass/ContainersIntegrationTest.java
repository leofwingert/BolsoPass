package com.bolsopass;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.bolsopass.iam.Role;
import com.bolsopass.iam.User;
import com.bolsopass.iam.UserRepository;
import com.bolsopass.inventory.ReservationService;
import com.bolsopass.inventory.Seat;
import com.bolsopass.inventory.SeatRepository;
import com.bolsopass.orders.CheckoutService;
import com.bolsopass.orders.CheckoutService.Holder;
import com.bolsopass.orders.CheckoutService.PayRequest;
import com.bolsopass.orders.Order;
import com.bolsopass.orders.OrderViews.ReservationView;
import com.bolsopass.outbox.OutboxEvent;
import com.bolsopass.outbox.OutboxRepository;
import com.bolsopass.outbox.OutboxWriter;
import com.bolsopass.shared.ApiException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpStatus;
import org.springframework.test.annotation.DirtiesContext;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;

/**
 * Integração com contêineres reais (RNF-07): PostgreSQL 16 e Kafka 3.8 via Testcontainers.
 *
 * <ul>
 *   <li>O relay do outbox publica no Kafka com o envelope do doc 03 (RF-10).
 *   <li>Kafka fora do ar no pagamento: nenhum evento se perde, sai quando o broker volta (RNF-04).
 *   <li>Zero overselling com o {@code SELECT ... FOR UPDATE} do PostgreSQL de verdade (RNF-02).
 * </ul>
 *
 * Sem Docker na máquina, a classe é pulada (o CI tem Docker).
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(properties = {"spring.profiles.active=postgres", "bolsopass.kafka.enabled=true"})
// Fecha o Spring antes de parar os contêineres (senão os jobs agendados reclamam do banco sumindo).
@DirtiesContext
class ContainersIntegrationTest {
  private static final String EVENT = "evt-neon"; // sem fila virtual

  @Container @ServiceConnection
  static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

  @Container @ServiceConnection
  static final KafkaContainer KAFKA = new KafkaContainer("apache/kafka:3.8.0");

  @Autowired ReservationService reservations;
  @Autowired CheckoutService checkout;
  @Autowired SeatRepository seats;
  @Autowired UserRepository users;
  @Autowired OutboxRepository outbox;
  @Autowired ObjectMapper json;

  @Test
  void relayPublicaNoKafkaComEnvelopeEHeaders() throws Exception {
    ReservationView r = reservations.reserve(newClient(), EVENT, List.of(freeSeat()));
    OutboxEvent row = awaitPublished(OutboxWriter.RESERVAS_CRIADAS, r.id());

    ConsumerRecord<String, String> msg = consumeById(OutboxWriter.RESERVAS_CRIADAS, row.getId()).getFirst();
    assertThat(msg.key()).isEqualTo(EVENT);
    assertThat(header(msg, "event-type")).isEqualTo("ReservaCriadaEvent");
    assertThat(header(msg, "content-type")).isEqualTo("application/json");

    JsonNode envelope = json.readTree(msg.value());
    assertThat(envelope.path("eventId").asText()).isEqualTo(row.getId());
    assertThat(envelope.path("eventType").asText()).isEqualTo("ReservaCriadaEvent");
    assertThat(envelope.path("eventVersion").asText()).isEqualTo("1.0");
    assertThat(envelope.path("producer").asText()).isEqualTo("bolsopass-api");
    assertThat(envelope.path("aggregateId").asText()).isEqualTo(EVENT);
    assertThat(envelope.path("occurredAt").asText()).isNotBlank();
    assertThat(envelope.path("payload").path("reservationId").asText()).isEqualTo(r.id());
    assertThat(envelope.path("payload").path("orderId").asText()).isEqualTo(r.orderId());
  }

  @Test
  void kafkaForaNoPagamento_eventoNaoSePerdeESaiQuandoVolta() throws Exception {
    String client = newClient();
    String seatId = freeSeat();
    ReservationView r = reservations.reserve(client, EVENT, List.of(seatId));
    awaitPublished(OutboxWriter.RESERVAS_CRIADAS, r.id());

    var docker = DockerClientFactory.instance().client();
    docker.pauseContainerCmd(KAFKA.getContainerId()).exec();
    try {
      // O pagamento é aprovado mesmo com o broker fora: o evento fica guardado no outbox.
      checkout.pay(client, pix(r.id(), seatId), UUID.randomUUID().toString());
      OutboxEvent pending = findRow(OutboxWriter.PEDIDOS_PAGOS, r.orderId());
      Thread.sleep(4_000); // várias rodadas do relay falhando
      assertThat(outbox.findById(pending.getId()).orElseThrow().getStatus()).isEqualTo(OutboxEvent.Status.PENDING);
    } finally {
      docker.unpauseContainerCmd(KAFKA.getContainerId()).exec();
    }

    OutboxEvent row = awaitPublished(OutboxWriter.PEDIDOS_PAGOS, r.orderId());
    List<ConsumerRecord<String, String>> delivered = consumeById(OutboxWriter.PEDIDOS_PAGOS, row.getId());
    // At-least-once: pode chegar repetido, mas sempre com o mesmo eventId (o consumidor deduplica).
    assertThat(delivered).isNotEmpty().allSatisfy(m -> assertThat(m.key()).isEqualTo(r.orderId()));
  }

  @Test
  void zeroOverselling_noPostgresDeVerdade() throws Exception {
    String seatId = freeSeat();
    int buyers = 20;
    List<String> clients = new ArrayList<>();
    for (int i = 0; i < buyers; i++) clients.add(newClient());

    ExecutorService pool = Executors.newFixedThreadPool(buyers);
    CountDownLatch start = new CountDownLatch(1);
    List<Future<Boolean>> results = new ArrayList<>();
    for (String client : clients) {
      Callable<Boolean> task =
          () -> {
            start.await();
            try {
              reservations.reserve(client, EVENT, List.of(seatId));
              return true;
            } catch (ApiException e) {
              assertThat(e.status()).isEqualTo(HttpStatus.CONFLICT);
              return false;
            }
          };
      results.add(pool.submit(task));
    }
    start.countDown();
    int winners = 0;
    for (Future<Boolean> f : results) if (f.get()) winners++;
    pool.shutdown();

    assertThat(winners).isEqualTo(1);
    assertThat(seats.findById(seatId).orElseThrow().getStatus()).isEqualTo(Seat.Status.BLOQUEADO_TEMPORARIO);
  }

  // ---- auxiliares ----

  private String newClient() {
    String id = "u-" + UUID.randomUUID();
    users.save(new User(id, "Cliente Teste", id + "@test.io", "x", Set.of(Role.ROLE_CLIENT)));
    return id;
  }

  private String freeSeat() {
    return seats.findByEventIdOrderBySectorIdAscNumberAsc(EVENT).stream()
        .filter(Seat::isSelectable)
        .filter(s -> s.getSectorId().endsWith(":B") || s.getSectorId().endsWith(":T"))
        .findFirst()
        .orElseThrow()
        .getId();
  }

  private static PayRequest pix(String reservationId, String seatId) {
    return new PayRequest(
        reservationId, Order.PaymentMethod.PIX, 1, List.of(new Holder(seatId, "Ana Souza", "52998224725")), null);
  }

  /** Linha do outbox do tópico cujo payload menciona o id (reserva ou pedido). */
  private OutboxEvent findRow(String topic, String id) {
    return outbox.findAll().stream()
        .filter(e -> e.getTopic().equals(topic) && e.getPayload().contains(id))
        .findFirst()
        .orElseThrow();
  }

  private OutboxEvent awaitPublished(String topic, String id) {
    return await()
        .atMost(Duration.ofSeconds(30))
        .until(() -> findRow(topic, id), row -> row.getStatus() == OutboxEvent.Status.PUBLISHED);
  }

  /** Lê o tópico do início e devolve as mensagens com o event-id pedido. */
  private List<ConsumerRecord<String, String>> consumeById(String topic, String eventId) {
    Map<String, Object> props =
        Map.of(
            ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers(),
            ConsumerConfig.GROUP_ID_CONFIG, "teste-" + UUID.randomUUID(),
            ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
            ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
            ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
    try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props)) {
      consumer.subscribe(List.of(topic));
      List<ConsumerRecord<String, String>> found = new ArrayList<>();
      await()
          .atMost(Duration.ofSeconds(30))
          .until(
              () -> {
                for (ConsumerRecord<String, String> m : consumer.poll(Duration.ofMillis(500)))
                  if (eventId.equals(header(m, "event-id"))) found.add(m);
                return !found.isEmpty();
              });
      return found;
    }
  }

  private static String header(ConsumerRecord<String, String> m, String name) {
    var h = m.headers().lastHeader(name);
    return h == null ? null : new String(h.value(), StandardCharsets.UTF_8);
  }
}
