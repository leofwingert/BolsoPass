/** Variáveis de ambiente (veja .env.example). */
function defaultWsUrl(): string {
  // Mesmo host da página: o Vite faz proxy de /ws para o backend.
  if (typeof window === 'undefined') return 'ws://localhost:8080/ws';
  const proto = window.location.protocol === 'https:' ? 'wss' : 'ws';
  return `${proto}://${window.location.host}/ws`;
}

export const env = {
  apiMode: (import.meta.env.VITE_API_MODE ?? 'mock') as 'mock' | 'http',
  apiBaseUrl: import.meta.env.VITE_API_BASE_URL ?? '/api/v1',
  wsUrl: import.meta.env.VITE_WS_URL ?? defaultWsUrl(),
};
