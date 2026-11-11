package com.bolsopass.facial.presentation;

import com.bolsopass.facial.application.ConsentService;
import com.bolsopass.facial.application.ConsentService.ConsentView;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Termo de uso de dados biométricos: ver, aceitar e revogar (revogar apaga os rostos). */
@RestController
@RequestMapping("/api/v1/facial/consent")
public class ConsentController {
  private final ConsentService consents;

  public ConsentController(ConsentService consents) {
    this.consents = consents;
  }

  public record AcceptRequest(String termVersion, boolean accepted) {}

  @GetMapping
  ConsentView get(@AuthenticationPrincipal Jwt jwt) {
    return consents.get(jwt.getSubject());
  }

  @PostMapping
  ConsentView accept(@AuthenticationPrincipal Jwt jwt, @RequestBody AcceptRequest body) {
    return consents.accept(jwt.getSubject(), body.termVersion(), body.accepted());
  }

  @DeleteMapping
  ConsentView revoke(@AuthenticationPrincipal Jwt jwt) {
    return consents.revoke(jwt.getSubject());
  }
}
