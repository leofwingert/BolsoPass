package com.bolsopass.orders;

import java.time.Instant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

public interface IdempotencyRepository extends JpaRepository<IdempotencyRecord, String> {
  @Modifying
  @Transactional
  @Query("delete from IdempotencyRecord r where r.createdAt < :before")
  int deleteOlderThan(Instant before);
}
