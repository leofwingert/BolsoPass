package com.bolsopass.inventory;

import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface SeatRepository extends JpaRepository<Seat, String> {
  List<Seat> findByEventIdOrderBySectorIdAscNumberAsc(String eventId);

  /**
   * SELECT ... FOR UPDATE (RF-06). Ordenado por id para que duas transações travem
   * na mesma ordem e não entrem em deadlock.
   */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select s from Seat s where s.id in :ids order by s.id")
  List<Seat> lockAll(Collection<String> ids);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select s from Seat s where s.heldBy = :reservationId order by s.id")
  List<Seat> lockHeldBy(String reservationId);

  long countByEventIdAndStatus(String eventId, Seat.Status status);

  long countBySectorIdAndStatusIn(String sectorId, Collection<Seat.Status> statuses);

  long countByEventIdInAndStatus(Collection<String> eventIds, Seat.Status status);
}
