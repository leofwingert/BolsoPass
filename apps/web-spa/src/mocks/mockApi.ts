import type { BolsoPassApi } from '@/api/contract';
import {
  ConflictError,
  ForbiddenError,
  ReservationExpiredError,
  UnauthorizedError,
} from '@/api/errors';
import { getAccessToken } from '@/api/session';
import {
  TopicBus,
  topics,
  type ConnectionStatus,
  type RealtimeClient,
  type RealtimeMessage,
} from '@/api/realtime';
import type {
  AssignedEvent,
  EventDetail,
  EventSummary,
  OfflineTicketCache,
  Order,
  OrganizerEventDetail,
  OrganizerEventRow,
  Reservation,
  Role,
  Session,
  Ticket,
  User,
} from '@/api/types';
import { canSubmitPayment, transition } from '@/domain/order';
import { computeTotals } from '@/domain/pricing';
import { validateHolders } from '@/domain/purchaseRules';
import { RESERVATION_TTL_MS } from '@/domain/reservation';
import { isSelectable, seatTransition, type Seat } from '@/domain/seat';
import { encodeQr, qrWindow, signedPart, type QrPayload } from '@/domain/ticketQr';
import { exportPublicKey, generateKeyPair, sign } from '@/lib/ecdsa';
import { buildSeats, events, gates, holderCpfs, holderNames, users } from './data';

// ---------------------------------------------------------------------------
// Estado em memória (some ao recarregar a página, exceto a sessão).
// ---------------------------------------------------------------------------

// Latência simulada de rede; nos testes automatizados responde na hora.
const delay = (min = 150, max = 450) =>
  import.meta.env.MODE === 'test' ? Promise.resolve() : new Promise((r) => setTimeout(r, min + Math.random() * (max - min)));

const seatsByEvent = new Map<string, Seat[]>(events.map((e) => [e.id, buildSeats(e)]));
const reservations = new Map<string, Reservation & { userId: string }>();
const orders = new Map<string, Order & { userId: string }>();
const tickets = new Map<string, Ticket & { userId: string; holderCpf: string; eventId: string }>();
const idempotency = new Map<string, { state: 'PROCESSING' | 'COMPLETED'; orderId?: string }>();
const usedTickets = new Map<string, Record<string, { at: string; gate: string }>>();
const queueStart = new Map<string, number>();
let keyPair: Promise<CryptoKeyPair> | undefined;
const platformKeys = () => (keyPair ??= generateKeyPair());

const bus = new TopicBus();
const statusListeners = new Set<(s: ConnectionStatus) => void>();

const uid = (prefix: string) => `${prefix}-${Math.random().toString(36).slice(2, 10)}`;

function currentUser(): User {
  const token = getAccessToken();
  const id = token?.startsWith('mock.') ? atob(token.split('.')[1] ?? '') : undefined;
  const user = users.find((u) => u.id === id);
  if (!user) throw new UnauthorizedError();
  return user;
}

function requireRole(...roles: Role[]): User {
  const user = currentUser();
  if (!user.roles.some((r) => roles.includes(r))) throw new ForbiddenError();
  return user;
}

function session(user: User): Session {
  const { password: _p, pin: _pin, ...safe } = user as User & { password?: string; pin?: string };
  return {
    user: safe,
    accessToken: `mock.${btoa(user.id)}`,
    expiresAt: new Date(Date.now() + 12 * 60 * 60 * 1000).toISOString(),
  };
}

function findEvent(id: string): EventDetail {
  const ev = events.find((e) => e.id === id);
  if (!ev) throw new Error('Evento não encontrado');
  return ev;
}

function summary(ev: EventDetail): EventSummary {
  const { description: _d, address: _a, ageRating: _r, sectors: _s, lots: _l, lineup: _li, nextLotAt: _n, ...s } = ev;
  return s;
}

function seatsOf(eventId: string): Seat[] {
  return seatsByEvent.get(eventId) ?? [];
}

function publishSeats(eventId: string, msg: RealtimeMessage) {
  bus.publish(topics.eventSeats(eventId), msg);
}

function sectorPrice(ev: EventDetail, sectorId: string): number {
  return ev.lots.find((l) => l.sectorId === sectorId)?.priceCents ?? 0;
}

