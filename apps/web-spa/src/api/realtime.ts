import type { OrderStatus } from '@/domain/order';

/**
 * Mensagens empurradas pelo notification-service via WebSocket (RF-07, RF-14).
 * Tópicos:
 *  - events/{eventId}/seats  -> travas, liberações (reservas.expiradas) e vendas
 *  - orders/{orderId}        -> mudança de status do pedido / ingresso emitido
 */
export type RealtimeMessage =
  | { type: 'SEAT_LOCKED'; eventId: string; seatIds: string[]; reservationId: string }
  | { type: 'SEAT_RELEASED'; eventId: string; seatIds: string[]; reason: 'EXPIRADA' | 'CANCELADA' }
  | { type: 'SEAT_SOLD'; eventId: string; seatIds: string[] }
  | { type: 'LIVE_BUYERS'; eventId: string; count: number }
  | { type: 'ORDER_STATUS'; orderId: string; status: OrderStatus };

export type ConnectionStatus = 'connecting' | 'connected' | 'offline';

export interface RealtimeClient {
  subscribe(topic: string, handler: (msg: RealtimeMessage) => void): () => void;
  getStatus(): ConnectionStatus;
  onStatus(cb: (s: ConnectionStatus) => void): () => void;
}

export const topics = {
  eventSeats: (eventId: string) => `events/${eventId}/seats`,
  order: (orderId: string) => `orders/${orderId}`,
};

/** Barramento simples em memória: base do cliente WS e do mock. */
export class TopicBus {
  private handlers = new Map<string, Set<(msg: RealtimeMessage) => void>>();

  subscribe(topic: string, handler: (msg: RealtimeMessage) => void): () => void {
    const set = this.handlers.get(topic) ?? new Set();
    set.add(handler);
    this.handlers.set(topic, set);
    return () => {
      set.delete(handler);
      if (set.size === 0) this.handlers.delete(topic);
    };
  }

  publish(topic: string, msg: RealtimeMessage): void {
    this.handlers.get(topic)?.forEach((h) => h(msg));
  }

  topicsWithSubscribers(): string[] {
    return [...this.handlers.keys()];
  }
}

/** Cliente WebSocket real com reconexão exponencial. */
export class WebSocketRealtime implements RealtimeClient {
  private bus = new TopicBus();
  private ws?: WebSocket;
  private status: ConnectionStatus = 'offline';
  private statusListeners = new Set<(s: ConnectionStatus) => void>();
  private retry = 0;

  constructor(
    private readonly url: string,
    private readonly getToken: () => string | undefined,
  ) {}

  subscribe(topic: string, handler: (msg: RealtimeMessage) => void): () => void {
    const unsubscribe = this.bus.subscribe(topic, handler);
    this.ensureConnected();
    this.send({ action: 'subscribe', topic });
    return () => {
      unsubscribe();
      if (!this.bus.topicsWithSubscribers().includes(topic)) this.send({ action: 'unsubscribe', topic });
    };
  }

  getStatus(): ConnectionStatus {
    return this.status;
  }

  onStatus(cb: (s: ConnectionStatus) => void): () => void {
    this.statusListeners.add(cb);
    return () => this.statusListeners.delete(cb);
  }

  private setStatus(s: ConnectionStatus) {
    this.status = s;
    this.statusListeners.forEach((cb) => cb(s));
  }

  private send(data: unknown) {
    if (this.ws?.readyState === WebSocket.OPEN) this.ws.send(JSON.stringify(data));
  }

  private ensureConnected() {
    if (this.ws && this.ws.readyState <= WebSocket.OPEN) return;
    const token = this.getToken();
    this.setStatus('connecting');
    this.ws = new WebSocket(token ? `${this.url}?access_token=${encodeURIComponent(token)}` : this.url);
    this.ws.onopen = () => {
      this.retry = 0;
      this.setStatus('connected');
      for (const topic of this.bus.topicsWithSubscribers()) this.send({ action: 'subscribe', topic });
    };
    this.ws.onmessage = (ev) => {
      const { topic, payload } = JSON.parse(String(ev.data)) as { topic: string; payload: RealtimeMessage };
      this.bus.publish(topic, payload);
    };
    this.ws.onclose = () => {
      this.setStatus('offline');
      if (this.bus.topicsWithSubscribers().length === 0) return;
      const delay = Math.min(30_000, 500 * 2 ** this.retry++);
      setTimeout(() => this.ensureConnected(), delay);
    };
  }
}
