package com.bolsopass.inventory.api;

import com.bolsopass.inventory.domain.Reservation;
import com.bolsopass.inventory.domain.SeatAlreadyReservedException;
import com.bolsopass.inventory.service.InventoryService;
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
@RequestMapping({"/reservations", "/api/v1/reservations"})
public class ReservationController {

    private final InventoryService inventoryService;

    public record CreateReservationRequest(UUID event_id, String seat_id, UUID user_id) {
    }

    public ReservationController(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    @PostMapping
    public ResponseEntity<?> createReservation(
            @RequestBody CreateReservationRequest request,
            @RequestHeader(value = "X-User-Id", required = false) UUID headerUserId
    ) {
        if (request.event_id() == null || request.seat_id() == null || request.seat_id().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "INVALID_REQUEST",
                    "message", "event_id e seat_id sao obrigatorios."
            ));
        }
        UUID userId = request.user_id() != null ? request.user_id()
                : (headerUserId != null ? headerUserId : UUID.fromString("11111111-1111-1111-1111-111111111111"));
        try {
            Reservation reservation = inventoryService.allocateSeat(request.event_id(), request.seat_id(), userId);
            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                    "reservation_id", reservation.getReservationId().toString(),
                    "event_id", reservation.getEventId().toString(),
                    "seat_id", reservation.getSeatId(),
                    "status", reservation.getStatus().name(),
                    "expires_at", reservation.getExpiresAt().toString(),
                    "ttl_seconds", reservation.getTtlSeconds()
            ));
        } catch (SeatAlreadyReservedException ex) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                    "error", "SEAT_ALREADY_RESERVED",
                    "message", ex.getMessage()
            ));
        }
    }
}

