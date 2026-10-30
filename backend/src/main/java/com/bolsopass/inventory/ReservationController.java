package com.bolsopass.inventory;

import com.bolsopass.iam.CurrentUser;
import com.bolsopass.orders.OrderViews.ReservationView;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/reservations")
@PreAuthorize("hasRole('CLIENT')")
public class ReservationController {
  private final ReservationService service;

  public ReservationController(ReservationService service) {
    this.service = service;
  }

  public record ReserveRequest(@NotBlank String eventId, @NotEmpty @Size(max = 10) List<String> seatIds) {}

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  ReservationView reserve(@Valid @RequestBody ReserveRequest req) {
    return service.reserve(CurrentUser.id(), req.eventId(), req.seatIds());
  }
}
