package com.bolsopass.facial.domain.model;

/** Erro de regra de negócio, com o status HTTP que a camada de apresentação devolve. */
public class FacialException extends RuntimeException {
  private final int status;

  public FacialException(int status, String message) {
    super(message);
    this.status = status;
  }

  public int status() {
    return status;
  }

  public static FacialException badRequest(String message) {
    return new FacialException(400, message);
  }

  public static FacialException forbidden(String message) {
    return new FacialException(403, message);
  }

  public static FacialException notFound(String message) {
    return new FacialException(404, message);
  }

  public static FacialException conflict(String message) {
    return new FacialException(409, message);
  }

  /** A foto não serve (sem rosto, mais de um rosto, rosto pequeno ou escuro demais). */
  public static FacialException unprocessable(String message) {
    return new FacialException(422, message);
  }

  public static FacialException unavailable(String message) {
    return new FacialException(503, message);
  }
}
