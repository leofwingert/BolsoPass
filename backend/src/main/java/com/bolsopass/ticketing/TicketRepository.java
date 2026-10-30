package com.bolsopass.ticketing;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TicketRepository extends JpaRepository<Ticket, String> {
  List<Ticket> findByUserIdOrderByIssuedAtDesc(String userId);

  List<Ticket> findByEventId(String eventId);
}
