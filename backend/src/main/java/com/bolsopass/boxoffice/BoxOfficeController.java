package com.bolsopass.boxoffice;

import com.bolsopass.catalog.CatalogService.SeatMapView;
import com.bolsopass.orders.CheckoutService;
import com.bolsopass.orders.OrderViews.OrderView;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Venda no balcão da bilheteria (operador de portaria ou terminal de venda; o admin não vende). */
@RestController
@RequestMapping("/api/v1/operator/box-office")
@PreAuthorize("hasAnyRole('OPERATOR', 'SELLER')")
public class BoxOfficeController {
  public record ReserveRequest(String customerId, List<String> seatIds) {}

  public record PayRequest(String customerId, CheckoutService.PayRequest payment) {}

  public record GiveUpRequest(String customerId) {}

  private final BoxOfficeService service;

  public BoxOfficeController(BoxOfficeService service) {
    this.service = service;
  }

  /** Busca o cliente pelo CPF: nome e telefone para confirmar no balcão. */
  @GetMapping("/customers")
  BoxOfficeService.Customer find(@RequestParam String cpf) {
    return service.find(cpf);
  }

  /** Cadastro rápido de quem ainda não tem conta. */
  @PostMapping("/customers")
  @ResponseStatus(HttpStatus.CREATED)
  BoxOfficeService.Customer register(@RequestBody BoxOfficeService.NewCustomer in) {
    return service.register(in);
  }

  /** Novo código de ativação (vencido, perdido ou conta antiga sem código). */
  @PostMapping("/customers/{customerId}/activation-code")
  BoxOfficeService.Customer activationCode(@PathVariable String customerId) {
    return service.issueActivationCode(customerId);
  }

  /** Mapa de assentos do evento (lugares vagos para vender). */
  @GetMapping("/events/{eventId}/seats")
  SeatMapView seats(@PathVariable String eventId) {
    return service.seatMap(eventId);
  }

  @PostMapping("/events/{eventId}/reservations")
  @ResponseStatus(HttpStatus.CREATED)
  Map<String, Object> reserve(@PathVariable String eventId, @RequestBody ReserveRequest req) {
    return service.reserve(eventId, req.customerId(), req.seatIds());
  }

  @PostMapping("/checkout")
  OrderView pay(@RequestBody PayRequest req, @RequestHeader(value = "Idempotency-Key", required = false) String key) {
    return service.pay(req.customerId(), req.payment(), key);
  }

  @PostMapping("/reservations/{reservationId}/give-up")
  ResponseEntity<Void> giveUp(@PathVariable String reservationId, @RequestBody GiveUpRequest req) {
    service.giveUp(req.customerId(), reservationId);
    return ResponseEntity.noContent().build();
  }
}
