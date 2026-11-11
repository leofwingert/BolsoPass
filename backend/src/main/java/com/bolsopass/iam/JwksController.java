package com.bolsopass.iam;

import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Chave pública dos tokens (JWKS, RFC 7517). Os outros microsserviços (ex.: facial-checkin-service)
 * validam o JWT com ela, sem chamar o backend a cada requisição. Com o Keycloak, vira o JWKS do realm.
 */
@RestController
public class JwksController {
  private final JwtKeys keys;

  public JwksController(JwtKeys keys) {
    this.keys = keys;
  }

  @GetMapping("/api/v1/auth/jwks")
  Map<String, Object> jwks() {
    return keys.publicJwkSet().toJSONObject();
  }
}
