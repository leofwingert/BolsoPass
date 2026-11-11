package com.bolsopass.facial.domain.port;

import com.bolsopass.facial.domain.model.FaceEnrollment;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface EnrollmentRepository {
  Optional<FaceEnrollment> findByTicketId(String ticketId);

  List<FaceEnrollment> findByUserIdOrderByCreatedAtDesc(String userId);

  List<FaceEnrollment> findByEventStartsAtBefore(Instant limit);

  FaceEnrollment save(FaceEnrollment enrollment);

  void delete(FaceEnrollment enrollment);

  /** Grava as pendências agora (o recadastro apaga o rosto antigo antes de inserir o novo). */
  void flush();
}
