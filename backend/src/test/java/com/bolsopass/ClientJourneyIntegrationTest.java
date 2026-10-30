package com.bolsopass;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bolsopass.iam.Role;
import com.bolsopass.iam.User;
import com.bolsopass.iam.UserRepository;
import com.bolsopass.inventory.ReservationService;
import com.bolsopass.inventory.Seat;
import com.bolsopass.inventory.SeatRepository;
import com.bolsopass.orders.CheckoutService;
import com.bolsopass.orders.CheckoutService.Card;
import com.bolsopass.orders.CheckoutService.Holder;
import com.bolsopass.orders.CheckoutService.PayRequest;
import com.bolsopass.orders.Order;
import com.bolsopass.orders.OrderStatus;
import com.bolsopass.orders.OrderViews.OrderView;
import com.bolsopass.orders.OrderViews.ReservationView;
import com.bolsopass.outbox.OutboxRepository;
import com.bolsopass.shared.ApiException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;

/**
 * Jornada do cliente contra o banco (H2 no perfil dev).
 * TODO: rodar também com Testcontainers/PostgreSQL quando o Docker estiver disponível no CI.
 */
@SpringBootTest
class ClientJourneyIntegrationTest {
  private static final String EVENT = "evt-neon"; // sem fila virtual

  @Autowired ReservationService reservations;
  @Autowired CheckoutService checkout;
  @Autowired SeatRepository seats;
  @Autowired UserRepository users;
  @Autowired OutboxRepository outbox;

  private String freeSeat() {
    return seats.findByEventIdOrderBySectorIdAscNumberAsc(EVENT).stream()
        .filter(Seat::isSelectable)
        .filter(s -> s.getSectorId().endsWith(":B") || s.getSectorId().endsWith(":T"))
        .findFirst()
        .orElseThrow()
        .getId();
  }

  private String newClient() {
    String id = "u-" + UUID.randomUUID();
    users.save(new User(id, "Cliente Teste", id + "@test.io", "x", Set.of(Role.ROLE_CLIENT)));
    return id;
  }

  @Test
  void zeroOverselling_apenasUmCompradorLevaOAssento() throws Exception {
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

  @Test
  void pagamentoIdempotente_mesmaChaveNaoCobraDuasVezes() {
    String client = newClient();
    String seatId = freeSeat();
    ReservationView r = reservations.reserve(client, EVENT, List.of(seatId));
    PayRequest req =
        new PayRequest(
            r.id(), Order.PaymentMethod.CREDIT_CARD, 1,
            List.of(new Holder(seatId, "Ana Souza", "529.982.247-25")),
            new Card("4532 1111 2222 8841", "ANA SOUZA", "08/29", "123"));
    String key = UUID.randomUUID().toString();
    long eventsBefore = outbox.count();

    OrderView first = checkout.pay(client, req, key);
    OrderView replay = checkout.pay(client, req, key);

    assertThat(first.status()).isEqualTo(OrderStatus.PAGO);
    assertThat(replay.id()).isEqualTo(first.id());
    assertThat(first.items()).allSatisfy(i -> assertThat(i.ticketId()).isNotBlank());
    assertThat(seats.findById(seatId).orElseThrow().getStatus()).isEqualTo(Seat.Status.VENDIDO);
    // Só um PedidoPago no outbox, apesar das duas chamadas.
    assertThat(outbox.count() - eventsBefore).isEqualTo(1);

    // Chave nova num pedido PAGO: a FSM recusa.
    assertThatThrownBy(() -> checkout.pay(client, req, UUID.randomUUID().toString()))
        .isInstanceOf(ApiException.class)
        .extracting(e -> ((ApiException) e).status())
        .isEqualTo(HttpStatus.CONFLICT);
  }

  @Test
  void pagamentoRecusado_permiteNovaTentativaOuDesistencia() {
    String client = newClient();
    String seatId = freeSeat();
    ReservationView r = reservations.reserve(client, EVENT, List.of(seatId));
    List<Holder> holders = List.of(new Holder(seatId, "Ana Souza", "529.982.247-25"));

    OrderView declined =
        checkout.pay(
            client,
            new PayRequest(r.id(), Order.PaymentMethod.CREDIT_CARD, 1, holders,
                new Card("4000 0000 0000 0000", "ANA", "08/29", "123")),
            UUID.randomUUID().toString());
    assertThat(declined.status()).isEqualTo(OrderStatus.RECUSADO);
    assertThat(declined.declineReason()).contains("saldo insuficiente");

    checkout.giveUp(client, r.id());
    assertThat(seats.findById(seatId).orElseThrow().getStatus()).isEqualTo(Seat.Status.DISPONIVEL);
  }

  @Test
  void cpfInvalidoEhRecusado() {
    String client = newClient();
    String seatId = freeSeat();
    ReservationView r = reservations.reserve(client, EVENT, List.of(seatId));
    PayRequest req =
        new PayRequest(r.id(), Order.PaymentMethod.PIX, 1, List.of(new Holder(seatId, "Ana Souza", "111.111.111-11")), null);
    assertThatThrownBy(() -> checkout.pay(client, req, UUID.randomUUID().toString()))
        .isInstanceOf(ApiException.class)
        .hasMessageContaining("CPF inválido");
  }

  @Test
  void eventoComFilaExigeAdmissao() {
    String client = newClient();
    String seat =
        seats.findByEventIdOrderBySectorIdAscNumberAsc("evt-synthwave").stream()
            .filter(Seat::isSelectable).findFirst().orElseThrow().getId();
    assertThatThrownBy(() -> reservations.reserve(client, "evt-synthwave", List.of(seat)))
        .hasMessageContaining("sala de espera");
  }
}
