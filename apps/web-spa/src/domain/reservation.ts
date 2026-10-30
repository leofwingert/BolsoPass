/** TTL estrito de reserva: exatamente 10 minutos (600 s). */
export const RESERVATION_TTL_MS = 10 * 60 * 1000;
/** Aviso sonoro e visual nos 2 minutos finais (doc 07, acessibilidade). */
export const RESERVATION_WARNING_MS = 2 * 60 * 1000;

/**
 * O cronômetro segue o relógio do servidor, nunca o do navegador.
 * offset = horário do servidor - horário local no momento da resposta.
 */
export function clockOffset(serverNowIso: string, clientNowMs: number): number {
  return Date.parse(serverNowIso) - clientNowMs;
}

export function remainingMs(expiresAtIso: string, clientNowMs: number, offsetMs: number): number {
  return Math.max(0, Date.parse(expiresAtIso) - (clientNowMs + offsetMs));
}

export type TimerPhase = 'normal' | 'warning' | 'expired';

export function timerPhase(remaining: number): TimerPhase {
  if (remaining <= 0) return 'expired';
  if (remaining <= RESERVATION_WARNING_MS) return 'warning';
  return 'normal';
}

export function formatCountdown(ms: number): string {
  const total = Math.ceil(Math.max(0, ms) / 1000);
  const m = Math.floor(total / 60);
  const s = total % 60;
  return `${String(m).padStart(2, '0')}:${String(s).padStart(2, '0')}`;
}
