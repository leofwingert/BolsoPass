package com.bolsopass.facial.application;

import com.bolsopass.facial.domain.model.BiometricConsent;
import com.bolsopass.facial.domain.model.FaceEnrollment;
import com.bolsopass.facial.domain.model.FacialException;
import com.bolsopass.facial.domain.port.ConsentRepository;
import com.bolsopass.facial.domain.port.EnrollmentRepository;
import com.bolsopass.facial.domain.port.FaceRecognition;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Aceite e revogação do termo de uso de dados biométricos (LGPD). */
@Service
public class ConsentService {
  public record ConsentView(
      boolean active, String termVersion, Instant acceptedAt, Instant revokedAt, String currentTermVersion) {}

  private final ConsentRepository consents;
  private final EnrollmentRepository enrollments;
  private final FaceRecognition faces;
  private final FacialProperties props;
  private final Clock clock;

  public ConsentService(
      ConsentRepository consents, EnrollmentRepository enrollments, FaceRecognition faces, FacialProperties props,
      Clock clock) {
    this.consents = consents;
    this.enrollments = enrollments;
    this.faces = faces;
    this.props = props;
    this.clock = clock;
  }

  @Transactional(readOnly = true)
  public ConsentView get(String userId) {
    return consents.findById(userId).map(this::view)
        .orElse(new ConsentView(false, null, null, null, props.consentTermVersion()));
  }

  /** Aceite explícito da versão atual do termo. */
  @Transactional
  public ConsentView accept(String userId, String termVersion, boolean accepted) {
    if (!accepted) throw FacialException.badRequest("É preciso marcar o aceite do termo de dados biométricos");
    if (!props.consentTermVersion().equals(termVersion)) {
      throw FacialException.conflict(
          "O termo mudou: leia a versão atual (" + props.consentTermVersion() + ") e aceite de novo");
    }
    BiometricConsent c = consents.findById(userId).orElseGet(() -> new BiometricConsent(userId));
    c.accept(termVersion, Instant.now(clock));
    return view(consents.save(c));
  }

  /** Revogação: apaga todos os rostos da pessoa no Rekognition e os registros aqui. */
  @Transactional
  public ConsentView revoke(String userId) {
    List<FaceEnrollment> mine = enrollments.findByUserIdOrderByCreatedAtDesc(userId);
    mine.stream()
        .collect(Collectors.groupingBy(FaceEnrollment::getEventId,
            Collectors.mapping(FaceEnrollment::getFaceId, Collectors.toList())))
        .forEach(faces::deleteFaces);
    mine.forEach(enrollments::delete);
    BiometricConsent c = consents.findById(userId).orElseGet(() -> new BiometricConsent(userId));
    c.revoke(Instant.now(clock));
    return view(consents.save(c));
  }

  boolean isActive(String userId) {
    return consents.findById(userId).map(c -> c.isActive(props.consentTermVersion())).orElse(false);
  }

  private ConsentView view(BiometricConsent c) {
    return new ConsentView(c.isActive(props.consentTermVersion()), c.getTermVersion(), c.getAcceptedAt(),
        c.getRevokedAt(), props.consentTermVersion());
  }
}
