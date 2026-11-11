package com.bolsopass.facial.application;

import com.bolsopass.facial.domain.model.FaceEnrollment;
import com.bolsopass.facial.domain.port.EnrollmentRepository;
import com.bolsopass.facial.domain.port.FaceRecognition;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Retenção mínima (LGPD art. 15 e 16): alguns dias depois do evento, os rostos e a collection do
 * evento são apagados. O dado biométrico só existe enquanto serve para a entrada.
 */
@Component
public class RetentionJob {
  private static final Logger log = LoggerFactory.getLogger(RetentionJob.class);

  private final EnrollmentRepository enrollments;
  private final FaceRecognition faces;
  private final FacialProperties props;
  private final Clock clock;

  public RetentionJob(EnrollmentRepository enrollments, FaceRecognition faces, FacialProperties props, Clock clock) {
    this.enrollments = enrollments;
    this.faces = faces;
    this.props = props;
    this.clock = clock;
  }

  /** Todo dia às 4h30. Devolve quantos rostos foram apagados. */
  @Scheduled(cron = "0 30 4 * * *")
  @Transactional
  public int purgeFinishedEvents() {
    Instant limit = Instant.now(clock).minus(Duration.ofDays(props.retentionDays()));
    Map<String, List<FaceEnrollment>> byEvent = enrollments.findByEventStartsAtBefore(limit).stream()
        .collect(Collectors.groupingBy(FaceEnrollment::getEventId));
    byEvent.forEach((eventId, list) -> {
      faces.deleteCollection(eventId);
      list.forEach(enrollments::delete);
      log.info("Retenção: {} rosto(s) do evento {} apagados", list.size(), eventId);
    });
    return byEvent.values().stream().mapToInt(List::size).sum();
  }
}
