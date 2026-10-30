/**
 * QR Code dinâmico do ingresso (RF-13 + mockups "Carteira" e "Leitor de QR").
 *
 * Formato: BP1.<payload base64url>.<assinatura ECDSA P-256 base64url>
 * O payload muda a cada 15 s (campo `win`), então um print de tela vence rápido.
 * A catraca valida offline: só precisa da chave pública da plataforma.
 */
export const QR_ROTATION_MS = 15_000;
/** Aceita a janela atual e a anterior/seguinte para tolerar relógios levemente fora. */
export const QR_WINDOW_TOLERANCE = 1;
const PREFIX = 'BP1';

export interface QrPayload {
  /** ticket_id */
  tid: string;
  /** event_id */
  eid: string;
  /** seat_id */
  sid: string;
  /** issued_at (ISO) */
  iat: string;
  /** janela de rotação = floor(agora / 15 s) */
  win: number;
}

export function qrWindow(nowMs: number): number {
  return Math.floor(nowMs / QR_ROTATION_MS);
}

/** Milissegundos até o QR atual trocar. */
export function msUntilRotation(nowMs: number): number {
  return QR_ROTATION_MS - (nowMs % QR_ROTATION_MS);
}

export function toBase64Url(bytes: Uint8Array): string {
  let bin = '';
  for (const b of bytes) bin += String.fromCharCode(b);
  return btoa(bin).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
}

export function fromBase64Url(value: string): Uint8Array {
  const b64 = value.replace(/-/g, '+').replace(/_/g, '/');
  const bin = atob(b64 + '='.repeat((4 - (b64.length % 4)) % 4));
  return Uint8Array.from(bin, (c) => c.charCodeAt(0));
}

/** Parte assinada do QR (prefixo + payload). */
export function signedPart(payload: QrPayload): string {
  const json = new TextEncoder().encode(JSON.stringify(payload));
  return `${PREFIX}.${toBase64Url(json)}`;
}

export function encodeQr(payload: QrPayload, signatureB64Url: string): string {
  return `${signedPart(payload)}.${signatureB64Url}`;
}

export interface DecodedQr {
  payload: QrPayload;
  signed: string;
  signature: string;
}

export function decodeQr(raw: string): DecodedQr | null {
  const parts = raw.trim().split('.');
  if (parts.length !== 3 || parts[0] !== PREFIX) return null;
  const [, body, signature] = parts as [string, string, string];
  try {
    const payload = JSON.parse(new TextDecoder().decode(fromBase64Url(body))) as QrPayload;
    if (
      typeof payload.tid !== 'string' ||
      typeof payload.eid !== 'string' ||
      typeof payload.win !== 'number'
    ) {
      return null;
    }
    return { payload, signed: `${PREFIX}.${body}`, signature };
  } catch {
    return null;
  }
}

/** Resultados exibidos no mockup "Histórico de Leituras". */
export type ScanStatus = 'VALIDO' | 'JA_UTILIZADO' | 'INVALIDO' | 'EVENTO_INCORRETO';

export type InvalidReason = 'FORMATO' | 'ASSINATURA' | 'TOKEN_EXPIRADO';

export interface PreviousUse {
  at: string;
  gate: string;
}

export interface ScanOutcome {
  status: ScanStatus;
  payload?: QrPayload;
  reason?: InvalidReason;
  previousUse?: PreviousUse;
}

export interface ScanContext {
  /** Evento selecionado pelo operador no posto. */
  eventId: string;
  nowMs: number;
  /** Verifica a assinatura com a chave pública (em cache para modo offline). */
  verify: (signed: string, signature: string) => Promise<boolean>;
  /** Registro de entradas já feitas (cache local + servidor). */
  findUse: (ticketId: string) => PreviousUse | undefined;
}

export async function validateScan(raw: string, ctx: ScanContext): Promise<ScanOutcome> {
  const decoded = decodeQr(raw);
  if (!decoded) return { status: 'INVALIDO', reason: 'FORMATO' };

  const { payload } = decoded;
  const signatureOk = await ctx.verify(decoded.signed, decoded.signature);
  if (!signatureOk) return { status: 'INVALIDO', reason: 'ASSINATURA', payload };

  if (Math.abs(payload.win - qrWindow(ctx.nowMs)) > QR_WINDOW_TOLERANCE) {
    return { status: 'INVALIDO', reason: 'TOKEN_EXPIRADO', payload };
  }
  if (payload.eid !== ctx.eventId) return { status: 'EVENTO_INCORRETO', payload };

  const previousUse = ctx.findUse(payload.tid);
  if (previousUse) return { status: 'JA_UTILIZADO', payload, previousUse };

  return { status: 'VALIDO', payload };
}

export const INVALID_REASON_LABEL: Record<InvalidReason, string> = {
  FORMATO: 'Código não reconhecido como ingresso BolsoPass',
  ASSINATURA: 'Assinatura digital inválida (possível falsificação)',
  TOKEN_EXPIRADO: 'Token expirado: código estático ou captura de tela',
};
