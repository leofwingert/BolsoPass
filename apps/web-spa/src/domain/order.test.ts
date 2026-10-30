import { describe, expect, it } from 'vitest';
import { DomainError } from './errors';
import {
  canSubmitPayment,
  canTransition,
  isTerminal,
  transition,
  type OrderEvent,
  type OrderStatus,
} from './order';

describe('FSM do pedido', () => {
  it.each<[OrderStatus, OrderEvent, OrderStatus]>([
    ['CRIADO', 'ASSENTO_BLOQUEADO', 'RESERVADO'],
    ['CRIADO', 'BLOQUEIO_FALHOU', 'CANCELADO'],
    ['RESERVADO', 'SUBMETER_PAGAMENTO', 'AGUARDANDO_PAGAMENTO'],
    ['RESERVADO', 'TIMEOUT_10_MINUTOS', 'EXPIRADO'],
    ['AGUARDANDO_PAGAMENTO', 'PAGAMENTO_APROVADO', 'PAGO'],
    ['AGUARDANDO_PAGAMENTO', 'PAGAMENTO_RECUSADO', 'RECUSADO'],
    ['AGUARDANDO_PAGAMENTO', 'TIMEOUT_10_MINUTOS', 'EXPIRADO'],
    ['RECUSADO', 'SUBMETER_PAGAMENTO', 'AGUARDANDO_PAGAMENTO'],
    ['RECUSADO', 'DESISTENCIA', 'CANCELADO'],
  ])('%s --%s--> %s', (from, event, to) => {
    expect(transition(from, event)).toBe(to);
  });

  it('PAGO é terminal e imutável', () => {
    const events: OrderEvent[] = [
      'ASSENTO_BLOQUEADO',
      'SUBMETER_PAGAMENTO',
      'PAGAMENTO_RECUSADO',
      'TIMEOUT_10_MINUTOS',
      'DESISTENCIA',
    ];
    for (const e of events) expect(() => transition('PAGO', e)).toThrow(DomainError);
    expect(isTerminal('PAGO')).toBe(true);
  });

  it('EXPIRADO jamais vira PAGO', () => {
    expect(canTransition('EXPIRADO', 'PAGAMENTO_APROVADO')).toBe(false);
    expect(() => transition('EXPIRADO', 'PAGAMENTO_APROVADO')).toThrow(DomainError);
  });

  it('não pula etapas (CRIADO -> PAGO)', () => {
    expect(() => transition('CRIADO', 'PAGAMENTO_APROVADO')).toThrow(/Transição proibida/);
  });

  it('pagamento só dentro do prazo da reserva', () => {
    expect(canSubmitPayment('RESERVADO', 1000)).toBe(true);
    expect(canSubmitPayment('RECUSADO', 1000)).toBe(true);
    expect(canSubmitPayment('RESERVADO', 0)).toBe(false);
    expect(canSubmitPayment('RECUSADO', 0)).toBe(false);
    expect(canSubmitPayment('AGUARDANDO_PAGAMENTO', 1000)).toBe(false);
    expect(canSubmitPayment('EXPIRADO', 1000)).toBe(false);
  });

  it('estados não terminais', () => {
    expect(isTerminal('RESERVADO')).toBe(false);
    expect(isTerminal('RECUSADO')).toBe(false);
  });
});
