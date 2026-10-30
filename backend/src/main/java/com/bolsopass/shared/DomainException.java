package com.bolsopass.shared;

/** Violação de invariante de domínio (ex.: transição proibida na FSM). Vira HTTP 409. */
public class DomainException extends IllegalStateException {
  public DomainException(String message) {
    super(message);
  }
}
