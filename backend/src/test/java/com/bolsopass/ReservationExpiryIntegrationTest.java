package com.bolsopass;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bolsopass.iam.Role;
import com.bolsopass.iam.User;
import com.bolsopass.iam.UserRepository;
import com.bolsopass.inventory.ReservationExpiryJob;
import com.bolsopass.inventory.ReservationService;
import com.bolsopass.inventory.Seat;
import com.bolsopass.inventory.SeatRepository;
import com.bolsopass.orders.CheckoutService;
import com.bolsopass.orders.CheckoutService.Holder;
import com.bolsopass.orders.CheckoutService.PayRequest;
import com.bolsopass.orders.Order;
import com.bolsopass.orders.OrderRepository;
import com.bolsopass.orders.OrderStatus;
import com.bolsopass.orders.OrderViews.ReservationView;
import com.bolsopass.shared.ApiException;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;

/** TTL encurtado para 1 s só neste teste. */
@SpringBootTest(properties = "bolsopass.reservation-ttl=1s")
class ReservationExpiryIntegrationTest {
  @Autowired ReservationService reservations;
  @Autowired ReservationExpiryJob job;
  @Autowired CheckoutService checkout;
  @Autowired SeatRepository seats;
  @Autowired OrderRepository orders;
  @Autowired UserRepository users;

  @Test
  void reservaExpiradaDevolveAssentoEBloqueiaPagamento() throws Exception {
    String client = "u-" + UUID.randomUUID();
    users.save(new User(client, "Cliente Teste", client + "@test.io", "x", Set.of(Role.ROLE_CLIENT)));
    String seatId =
        seats.findByEventIdOrderBySectorIdAscNumberAsc("evt-metal").stream()
            .filter(Seat::isSelectable).findFirst().orElseThrow().getId();

    ReservationView r = reservations.reserve(client, "evt-metal", List.of(seatId));
    Thread.sleep(1_200);

    // Antes do job rodar, o pagamento já é recusado com 410.
    PayRequest req =
        new PayRequest(r.id(), Order.PaymentMethod.PIX, 1, List.of(new Holder(seatId, "Ana Souza", "52998224725")), null);
    assertThatThrownBy(() -> checkout.pay(client, req, UUID.randomUUID().toString()))
        .isInstanceOf(ApiException.class)
        .extracting(e -> ((ApiException) e).status())
        .isEqualTo(HttpStatus.GONE);

    job.expireDue();
    assertThat(orders.findById(r.orderId()).orElseThrow().getStatus()).isEqualTo(OrderStatus.EXPIRADO);
    assertThat(seats.findById(seatId).orElseThrow().getStatus()).isEqualTo(Seat.Status.DISPONIVEL);
  }
}
