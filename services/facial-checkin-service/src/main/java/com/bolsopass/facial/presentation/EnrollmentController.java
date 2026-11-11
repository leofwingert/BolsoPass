package com.bolsopass.facial.presentation;

import com.bolsopass.facial.application.EnrollmentService;
import com.bolsopass.facial.application.EnrollmentService.EnrollmentView;
import java.io.IOException;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** Cadastro facial do cliente, por ingresso. A selfie só passa em memória. */
@RestController
@RequestMapping("/api/v1/facial/enrollments")
public class EnrollmentController {
  private final EnrollmentService enrollments;

  public EnrollmentController(EnrollmentService enrollments) {
    this.enrollments = enrollments;
  }

  @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @ResponseStatus(HttpStatus.CREATED)
  EnrollmentView enroll(
      @AuthenticationPrincipal Jwt jwt, @RequestParam String ticketId, @RequestPart("image") MultipartFile image)
      throws IOException {
    return enrollments.enroll(jwt.getSubject(), jwt.getTokenValue(), ticketId, image.getContentType(), image.getBytes());
  }

  @GetMapping
  List<EnrollmentView> mine(@AuthenticationPrincipal Jwt jwt) {
    return enrollments.mine(jwt.getSubject());
  }

  @DeleteMapping("/{ticketId}")
  ResponseEntity<Void> remove(@AuthenticationPrincipal Jwt jwt, @PathVariable String ticketId) {
    enrollments.remove(jwt.getSubject(), ticketId);
    return ResponseEntity.noContent().build();
  }
}
