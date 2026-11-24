package com.bolsopass.integration;

import com.bolsopass.inventory.service.InventoryService;
import com.bolsopass.orders.domain.OrderStatus;
import com.bolsopass.orders.domain.PedidoPagoEvent;
import com.bolsopass.orders.infrastructure.OrderEntity;
import com.bolsopass.orders.infrastructure.OrderJpaRepository;
import com.bolsopass.orders.infrastructure.OutboxEventEntity;
import com.bolsopass.orders.infrastructure.OutboxEventRepository;
import com.bolsopass.orders.service.OutboxRelayWorker;
import com.bolsopass.orders.service.PaymentGatewaySimulator;
import com.bolsopass.ticketing.domain.DigitalTicket;
import com.bolsopass.ticketing.domain.ScanResultStatus;
import com.bolsopass.ticketing.infrastructure.ProcessedMessageRepository;
import com.bolsopass.ticketing.service.TicketingService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@EmbeddedKafka(partitions = 3, topics = {"pedidos.pagos", "reservas.expiradas"})
class TransactionalOutboxIntegrationTest {

    private static PostgreSQLContainer<?> postgresContainer;

    static {
        try {
            PostgreSQLContainer<?> container = new PostgreSQLContainer<>("postgres:15-alpine")
                    .withDatabaseName("bolsopass_it")
                    .withUsername("bolsopass")
                    .withPassword("bolsopass");
            container.start();
            postgresContainer = container;
        } catch (Throwable ex) {
            postgresContainer = null;
        }
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        if (postgresContainer != null && postgresContainer.isRunning()) {
            registry.add("spring.datasource.url", postgresContainer::getJdbcUrl);
            registry.add("spring.datasource.username", postgresContainer::getUsername);
            registry.add("spring.datasource.password", postgresContainer::getPassword);
            registry.add("spring.datasource.driver-class-name", postgresContainer::getDriverClassName);
        }
        registry.add("spring.kafka.bootstrap-servers", () -> "${spring.embedded.kafka.brokers}");
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private InventoryService inventoryService;

    @Autowired
    private PaymentGatewaySimulator paymentGatewaySimulator;

    @Autowired
    private OrderJpaRepository orderJpaRepository;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Autowired
    private OutboxRelayWorker outboxRelayWorker;

    @Autowired
    private TicketingService ticketingService;

    @Autowired
    private ProcessedMessageRepository processedMessageRepository;

    @BeforeEach
    void setUp() {
        outboxRelayWorker.setBrokerAvailable(true);
        paymentGatewaySimulator.resetCounter();
    }

    @Test
    @DisplayName("IAM REST: Login JWT RS256, Rotacao de Refresh Token e Bloqueio de Reuso Malicioso (US-01 / US-02)")
    void shouldAuthenticateRotateRefreshTokenAndBlockReplayViaRest() throws Exception {
        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"gabriel@bolsopass.com\",\"password\":\"Pass123!\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.access_token").isNotEmpty())
                .andExpect(jsonPath("$.refresh_token").isNotEmpty())
                .andExpect(jsonPath("$.expires_in").value(900))
                .andExpect(jsonPath("$.roles[0]").value("ROLE_CLIENT"))
                .andReturn();

        JsonNode loginJson = objectMapper.readTree(loginResult.getResponse().getContentAsString());
        String refreshTokenV1 = loginJson.get("refresh_token").asText();

        // Rotaciona Refresh Token V1 -> V2
        MvcResult refreshResult = mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refresh_token\":\"" + refreshTokenV1 + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.refresh_token").isNotEmpty())
                .andReturn();

        JsonNode refreshJson = objectMapper.readTree(refreshResult.getResponse().getContentAsString());
        String refreshTokenV2 = refreshJson.get("refresh_token").asText();
        assertThat(refreshTokenV2).isNotEqualTo(refreshTokenV1);

