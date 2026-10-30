import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ApiError, ConflictError, ForbiddenError, ReservationExpiredError, UnauthorizedError, errorMessage } from './errors';
import { http } from './http';
import { httpApi } from './httpApi';
import { onUnauthorized, setAccessToken, setRefresher } from './session';

const json = (status: number, body?: unknown) =>
  new Response(body === undefined ? null : JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } });

/** Cliente HTTP real (modo backend): cabeçalhos, renovação do token e tradução de erros. */
describe('http', () => {
  const fetchMock = vi.fn();
  beforeEach(() => {
    fetchMock.mockReset();
    vi.stubGlobal('fetch', fetchMock);
    setAccessToken(undefined);
    setRefresher(undefined);
  });
  afterEach(() => vi.unstubAllGlobals());

  it('envia token, corpo JSON e Idempotency-Key', async () => {
    setAccessToken('tok');
    fetchMock.mockResolvedValue(json(200, { ok: true }));
    await expect(http('/checkout', { method: 'POST', body: { a: 1 }, idempotencyKey: 'k-1' })).resolves.toEqual({ ok: true });
    const [url, init] = fetchMock.mock.calls[0]!;
    expect(url).toBe('/api/v1/checkout');
    expect(init.headers).toMatchObject({ Authorization: 'Bearer tok', 'Content-Type': 'application/json', 'Idempotency-Key': 'k-1' });
    expect(init.body).toBe('{"a":1}');
  });

  it('204 devolve vazio', async () => {
    fetchMock.mockResolvedValue(new Response(null, { status: 204 }));
    await expect(http('/x', { method: 'POST' })).resolves.toBeUndefined();
  });

  it('token vencido: renova uma vez e repete a requisição', async () => {
    setAccessToken('velho');
    setRefresher(async () => 'novo');
    fetchMock.mockResolvedValueOnce(json(401, { message: 'expirado' })).mockResolvedValueOnce(json(200, [1]));
    await expect(http('/orders/me')).resolves.toEqual([1]);
    expect(fetchMock.mock.calls[1]![1].headers.Authorization).toBe('Bearer novo');
  });

  it('sem como renovar: avisa a sessão e lança 401', async () => {
    setAccessToken('velho');
    const handler = vi.fn();
    onUnauthorized(handler);
    fetchMock.mockResolvedValue(json(401, { message: 'Sessão expirada' }));
    await expect(http('/orders/me')).rejects.toBeInstanceOf(UnauthorizedError);
    expect(handler).toHaveBeenCalled();
  });

  it('traduz 403, 409, 410 e demais erros', async () => {
    fetchMock.mockResolvedValueOnce(json(403, { message: 'proibido' }));
    await expect(http('/a')).rejects.toBeInstanceOf(ForbiddenError);

    fetchMock.mockResolvedValueOnce(json(409, { message: 'ocupado', seatIds: ['s1'] }));
    const conflict = await http('/a').catch((e: unknown) => e);
    expect(conflict).toBeInstanceOf(ConflictError);
    expect((conflict as ConflictError).seatIds).toEqual(['s1']);

    fetchMock.mockResolvedValueOnce(json(410));
    await expect(http('/a')).rejects.toBeInstanceOf(ReservationExpiredError);

    fetchMock.mockResolvedValueOnce(new Response('falhou', { status: 500 }));
    const err = await http('/a').catch((e: unknown) => e);
    expect(err).toBeInstanceOf(ApiError);
    expect((err as ApiError).message).toBe('Erro 500 na requisição');
  });

  it('mensagens de erro para a interface', () => {
    expect(errorMessage(new ForbiddenError())).toMatch(/Acesso negado/);
    expect(errorMessage(new UnauthorizedError())).toBe('Sessão expirada');
    expect(errorMessage('x')).toMatch(/Algo deu errado/);
  });

  it('cada chamada da API real vai para a rota certa', async () => {
    fetchMock.mockImplementation(async () => json(200, {}));
    const calls: [() => Promise<unknown>, string][] = [
      [() => httpApi.auth.login('a@b.c', 'x'), '/auth/login'],
      [() => httpApi.auth.loginOperator('OP-1', '1'), '/auth/operator/login'],
      [() => httpApi.auth.refresh('rt'), '/auth/refresh'],
      [() => httpApi.auth.logout('rt'), '/auth/logout'],
      [() => httpApi.catalog.listEvents({ q: 'rock' }), '/events?q=rock'],
      [() => httpApi.catalog.getEvent('e1'), '/events/e1'],
      [() => httpApi.catalog.getQueue('e1'), '/events/e1/queue'],
      [() => httpApi.catalog.getSeatMap('e1'), '/events/e1/seats'],
      [() => httpApi.inventory.reserveSeats('e1', ['s']), '/reservations'],
      [() => httpApi.orders.getReservation('r1'), '/reservations/r1'],
      [() => httpApi.orders.pay({ reservationId: 'r1' } as never, 'k'), '/checkout'],
      [() => httpApi.orders.giveUp('r1'), '/reservations/r1/give-up'],
      [() => httpApi.orders.listMine(), '/orders/me'],
      [() => httpApi.orders.getOrder('o1'), '/orders/o1'],
      [() => httpApi.tickets.listMine(), '/tickets/me'],
      [() => httpApi.tickets.get('t1'), '/tickets/t1'],
      [() => httpApi.tickets.getQr('t1'), '/tickets/t1/qr'],
      [() => httpApi.organizer.getDashboard(), '/organizer/dashboard'],
      [() => httpApi.organizer.listEvents(), '/organizer/events'],
      [() => httpApi.organizer.getEvent('e1'), '/organizer/events/e1'],
      [() => httpApi.organizer.publish('e1'), '/organizer/events/e1/publish'],
      [() => httpApi.organizer.pause('e1'), '/organizer/events/e1/pause'],
      [() => httpApi.organizer.resume('e1'), '/organizer/events/e1/resume'],
      [() => httpApi.organizer.getSectorSeats('e1', 'A'), '/organizer/events/e1/sectors/A/seats'],
      [() => httpApi.organizer.getSeatSale('e1', 'e1:A1'), '/organizer/events/e1/seats/e1%3AA1/sale'],
      [() => httpApi.operator.listAssignedEvents(), '/operator/events'],
      [() => httpApi.operator.downloadCache('e1'), '/operator/events/e1/cache'],
      [() => httpApi.operator.syncScans('e1', []), '/operator/events/e1/scans'],
    ];
    for (const [call, path] of calls) {
      fetchMock.mockClear();
      await call().catch(() => undefined);
      expect(String(fetchMock.mock.calls[0]?.[0])).toContain(path);
    }
  });
});
