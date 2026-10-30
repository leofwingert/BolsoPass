package com.bolsopass.outbox;

import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;

public interface OutboxRepository extends JpaRepository<OutboxEvent, String> {
  /**
   * SELECT ... FOR UPDATE SKIP LOCKED: vários relays em paralelo pegam lotes diferentes
   * sem esperar uns pelos outros (lock.timeout = -2 é o SKIP LOCKED do Hibernate).
   */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2"))
  @Query(
      "select e from OutboxEvent e where e.status = com.bolsopass.outbox.OutboxEvent.Status.PENDING"
          + " order by e.createdAt")
  List<OutboxEvent> lockPendingBatch(Pageable page);

  long countByStatus(OutboxEvent.Status status);
}
