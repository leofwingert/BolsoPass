package com.bolsopass.ticketing;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * QR dinâmico assinado com ECDSA P-256 / SHA-256 (RF-13).
 * Formato igual ao do front (src/domain/ticketQr.ts): BP1.<payload b64url>.<assinatura b64url>.
 * A assinatura usa o formato P1363 (r||s), que é o que o WebCrypto do navegador verifica.
 * Em dev a chave é gerada ao subir; em produção virá de um cofre de segredos.
 */
@Component
public class QrSigner {
  public static final long ROTATION_MS = 15_000;
  private static final Base64.Encoder B64URL = Base64.getUrlEncoder().withoutPadding();

  private final KeyPair keys;
  private final ObjectMapper json;

  public QrSigner(ObjectMapper json) throws GeneralSecurityException {
    KeyPairGenerator gen = KeyPairGenerator.getInstance("EC");
    gen.initialize(new ECGenParameterSpec("secp256r1"));
    this.keys = gen.generateKeyPair();
    this.json = json;
  }

  public static long window(long nowMs) {
    return nowMs / ROTATION_MS;
  }

  public String sign(Ticket ticket, long window) {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("tid", ticket.getId());
    payload.put("eid", ticket.getEventId());
    payload.put("sid", ticket.getSeatLabel());
    payload.put("iat", ticket.getIssuedAt().toString());
    payload.put("win", window);
    try {
      String signed =
          "BP1." + B64URL.encodeToString(json.writeValueAsString(payload).getBytes(StandardCharsets.UTF_8));
      Signature sig = Signature.getInstance("SHA256withECDSAinP1363Format");
      sig.initSign(keys.getPrivate());
      sig.update(signed.getBytes(StandardCharsets.UTF_8));
      return signed + "." + B64URL.encodeToString(sig.sign());
    } catch (JsonProcessingException | GeneralSecurityException e) {
      throw new IllegalStateException("Falha ao assinar QR", e);
    }
  }

  /** Chave pública em JWK, para a portaria validar offline (parte do operador). */
  public Map<String, String> publicJwk() {
    ECPublicKey pub = (ECPublicKey) keys.getPublic();
    return Map.of(
        "kty", "EC",
        "crv", "P-256",
        "x", B64URL.encodeToString(fixed(pub.getW().getAffineX().toByteArray())),
        "y", B64URL.encodeToString(fixed(pub.getW().getAffineY().toByteArray())));
  }

  private static byte[] fixed(byte[] v) {
    byte[] out = new byte[32];
    int len = Math.min(v.length, 32);
    System.arraycopy(v, v.length - len, out, 32 - len, len);
    return out;
  }
}