function releaseSeats(eventId: string, seatIds: string[], reason: 'EXPIRADA' | 'CANCELADA') {
  const released: string[] = [];
  for (const seat of seatsOf(eventId)) {
    if (seatIds.includes(seat.id) && seat.status === 'BLOQUEADO_TEMPORARIO') {
      seat.status = seatTransition(seat.status, 'TIMEOUT_OU_CANCELAMENTO');
      seat.heldBy = undefined;
      released.push(seat.id);
    }
  }
  if (released.length) publishSeats(eventId, { type: 'SEAT_RELEASED', eventId, seatIds: released, reason });
}

/** Trava atômica: tudo ou nada. JS é single-thread, então este bloco não é interrompido. */
function lockSeats(eventId: string, seatIds: string[], holder: string): string[] {
  const seats = seatsOf(eventId).filter((s) => seatIds.includes(s.id));
  const conflicts = seats.filter((s) => !isSelectable(s)).map((s) => s.id);
  if (conflicts.length || seats.length !== seatIds.length) return conflicts;
  for (const s of seats) {
    s.status = seatTransition(s.status, 'TRAVA_ADQUIRIDA');
    s.heldBy = holder;
  }
  publishSeats(eventId, { type: 'SEAT_LOCKED', eventId, seatIds, reservationId: holder });
  return [];
}

function expireReservation(reservationId: string) {
  const res = reservations.get(reservationId);
  if (!res) return;
  const order = orders.get(res.orderId);
  if (order && (order.status === 'RESERVADO' || order.status === 'AGUARDANDO_PAGAMENTO')) {
    order.status = transition(order.status, 'TIMEOUT_10_MINUTOS');
    res.status = order.status;
    bus.publish(topics.order(order.id), { type: 'ORDER_STATUS', orderId: order.id, status: order.status });
  }
  // A trava no Redis tem TTL próprio: some ao fim dos 10 min mesmo se o pedido estiver RECUSADO.
  if (order?.status !== 'PAGO') releaseSeats(res.eventId, res.seatIds, 'EXPIRADA');
}

// ---------------------------------------------------------------------------
// Compradores concorrentes simulados (alimenta o mapa em tempo real).
// ---------------------------------------------------------------------------

function simulateCrowd() {
  for (const topic of bus.topicsWithSubscribers()) {
    const match = /^events\/(.+)\/seats$/.exec(topic);
    if (!match) continue;
    const eventId = match[1]!;
    const free = seatsOf(eventId).filter(isSelectable);
    if (free.length > 4 && Math.random() < 0.7) {
      const seat = free[Math.floor(Math.random() * free.length)]!;
      const holder = uid('other');
      lockSeats(eventId, [seat.id], holder);
      setTimeout(
        () => {
          if (seat.heldBy !== holder) return;
          if (Math.random() < 0.5) {
            seat.status = seatTransition(seat.status, 'PEDIDO_PAGO');
            seat.heldBy = undefined;
            publishSeats(eventId, { type: 'SEAT_SOLD', eventId, seatIds: [seat.id] });
          } else {
            releaseSeats(eventId, [seat.id], 'EXPIRADA');
          }
        },
        15_000 + Math.random() * 25_000,
      );
    }
    publishSeats(eventId, {
      type: 'LIVE_BUYERS',
      eventId,
      count: 1_780 + Math.floor(Math.random() * 120),
    });
  }
}
setInterval(simulateCrowd, 6_000);

export const mockRealtime: RealtimeClient = {
  subscribe: (topic, handler) => bus.subscribe(topic, handler),
  getStatus: () => 'connected',
  onStatus: (cb) => {
    statusListeners.add(cb);
    return () => statusListeners.delete(cb);
  },
};

// ---------------------------------------------------------------------------
// Pedidos iniciais da Sarah (mockup "Meus Pedidos & Ingressos").
// ---------------------------------------------------------------------------

