package com.bolsopass.organizer;

import com.bolsopass.organizer.OrganizerViews.Dashboard;
import com.bolsopass.organizer.OrganizerViews.EventDetail;
import com.bolsopass.organizer.OrganizerViews.EventRow;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Área administrativa (doc 07: /organizer/* para ROLE_ORGANIZER e ROLE_ADMIN). */
@RestController
@RequestMapping("/api/v1/organizer")
@PreAuthorize("hasAnyRole('ORGANIZER', 'ADMIN')")
public class OrganizerController {
  private final OrganizerService service;

  private final SeatSalesService sales;

  public OrganizerController(OrganizerService service, SeatSalesService sales) {
    this.service = service;
    this.sales = sales;
  }

  @GetMapping("/dashboard")
  Dashboard dashboard() {
    return service.dashboard();
  }

  @GetMapping("/events")
  List<EventRow> events() {
    return service.list();
  }

  @GetMapping("/events/{id}")
  EventDetail event(@PathVariable String id) {
    return service.detail(id);
  }

  @PostMapping("/events")
  @ResponseStatus(HttpStatus.CREATED)
  Map<String, String> create(@Valid @RequestBody CreateEventRequest req) {
    return Map.of("id", service.create(req));
  }

  @PostMapping("/events/{id}/publish")
  ResponseEntity<Void> publish(@PathVariable String id) {
    service.publish(id);
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/events/{id}/pause")
  ResponseEntity<Void> pause(@PathVariable String id) {
    service.pause(id);
    return ResponseEntity.noContent().build();
  }

  /** Mapa de assentos de um setor, com o status de cada um. */
  @GetMapping("/events/{id}/sectors/{sectorId}/seats")
  SeatSalesService.SectorSeats sectorSeats(@PathVariable String id, @PathVariable String sectorId) {
    return sales.sectorSeats(id, sectorId);
  }

  /** Para quem o assento foi vendido: comprador, titular (CPF) e pedido. Consulta auditada. */
  @GetMapping("/events/{id}/seats/{seatId}/sale")
  SeatSalesService.SeatSale seatSale(@PathVariable String id, @PathVariable String seatId) {
    return sales.seatSale(id, seatId);
  }

  @PostMapping("/events/{id}/resume")
  ResponseEntity<Void> resume(@PathVariable String id) {
    service.resume(id);
    return ResponseEntity.noContent().build();
  }
}
