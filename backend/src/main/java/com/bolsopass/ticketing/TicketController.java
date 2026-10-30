package com.bolsopass.ticketing;

import com.bolsopass.iam.CurrentUser;
import com.bolsopass.ticketing.TicketService.QrView;
import com.bolsopass.ticketing.TicketService.TicketView;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/tickets")
@PreAuthorize("hasRole('CLIENT')")
public class TicketController {
  private final TicketService tickets;

  public TicketController(TicketService tickets) {
    this.tickets = tickets;
  }

  @GetMapping("/me")
  List<TicketView> mine() {
    return tickets.mine(CurrentUser.id());
  }

  @GetMapping("/{id}")
  TicketView one(@PathVariable String id) {
    return tickets.get(CurrentUser.id(), id);
  }

  @GetMapping("/{id}/qr")
  QrView qr(@PathVariable String id) {
    return tickets.qr(CurrentUser.id(), id);
  }
}
