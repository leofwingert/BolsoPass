/** Erro de invariante de domínio (equivale ao IllegalStateException do backend). */
export class DomainError extends Error {
  constructor(message: string) {
    super(message);
    this.name = 'DomainError';
  }
}
