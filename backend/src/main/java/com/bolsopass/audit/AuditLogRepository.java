package com.bolsopass.audit;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditLogRepository extends JpaRepository<AuditLog, String> {
  List<AuditLog> findTop20ByEventIdOrderByAtDesc(String eventId);
}