function seedOrder(opts: {
  eventId: string;
  sectorId: string;
  numbers: number[];
  code: string;
  method: 'CREDIT_CARD' | 'PIX';
  daysAgo: number;
  used?: boolean;
}) {
  const ev = findEvent(opts.eventId);
  const sarah = users[0]!;
  const sector = ev.sectors.find((s) => s.id === opts.sectorId)!;
  const lot = ev.lots.find((l) => l.sectorId === opts.sectorId)!;
  const orderId = uid('ord');
  const paidAt = new Date(Date.now() - opts.daysAgo * 24 * 60 * 60 * 1000).toISOString();
  const items = opts.numbers.map((n, i) => {
    const seat = seatsOf(ev.id).find((s) => s.sectorId === opts.sectorId && s.number === n)!;
    seat.status = opts.used ? 'UTILIZADO' : 'VENDIDO';
    seat.heldBy = undefined;
    const ticketId = `TK-${opts.code.slice(4, 7)}-${seat.label}`;
    const holderName = i === 0 ? sarah.name : 'Marcus Vinicius Andrade';
    const holderCpf = i === 0 ? sarah.cpf! : '412.008.971-89';
    tickets.set(ticketId, {
      id: ticketId,
      orderId,
      eventId: ev.id,
      event: summary(ev),
      seatLabel: seat.label,
      sectorName: sector.name,
      holderName,
      holderCpf,
      holderEmail: sarah.email,
      gate: lot.gate,
      status: opts.used ? 'UTILIZADO' : 'ATIVO',
      issuedAt: paidAt,
      userId: sarah.id,
    });
    return {
      seatId: seat.id,
      seatLabel: seat.label,
      sectorName: sector.name,
      priceCents: lot.priceCents,
      holderName,
      holderCpf,
      ticketId,
    };
  });
  orders.set(orderId, {
    id: orderId,
    code: opts.code,
    reservationId: uid('res'),
    event: summary(ev),
    status: 'PAGO',
    items,
    totals: computeTotals(items),
    payment: {
      method: opts.method,
      last4: opts.method === 'CREDIT_CARD' ? '4291' : undefined,
      installments: 1,
      approvedAt: paidAt,
    },
    createdAt: paidAt,
    paidAt,
    userId: sarah.id,
  });
}

seedOrder({ eventId: 'evt-synthwave', sectorId: 'A', numbers: [14, 15], code: 'PLS-98402-BR', method: 'CREDIT_CARD', daysAgo: 0 });
seedOrder({ eventId: 'evt-neon', sectorId: 'A', numbers: [7], code: 'PLS-91204-BR', method: 'PIX', daysAgo: 20 });
seedOrder({ eventId: 'evt-metal', sectorId: 'V', numbers: [2, 3], code: 'PLS-84391-BR', method: 'CREDIT_CARD', daysAgo: 40 });
seedOrder({ eventId: 'evt-retro', sectorId: 'A', numbers: [1, 2], code: 'PLS-70119-BR', method: 'CREDIT_CARD', daysAgo: 150, used: true });

/** Titulares fictícios para os demais assentos vendidos (base offline da portaria). */
function syntheticHolder(seatId: string) {
  let h = 0;
  for (const c of seatId) h = (h * 31 + c.charCodeAt(0)) >>> 0;
  return { name: holderNames[h % holderNames.length]!, cpf: holderCpfs[h % holderCpfs.length]! };
}

// ---------------------------------------------------------------------------
// Implementação do contrato
// ---------------------------------------------------------------------------

