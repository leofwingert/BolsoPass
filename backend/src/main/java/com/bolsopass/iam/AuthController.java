package com.bolsopass.iam;

import com.bolsopass.iam.AuthService.SessionDto;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
  private final AuthService auth;

  public AuthController(AuthService auth) {
    this.auth = auth;
  }

  public record LoginRequest(@NotBlank String email, @NotBlank String password) {}

  public record OperatorLoginRequest(@NotBlank String badge, @NotBlank String pin) {}

  public record RefreshRequest(@NotBlank String refreshToken) {}

  public record LogoutRequest(String refreshToken) {}

  @PostMapping("/login")
  SessionDto login(@Valid @RequestBody LoginRequest req) {
    return auth.login(req.email(), req.password());
  }

  @PostMapping("/operator/login")
  SessionDto operatorLogin(@Valid @RequestBody OperatorLoginRequest req) {
    return auth.loginOperator(req.badge(), req.pin());
  }

  @PostMapping("/refresh")
  SessionDto refresh(@Valid @RequestBody RefreshRequest req) {
    return auth.refresh(req.refreshToken());
  }

  @PostMapping("/logout")
  ResponseEntity<Void> logout(@RequestBody(required = false) LogoutRequest req) {
    auth.logout(req == null ? null : req.refreshToken());
    return ResponseEntity.noContent().build();
  }
}
