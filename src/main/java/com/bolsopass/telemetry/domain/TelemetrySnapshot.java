package com.bolsopass.telemetry.domain;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Agregador de Telemetria em Tempo Real para Abertura de Lotes sob Flash Crowd (RF-15 / RF-16 / US-18).
 */
public class TelemetrySnapshot {

    private final AtomicLong totalRequests = new AtomicLong(0);
    private final AtomicLong reservationsCreated = new AtomicLong(0);
    private final AtomicLong reservationsRejectedConflict = new AtomicLong(0);
    private final AtomicLong reservationsExpired = new AtomicLong(0);
    private final AtomicLong ordersPaid = new AtomicLong(0);
    private final AtomicLong totalRevenueCents = new AtomicLong(0);
    private final AtomicLong oversellingIncidents = new AtomicLong(0);

    public void recordReservationAttempt(boolean success) {
        totalRequests.incrementAndGet();
        if (success) {
            reservationsCreated.incrementAndGet();
        } else {
            reservationsRejectedConflict.incrementAndGet();
        }
    }

    public void recordReservationExpired() {
        reservationsExpired.incrementAndGet();
    }

    public void recordOrderPaid(long amountCents) {
        if (amountCents <= 0) {
            throw new IllegalArgumentException("amountCents must be positive");
        }
        ordersPaid.incrementAndGet();
        totalRevenueCents.addAndGet(amountCents);
    }

    public double getCheckoutConversionRatePercent() {
        long created = reservationsCreated.get();
        if (created == 0) {
            return 0.0;
        }
        return (ordersPaid.get() * 100.0) / created;
    }

    public long getTotalRequests() {
        return totalRequests.get();
    }

    public long getReservationsCreated() {
        return reservationsCreated.get();
    }

    public long getReservationsRejectedConflict() {
        return reservationsRejectedConflict.get();
    }

    public long getReservationsExpired() {
        return reservationsExpired.get();
    }

    public long getOrdersPaid() {
        return ordersPaid.get();
    }

    public long getTotalRevenueCents() {
        return totalRevenueCents.get();
    }

    public long getOversellingIncidents() {
        return oversellingIncidents.get();
    }
}

