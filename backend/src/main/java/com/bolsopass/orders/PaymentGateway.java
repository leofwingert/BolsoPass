package com.bolsopass.orders;

import org.springframework.stereotype.Component;

/**
 * Simulador do gateway financeiro (RF-11).
 * Cartão terminado em 0000 = recusa por saldo insuficiente; PIX e demais cartões aprovam.
 */
@Component
public class PaymentGateway {
  public record Result(boolean approved, String reason) {}

  public Result charge(String idempotencyKey, Order.PaymentMethod method, String cardLast4, long amountCents) {
    if (method == Order.PaymentMethod.CREDIT_CARD && "0000".equals(cardLast4)) {
      return new Result(false, "Cartão recusado: saldo insuficiente");
    }
    return new Result(true, null);
  }
}