        // Reuso malicioso do Refresh Token V1 ja rotacionado retorna HTTP 401 e invalida toda a familia (inclusive V2)
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refresh_token\":\"" + refreshTokenV1 + "\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("TOKEN_REVOKED_OR_INVALID"));

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refresh_token\":\"" + refreshTokenV2 + "\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Concorrencia HTTP REST: 100 requisicoes simultaneas para o mesmo assento -> 1 HTTP 201 e 99 HTTP 409 (Zero Overselling)")
    void shouldEnforceZeroOversellingAcross100ConcurrentRestRequests() throws Exception {
        UUID eventId = UUID.randomUUID();
        String contestedSeat = "VIP-99";
        inventoryService.registerSeat(eventId, contestedSeat, "Camarote VIP", 95000L);

        int threads = 100;
        ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor();
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);

        AtomicInteger created201 = new AtomicInteger(0);
        AtomicInteger conflict409 = new AtomicInteger(0);

        for (int i = 0; i < threads; i++) {
            final UUID userId = UUID.randomUUID();
            pool.submit(() -> {
                ready.countDown();
                try {
                    start.await();
                    String payload = String.format("{\"event_id\":\"%s\",\"seat_id\":\"%s\",\"user_id\":\"%s\"}",
                            eventId, contestedSeat, userId);
                    int statusCode = mockMvc.perform(post("/api/v1/reservations")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(payload))
                            .andReturn()
                            .getResponse()
                            .getStatus();
                    if (statusCode == 201) {
                        created201.incrementAndGet();
                    } else if (statusCode == 409) {
                        conflict409.incrementAndGet();
                    }
                } catch (Exception e) {
                    throw new RuntimeException(e);
                } finally {
                    done.countDown();
                }
            });
        }

        assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
        start.countDown();
        assertThat(done.await(15, TimeUnit.SECONDS)).isTrue();
        pool.shutdownNow();

        assertThat(created201.get()).isEqualTo(1);
        assertThat(conflict409.get()).isEqualTo(99);
    }

    @Test
    @DisplayName("Idempotency-Key & Transactional Outbox com Simulador de Queda e Recuperacao do Kafka (US-09 / US-10 / RNF-04 / RNF-05)")
    void shouldPersistAtomicallyDuringKafkaOutageDeduplicateIdempotencyKeyAndRelayOnRecovery() throws Exception {
        // 1. Cria reserva temporaria via REST
        UUID eventId = InventoryService.DEFAULT_EVENT_ID;
        MvcResult resResult = mockMvc.perform(post("/api/v1/reservations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"event_id\":\"" + eventId + "\",\"seat_id\":\"A-15\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.ttl_seconds").value(600))
                .andReturn();

        String reservationId = objectMapper.readTree(resResult.getResponse().getContentAsString())
                .get("reservation_id").asText();

        // 2. Simula queda/indisponibilidade do cluster Apache Kafka no exato momento do pagamento
        outboxRelayWorker.setBrokerAvailable(false);

        String idempotencyKey = UUID.randomUUID().toString();
        String checkoutJsonBody = String.format(
                "{\"reservation_id\":\"%s\",\"payment_method\":\"CREDIT_CARD\",\"installments\":1,\"payment_token\":\"tok_visa_ok\"}",
                reservationId
        );

        // 3. Executa 3 requisicoes consecutivas com a mesma Idempotency-Key (teste de deduplicacao RNF-05)
        String firstOrderId = null;
        for (int attempt = 1; attempt <= 3; attempt++) {
            MvcResult checkoutResult = mockMvc.perform(post("/api/v1/checkout")
                            .header("Idempotency-Key", idempotencyKey)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(checkoutJsonBody))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("PAGO"))
                    .andExpect(jsonPath("$.amount_cents").value(38500))
                    .andReturn();

            String currentOrderId = objectMapper.readTree(checkoutResult.getResponse().getContentAsString())
                    .get("order_id").asText();
            if (firstOrderId == null) {
                firstOrderId = currentOrderId;
            } else {
                assertThat(currentOrderId).isEqualTo(firstOrderId);
            }
        }

        // O simulador financeiro recebeu APENAS 1 instrucao de debito para as 3 requisicoes!
        assertThat(paymentGatewaySimulator.getTotalChargeAttempts()).isEqualTo(1);

        // 4. Verifica que o pedido foi comitado como PAGO e o evento está salvo como PENDING na tabela outbox_events
        UUID orderId = UUID.fromString(firstOrderId);
        Optional<OrderEntity> savedOrder = orderJpaRepository.findById(orderId);
        assertThat(savedOrder).isPresent();
        assertThat(savedOrder.get().getStatus()).isEqualTo(OrderStatus.PAGO);

        // Enquanto o Kafka esta indisponivel, o relay worker nao perde a mensagem (permanece PENDING)
        assertThat(outboxRelayWorker.relayPendingEvents()).isZero();
        OutboxEventEntity pendingOutbox = outboxEventRepository.findAll().stream()
                .filter(e -> e.getAggregateId().equals(orderId.toString()))
                .findFirst()
                .orElseThrow();
        assertThat(pendingOutbox.getStatus()).isEqualTo("PENDING");

        // 5. Kafka restabelece conectividade -> Relay Worker publica com FOR UPDATE SKIP LOCKED no topico pedidos.pagos
        outboxRelayWorker.setBrokerAvailable(true);
        int published = outboxRelayWorker.relayPendingEvents();
        assertThat(published).isGreaterThanOrEqualTo(1);

        OutboxEventEntity publishedOutbox = outboxEventRepository.findById(pendingOutbox.getId()).orElseThrow();
        assertThat(publishedOutbox.getStatus()).isEqualTo("PUBLISHED");
        assertThat(publishedOutbox.getPublishedAt()).isNotNull();
    }

    @Test
    @DisplayName("Ticketing & Despacho: Consumo Idempotente via processed_messages, PDF com QR ECDSA P-256 e Validacao na Catraca (US-12 / US-13 / US-14 / US-15)")
    void shouldConsumePaymentEventIdempotentlyGenerateSignedPdfAndVerifyAtGate() {
        UUID orderId = UUID.randomUUID();
        UUID clientId = UUID.randomUUID();
        UUID eventId = InventoryService.DEFAULT_EVENT_ID;
        PedidoPagoEvent paidEvent = new PedidoPagoEvent(orderId, clientId, eventId, "B-08", 35000L, Instant.now());
        String kafkaMessageId = "kafka_msg_" + orderId;

        // 1a entrega da mensagem pelo Kafka -> processa, grava em processed_messages, gera PDF e notifica WebSocket
        Optional<DigitalTicket> firstDelivery = ticketingService.consumeOrderPaidIdempotently(kafkaMessageId, paidEvent);
        assertThat(firstDelivery).isPresent();
        assertThat(processedMessageRepository.existsById(kafkaMessageId)).isTrue();

        DigitalTicket ticket = firstDelivery.get();
        String pdfHeader = new String(ticket.getPdfContent(), 0, 5, StandardCharsets.US_ASCII);
        assertThat(pdfHeader).isEqualTo("%PDF-");
        assertThat(ticket.getQrSignature()).isNotBlank();
        assertThat(ticketingService.getWebsocketNotifications())
                .anyMatch(msg -> msg.startsWith("/ws/orders/" + orderId + ":TICKET_ISSUED:"));

        // 2a entrega duplicada da mesma mensagem (At-Least-Once retry) -> descartada como no-op idempotente!
        int countBeforeDuplicate = ticketingService.countIssuedTickets();
        Optional<DigitalTicket> duplicateDelivery = ticketingService.consumeOrderPaidIdempotently(kafkaMessageId, paidEvent);
        assertThat(duplicateDelivery).isEmpty();
        assertThat(ticketingService.countIssuedTickets()).isEqualTo(countBeforeDuplicate);

        // Validacao na catraca (online/offline via ECDSA P-256): 1o acesso AUTORIZADO, 2o acesso DUPLICATA
        assertThat(ticketingService.scanTicketAtGate(ticket.getTicketId(), ticket.getQrPayload(), ticket.getQrSignature()))
                .isEqualTo(ScanResultStatus.AUTORIZADO);
        assertThat(ticketingService.scanTicketAtGate(ticket.getTicketId(), ticket.getQrPayload(), ticket.getQrSignature()))
                .isEqualTo(ScanResultStatus.DUPLICATA);
    }
}
