import type { OrderStatus } from '@/domain/order';
import type { OrderTotals } from '@/domain/pricing';
import type { Holder } from '@/domain/purchaseRules';
import type { Seat, SeatKind, SeatStatus } from '@/domain/seat';
import type { InvalidReason, ScanStatus } from '@/domain/ticketQr';

/** Perfis do RF-02 (claim `roles` do JWT). */
export type Role = 'ROLE_CLIENT' | 'ROLE_ORGANIZER' | 'ROLE_OPERATOR' | 'ROLE_ADMIN';

export interface User {
  id: string;
  name: string;
  email: string;
  roles: Role[];
  cpf?: string;
  phone?: string;
  /** Matrícula do operador de portaria. */
  badge?: string;
}

export interface Session {
  user: User;
  accessToken: string;
  /** ISO. Access token curto (15 min, RF-01). */
  expiresAt: string;
  /** Refresh token de 7 dias (RF-01). */
  refreshToken?: string;
}

export type EventStatus =
  | 'RASCUNHO'
  | 'AGENDADO'
  | 'EM_VENDAS'
  | 'PAUSADO'
  | 'ESGOTADO'
  | 'ENCERRADO'
  | 'CANCELADO';

export type EventCategory =
  | 'FESTIVAIS'
  | 'ELETRONICA'
  | 'ROCK'
  | 'SERTANEJO_POP'
  | 'TEATRO'
  | 'ESPORTES';

export interface EventSummary {
  id: string;
  title: string;
  venue: string;
  city: string;
  startsAt: string;
  endsAt?: string;
  category: EventCategory;
  status: EventStatus;
  fromPriceCents: number;
  capacity: number;
  sold: number;
  /** Matiz usada no placeholder de capa (0-360). */
  hue: number;
  highlight?: string;
  hasVirtualQueue: boolean;
  numberedSeats: boolean;
}

export type LotStatus = 'ATIVO' | 'ESGOTADO' | 'PROGRAMADO';

export interface Lot {
  id: string;
  sectorId: string;
  name: string;
  description: string;
  priceCents: number;
  originalPriceCents?: number;
  quantity: number;
  sold: number;
  limitPerCpf: number;
  maxInstallments: number;
  status: LotStatus;
  perks: string[];
  gate: string;
  /** Preço do próximo lote (virada automática). */
  nextPriceCents?: number;
}

export interface Sector {
  id: string;
  name: string;
  kind: 'NUMERADO' | 'PISTA';
  capacity: number;
  priceCents: number;
  color: string;
}

export interface EventDetail extends EventSummary {
  description: string;
  address: string;
  ageRating: string;
  sectors: Sector[];
  lots: Lot[];
  lineup: { stage: string; artists: string; description: string }[];
  nextLotAt?: string;
}

export interface QueueStatus {
  position: number;
  total: number;
  etaSeconds: number;
  progressPct: number;
  sessionId: string;
  /** Liberado para entrar na seleção de assentos. */
  admitted: boolean;
}

export interface SeatMap {
  eventId: string;
  sectors: (Sector & { rows: string[] })[];
  seats: Seat[];
  liveBuyers: number;
}

export interface Reservation {
  id: string;
  eventId: string;
  seatIds: string[];
  orderId: string;
  status: OrderStatus;
  createdAt: string;
  expiresAt: string;
  /** Hora do servidor na resposta, para sincronizar o cronômetro. */
  serverNow: string;
}

export interface OrderItem {
  seatId: string;
  seatLabel: string;
  sectorName: string;
  priceCents: number;
  holderName?: string;
  holderCpf?: string;
  ticketId?: string;
}

export type PaymentMethod = 'CREDIT_CARD' | 'PIX';

export interface Order {
  id: string;
  code: string;
  reservationId: string;
  event: EventSummary;
  status: OrderStatus;
  items: OrderItem[];
  totals: OrderTotals;
  payment?: { method: PaymentMethod; last4?: string; installments: number; approvedAt?: string };
  createdAt: string;
  paidAt?: string;
  /** Motivo de recusa do gateway, quando RECUSADO. */
  declineReason?: string;
}

export interface PaymentRequest {
  reservationId: string;
  method: PaymentMethod;
  installments: number;
  holders: Holder[];
  card?: { number: string; holderName: string; expiry: string; cvv: string };
}

export type TicketStatus = 'ATIVO' | 'UTILIZADO' | 'TRANSFERIDO' | 'CANCELADO';

export interface Ticket {
  id: string;
  orderId: string;
  event: EventSummary;
  seatLabel: string;
  sectorName: string;
  holderName: string;
  holderEmail: string;
  gate: string;
  status: TicketStatus;
  issuedAt: string;
}

export interface TicketQr {
  qr: string;
  window: number;
}

// ---------- Organizador / Admin ----------

