package com.bolsopass.orders.api;

import com.bolsopass.inventory.domain.ReservationExpiredException;
import com.bolsopass.orders.domain.IdempotencyConflictException;
import com.bolsopass.orders.domain.OrderStatus;
import com.bolsopass.orders.service.CheckoutService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping({"/checkout", "/api/v1/checkout"})
public class CheckoutController {

    private final CheckoutService checkoutService;

    public record CheckoutRequest(
            UUID reservation_id,
            String payment_method,
            Integer installments,
            String payment_token
    ) {
    }

    public CheckoutController(CheckoutService checkoutService) {
        this.checkoutService = checkoutService;
    }

    @PostMapping
    public ResponseEntity<?> checkout(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestBody CheckoutRequest request
    ) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "MISSING_IDEMPOTENCY_KEY",
                    "message", "O cabecalho HTTP Idempotency-Key e obrigatorio."
            ));
        }
        if (request == null || request.reservation_id() == null) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "INVALID_REQUEST",
                    "message", "O campo reservation_id e obrigatorio."
            ));
        }

        try {
            CheckoutService.CheckoutResponse response = checkoutService.processCheckout(
                    new CheckoutService.CheckoutCommand(
                            idempotencyKey,
                            request.reservation_id(),
                            request.payment_method() == null ? "CREDIT_CARD" : request.payment_method(),
                            request.installments() == null ? 1 : request.installments(),
                            request.payment_token() == null ? "tok_visa_ok" : request.payment_token()
                    )
            );

            if (response.status() == OrderStatus.RECUSADO) {
                return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(Map.of(
                        "order_id", response.orderId().toString(),
                        "reservation_id", response.reservationId().toString(),
                        "status", response.status().name(),
                        "decline_reason", response.declineReason()
                ));
            }

            return ResponseEntity.ok(Map.of(
                    "order_id", response.orderId().toString(),
                    "reservation_id", response.reservationId().toString(),
                    "status", response.status().name(),
                    "amount_cents", response.amountCents(),
                    "paid_at", response.paidAt().toString()
            ));
        } catch (ReservationExpiredException ex) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                    "error", "RESERVATION_EXPIRED",
                    "message", ex.getMessage()
            ));
        } catch (IdempotencyConflictException ex) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                    "error", "IDEMPOTENCY_CONFLICT",
                    "message", ex.getMessage()
            ));
        }
    }
}

