import { env } from '@/config/env';
import { ApiError, ConflictError, ForbiddenError, ReservationExpiredError, UnauthorizedError } from './errors';
import { getAccessToken, notifyUnauthorized, refreshAccessToken } from './session';

export interface RequestOptions {
  method?: 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE';
  body?: unknown;
  idempotencyKey?: string;
  signal?: AbortSignal;
}

function send(path: string, opts: RequestOptions, token: string | undefined) {
  const headers: Record<string, string> = { Accept: 'application/json' };
  if (token) headers.Authorization = `Bearer ${token}`;
  if (opts.body !== undefined) headers['Content-Type'] = 'application/json';
  // RF-09: o checkout exige Idempotency-Key.
  if (opts.idempotencyKey) headers['Idempotency-Key'] = opts.idempotencyKey;
  return fetch(`${env.apiBaseUrl}${path}`, {
    method: opts.method ?? 'GET',
    headers,
    body: opts.body === undefined ? undefined : JSON.stringify(opts.body),
    signal: opts.signal,
  });
}

export async function http<T>(path: string, opts: RequestOptions = {}): Promise<T> {
  const token = getAccessToken();
  let res = await send(path, opts, token);

  // Access token vencido (15 min): renova uma vez e repete a mesma requisição.
  if (res.status === 401 && token && !path.startsWith('/auth/')) {
    const fresh = await refreshAccessToken();
    if (fresh) res = await send(path, opts, fresh);
  }

  if (res.status === 204) return undefined as T;
  const data: unknown = await res.json().catch(() => undefined);
  if (res.ok) return data as T;

  const message =
    (data as { message?: string } | undefined)?.message ?? `Erro ${res.status} na requisição`;
  if (res.status === 401) {
    if (!path.startsWith('/auth/')) notifyUnauthorized();
    throw new UnauthorizedError(message);
  }
  if (res.status === 403) throw new ForbiddenError(message);
  if (res.status === 409) {
    throw new ConflictError(message, (data as { seatIds?: string[] } | undefined)?.seatIds ?? []);
  }
  if (res.status === 410) throw new ReservationExpiredError();
  throw new ApiError(res.status, message, data);
}
