package com.bolsopass.shared;

import java.util.List;
import org.springframework.http.HttpStatus;

/** Erro de negócio com status HTTP. O corpo segue o formato que o front espera: {message, seatIds?}. */
public class ApiException extends RuntimeException {
  private final HttpStatus status;
  private final List<String> seatIds;

  public ApiException(HttpStatus status, String message) {
    this(status, message, List.of());
  }

  public ApiException(HttpStatus status, String message, List<String> seatIds) {
    super(message);
    this.status = status;
    this.seatIds = seatIds;
  }

  public HttpStatus status() {
    return status;
  }

  public List<String> seatIds() {
    return seatIds;
  }

  public static ApiException notFound(String what) {
    return new ApiException(HttpStatus.NOT_FOUND, what + " não encontrado");
  }

  public static ApiException forbidden(String message) {
    return new ApiException(HttpStatus.FORBIDDEN, message);
  }

  public static ApiException conflict(String message) {
    return new ApiException(HttpStatus.CONFLICT, message);
  }

  public static ApiException badRequest(String message) {
    return new ApiException(HttpStatus.BAD_REQUEST, message);
  }
}
