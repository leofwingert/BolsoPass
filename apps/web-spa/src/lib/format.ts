const brl = new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' });
const num = new Intl.NumberFormat('pt-BR');

export const formatMoney = (cents: number) => brl.format(cents / 100);
export const formatNumber = (n: number) => num.format(n);

export function formatDate(iso: string, opts: Intl.DateTimeFormatOptions = {}) {
  return new Date(iso).toLocaleDateString('pt-BR', {
    day: '2-digit',
    month: 'long',
    year: 'numeric',
    ...opts,
  });
}

export function formatDateTime(iso: string) {
  const d = new Date(iso);
  const date = d.toLocaleDateString('pt-BR', { weekday: 'long', day: '2-digit', month: 'long' });
  const time = d.toLocaleTimeString('pt-BR', { hour: '2-digit', minute: '2-digit' });
  return `${date.charAt(0).toUpperCase()}${date.slice(1)} • ${time}`;
}

export function formatTime(iso: string, seconds = false) {
  return new Date(iso).toLocaleTimeString('pt-BR', {
    hour: '2-digit',
    minute: '2-digit',
    second: seconds ? '2-digit' : undefined,
  });
}

export function daysUntil(iso: string, nowMs = Date.now()) {
  return Math.ceil((Date.parse(iso) - nowMs) / 86_400_000);
}

export function relativeTime(iso: string, nowMs = Date.now()) {
  const min = Math.round((nowMs - Date.parse(iso)) / 60_000);
  if (min < 1) return 'agora';
  if (min < 60) return `${min} min atrás`;
  const h = Math.round(min / 60);
  if (h < 24) return `${h}h atrás`;
  return formatDate(iso, { month: 'short', year: undefined });
}

export const pct = (part: number, total: number) => (total ? Math.round((part / total) * 100) : 0);

export function cx(...classes: (string | false | null | undefined)[]) {
  return classes.filter(Boolean).join(' ');
}
