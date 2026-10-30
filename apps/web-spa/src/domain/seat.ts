import { DomainError } from './errors';

/** Ciclo de vida do assento no inventário (seção 2 de "06 - Maquinas de Estados"). */
export type SeatStatus = 'DISPONIVEL' | 'BLOQUEADO_TEMPORARIO' | 'VENDIDO' | 'UTILIZADO';

export type SeatEvent = 'TRAVA_ADQUIRIDA' | 'PEDIDO_PAGO' | 'TIMEOUT_OU_CANCELAMENTO' | 'CHECKIN';

const SEAT_TRANSITIONS: Record<SeatStatus, Partial<Record<SeatEvent, SeatStatus>>> = {
  DISPONIVEL: { TRAVA_ADQUIRIDA: 'BLOQUEADO_TEMPORARIO' },
  BLOQUEADO_TEMPORARIO: { PEDIDO_PAGO: 'VENDIDO', TIMEOUT_OU_CANCELAMENTO: 'DISPONIVEL' },
  VENDIDO: { CHECKIN: 'UTILIZADO' },
  UTILIZADO: {},
};

export function seatTransition(from: SeatStatus, event: SeatEvent): SeatStatus {
  const next = SEAT_TRANSITIONS[from][event];
  if (!next) throw new DomainError(`Transição de assento proibida: ${from} --${event}-->`);
  return next;
}

/** Marcações do produtor no editor de assentos (mockup Admin, passo 4). */
export type SeatKind = 'PADRAO' | 'PCD' | 'BLOQUEADO_PRODUCAO' | 'OBSTRUCAO';

export interface Seat {
  id: string;
  sectorId: string;
  row: string;
  number: number;
  label: string;
  kind: SeatKind;
  status: SeatStatus;
  /** Id da reserva que segura a trava temporária. */
  heldBy?: string;
}

/** Como o assento aparece no mapa (legenda do mockup "Mapa Interativo de Assentos"). */
export type SeatView =
  | 'DISPONIVEL'
  | 'SELECIONADO'
  | 'RESERVADO_OUTRO'
  | 'OCUPADO'
  | 'INDISPONIVEL';

export function seatView(
  seat: Seat,
  selectedIds: ReadonlySet<string>,
  myReservationId?: string,
): SeatView {
  if (seat.kind === 'BLOQUEADO_PRODUCAO' || seat.kind === 'OBSTRUCAO') return 'INDISPONIVEL';
  if (seat.status === 'VENDIDO' || seat.status === 'UTILIZADO') return 'OCUPADO';
  if (seat.status === 'BLOQUEADO_TEMPORARIO') {
    return myReservationId && seat.heldBy === myReservationId ? 'SELECIONADO' : 'RESERVADO_OUTRO';
  }
  return selectedIds.has(seat.id) ? 'SELECIONADO' : 'DISPONIVEL';
}

export function isSelectable(seat: Seat): boolean {
  return seat.status === 'DISPONIVEL' && (seat.kind === 'PADRAO' || seat.kind === 'PCD');
}

/** Invariante de conservação: disponíveis + bloqueados + vendidos + utilizados == capacidade. */
export function checkConservation(capacity: number, seats: readonly Seat[]): boolean {
  const counted = seats.filter((s) => s.kind !== 'OBSTRUCAO').length;
  return counted === capacity;
}

export function countByStatus(seats: readonly Seat[]): Record<SeatStatus, number> {
  const out: Record<SeatStatus, number> = {
    DISPONIVEL: 0,
    BLOQUEADO_TEMPORARIO: 0,
    VENDIDO: 0,
    UTILIZADO: 0,
  };
  for (const s of seats) out[s.status] += 1;
  return out;
}
