package com.bolsopass.facial.presentation;

import com.bolsopass.facial.domain.model.FacialException;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

/** Mesmo formato de erro do backend: {"message": "..."}. */
@RestControllerAdvice
public class ApiExceptionHandler {
  private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

  @ExceptionHandler(FacialException.class)
  ResponseEntity<Map<String, String>> facial(FacialException e) {
    return ResponseEntity.status(e.status()).body(Map.of("message", e.getMessage()));
  }

  @ExceptionHandler({MissingServletRequestPartException.class, MissingServletRequestParameterException.class})
  ResponseEntity<Map<String, String>> missing(Exception e) {
    return ResponseEntity.badRequest().body(Map.of("message", "Envie a foto e o ingresso"));
  }

  @ExceptionHandler(MaxUploadSizeExceededException.class)
  ResponseEntity<Map<String, String>> tooLarge(MaxUploadSizeExceededException e) {
    return ResponseEntity.badRequest().body(Map.of("message", "A foto passa de 5 MB"));
  }

  @ExceptionHandler(Exception.class)
  ResponseEntity<Map<String, String>> unexpected(Exception e) {
    log.error("Erro inesperado", e);
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("message", "Erro interno. Tente novamente."));
  }
}
