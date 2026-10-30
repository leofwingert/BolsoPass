package com.bolsopass.orders;

import com.bolsopass.iam.CurrentUser;
import com.bolsopass.orders.CheckoutService.PayRequest;
import com.bolsopass.orders.OrderViews.OrderView;
import com.bolsopass.orders.OrderViews.ReservationView;
import com.bolsopass.shared.ApiException;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@PreAuthorize("hasRole('CLIENT')")
public class OrderController {
  private final CheckoutService checkout;
  private final OrderRepository orders;
  private final OrderViews views;

  public OrderController(CheckoutService checkout, OrderRepository orders, OrderViews views) {
    this.checkout = checkout;
    this.orders = orders;
    this.views = views;
  }

  public record ReservationWithOrder(ReservationView reservation, OrderView order) {}

  @GetMapping("/reservations/{id}")
  ReservationWithOrder reservation(@PathVariable String id) {
    var pair = checkout.reservation(CurrentUser.id(), id);
    return new ReservationWithOrder(pair.getKey(), pair.getValue());
  }

  @PostMapping("/reservations/{id}/give-up")
  ResponseEntity<Void> giveUp(@PathVariable String id) {
    checkout.giveUp(CurrentUser.id(), id);
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/checkout")
  OrderView pay(
      @RequestHeader(value = "Idempotency-Key", required = false) String key, @RequestBody PayRequest req) {
    return checkout.pay(CurrentUser.id(), req, key);
  }

  /** Histórico do comprador: só pedidos pagos (o resto é tentativa de compra). */
  @GetMapping("/orders/me")
  @Transactional(readOnly = true)
  List<OrderView> mine() {
    return orders.findByUserIdAndStatusOrderByCreatedAtDesc(CurrentUser.id(), OrderStatus.PAGO).stream()
        .map(views::order)
        .toList();
  }

  @GetMapping("/orders/{id}")
  @Transactional(readOnly = true)
  OrderView one(@PathVariable String id) {
    Order o = orders.findById(id).orElseThrow(() -> ApiException.notFound("Pedido"));
    if (!o.getUserId().equals(CurrentUser.id())) throw ApiException.forbidden("Pedido não pertence a você");
    return views.order(o);
  }
}
