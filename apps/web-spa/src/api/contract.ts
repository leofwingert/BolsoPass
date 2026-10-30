import type {
  AssignedEvent,
  EventCategory,
  EventDetail,
  CreateEventInput,
  EventSummary,
  OfflineTicketCache,
  Order,
  OrganizerDashboard,
  OrganizerEventDetail,
  OrganizerEventRow,
  OrganizerSectorSeats,
  SeatSale,
  PaymentRequest,
  QueueStatus,
  Reservation,
  ScanRecord,
  SeatMap,
  Session,
  Ticket,
  TicketQr,
} from './types';

export interface EventFilters {
  q?: string;
  city?: string;
  category?: EventCategory;
}

/**
 * Contrato único que as telas usam. Há duas implementações:
 * - mockApi (src/mocks): roda tudo em memória, para desenvolver sem backend;
 * - httpApi (src/api/httpApi.ts): chama o gateway-bff de verdade.
 */
export interface BolsoPassApi {
  auth: {
    login(email: string, password: string): Promise<Session>;
    loginOperator(badge: string, pin: string): Promise<Session>;
    /** Rotação do refresh token (RF-01). */
    refresh(refreshToken: string): Promise<Session>;
    logout(refreshToken?: string): Promise<void>;
  };
  catalog: {
    listEvents(filters?: EventFilters): Promise<EventSummary[]>;
    getEvent(id: string): Promise<EventDetail>;
    getQueue(eventId: string): Promise<QueueStatus>;
    getSeatMap(eventId: string): Promise<SeatMap>;
  };
  inventory: {
    /** Trava atômica dos assentos. 409 (ConflictError) se algum já estiver travado. */
    reserveSeats(eventId: string, seatIds: string[]): Promise<Reservation>;
  };
  orders: {
    getReservation(reservationId: string): Promise<{ reservation: Reservation; order: Order }>;
    /** POST /checkout com header Idempotency-Key. */
    pay(request: PaymentRequest, idempotencyKey: string): Promise<Order>;
    /** Desistência após pagamento RECUSADO (RECUSADO -> CANCELADO). */
    giveUp(reservationId: string): Promise<void>;
    listMine(): Promise<Order[]>;
    getOrder(orderId: string): Promise<Order>;
  };
  tickets: {
    listMine(): Promise<Ticket[]>;
    get(ticketId: string): Promise<Ticket>;
    getQr(ticketId: string): Promise<TicketQr>;
  };
  organizer: {
    getDashboard(): Promise<OrganizerDashboard>;
    listEvents(): Promise<OrganizerEventRow[]>;
    getEvent(id: string): Promise<OrganizerEventDetail>;
    createEvent(input: CreateEventInput): Promise<{ id: string }>;
    publish(id: string): Promise<void>;
    pause(id: string): Promise<void>;
    resume(id: string): Promise<void>;
    getSectorSeats(eventId: string, sectorId: string): Promise<OrganizerSectorSeats>;
    getSeatSale(eventId: string, seatId: string): Promise<SeatSale>;
  };
  operator: {
    listAssignedEvents(): Promise<AssignedEvent[]>;
    /** Baixa chave pública + base de ingressos para validar offline. */
    downloadCache(eventId: string): Promise<OfflineTicketCache>;
    /** Envia leituras (inclusive as feitas offline). Idempotente por id da leitura. */
    syncScans(eventId: string, scans: ScanRecord[]): Promise<{ accepted: number }>;
  };
}
