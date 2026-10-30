import { describe, expect, it } from 'vitest';
import { isValidCpf, maskCpf, redactCpf } from './cpf';
import { canEditLotPrice, lotAllocation, validateLots, type SectorDraft } from './lots';
import { computeTotals, installmentCents } from './pricing';
import { canAddSeat, canRequestRefund, validateHolders } from './purchaseRules';
import {
  RESERVATION_TTL_MS,
  clockOffset,
  formatCountdown,
  remainingMs,
  timerPhase,
} from './reservation';

describe('cronômetro da reserva (TTL 10 min)', () => {
  it('usa o relógio do servidor, não o do navegador', () => {
    const clientNow = Date.parse('2026-10-29T12:00:00Z');
    // Relógio local 30 s adiantado em relação ao servidor.
    const offset = clockOffset('2026-10-29T11:59:30Z', clientNow);
    const expiresAt = new Date(Date.parse('2026-10-29T11:59:30Z') + RESERVATION_TTL_MS).toISOString();
    expect(remainingMs(expiresAt, clientNow, offset)).toBe(RESERVATION_TTL_MS);
  });

  it('nunca fica negativo', () => {
    expect(remainingMs('2026-01-01T00:00:00Z', Date.parse('2026-01-02T00:00:00Z'), 0)).toBe(0);
  });

  it('entra em alerta nos 2 minutos finais', () => {
    expect(timerPhase(RESERVATION_TTL_MS)).toBe('normal');
    expect(timerPhase(120_000)).toBe('warning');
    expect(timerPhase(0)).toBe('expired');
  });

  it('formata mm:ss', () => {
    expect(formatCountdown(RESERVATION_TTL_MS)).toBe('10:00');
    expect(formatCountdown(582_000)).toBe('09:42');
    expect(formatCountdown(-5)).toBe('00:00');
  });
});

describe('preço', () => {
  it('aplica 10% de taxa de conveniência em centavos', () => {
    expect(computeTotals([{ priceCents: 58_000 }, { priceCents: 58_000 }])).toEqual({
      subtotalCents: 116_000,
      feeCents: 11_600,
      totalCents: 127_600,
    });
  });

  it('calcula parcelas arredondando para cima', () => {
    expect(installmentCents(127_600, 6)).toBe(21_267);
    expect(() => installmentCents(100, 0)).toThrow(RangeError);
  });
});

describe('CPF', () => {
  it('valida dígitos verificadores', () => {
    expect(isValidCpf('529.982.247-25')).toBe(true);
    expect(isValidCpf('529.982.247-24')).toBe(false);
    expect(isValidCpf('111.111.111-11')).toBe(false);
    expect(isValidCpf('123')).toBe(false);
  });

  it('mascara e oculta (LGPD)', () => {
    expect(maskCpf('52998224725')).toBe('529.982.247-25');
    expect(redactCpf('52998224725')).toBe('***.982.247-**');
    expect(redactCpf('1')).toBe('***');
  });
});

describe('regras de compra', () => {
  it('exige titular nominal com CPF único por pedido', () => {
    const issues = validateHolders([
      { seatId: 'A14', name: 'Sarah Jensen', cpf: '529.982.247-25' },
      { seatId: 'A15', name: 'Marcus', cpf: '529.982.247-25' },
      { seatId: 'A16', name: 'Ana Souza', cpf: '000' },
    ]);
    expect(issues).toEqual([
      { seatId: 'A15', field: 'name', message: 'Informe nome e sobrenome' },
      { seatId: 'A15', field: 'cpf', message: 'CPF já usado em outro ingresso' },
      { seatId: 'A16', field: 'cpf', message: 'CPF inválido' },
    ]);
  });

  it('respeita o limite por CPF do lote', () => {
    expect(canAddSeat(1, 2)).toBe(true);
    expect(canAddSeat(2, 2)).toBe(false);
  });

  it('reembolso só até 7 dias após a compra e antes do evento', () => {
    const paid = '2026-10-01T10:00:00Z';
    const event = '2026-12-01T20:00:00Z';
    expect(canRequestRefund(paid, event, Date.parse('2026-10-08T09:00:00Z'))).toBe(true);
    expect(canRequestRefund(paid, event, Date.parse('2026-10-09T10:00:00Z'))).toBe(false);
    expect(canRequestRefund(paid, '2026-10-02T10:00:00Z', Date.parse('2026-10-03T10:00:00Z'))).toBe(
      false,
    );
  });
});

describe('lotes do produtor', () => {
  const sector: SectorDraft = {
    id: 's1',
    name: 'Pista Premium',
    capacity: 15_000,
    lots: [
      { id: 'l1', name: '1º Lote', priceCents: 28_000, quantity: 5_000, limitPerCpf: 2, soldCount: 10 },
      { id: 'l2', name: '2º Lote', priceCents: 34_000, quantity: 6_000, limitPerCpf: 4, soldCount: 0 },
      { id: 'l3', name: '3º Lote', priceCents: 42_000, quantity: 4_000, limitPerCpf: 4, soldCount: 0 },
    ],
  };

  it('soma das cotas deve fechar com a capacidade', () => {
    expect(lotAllocation(sector)).toEqual({ allocated: 15_000, capacity: 15_000, ok: true });
    expect(lotAllocation({ ...sector, capacity: 16_000 }).ok).toBe(false);
  });

  it('preço de lote com vendas não pode mudar', () => {
    expect(canEditLotPrice(sector.lots[0]!)).toBe(false);
    expect(canEditLotPrice(sector.lots[1]!)).toBe(true);
  });

  it('aponta lotes inválidos', () => {
    const bad: SectorDraft = {
      ...sector,
      lots: [{ id: 'x', name: 'x', priceCents: 0, quantity: 0, limitPerCpf: 0, soldCount: 0 }],
    };
    expect(validateLots(bad)).toHaveLength(3);
    expect(validateLots(sector)).toHaveLength(0);
  });
});
