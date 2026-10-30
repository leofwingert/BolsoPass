package com.bolsopass.ticketing;

import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Chave pública (JWK) para validar QR offline na catraca (RF-13). Chave pública não é segredo. */
@RestController
public class PublicKeyController {
  private final QrSigner signer;

  public PublicKeyController(QrSigner signer) {
    this.signer = signer;
  }

  @GetMapping("/api/v1/keys/qr")
  Map<String, String> qrPublicKey() {
    return signer.publicJwk();
  }
}
