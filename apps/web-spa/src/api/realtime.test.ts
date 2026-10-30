import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { WebSocketRealtime, topics, type RealtimeMessage } from './realtime';

/** WebSocket falso: guarda o que foi enviado e permite simular abertura, mensagem e queda. */
class FakeSocket {
  static CONNECTING = 0;
  static OPEN = 1;
  static CLOSING = 2;
  static CLOSED = 3;
  static all: FakeSocket[] = [];
  readyState = FakeSocket.CONNECTING;
  sent: unknown[] = [];
  onopen?: () => void;
  onmessage?: (ev: { data: string }) => void;
  onclose?: () => void;
  constructor(public url: string) {
    FakeSocket.all.push(this);
  }
  send(data: string) {
    this.sent.push(JSON.parse(data));
  }
  open() {
    this.readyState = FakeSocket.OPEN;
    this.onopen?.();
  }
  drop() {
    this.readyState = FakeSocket.CLOSED;
    this.onclose?.();
  }
}

/** Cliente WebSocket real (modo backend): inscrição em tópicos, mensagens e reconexão. */
describe('WebSocketRealtime', () => {
  beforeEach(() => {
    FakeSocket.all = [];
    vi.stubGlobal('WebSocket', FakeSocket);
    vi.useFakeTimers();
  });
  afterEach(() => {
    vi.useRealTimers();
    vi.unstubAllGlobals();
  });

  it('conecta com o token, assina o tópico e entrega mensagens', () => {
    const rt = new WebSocketRealtime('ws://x/ws', () => 'tok');
    const statuses: string[] = [];
    rt.onStatus((s) => statuses.push(s));
    const got: RealtimeMessage[] = [];
    const unsubscribe = rt.subscribe(topics.eventSeats('e1'), (m) => got.push(m));

    const ws = FakeSocket.all[0]!;
    expect(ws.url).toBe('ws://x/ws?access_token=tok');
    ws.open();
    expect(rt.getStatus()).toBe('connected');
    expect(ws.sent).toContainEqual({ action: 'subscribe', topic: 'events/e1/seats' });

    const msg: RealtimeMessage = { type: 'SEAT_SOLD', eventId: 'e1', seatIds: ['A1'] };
    ws.onmessage?.({ data: JSON.stringify({ topic: 'events/e1/seats', payload: msg }) });
    expect(got).toEqual([msg]);

    unsubscribe();
    expect(ws.sent).toContainEqual({ action: 'unsubscribe', topic: 'events/e1/seats' });
    expect(statuses).toEqual(['connecting', 'connected']);
  });

  it('reconecta quando a conexão cai e ainda há inscritos', () => {
    const rt = new WebSocketRealtime('ws://x/ws', () => undefined);
    rt.subscribe(topics.order('o1'), () => undefined);
    FakeSocket.all[0]!.open();
    FakeSocket.all[0]!.drop();
    expect(rt.getStatus()).toBe('offline');
    vi.advanceTimersByTime(600);
    expect(FakeSocket.all).toHaveLength(2);
    FakeSocket.all[1]!.open();
    expect(FakeSocket.all[1]!.sent).toContainEqual({ action: 'subscribe', topic: 'orders/o1' });
  });

  it('não reconecta se ninguém mais escuta', () => {
    const rt = new WebSocketRealtime('ws://x/ws', () => undefined);
    const off = rt.subscribe(topics.order('o1'), () => undefined);
    FakeSocket.all[0]!.open();
    off();
    FakeSocket.all[0]!.drop();
    vi.advanceTimersByTime(60_000);
    expect(FakeSocket.all).toHaveLength(1);
  });
});
