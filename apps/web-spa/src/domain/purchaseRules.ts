import { isValidCpf, normalizeCpf } from './cpf';

/**
 * Regras de compra que o front valida antes de chamar a API.
 * A API valida de novo; aqui é só para dar resposta rápida ao usuário.
 */
export interface Holder {
  seatId: string;
  name: string;
  cpf: string;
}

export type HolderIssue = { seatId: string; field: 'name' | 'cpf'; message: string };

/** Cada ingresso é nominal: nome completo + CPF válido, sem CPF repetido no mesmo pedido. */
export function validateHolders(holders: readonly Holder[]): HolderIssue[] {
  const issues: HolderIssue[] = [];
  const seen = new Set<string>();
  for (const h of holders) {
    if (h.name.trim().split(/\s+/).length < 2) {
      issues.push({ seatId: h.seatId, field: 'name', message: 'Informe nome e sobrenome' });
    }
    if (!isValidCpf(h.cpf)) {
      issues.push({ seatId: h.seatId, field: 'cpf', message: 'CPF inválido' });
    } else {
      const cpf = normalizeCpf(h.cpf);
      if (seen.has(cpf)) {
        issues.push({ seatId: h.seatId, field: 'cpf', message: 'CPF já usado em outro ingresso' });
      }
      seen.add(cpf);
    }
  }
  return issues;
}

/** Limite de ingressos por CPF definido no lote (wizard do produtor, coluna "Limite CPF"). */
export function canAddSeat(currentCount: number, limitPerCpf: number): boolean {
  return currentCount < limitPerCpf;
}

/** Direito de arrependimento (CDC): cancelamento em até 7 dias corridos após a compra. */
export const REFUND_WINDOW_DAYS = 7;

export function canRequestRefund(
  paidAtIso: string,
  eventStartsAtIso: string,
  nowMs: number,
): boolean {
  const deadline = Date.parse(paidAtIso) + REFUND_WINDOW_DAYS * 24 * 60 * 60 * 1000;
  return nowMs <= deadline && nowMs < Date.parse(eventStartsAtIso);
}
