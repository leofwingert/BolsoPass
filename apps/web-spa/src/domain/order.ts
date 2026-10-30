import { DomainError } from './errors';

/**
 * Máquina de estados do pedido, conforme "06 - Maquinas de Estados e Regras de Negocio".
 * O backend é a fonte da verdade. O front usa a mesma tabela para decidir o que mostrar
 * e para nunca disparar uma ação inválida (ex.: pagar um pedido EXPIRADO).
 */
export type OrderStatus =
  | 'CRIADO'
  | 'RESERVADO'
  | 'AGUARDANDO_PAGAMENTO'
  | 'PAGO'
  | 'RECUSADO'
  | 'EXPIRADO'
  | 'CANCELADO';

export type OrderEvent =
  | 'ASSENTO_BLOQUEADO'
  | 'BLOQUEIO_FALHOU'
  | 'SUBMETER_PAGAMENTO'
  | 'PAGAMENTO_APROVADO'
  | 'PAGAMENTO_RECUSADO'
  | 'TIMEOUT_10_MINUTOS'
  | 'DESISTENCIA';

const TRANSITIONS: Record<OrderStatus, Partial<Record<OrderEvent, OrderStatus>>> = {
  CRIADO: { ASSENTO_BLOQUEADO: 'RESERVADO', BLOQUEIO_FALHOU: 'CANCELADO' },
  RESERVADO: { SUBMETER_PAGAMENTO: 'AGUARDANDO_PAGAMENTO', TIMEOUT_10_MINUTOS: 'EXPIRADO' },
  AGUARDANDO_PAGAMENTO: {
    PAGAMENTO_APROVADO: 'PAGO',
    PAGAMENTO_RECUSADO: 'RECUSADO',
    TIMEOUT_10_MINUTOS: 'EXPIRADO',
  },
  RECUSADO: { SUBMETER_PAGAMENTO: 'AGUARDANDO_PAGAMENTO', DESISTENCIA: 'CANCELADO' },
  PAGO: {},
  EXPIRADO: {},
  CANCELADO: {},
};

export const TERMINAL_STATUSES: readonly OrderStatus[] = ['PAGO', 'EXPIRADO', 'CANCELADO'];

export function isTerminal(status: OrderStatus): boolean {
  return TERMINAL_STATUSES.includes(status);
}

export function canTransition(from: OrderStatus, event: OrderEvent): boolean {
  return TRANSITIONS[from][event] !== undefined;
}

export function transition(from: OrderStatus, event: OrderEvent): OrderStatus {
  const next = TRANSITIONS[from][event];
  if (!next) {
    throw new DomainError(`Transição proibida: ${from} --${event}-->`);
  }
  return next;
}

/**
 * Pagamento (ou nova tentativa após RECUSADO) só é permitido dentro do prazo da reserva.
 * Um pedido EXPIRADO jamais pode virar PAGO.
 */
export function canSubmitPayment(status: OrderStatus, remainingMs: number): boolean {
  return canTransition(status, 'SUBMETER_PAGAMENTO') && remainingMs > 0;
}

export const ORDER_STATUS_LABEL: Record<OrderStatus, string> = {
  CRIADO: 'Criado',
  RESERVADO: 'Reservado',
  AGUARDANDO_PAGAMENTO: 'Aguardando pagamento',
  PAGO: 'Pago & confirmado',
  RECUSADO: 'Pagamento recusado',
  EXPIRADO: 'Expirado',
  CANCELADO: 'Cancelado',
};
