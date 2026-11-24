package com.bolsopass.catalog.domain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Agregado de Evento e Lotes do Catalogo (RF-04 / RF-05).
 * Garante as invariantes de dominio:
 * - lot.endsAt > lot.startsAt
 * - sum(lot.quota) <= venueCapacityTotal
 */
public class EventCatalogItem {

    private final UUID eventId;
    private final UUID organizerId;
    private final String title;
    private final String venueName;
    private final int venueCapacityTotal;
    private final Instant startsAt;
    private final Instant endsAt;
    private final List<TicketLot> lots = new ArrayList<>();

    public record TicketLot(
            UUID lotId,
            String name,
            long priceCents,
            int quota,
            Instant startsAt,
            Instant endsAt
    ) {
        public TicketLot {
            Objects.requireNonNull(lotId, "Lot ID cannot be null");
            if (name == null || name.isBlank()) {
                throw new IllegalArgumentException("Lot name cannot be blank");
            }
            if (priceCents <= 0) {
                throw new IllegalArgumentException("Lot price must be positive");
            }
            if (quota <= 0) {
                throw new IllegalArgumentException("Lot quota must be positive");
            }
            Objects.requireNonNull(startsAt, "startsAt is required");
            Objects.requireNonNull(endsAt, "endsAt is required");
            if (!endsAt.isAfter(startsAt)) {
                throw new IllegalArgumentException("Lot endsAt must be strictly after startsAt");
            }
        }

        public boolean isOnSaleAt(Instant now) {
            Objects.requireNonNull(now, "now is required");
            return !now.isBefore(startsAt) && now.isBefore(endsAt);
        }
    }

    public EventCatalogItem(
            UUID eventId,
            UUID organizerId,
            String title,
            String venueName,
            int venueCapacityTotal,
            Instant startsAt,
            Instant endsAt
    ) {
        this.eventId = Objects.requireNonNull(eventId, "Event ID cannot be null");
        this.organizerId = Objects.requireNonNull(organizerId, "Organizer ID cannot be null");
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("Event title cannot be blank");
        }
        if (venueName == null || venueName.isBlank()) {
            throw new IllegalArgumentException("Venue name cannot be blank");
        }
        if (venueCapacityTotal <= 0) {
            throw new IllegalArgumentException("Venue capacity must be positive");
        }
        Objects.requireNonNull(startsAt, "Event startsAt is required");
        Objects.requireNonNull(endsAt, "Event endsAt is required");
        if (!endsAt.isAfter(startsAt)) {
            throw new IllegalArgumentException("Event endsAt must be after startsAt");
        }
        this.title = title.trim();
        this.venueName = venueName.trim();
        this.venueCapacityTotal = venueCapacityTotal;
        this.startsAt = startsAt;
        this.endsAt = endsAt;
    }

    public void addLot(TicketLot lot) {
        Objects.requireNonNull(lot, "Lot cannot be null");
        int currentAllocated = lots.stream().mapToInt(TicketLot::quota).sum();
        if (currentAllocated + lot.quota() > venueCapacityTotal) {
            throw new IllegalStateException("Total lot quota (" + (currentAllocated + lot.quota())
                    + ") exceeds venue capacity (" + venueCapacityTotal + ")");
        }
        lots.add(lot);
    }

    public int getTotalAllocatedQuota() {
        return lots.stream().mapToInt(TicketLot::quota).sum();
    }

    public int getRemainingCapacity() {
        return venueCapacityTotal - getTotalAllocatedQuota();
    }

    public List<TicketLot> getLotsOnSaleAt(Instant now) {
        Objects.requireNonNull(now, "now is required");
        return lots.stream()
                .filter(lot -> lot.isOnSaleAt(now))
                .toList();
    }

    public boolean isUpcoming(Instant now) {
        Objects.requireNonNull(now, "now is required");
        return now.isBefore(startsAt);
    }

    public UUID getEventId() {
        return eventId;
    }

    public UUID getOrganizerId() {
        return organizerId;
    }

    public String getTitle() {
        return title;
    }

    public String getVenueName() {
        return venueName;
    }

    public int getVenueCapacityTotal() {
        return venueCapacityTotal;
    }

    public Instant getStartsAt() {
        return startsAt;
    }

    public Instant getEndsAt() {
        return endsAt;
    }

    public List<TicketLot> getLots() {
        return Collections.unmodifiableList(lots);
    }
}

