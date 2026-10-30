import type { BolsoPassApi, EventFilters } from './contract';
import { http } from './http';

/**
 * Implementação real, contra o gateway-bff (Spring Cloud Gateway, porta 8080).
 * Os caminhos seguem o padrão /api/v1 dos documentos. Ajuste aqui quando o
 * contrato OpenAPI do backend for publicado.
 */
function query(filters: EventFilters = {}): string {
  const params = new URLSearchParams();
  for (const [k, v] of Object.entries(filters)) if (v) params.set(k, String(v));
  const s = params.toString();
  return s ? `?${s}` : '';
}

export const httpApi: BolsoPassApi = {
  auth: {
    login: (email, password) => http('/auth/login', { method: 'POST', body: { email, password } }),
    loginOperator: (badge, pin) =>
      http('/auth/operator/login', { method: 'POST', body: { badge, pin } }),
    refresh: (refreshToken) => http('/auth/refresh', { method: 'POST', body: { refreshToken } }),
    logout: (refreshToken) => http('/auth/logout', { method: 'POST', body: { refreshToken } }),
  },
  catalog: {
    listEvents: (filters) => http(`/events${query(filters)}`),
    getEvent: (id) => http(`/events/${id}`),
    getQueue: (eventId) => http(`/events/${eventId}/queue`),
    getSeatMap: (eventId) => http(`/events/${eventId}/seats`),
  },
  inventory: {
    reserveSeats: (eventId, seatIds) =>
      http('/reservations', { method: 'POST', body: { eventId, seatIds } }),
  },
  orders: {
    getReservation: (id) => http(`/reservations/${id}`),
    pay: (request, idempotencyKey) =>
      http('/checkout', { method: 'POST', body: request, idempotencyKey }),
    giveUp: (id) => http(`/reservations/${id}/give-up`, { method: 'POST' }),
    listMine: () => http('/orders/me'),
    getOrder: (id) => http(`/orders/${id}`),
  },
  tickets: {
    listMine: () => http('/tickets/me'),
    get: (id) => http(`/tickets/${id}`),
    getQr: (id) => http(`/tickets/${id}/qr`),
  },
  organizer: {
    getDashboard: () => http('/organizer/dashboard'),
    listEvents: () => http('/organizer/events'),
    getEvent: (id) => http(`/organizer/events/${id}`),
    createEvent: (input) => http('/organizer/events', { method: 'POST', body: input }),
    publish: (id) => http(`/organizer/events/${id}/publish`, { method: 'POST' }),
    pause: (id) => http(`/organizer/events/${id}/pause`, { method: 'POST' }),
    resume: (id) => http(`/organizer/events/${id}/resume`, { method: 'POST' }),
    getSectorSeats: (eventId, sectorId) => http(`/organizer/events/${eventId}/sectors/${sectorId}/seats`),
    getSeatSale: (eventId, seatId) => http(`/organizer/events/${eventId}/seats/${encodeURIComponent(seatId)}/sale`),
  },
  operator: {
    listAssignedEvents: () => http('/operator/events'),
    downloadCache: (eventId) => http(`/operator/events/${eventId}/cache`),
    syncScans: (eventId, scans) =>
      http(`/operator/events/${eventId}/scans`, { method: 'POST', body: { scans } }),
  },
};
