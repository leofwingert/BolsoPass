package com.bolsopass.inventory;

import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ReservationRepository extends JpaRepository<Reservation, String> {
  @Query("select r.id from Reservation r where r.released = false and r.expiresAt <= :now")
  List<String> findExpiredOpenIds(Instant now);

  @Query(
      "select r from Reservation r where r.userId = :userId and r.eventId = :eventId"
          + " and r.released = false and r.expiresAt > :now")
  List<Reservation> findOpen(String userId, String eventId, Instant now);

  long countByEventIdInAndCreatedAtAfter(java.util.Collection<String> eventIds, Instant after);
}
