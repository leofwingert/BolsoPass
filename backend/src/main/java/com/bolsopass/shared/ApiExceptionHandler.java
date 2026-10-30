package com.bolsopass.shared;

import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {
  private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

  @ExceptionHandler(ApiException.class)
  ResponseEntity<Map<String, Object>> api(ApiException e) {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("message", e.getMessage());
    if (!e.seatIds().isEmpty()) body.put("seatIds", e.seatIds());
    return ResponseEntity.status(e.status()).body(body);
  }

  @ExceptionHandler(DomainException.class)
  ResponseEntity<Map<String, Object>> domain(DomainException e) {
    return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("message", e.getMessage()));
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  ResponseEntity<Map<String, Object>> invalid(MethodArgumentNotValidException e) {
    String msg =
        e.getBindingResult().getFieldErrors().stream()
            .findFirst()
            .map(f -> f.getField() + ": " + f.getDefaultMessage())
            .orElse("Requisição inválida");
    return ResponseEntity.badRequest().body(Map.of("message", msg));
  }

  @ExceptionHandler(AccessDeniedException.class)
  ResponseEntity<Map<String, Object>> denied(AccessDeniedException e) {
    return ResponseEntity.status(HttpStatus.FORBIDDEN)
        .body(Map.of("message", "Acesso negado para o seu perfil"));
  }

  @ExceptionHandler(Exception.class)
  ResponseEntity<Map<String, Object>> unexpected(Exception e) {
    // Exceções do próprio Spring (404 de rota, 405, corpo ilegível...) mantêm seu status.
    if (e instanceof org.springframework.web.ErrorResponse er) {
      return ResponseEntity.status(er.getStatusCode()).body(Map.of("message", e.getMessage()));
    }
    if (e instanceof org.springframework.http.converter.HttpMessageNotReadableException) {
      return ResponseEntity.badRequest().body(Map.of("message", "Corpo da requisição inválido"));
    }
    log.error("Erro inesperado", e);
    return ResponseEntity.internalServerError()
        .body(Map.of("message", "Erro interno. Tente novamente."));
  }
}