/** Painel do organizador: tudo calculado no banco pelo backend. */
export interface OrganizerDashboard {
  eventsActive: number;
  eventsNewThisMonth: number;
  ticketsToday: number;
  ticketsTodayGrowthPct: number;
  revenueTodayCents: number;
  grossRevenueCents: number;
  netRevenueCents: number;
  platformFeePct: number;
  weeklyRevenue: { day: string; cents: number }[];
  critical?: { eventId: string; title: string; venue: string; daysLeft: number; occupancyPct: number };
  lotTurn?: {
    eventId: string;
    eventTitle: string;
    sectorName: string;
    lotName: string;
    remaining: number;
    currentPriceCents: number;
    nextPriceCents: number;
  };
  health: {
    locksLastMinute: number;
    activeLocks: number;
    oversellingIncidents: number;
    expiredReservationsToday: number;
    checkoutConversionPct: number;
    outboxPending: number;
  };
}

export interface OrganizerEventRow {
  event: EventSummary;
  revenueCents: number;
  ticketsSold: number;
  avgTicketCents: number;
  note: string;
}

export type LotSummaryStatus = 'ESGOTADO' | 'ATIVO' | 'PROGRAMADO' | 'FECHADO';

export interface SectorSummary {
  id: string;
  name: string;
  capacity: number;
  sold: number;
  locked: number;
  closed: boolean;
  lots: { name: string; priceCents: number; quantity: number; sold: number; status: LotSummaryStatus }[];
  /** Portão de entrada do setor e quantos já entraram por ele. */
  gate?: string;
  checkedIn: number;
}

export interface AuditLogEntry {
  id: string;
  at: string;
  kind: 'EVENTO' | 'LOTE' | 'BILHETERIA' | 'SEGURANCA';
  title: string;
  description: string;
  actor: string;
}

export interface OrganizerEventDetail {
  event: EventDetail;
  revenueCents: number;
  netRevenueCents: number;
  ticketsIssued: number;
  activeLocks: number;
  expiredToday: number;
  salesLast6h: number[];
  sectors: SectorSummary[];
  logs: AuditLogEntry[];
}

/** Mapa de um setor visto pelo organizador (GET /organizer/events/{id}/sectors/{sectorId}/seats). */
export interface OrganizerSectorSeats {
  sectorId: string;
  sectorName: string;
  gate?: string;
  seats: { id: string; label: string; row: string; number: number; status: SeatStatus; kind: SeatKind }[];
}

export interface PersonData {
  name: string;
  email?: string;
  phone?: string;
  cpf?: string;
}

/** Para quem o assento foi vendido. Cada consulta é registrada na auditoria (LGPD). */
export interface SeatSale {
  seatId: string;
  seatLabel: string;
  sectorName: string;
  seatStatus: SeatStatus;
  orderCode: string;
  paidAt: string;
  paymentMethod?: PaymentMethod;
  cardLast4?: string;
  installments: number;
  priceCents: number;
  orderTotalCents: number;
  orderSeats: number;
  buyer?: PersonData;
  holder: PersonData;
  ticketId?: string;
  checkedInAt?: string;
  checkedInGate?: string;
}

export type MarkedSeatKind = 'PCD' | 'BLOQUEADO_PRODUCAO' | 'OBSTRUCAO';

/** Corpo do wizard de criação (POST /organizer/events). */
export interface CreateEventInput {
  title: string;
  category: EventCategory;
  startsAt: string;
  venue: string;
  city: string;
  address?: string;
  description?: string;
  ageRating?: string;
  hasVirtualQueue: boolean;
  sectors: {
    name: string;
    capacity: number;
    seatsPerRow: number;
    gate?: string;
    description?: string;
    lots: { name: string; priceCents: number; quantity: number; limitPerCpf: number; maxInstallments: number }[];
  }[];
  /** Marcações do mini editor: índice do setor + número do assento. */
  blockedSeats: { sector: number; number: number; kind: MarkedSeatKind }[];
  publish: boolean;
}

// ---------- Operador de portaria ----------

export interface Gate {
  id: string;
  name: string;
  turnstiles: string;
  description: string;
  icon: 'door' | 'star' | 'accessible';
}

export interface AssignedEvent {
  event: EventSummary;
  checkedInPct: number;
  gatesOpen: boolean;
  gates: Gate[];
}

export interface ScanRecord {
  id: string;
  at: string;
  status: ScanStatus;
  reason?: InvalidReason;
  ticketId?: string;
  holderName: string;
  sectorName?: string;
  categoryName?: string;
  holderCpf?: string;
  gate: string;
  turnstile: string;
  previousUse?: { at: string; gate: string };
  /** false enquanto estiver na fila offline. */
  synced: boolean;
  offline: boolean;
}

/** Base que o terminal guarda para validar sem internet (RF-13). */
export interface OfflineTicketCache {
  eventId: string;
  publicKeyJwk: JsonWebKey;
  syncedAt: string;
  tickets: Record<string, { holderName: string; holderCpf: string; sectorName: string; categoryName: string }>;
  used: Record<string, { at: string; gate: string }>;
}
