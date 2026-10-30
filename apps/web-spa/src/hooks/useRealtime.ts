import { useEffect, useRef, useState } from 'react';
import { realtime } from '@/api';
import type { ConnectionStatus, RealtimeMessage } from '@/api/realtime';

/** Assina um tópico do WebSocket enquanto o componente estiver montado. */
export function useRealtime(topic: string | undefined, handler: (msg: RealtimeMessage) => void) {
  const ref = useRef(handler);
  ref.current = handler;

  useEffect(() => {
    if (!topic) return;
    return realtime.subscribe(topic, (m) => ref.current(m));
  }, [topic]);
}

export function useConnectionStatus(): ConnectionStatus {
  const [status, setStatus] = useState(realtime.getStatus());
  useEffect(() => realtime.onStatus(setStatus), []);
  return status;
}

/** Status de rede do aparelho (usado no modo offline da portaria). */
export function useOnline(): boolean {
  const [online, setOnline] = useState(() => navigator.onLine);
  useEffect(() => {
    const on = () => setOnline(true);
    const off = () => setOnline(false);
    window.addEventListener('online', on);
    window.addEventListener('offline', off);
    return () => {
      window.removeEventListener('online', on);
      window.removeEventListener('offline', off);
    };
  }, []);
  return online;
}
