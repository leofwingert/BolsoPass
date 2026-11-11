package com.bolsopass.facial.presentation;

import com.bolsopass.facial.application.CheckinService;
import com.bolsopass.facial.domain.model.CheckinOutcome;
import java.io.IOException;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** Leitura facial na portaria: LIBERADO (nome e ingresso) ou NEGADO com o motivo. */
@RestController
@RequestMapping("/api/v1/facial/events/{eventId}/checkins")
public class CheckinController {
  private final CheckinService checkins;

  public CheckinController(CheckinService checkins) {
    this.checkins = checkins;
  }

  @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  CheckinOutcome checkin(
      @AuthenticationPrincipal Jwt jwt, @PathVariable String eventId,
      @RequestParam(required = false, defaultValue = "") String gate, @RequestPart("image") MultipartFile image)
      throws IOException {
    return checkins.checkin(jwt.getTokenValue(), eventId, gate, image.getContentType(), image.getBytes());
  }
}
