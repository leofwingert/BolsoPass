import { storage } from './storage';

/**
 * Idempotency-Key do checkout (RF-09).
 * - Uma chave por tentativa de pagamento.
 * - Se a rede cair ou a página recarregar no meio, a MESMA chave é reenviada,
 *   e o servidor devolve o resultado original sem cobrar de novo.
 * - Só depois de uma resposta definitiva (aprovado/recusado) a chave é descartada.
 */
const key = (scope: string) => `bp.idem.${scope}`;

export function getOrCreateIdempotencyKey(scope: string): string {
  const existing = storage.get<string>(key(scope), 'session');
  if (existing) return existing;
  const created = crypto.randomUUID();
  storage.set(key(scope), created, 'session');
  return created;
}

export function clearIdempotencyKey(scope: string): void {
  storage.remove(key(scope), 'session');
}
