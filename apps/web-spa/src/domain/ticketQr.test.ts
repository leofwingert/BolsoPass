import { beforeAll, describe, expect, it } from 'vitest';
import { generateKeyPair, sign, verify } from '@/lib/ecdsa';
import {
  QR_ROTATION_MS,
  decodeQr,
  encodeQr,
  msUntilRotation,
  qrWindow,
  signedPart,
  validateScan,
  type PreviousUse,
  type QrPayload,
  type ScanContext,
} from './ticketQr';

let keys: CryptoKeyPair;
const NOW = Date.parse('2026-10-24T19:42:15Z');

beforeAll(async () => {
  keys = await generateKeyPair();
});

async function makeQr(over: Partial<QrPayload> = {}, key = keys.privateKey): Promise<string> {
  const payload: QrPayload = {
    tid: 'TK-992-VIP',
    eid: 'evt-synthwave',
    sid: 'A-14',
    iat: '2026-10-01T10:00:00Z',
    win: qrWindow(NOW),
    ...over,
  };
  return encodeQr(payload, await sign(key, signedPart(payload)));
}

function ctx(over: Partial<ScanContext> = {}, used = new Map<string, PreviousUse>()): ScanContext {
  return {
    eventId: 'evt-synthwave',
    nowMs: NOW,
    verify: (data, sig) => verify(keys.publicKey, data, sig),
    findUse: (id) => used.get(id),
    ...over,
  };
}

describe('QR dinâmico', () => {
  it('codifica e decodifica o payload', async () => {
    const decoded = decodeQr(await makeQr());
    expect(decoded?.payload.tid).toBe('TK-992-VIP');
  });

  it('rejeita lixo e outros formatos', () => {
    expect(decodeQr('https://example.com')).toBeNull();
    expect(decodeQr('BP1.@@@.x')).toBeNull();
    expect(decodeQr('BP1.e30.x')).toBeNull(); // {} sem campos obrigatórios
  });

  it('troca a cada 15 segundos', () => {
    expect(qrWindow(NOW + QR_ROTATION_MS)).toBe(qrWindow(NOW) + 1);
    expect(msUntilRotation(0)).toBe(QR_ROTATION_MS);
  });
});

describe('validação na catraca', () => {
  it('libera ingresso válido no primeiro acesso', async () => {
    expect((await validateScan(await makeQr(), ctx())).status).toBe('VALIDO');
  });

  it('aceita uma janela de tolerância de relógio', async () => {
    const qr = await makeQr({ win: qrWindow(NOW) - 1 });
    expect((await validateScan(qr, ctx())).status).toBe('VALIDO');
  });

  it('bloqueia print de tela (token de janela antiga)', async () => {
    const qr = await makeQr({ win: qrWindow(NOW) - 5 });
    expect(await validateScan(qr, ctx())).toMatchObject({
      status: 'INVALIDO',
      reason: 'TOKEN_EXPIRADO',
    });
  });

  it('bloqueia assinatura falsa', async () => {
    const other = await generateKeyPair();
    const qr = await makeQr({}, other.privateKey);
    expect(await validateScan(qr, ctx())).toMatchObject({ status: 'INVALIDO', reason: 'ASSINATURA' });
  });

  it('bloqueia código que não é BolsoPass', async () => {
    expect(await validateScan('qualquer', ctx())).toEqual({ status: 'INVALIDO', reason: 'FORMATO' });
  });

  it('avisa evento incorreto', async () => {
    const qr = await makeQr({ eid: 'evt-neon' });
    expect((await validateScan(qr, ctx())).status).toBe('EVENTO_INCORRETO');
  });

  it('bloqueia reuso (clonagem) e informa a entrada anterior', async () => {
    const used = new Map([['TK-992-VIP', { at: '18:24', gate: 'Portão 02' }]]);
    expect(await validateScan(await makeQr(), ctx({}, used))).toMatchObject({
      status: 'JA_UTILIZADO',
      previousUse: { gate: 'Portão 02' },
    });
  });
});
