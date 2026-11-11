package com.bolsopass.facial.infrastructure.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;

/**
 * JWT stateless (RF-01): valida o token pela chave pública (JWKS) do emissor, sem chamar ninguém a
 * cada requisição. RBAC (RF-02): cadastro e consentimento só para o cliente; leitura facial só
 * para a portaria ({@code ROLE_OPERATOR}; o terminal de venda e o admin não entram).
 */
@Configuration
public class SecurityConfig {

  @Bean
  SecurityFilterChain api(HttpSecurity http) throws Exception {
    return http.csrf(AbstractHttpConfigurer::disable)
        .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(a -> a
            .requestMatchers("/actuator/health").permitAll()
            .requestMatchers(HttpMethod.POST, "/api/v1/facial/events/*/checkins").hasAuthority("ROLE_OPERATOR")
            .requestMatchers("/api/v1/facial/consent", "/api/v1/facial/enrollments/**").hasAuthority("ROLE_CLIENT")
            .anyRequest().denyAll())
        .oauth2ResourceServer(o -> o.jwt(j -> j.jwtAuthenticationConverter(rolesConverter())))
        .build();
  }

  /** O token traz os papéis na claim "roles", já com o prefixo ROLE_. */
  static JwtAuthenticationConverter rolesConverter() {
    JwtGrantedAuthoritiesConverter roles = new JwtGrantedAuthoritiesConverter();
    roles.setAuthoritiesClaimName("roles");
    roles.setAuthorityPrefix("");
    JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
    converter.setJwtGrantedAuthoritiesConverter(roles);
    return converter;
  }
}
