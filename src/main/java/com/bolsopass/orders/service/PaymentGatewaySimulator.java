package com.bolsopass.orders.service;

import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Simulador de Gateway Financeiro (RF-11).
 * Reproduz autorizacoes de cartao/PIX, recusas por saldo insuficiente e contabiliza
 * chamadas reais de cobranca para auditoria de idempotencia.
 */
@Component
public class PaymentGatewaySimulator {

    private final AtomicInteger totalChargeAttempts = new AtomicInteger(0);

    public record GatewayAuthorizationResult(boolean approved, String transactionReference, String declineReason) {
    }

    public GatewayAuthorizationResult charge(UUID orderId, long amountCents, String paymentMethod, String paymentToken) {
        totalChargeAttempts.incrementAndGet();
        if (paymentToken != null && paymentToken.toLowerCase().contains("decline")) {
            return new GatewayAuthorizationResult(false, null, "INSUFFICIENT_FUNDS");
        }
        return new GatewayAuthorizationResult(true, "gw_tx_" + orderId, null);
    }

    public int getTotalChargeAttempts() {
        return totalChargeAttempts.get();
    }

    public void resetCounter() {
        totalChargeAttempts.set(0);
    }
}

