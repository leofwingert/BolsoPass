package com.bolsopass.facial.application;

import com.bolsopass.facial.domain.model.CheckinOutcome;
import com.bolsopass.facial.domain.port.EnrollmentRepository;
import com.bolsopass.facial.domain.port.FaceRecognition;
import com.bolsopass.facial.domain.port.Ticketing;
import org.springframework.stereotype.Service;

/**
 * Check-in facial na portaria: procura o rosto na collection do evento (similaridade ≥ 90%) e,
 * se achar, pede ao backend para registrar a entrada pela mesma regra do QR.
 */
@Service
public class CheckinService {
  private final FaceRecognition faces;
  private final EnrollmentRepository enrollments;
  private final Ticketing ticketing;
  private final FacialProperties props;

  public CheckinService(
      FaceRecognition faces, EnrollmentRepository enrollments, Ticketing ticketing, FacialProperties props) {
    this.faces = faces;
    this.enrollments = enrollments;
    this.ticketing = ticketing;
    this.props = props;
  }

  public CheckinOutcome checkin(String bearer, String eventId, String gate, String contentType, byte[] image) {
    EnrollmentService.validateImage(contentType, image);
    var match = faces.searchFace(eventId, image, props.similarityThreshold());
    if (match.isEmpty()) return CheckinOutcome.notRecognized();
    // Rosto que ainda está na collection mas já foi revogado (ou é de outro evento): não vale.
    boolean registered = enrollments.findByTicketId(match.get().ticketId())
        .filter(e -> e.getEventId().equals(eventId) && e.getFaceId().equals(match.get().faceId()))
        .isPresent();
    if (!registered) return CheckinOutcome.notRecognized();
    return ticketing.registerCheckin(eventId, match.get().ticketId(), gate, match.get().similarity(), bearer);
  }
}
