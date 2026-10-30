import { createContext, useCallback, useContext, useMemo, useState, type ReactNode } from 'react';
import type { Reservation } from '@/api/types';
import { clockOffset } from '@/domain/reservation';
import { storage } from '@/lib/storage';

/** Reserva em andamento: alimenta o selo "09:42 · 2" do cabeçalho e o checkout. */
export interface ActiveReservation {
  id: string;
  eventId: string;
  expiresAt: string;
  offsetMs: number;
  seatCount: number;
}

interface Ctx {
  active?: ActiveReservation;
  start(r: Reservation): void;
  clear(): void;
}

const ActiveReservationContext = createContext<Ctx | null>(null);

/** Uma reserva por usuário: ao trocar de conta no mesmo navegador, nada vaza. */
export function ActiveReservationProvider({ userId, children }: { userId?: string; children: ReactNode }) {
  const KEY = `bp.activeReservation.${userId ?? 'anon'}`;
  const [active, setActive] = useState<ActiveReservation | undefined>(() => {
    const saved = storage.get<ActiveReservation>(KEY, 'session');
    return saved && Date.parse(saved.expiresAt) > Date.now() ? saved : undefined;
  });

  const start = useCallback((r: Reservation) => {
    const next: ActiveReservation = {
      id: r.id,
      eventId: r.eventId,
      expiresAt: r.expiresAt,
      offsetMs: clockOffset(r.serverNow, Date.now()),
      seatCount: r.seatIds.length,
    };
    storage.set(KEY, next, 'session');
    setActive(next);
  }, [KEY]);

  const clear = useCallback(() => {
    storage.remove(KEY, 'session');
    setActive(undefined);
  }, [KEY]);

  const value = useMemo(() => ({ active, start, clear }), [active, start, clear]);
  return <ActiveReservationContext.Provider value={value}>{children}</ActiveReservationContext.Provider>;
}

export function useActiveReservation(): Ctx {
  const ctx = useContext(ActiveReservationContext);
  if (!ctx) throw new Error('useActiveReservation precisa do ActiveReservationProvider');
  return ctx;
}
