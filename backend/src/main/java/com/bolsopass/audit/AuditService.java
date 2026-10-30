package com.bolsopass.audit;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Grava na mesma transação da ação auditada: se a ação falhar, o log some junto. */
@Service
public class AuditService {
  public static final String SYSTEM = "Sistema BolsoPass";

  private final AuditLogRepository repo;

  public AuditService(AuditLogRepository repo) {
    this.repo = repo;
  }

  @Transactional
  public void log(String eventId, AuditLog.Kind kind, String title, String description, String actor) {
    repo.save(new AuditLog(eventId, kind, title, description, actor));
  }
}
