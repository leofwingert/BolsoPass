package com.bolsopass.facial.application;

import com.bolsopass.facial.domain.model.FaceEnrollment;
import com.bolsopass.facial.domain.model.FacialException;
import com.bolsopass.facial.domain.port.EnrollmentRepository;
import com.bolsopass.facial.domain.port.FaceRecognition;
import com.bolsopass.facial.domain.port.Ticketing;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Cadastro do rosto para um ingresso: só o titular, com consentimento ativo e ingresso válido. */
@Service
public class EnrollmentService {
  /** Formatos aceitos pelo Rekognition (bytes da imagem). */
  static final Set<String> IMAGE_TYPES = Set.of("image/jpeg", "image/png");

  static final int MAX_IMAGE_BYTES = 5 * 1024 * 1024;

  public record EnrollmentView(String ticketId, String eventId, Instant enrolledAt) {}

  private final EnrollmentRepository enrollments;
  private final ConsentService consents;
  private final FaceRecognition faces;
  private final Ticketing ticketing;
  private final Clock clock;

  public EnrollmentService(
      EnrollmentRepository enrollments, ConsentService consents, FaceRecognition faces, Ticketing ticketing,
      Clock clock) {
    this.enrollments = enrollments;
    this.consents = consents;
    this.faces = faces;
    this.ticketing = ticketing;
    this.clock = clock;
  }

  @Transactional
  public EnrollmentView enroll(String userId, String bearer, String ticketId, String contentType, byte[] image) {
    if (!consents.isActive(userId)) {
      throw FacialException.forbidden("Aceite o termo de uso de dados biométricos antes de cadastrar o rosto");
    }
    validateImage(contentType, image);
    Ticketing.Eligibility t = ticketing.eligibility(ticketId, bearer);
    // LGPD: dado biométrico é sensível; só a própria pessoa consente com o uso do rosto dela.
    if (!t.holder()) {
      throw FacialException.forbidden(
          "Só o titular do ingresso cadastra o próprio rosto. O acompanhante faz isso na conta dele.");
    }
    if (!t.active()) throw FacialException.conflict("Ingresso não está ativo");
    Instant now = Instant.now(clock);
    if (t.eventStartsAt() != null && t.eventStartsAt().plus(Duration.ofHours(12)).isBefore(now)) {
      throw FacialException.conflict("O evento já aconteceu");
    }

    // Recadastro troca o rosto anterior do mesmo ingresso.
    enrollments.findByTicketId(ticketId).ifPresent(old -> {
      faces.deleteFaces(old.getEventId(), List.of(old.getFaceId()));
      enrollments.delete(old);
      enrollments.flush();
    });
    faces.ensureCollection(t.eventId());
    String faceId = faces.indexFace(t.eventId(), ticketId, image);
    FaceEnrollment saved =
        enrollments.save(new FaceEnrollment(faceId, ticketId, t.eventId(), userId, t.eventStartsAt(), now));
    return view(saved);
  }

  @Transactional(readOnly = true)
  public List<EnrollmentView> mine(String userId) {
    return enrollments.findByUserIdOrderByCreatedAtDesc(userId).stream().map(EnrollmentService::view).toList();
  }

  @Transactional
  public void remove(String userId, String ticketId) {
    FaceEnrollment e = enrollments.findByTicketId(ticketId)
        .filter(x -> x.getUserId().equals(userId))
        .orElseThrow(() -> FacialException.notFound("Rosto não cadastrado para este ingresso"));
    faces.deleteFaces(e.getEventId(), List.of(e.getFaceId()));
    enrollments.delete(e);
  }

  static void validateImage(String contentType, byte[] image) {
    if (image == null || image.length == 0) throw FacialException.badRequest("Envie a foto");
    if (image.length > MAX_IMAGE_BYTES) throw FacialException.badRequest("A foto passa de 5 MB");
    if (contentType == null || !IMAGE_TYPES.contains(contentType.toLowerCase(Locale.ROOT))) {
      throw FacialException.badRequest("Formato de foto não aceito (use JPEG ou PNG)");
    }
  }

  private static EnrollmentView view(FaceEnrollment e) {
    return new EnrollmentView(e.getTicketId(), e.getEventId(), e.getCreatedAt());
  }
}
