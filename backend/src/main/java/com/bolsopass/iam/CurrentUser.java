package com.bolsopass.iam;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/** Id do usuário autenticado (claim "sub" do JWT). */
public final class CurrentUser {
  private CurrentUser() {}

  public static String id() {
    if (SecurityContextHolder.getContext().getAuthentication() instanceof JwtAuthenticationToken t) {
      return t.getToken().getSubject();
    }
    throw new IllegalStateException("Sem usuário autenticado");
  }

  /** Para endpoints públicos que mudam o retorno quando há sessão. */
  public static String idOrNull() {
    return SecurityContextHolder.getContext().getAuthentication() instanceof JwtAuthenticationToken t
        ? t.getToken().getSubject()
        : null;
  }

  public static boolean hasRole(String role) {
    return SecurityContextHolder.getContext().getAuthentication() != null
        && SecurityContextHolder.getContext().getAuthentication().getAuthorities().stream()
            .anyMatch(a -> role.equals(a.getAuthority()));
  }
}
