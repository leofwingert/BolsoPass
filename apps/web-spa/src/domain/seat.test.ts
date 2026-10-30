import { describe, expect, it } from 'vitest';
import { DomainError } from './errors';
import {
  checkConservation,
  countByStatus,
  isSelectable,
  seatTransition,
  seatView,
  type Seat,
} from './seat';

const seat = (over: Partial<Seat> = {}): Seat => ({
  id: 'A-01',
  sectorId: 'A',
  row: 'A',
  number: 1,
  label: '01',
  kind: 'PADRAO',
  status: 'DISPONIVEL',
  ...over,
});

describe('ciclo de vida do assento', () => {
  it('segue DISPONIVEL -> BLOQUEADO -> VENDIDO -> UTILIZADO', () => {
    let s = seatTransition('DISPONIVEL', 'TRAVA_ADQUIRIDA');
    expect(s).toBe('BLOQUEADO_TEMPORARIO');
    s = seatTransition(s, 'PEDIDO_PAGO');
    expect(s).toBe('VENDIDO');
    expect(seatTransition(s, 'CHECKIN')).toBe('UTILIZADO');
  });

  it('timeout devolve o assento ao catálogo', () => {
    expect(seatTransition('BLOQUEADO_TEMPORARIO', 'TIMEOUT_OU_CANCELAMENTO')).toBe('DISPONIVEL');
  });

  it('exclusão mútua: assento bloqueado não aceita nova trava', () => {
    expect(() => seatTransition('BLOQUEADO_TEMPORARIO', 'TRAVA_ADQUIRIDA')).toThrow(DomainError);
  });

  it('assento usado não volta a ser usado', () => {
    expect(() => seatTransition('UTILIZADO', 'CHECKIN')).toThrow(DomainError);
  });
});

describe('visão do assento no mapa', () => {
  const none = new Set<string>();

  it('mostra o estado certo para cada caso', () => {
    expect(seatView(seat(), none)).toBe('DISPONIVEL');
    expect(seatView(seat(), new Set(['A-01']))).toBe('SELECIONADO');
    expect(seatView(seat({ status: 'VENDIDO' }), none)).toBe('OCUPADO');
    expect(seatView(seat({ status: 'UTILIZADO' }), none)).toBe('OCUPADO');
    expect(seatView(seat({ kind: 'OBSTRUCAO' }), none)).toBe('INDISPONIVEL');
    expect(seatView(seat({ kind: 'BLOQUEADO_PRODUCAO' }), none)).toBe('INDISPONIVEL');
  });

  it('distingue a minha trava da trava de outro comprador', () => {
    const held = seat({ status: 'BLOQUEADO_TEMPORARIO', heldBy: 'res-1' });
    expect(seatView(held, none, 'res-1')).toBe('SELECIONADO');
    expect(seatView(held, none, 'res-2')).toBe('RESERVADO_OUTRO');
    expect(seatView(held, none)).toBe('RESERVADO_OUTRO');
  });

  it('só permite selecionar assento disponível e vendável', () => {
    expect(isSelectable(seat())).toBe(true);
    expect(isSelectable(seat({ kind: 'PCD' }))).toBe(true);
    expect(isSelectable(seat({ kind: 'OBSTRUCAO' }))).toBe(false);
    expect(isSelectable(seat({ status: 'BLOQUEADO_TEMPORARIO' }))).toBe(false);
  });
});

describe('invariante de conservação', () => {
  it('a soma dos estados é igual à capacidade', () => {
    const seats = [
      seat({ id: '1' }),
      seat({ id: '2', status: 'BLOQUEADO_TEMPORARIO' }),
      seat({ id: '3', status: 'VENDIDO' }),
      seat({ id: '4', kind: 'OBSTRUCAO' }),
    ];
    expect(checkConservation(3, seats)).toBe(true);
    expect(checkConservation(4, seats)).toBe(false);
    expect(countByStatus(seats)).toEqual({
      DISPONIVEL: 2,
      BLOQUEADO_TEMPORARIO: 1,
      VENDIDO: 1,
      UTILIZADO: 0,
    });
  });
});
