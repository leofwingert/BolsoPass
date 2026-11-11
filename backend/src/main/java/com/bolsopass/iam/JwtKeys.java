package com.bolsopass.iam;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

/**
 * Chaves RS256 do emissor de tokens (RF-01).
 * Em dev são geradas ao subir: reiniciar o backend invalida as sessões.
 * Quando o Keycloak entrar, troque por spring.security.oauth2.resourceserver.jwt.issuer-uri.
 */
@Configuration
public class JwtKeys {
  private final RSAKey rsaKey;

  public JwtKeys() throws NoSuchAlgorithmException {
    KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
    gen.initialize(2048);
    KeyPair pair = gen.generateKeyPair();
    this.rsaKey =
        new RSAKey.Builder((RSAPublicKey) pair.getPublic())
            .privateKey((RSAPrivateKey) pair.getPrivate())
            // kid único por subida: outros serviços (JWKS) percebem a chave nova e buscam de novo.
            .keyID("bolsopass-" + Long.toString(System.currentTimeMillis(), 36))
            .build();
  }

  /** Só a parte pública, para o endpoint JWKS (os outros serviços validam o token com ela). */
  public JWKSet publicJwkSet() {
    return new JWKSet(rsaKey.toPublicJWK());
  }

  @Bean
  JwtEncoder jwtEncoder() {
    return new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(rsaKey)));
  }

  @Bean
  JwtDecoder jwtDecoder() throws Exception {
    return NimbusJwtDecoder.withPublicKey(rsaKey.toRSAPublicKey()).build();
  }
}