export const mockApi: BolsoPassApi = {
  auth: {
    async login(email, password) {
      await delay();
      const user = users.find((u) => u.email.toLowerCase() === email.trim().toLowerCase());
      if (!user || (user as { password?: string }).password !== password) {
        throw new UnauthorizedError('E-mail ou senha incorretos');
      }
      return session(user);
    },
    async loginOperator(badge, pin) {
      await delay();
      const user = users.find(
        (u) =>
          (u.badge === badge.trim().toUpperCase() || u.email === badge.trim().toLowerCase()) &&
          u.roles.includes('ROLE_OPERATOR'),
      );
      if (!user || (user as { pin?: string }).pin !== pin) {
        throw new UnauthorizedError('Credencial ou PIN inválidos');
      }
      return session(user);
    },
    async refresh() {
      await delay(50, 100);
      return session(currentUser());
    },
    async logout() {
      await delay(50, 100);
    },
  },

  catalog: {
    async listEvents(filters = {}) {
      await delay();
      const q = filters.q?.toLowerCase().trim();
      return events
        .filter((e) => e.status === 'EM_VENDAS' || e.status === 'ESGOTADO')
        .filter((e) => !filters.category || e.category === filters.category)
        .filter((e) => !filters.city || e.city === filters.city)
        .filter((e) => !q || `${e.title} ${e.venue} ${e.city}`.toLowerCase().includes(q))
        .map(summary);
    },
    async getEvent(id) {
      await delay();
      const ev = findEvent(id);
      // Ocupação dos lotes vem do inventário (estoque real), não de um campo estático.
      const seats = seatsOf(id);
      return {
        ...ev,
        lots: ev.lots.map((l) => ({
          ...l,
          sold: seats.filter((s) => s.sectorId === l.sectorId && s.status !== 'DISPONIVEL').length,
        })),
      };
    },
    async getQueue(eventId) {
      await delay(80, 160);
      const start = queueStart.get(eventId) ?? Date.now();
      queueStart.set(eventId, start);
      const position = Math.max(0, 12 - Math.floor((Date.now() - start) / 1000));
      return {
        position,
        total: 12_450,
        etaSeconds: position * 5,
        progressPct: Math.round(((12 - position) / 12) * 100),
        sessionId: `tk_${eventId.slice(4, 9)}_${start.toString(36).slice(-4)}`,
        admitted: position === 0,
      };
    },
    async getSeatMap(eventId) {
      await delay();
      requireRole('ROLE_CLIENT', 'ROLE_ADMIN');
      const ev = findEvent(eventId);
      return {
        eventId,
        sectors: ev.sectors.map((s) => ({
          ...s,
          priceCents: sectorPrice(ev, s.id),
          rows: [...new Set(seatsOf(eventId).filter((x) => x.sectorId === s.id).map((x) => x.row))],
        })),
        seats: seatsOf(eventId).map((s) => ({ ...s })),
        liveBuyers: 1_842,
      };
    },
  },

  inventory: {
    async reserveSeats(eventId, seatIds) {
      await delay(80, 200);
      const user = requireRole('ROLE_CLIENT');
      const ev = findEvent(eventId);
      if (seatIds.length === 0) throw new Error('Selecione ao menos um assento');
      const limit = Math.min(
        ...seatIds.map((id) => {
          const sectorId = seatsOf(eventId).find((s) => s.id === id)?.sectorId;
          return ev.lots.find((l) => l.sectorId === sectorId)?.limitPerCpf ?? 4;
        }),
      );
      if (seatIds.length > limit) throw new Error(`Limite de ${limit} ingressos por CPF neste lote`);

      const reservationId = uid('res');
      const conflicts = lockSeats(eventId, seatIds, reservationId);
      if (conflicts.length) {
        throw new ConflictError('Assento já reservado por outro comprador', conflicts);
      }

      const now = Date.now();
      const orderId = uid('ord');
      const status = transition('CRIADO', 'ASSENTO_BLOQUEADO');
      const reservation = {
        id: reservationId,
        eventId,
        seatIds,
        orderId,
        status,
        createdAt: new Date(now).toISOString(),
        expiresAt: new Date(now + RESERVATION_TTL_MS).toISOString(),
        serverNow: new Date(now).toISOString(),
        userId: user.id,
      };
      reservations.set(reservationId, reservation);
      const items = seatsOf(eventId)
        .filter((s) => seatIds.includes(s.id))
        .map((s) => ({
          seatId: s.id,
          seatLabel: s.label,
          sectorName: ev.sectors.find((x) => x.id === s.sectorId)?.name ?? '',
          priceCents: sectorPrice(ev, s.sectorId),
        }));
      orders.set(orderId, {
        id: orderId,
        code: `PLS-${Math.floor(10_000 + Math.random() * 89_999)}-BR`,
        reservationId,
        event: summary(ev),
        status,
        items,
        totals: computeTotals(items),
        createdAt: reservation.createdAt,
        userId: user.id,
      });
      setTimeout(() => expireReservation(reservationId), RESERVATION_TTL_MS);
      const { userId: _u, ...out } = reservation;
      return out;
    },
  },

  orders: {
    async getReservation(reservationId) {
      await delay(80, 160);
      const user = requireRole('ROLE_CLIENT');
      const res = reservations.get(reservationId);
      if (!res || res.userId !== user.id) throw new ForbiddenError('Reserva não pertence a você');
      const order = orders.get(res.orderId)!;
      const { userId: _u, ...reservation } = res;
      const { userId: _o, ...o } = order;
      return {
        reservation: { ...reservation, status: order.status, serverNow: new Date().toISOString() },
        order: o,
      };
    },

    async pay(request, idempotencyKey) {
      const user = requireRole('ROLE_CLIENT');
      // RF-09: mesma chave => mesma resposta; em andamento => 409.
      const prior = idempotency.get(idempotencyKey);
      if (prior?.state === 'PROCESSING') throw new ConflictError('Pagamento já está em processamento');
      if (prior?.state === 'COMPLETED') {
        const { userId: _u, ...o } = orders.get(prior.orderId!)!;
        return o;
      }
      idempotency.set(idempotencyKey, { state: 'PROCESSING' });
      try {
        await delay(700, 1200);
        const res = reservations.get(request.reservationId);
        if (!res || res.userId !== user.id) throw new ForbiddenError('Reserva não pertence a você');
        const order = orders.get(res.orderId)!;
        const remaining = Date.parse(res.expiresAt) - Date.now();
        if (!canSubmitPayment(order.status, remaining)) {
          if (remaining <= 0) throw new ReservationExpiredError();
          throw new ConflictError(`Pedido em estado ${order.status} não aceita pagamento`);
        }
        const issues = validateHolders(request.holders);
        if (issues.length) throw new Error(issues[0]!.message);

        order.status = transition(order.status, 'SUBMETER_PAGAMENTO');
        order.items = order.items.map((it) => {
          const h = request.holders.find((x) => x.seatId === it.seatId);
          return { ...it, holderName: h?.name, holderCpf: h?.cpf };
        });
        order.payment = {
          method: request.method,
          last4: request.card?.number.replace(/\D/g, '').slice(-4),
          installments: request.installments,
        };

        // Simulador do gateway (RF-11): cartão terminado em 0000 é recusado.
        const declined = request.method === 'CREDIT_CARD' && order.payment.last4 === '0000';
        if (declined) {
          order.status = transition(order.status, 'PAGAMENTO_RECUSADO');
          order.declineReason = 'Cartão recusado: saldo insuficiente';
        } else {
          order.status = transition(order.status, 'PAGAMENTO_APROVADO');
          order.paidAt = new Date().toISOString();
          order.payment.approvedAt = order.paidAt;
          order.declineReason = undefined;
          const ev = findEvent(res.eventId);
          for (const seat of seatsOf(res.eventId)) {
            if (res.seatIds.includes(seat.id) && seat.heldBy === res.id) {
              seat.status = seatTransition(seat.status, 'PEDIDO_PAGO');
              seat.heldBy = undefined;
            }
          }
          publishSeats(res.eventId, { type: 'SEAT_SOLD', eventId: res.eventId, seatIds: res.seatIds });
          // Emissão do ingresso (ticketing-service, via Outbox + Kafka).
          order.items = order.items.map((it) => {
            const ticketId = `TK-${order.code.slice(4, 7)}-${it.seatLabel}`;
            const lot = ev.lots.find((l) => ev.sectors.find((s) => s.name === it.sectorName)?.id === l.sectorId);
            tickets.set(ticketId, {
              id: ticketId,
              orderId: order.id,
              eventId: ev.id,
              event: summary(ev),
              seatLabel: it.seatLabel,
              sectorName: it.sectorName,
              holderName: it.holderName ?? user.name,
              holderCpf: it.holderCpf ?? '',
              holderEmail: user.email,
              gate: lot?.gate ?? 'Portão 01',
              status: 'ATIVO',
              issuedAt: order.paidAt!,
              userId: user.id,
            });
            return { ...it, ticketId };
          });
        }
        res.status = order.status;
        bus.publish(topics.order(order.id), { type: 'ORDER_STATUS', orderId: order.id, status: order.status });
        idempotency.set(idempotencyKey, { state: 'COMPLETED', orderId: order.id });
        const { userId: _u, ...o } = order;
        return o;
      } catch (err) {
        idempotency.delete(idempotencyKey);
        throw err;
      }
    },

    async giveUp(reservationId) {
      await delay(80, 160);
      const user = requireRole('ROLE_CLIENT');
      const res = reservations.get(reservationId);
      if (!res || res.userId !== user.id) return;
      const order = orders.get(res.orderId)!;
      // FSM: só RECUSADO aceita desistência (RECUSADO -> CANCELADO).
      order.status = transition(order.status, 'DESISTENCIA');
      res.status = order.status;
      releaseSeats(res.eventId, res.seatIds, 'CANCELADA');
    },

    async listMine() {
      await delay();
      const user = requireRole('ROLE_CLIENT');
      return [...orders.values()]
        .filter((o) => o.userId === user.id && o.status === 'PAGO')
        .sort((a, b) => Date.parse(b.createdAt) - Date.parse(a.createdAt))
        .map(({ userId: _u, ...o }) => o);
    },

    async getOrder(orderId) {
      await delay();
      const user = requireRole('ROLE_CLIENT');
      const order = orders.get(orderId);
      if (!order || order.userId !== user.id) throw new ForbiddenError('Pedido não pertence a você');
      const { userId: _u, ...o } = order;
      return o;
    },
  },

  tickets: {
    async listMine() {
      await delay();
      const user = requireRole('ROLE_CLIENT');
      return [...tickets.values()]
        .filter((t) => t.userId === user.id)
        .map(({ userId: _u, holderCpf: _c, eventId: _e, ...t }) => t);
    },
    async get(ticketId) {
      await delay();
      const user = requireRole('ROLE_CLIENT');
      const t = tickets.get(ticketId);
      // Doc 07: só o titular do pedido vê o ingresso.
      if (!t || t.userId !== user.id) throw new ForbiddenError('Ingresso não pertence a você');
      const { userId: _u, holderCpf: _c, eventId: _e, ...out } = t;
      return out;
    },
    async getQr(ticketId) {
      await delay(40, 90);
      const user = requireRole('ROLE_CLIENT');
      const t = tickets.get(ticketId);
      if (!t || t.userId !== user.id) throw new ForbiddenError();
      const window = qrWindow(Date.now());
      const payload: QrPayload = {
        tid: t.id,
        eid: t.eventId,
        sid: t.seatLabel,
        iat: t.issuedAt,
        win: window,
      };
      const { privateKey } = await platformKeys();
      return { qr: encodeQr(payload, await sign(privateKey, signedPart(payload))), window };
    },
  },

  organizer: {
    async getDashboard() {
      await delay();
      requireRole('ROLE_ORGANIZER', 'ROLE_ADMIN');
      const paid = [...orders.values()].filter((o) => o.status === 'PAGO' && o.paidAt);
      const startToday = new Date().setHours(0, 0, 0, 0);
      const day = 86_400_000;
      const weekly = Array.from({ length: 7 }, (_, i) => {
        const from = startToday - (6 - i) * day;
        const label = new Date(from).toLocaleDateString('pt-BR', { weekday: 'short' }).replace('.', '');
        return {
          day: label.charAt(0).toUpperCase() + label.slice(1),
          cents: paid
            .filter((o) => Date.parse(o.paidAt!) >= from && Date.parse(o.paidAt!) < from + day)
            .reduce((s, o) => s + o.totals.totalCents, 0),
        };
      });
      const today = paid.filter((o) => Date.parse(o.paidAt!) >= startToday);
      const onSale = events.filter((e) => e.status === 'EM_VENDAS');
      const top = [...onSale].sort((a, b) => b.sold / b.capacity - a.sold / a.capacity)[0];
      return {
        eventsActive: onSale.length,
        eventsNewThisMonth: events.length,
        ticketsToday: today.reduce((s, o) => s + o.items.length, 0),
        ticketsTodayGrowthPct: 0,
        revenueTodayCents: today.reduce((s, o) => s + o.totals.totalCents, 0),
        grossRevenueCents: paid.reduce((s, o) => s + o.totals.totalCents, 0),
        netRevenueCents: paid.reduce((s, o) => s + o.totals.subtotalCents, 0),
        platformFeePct: 10,
        weeklyRevenue: weekly,
        critical: top && {
          eventId: top.id,
          title: top.title,
          venue: top.venue,
          daysLeft: Math.max(0, Math.ceil((Date.parse(top.startsAt) - Date.now()) / day)),
          occupancyPct: Math.round((top.sold / top.capacity) * 100),
        },
        health: {
          locksLastMinute: 0,
          activeLocks: [...seatsByEvent.values()].flat().filter((s) => s.status === 'BLOQUEADO_TEMPORARIO').length,
          oversellingIncidents: 0,
          expiredReservationsToday: [...orders.values()].filter((o) => o.status === 'EXPIRADO').length,
          checkoutConversionPct: 100,
          outboxPending: 0,
        },
      };
    },
    async listEvents() {
      await delay();
      requireRole('ROLE_ORGANIZER', 'ROLE_ADMIN');
      return events.map((e): OrganizerEventRow => {
        const paid = [...orders.values()].filter((o) => o.status === 'PAGO' && o.event.id === e.id);
        const revenue = paid.reduce((s, o) => s + o.totals.subtotalCents, 0);
        const tickets = paid.reduce((s, o) => s + o.items.length, 0);
        return {
          event: summary(e),
          revenueCents: revenue,
          ticketsSold: tickets,
          avgTicketCents: tickets ? Math.round(revenue / tickets) : 0,
          note: e.status === 'RASCUNHO' ? 'Rascunho: publique para abrir as vendas' : `${e.capacity - e.sold} disponíveis`,
        };
      });
    },
    async getEvent(id) {
      await delay();
      requireRole('ROLE_ORGANIZER', 'ROLE_ADMIN');
      const ev = findEvent(id);
      const seats = seatsOf(id);
      const paid = [...orders.values()].filter((o) => o.status === 'PAGO' && o.event.id === id);
      const detail: OrganizerEventDetail = {
        event: ev,
        revenueCents: paid.reduce((s, o) => s + o.totals.totalCents, 0),
        netRevenueCents: paid.reduce((s, o) => s + o.totals.subtotalCents, 0),
        ticketsIssued: paid.reduce((s, o) => s + o.items.length, 0),
        activeLocks: seats.filter((s) => s.status === 'BLOQUEADO_TEMPORARIO').length,
        expiredToday: 0,
        salesLast6h: [0, 0, 0, 0, 0, paid.length],
        sectors: ev.sectors.map((sec) => {
          const mine = seats.filter((s) => s.sectorId === sec.id);
          const sold = mine.filter((s) => s.status === 'VENDIDO' || s.status === 'UTILIZADO').length;
          const lot = ev.lots.find((l) => l.sectorId === sec.id);
          return {
            id: sec.id,
            name: sec.name,
            capacity: mine.length,
            sold,
            locked: mine.filter((s) => s.status === 'BLOQUEADO_TEMPORARIO').length,
            closed: lot?.status === 'ESGOTADO',
            lots: [
              {
                name: lot?.name ?? 'Lote único',
                priceCents: lot?.priceCents ?? 0,
                quantity: mine.length,
                sold,
                status: sold >= mine.length ? 'ESGOTADO' : 'ATIVO',
              },
            ],
            gate: lot?.gate,
            checkedIn: mine.filter((s) => s.status === 'UTILIZADO').length,
          };
        }),
        logs: [],
      };
      return detail;
    },
    async createEvent(input) {
      await delay(400, 800);
      requireRole('ROLE_ORGANIZER', 'ROLE_ADMIN');
      const id = uid('evt');
      const capacity = input.sectors.reduce((s, x) => s + x.capacity, 0);
      const prices = input.sectors.flatMap((s) => s.lots.map((l) => l.priceCents));
      events.push({
        id,
        title: input.title,
        venue: input.venue,
        city: input.city,
        startsAt: input.startsAt,
        category: input.category,
        status: input.publish ? 'EM_VENDAS' : 'RASCUNHO',
        fromPriceCents: prices.length ? Math.min(...prices) : 0,
        capacity,
        sold: 0,
        hue: Math.floor(Math.random() * 360),
        hasVirtualQueue: input.hasVirtualQueue,
        numberedSeats: true,
        description: input.description ?? '',
        address: input.address ?? input.venue,
        ageRating: input.ageRating ?? 'Livre',
        sectors: [],
        lots: [],
        lineup: [],
      });
      seatsByEvent.set(id, []);
      return { id };
    },
    async publish(id) {
      await delay();
      findEvent(id).status = 'EM_VENDAS';
    },
    async pause(id) {
      await delay();
      findEvent(id).status = 'PAUSADO';
    },
    async resume(id) {
      await delay();
      findEvent(id).status = 'EM_VENDAS';
    },
    async getSectorSeats(eventId, sectorId) {
      await delay();
      requireRole('ROLE_ORGANIZER', 'ROLE_ADMIN');
      const ev = findEvent(eventId);
      const sector = ev.sectors.find((s) => s.id === sectorId);
      if (!sector) throw new Error('Setor não encontrado');
      return {
        sectorId,
        sectorName: sector.name,
        gate: ev.lots.find((l) => l.sectorId === sectorId)?.gate,
        seats: seatsOf(eventId)
          .filter((s) => s.sectorId === sectorId)
          .map(({ id, label, row, number, status, kind }) => ({ id, label, row, number, status, kind })),
      };
    },
    async getSeatSale(eventId, seatId) {
      await delay();
      requireRole('ROLE_ORGANIZER', 'ROLE_ADMIN');
      const seat = seatsOf(eventId).find((s) => s.id === seatId);
      if (!seat || (seat.status !== 'VENDIDO' && seat.status !== 'UTILIZADO')) throw new ConflictError('Este assento não foi vendido');
      const order = [...orders.values()].find((o) => o.status === 'PAGO' && o.event.id === eventId && o.items.some((i) => i.seatId === seatId));
      const sector = findEvent(eventId).sectors.find((s) => s.id === seat.sectorId);
      if (order) {
        const item = order.items.find((i) => i.seatId === seatId)!;
        const buyer = users.find((u) => u.id === order.userId);
        return {
          seatId, seatLabel: seat.label, sectorName: item.sectorName, seatStatus: seat.status,
          orderCode: order.code, paidAt: order.paidAt!, paymentMethod: order.payment?.method, cardLast4: order.payment?.last4,
          installments: order.payment?.installments ?? 1, priceCents: item.priceCents, orderTotalCents: order.totals.totalCents,
          orderSeats: order.items.length,
          buyer: buyer && { name: buyer.name, email: buyer.email, phone: buyer.phone, cpf: buyer.cpf },
          holder: { name: item.holderName ?? '', cpf: item.holderCpf },
          ticketId: item.ticketId,
        };
      }
      // Assento vendido na carga inicial do modo simulado: comprador fictício e determinístico.
      const n = seat.number + seat.row.length;
      const name = holderNames[n % holderNames.length]!;
      return {
        seatId, seatLabel: seat.label, sectorName: sector?.name ?? '', seatStatus: seat.status,
        orderCode: `PLS-${40000 + n}-BR`, paidAt: new Date(Date.now() - (n % 9) * 864e5).toISOString(), paymentMethod: 'PIX',
        installments: 1, priceCents: 0, orderTotalCents: 0, orderSeats: 1,
        buyer: { name, email: `${name.split(' ')[0]!.toLowerCase()}@exemplo.com`, phone: '(11) 99999-0000', cpf: holderCpfs[n % holderCpfs.length] },
        holder: { name, cpf: holderCpfs[n % holderCpfs.length] },
      };
    },
  },

  operator: {
    async listAssignedEvents() {
      await delay();
      requireRole('ROLE_OPERATOR', 'ROLE_ADMIN');
      const list: AssignedEvent[] = [
        { event: summary(findEvent('evt-synthwave')), checkedInPct: 72, gatesOpen: true, gates },
        { event: summary(findEvent('evt-neon')), checkedInPct: 0, gatesOpen: false, gates },
      ];
      return list;
    },
    async downloadCache(eventId) {
      await delay(300, 600);
      requireRole('ROLE_OPERATOR', 'ROLE_ADMIN');
      const ev = findEvent(eventId);
      const cache: OfflineTicketCache = {
        eventId,
        publicKeyJwk: await exportPublicKey((await platformKeys()).publicKey),
        syncedAt: new Date().toISOString(),
        tickets: {},
        used: { ...(usedTickets.get(eventId) ?? {}) },
      };
      for (const t of tickets.values()) {
        if (t.eventId !== eventId || t.status === 'CANCELADO') continue;
        cache.tickets[t.id] = {
          holderName: t.holderName,
          holderCpf: t.holderCpf,
          sectorName: t.sectorName,
          categoryName: 'Inteira',
        };
      }
      for (const s of seatsOf(eventId)) {
        if (s.status !== 'VENDIDO') continue;
        const id = `TK-SIM-${s.label}`;
        if (Object.values(cache.tickets).length > 200) break;
        const h = syntheticHolder(s.id);
        cache.tickets[id] = {
          holderName: h.name,
          holderCpf: h.cpf,
          sectorName: ev.sectors.find((x) => x.id === s.sectorId)?.name ?? '',
          categoryName: 'Inteira',
        };
      }
      return cache;
    },
    async syncScans(eventId, scans) {
      await delay(200, 400);
      requireRole('ROLE_OPERATOR', 'ROLE_ADMIN');
      const used = usedTickets.get(eventId) ?? {};
      let accepted = 0;
      for (const s of scans) {
        if (s.status !== 'VALIDO' || !s.ticketId) continue;
        if (!used[s.ticketId]) {
          used[s.ticketId] = { at: s.at, gate: s.gate };
          accepted++;
        }
      }
      usedTickets.set(eventId, used);
      return { accepted };
    },
  },
};

// ---------------------------------------------------------------------------
// Ajuda para demonstração da portaria: gera QR de um ingresso do evento.
// ---------------------------------------------------------------------------

export type DemoQrKind = 'valido' | 'print' | 'falso' | 'outro-evento';

export async function demoQr(eventId: string, ticketIds: string[], kind: DemoQrKind): Promise<string> {
  const keys = kind === 'falso' ? await generateKeyPair() : await platformKeys();
  const tid = ticketIds[Math.floor(Math.random() * ticketIds.length)] ?? 'TK-SIM-A01';
  const now = Date.now();
  const payload: QrPayload = {
    tid,
    eid: kind === 'outro-evento' ? 'evt-neon' : eventId,
    sid: tid.split('-').pop() ?? '',
    iat: new Date(now - 86_400_000).toISOString(),
    win: kind === 'print' ? qrWindow(now) - 20 : qrWindow(now),
  };
  return encodeQr(payload, await sign(keys.privateKey, signedPart(payload)));
}
