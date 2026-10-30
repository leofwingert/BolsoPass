package com.bolsopass.catalog;

import com.bolsopass.catalog.CatalogService.EventDetailView;
import com.bolsopass.catalog.CatalogService.EventSummaryView;
import com.bolsopass.catalog.CatalogService.SeatMapView;
import com.bolsopass.iam.CurrentUser;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/events")
public class EventController {
  private final CatalogService catalog;
  private final VirtualQueue queue;

  public EventController(CatalogService catalog, VirtualQueue queue) {
    this.catalog = catalog;
    this.queue = queue;
  }

  @GetMapping
  List<EventSummaryView> list(
      @RequestParam(required = false) String q,
      @RequestParam(required = false) String city,
      @RequestParam(required = false) Event.Category category) {
    return catalog.list(q, city, category);
  }

  @GetMapping("/{id}")
  EventDetailView detail(@PathVariable String id) {
    return catalog.detail(id);
  }

  /** Sala de espera: exige login para que a vez fique presa ao comprador. */
  @GetMapping("/{id}/queue")
  VirtualQueue.Status queue(@PathVariable String id) {
    catalog.find(id);
    return queue.join(id, CurrentUser.id());
  }

  @GetMapping("/{id}/seats")
  SeatMapView seats(@PathVariable String id) {
    return catalog.seatMap(id);
  }
}
