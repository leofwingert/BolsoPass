import { useEffect, useState } from 'react';
import { remainingMs, timerPhase, type TimerPhase } from '@/domain/reservation';

/**
 * Cronômetro sincronizado com o servidor (offset calculado na resposta da reserva).
 * O tempo restante é calculado a cada render; o intervalo só força o re-render.
 */
export function useCountdown(
  expiresAt: string | undefined,
  offsetMs = 0,
): { ms: number; phase: TimerPhase } {
  const [, setTick] = useState(0);

  useEffect(() => {
    if (!expiresAt) return;
    const id = setInterval(() => setTick((t) => t + 1), 250);
    return () => clearInterval(id);
  }, [expiresAt]);

  const ms = expiresAt ? remainingMs(expiresAt, Date.now(), offsetMs) : 0;
  return { ms, phase: timerPhase(ms) };
}
