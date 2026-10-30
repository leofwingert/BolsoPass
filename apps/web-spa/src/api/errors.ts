/** Erros HTTP que a interface trata de forma específica. */
export class ApiError extends Error {
  constructor(
    public readonly status: number,
    message: string,
    public readonly body?: unknown,
  ) {
    super(message);
    this.name = 'ApiError';
  }
}

/** 409: assento já travado por outro comprador, ou pagamento com a mesma Idempotency-Key em andamento. */
export class ConflictError extends ApiError {
  constructor(
    message: string,
    public readonly seatIds: string[] = [],
  ) {
    super(409, message);
    this.name = 'ConflictError';
  }
}

export class UnauthorizedError extends ApiError {
  constructor(message = 'Sessão expirada') {
    super(401, message);
    this.name = 'UnauthorizedError';
  }
}

export class ForbiddenError extends ApiError {
  constructor(message = 'Acesso negado para o seu perfil') {
    super(403, message);
    this.name = 'ForbiddenError';
  }
}

/** Reserva expirou (TTL de 10 min) antes do pagamento. */
export class ReservationExpiredError extends ApiError {
  constructor() {
    super(410, 'Sua reserva expirou e os assentos voltaram ao catálogo.');
    this.name = 'ReservationExpiredError';
  }
}

export function errorMessage(err: unknown): string {
  if (err instanceof Error) return err.message;
  return 'Algo deu errado. Tente novamente.';
}
