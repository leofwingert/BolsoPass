import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { ChevronLeft, ChevronRight, Download, Pause, Plus, Search, ShoppingCart, Ticket, TrendingUp, Zap } from 'lucide-react';
import { useState } from 'react';
import { Link } from 'react-router-dom';
import { api } from '@/api';
import { errorMessage } from '@/api/errors';
import type { EventStatus } from '@/api/types';
import { Button, Card, ErrorState, EventCover, Progress, Spinner } from '@/components/ui';
import { cx, formatDate, formatMoney, formatNumber, formatTime, pct } from '@/lib/format';
import { STATUS_LABEL, SalesAction, StatusBadge, downloadCsv } from './shared';

const FILTERS: { key: EventStatus | 'TODOS'; label: string; dot?: string }[] = [
  { key: 'TODOS', label: 'Todos' },
  { key: 'EM_VENDAS', label: 'Em vendas', dot: 'bg-ok' },
  { key: 'AGENDADO', label: 'Agendados', dot: 'bg-slate-500' },
  { key: 'RASCUNHO', label: 'Rascunhos', dot: 'bg-slate-300' },
  { key: 'PAUSADO', label: 'Pausados', dot: 'bg-warn' },
  { key: 'ESGOTADO', label: 'Esgotados', dot: 'bg-brand-600' },
  { key: 'ENCERRADO', label: 'Encerrados', dot: 'bg-indigo-200' },
  { key: 'CANCELADO', label: 'Cancelados', dot: 'bg-slate-200' },
];
const PAGE = 7;

function Kpi({ label, icon, children }: { label: string; icon: React.ReactNode; children: React.ReactNode }) {
  return (
    <Card className="p-5">
      <div className="flex items-start justify-between gap-2">
        <p className="text-[11px] font-bold tracking-wider text-muted uppercase">{label}</p>
        <span className="grid size-8 place-items-center rounded-lg bg-indigo-50 text-indigo-700">{icon}</span>
      </div>
      {children}
    </Card>
  );
}

export default function OrganizerEventsPage() {
  const queryClient = useQueryClient();
  const [q, setQ] = useState('');
  const [status, setStatus] = useState<EventStatus | 'TODOS'>('TODOS');
  const [page, setPage] = useState(0);
  const [selected, setSelected] = useState<Set<string>>(new Set());
  const { data, isLoading, error, refetch } = useQuery({ queryKey: ['organizer', 'events'], queryFn: () => api.organizer.listEvents() });
  const dash = useQuery({ queryKey: ['organizer', 'dashboard'], queryFn: () => api.organizer.getDashboard() });

  const pauseMany = useMutation({
    mutationFn: async (ids: string[]) => {
      for (const id of ids) await api.organizer.pause(id);
    },
    onSuccess: () => {
      setSelected(new Set());
      queryClient.invalidateQueries({ queryKey: ['organizer'] });
      queryClient.invalidateQueries({ queryKey: ['events'] });
    },
    onError: (err) => window.alert(errorMessage(err)),
  });

  if (isLoading) return <Spinner />;
  if (error || !data) return <ErrorState message={errorMessage(error)} onRetry={() => refetch()} />;

  const needle = q.toLowerCase().trim();
  const list = data
    .filter((r) => status === 'TODOS' || r.event.status === status)
    .filter((r) => !needle || `${r.event.title} ${r.event.venue} ${r.event.city} ${r.event.id}`.toLowerCase().includes(needle));
  const pages = Math.max(1, Math.ceil(list.length / PAGE));
  const current = Math.min(page, pages - 1);
  const shown = list.slice(current * PAGE, current * PAGE + PAGE);
  const revenue = data.reduce((s, r) => s + r.revenueCents, 0);
  const sold = data.reduce((s, r) => s + r.event.sold, 0);
  const capacity = data.reduce((s, r) => s + r.event.capacity, 0);
  const pausable = [...selected].filter((id) => data.find((r) => r.event.id === id)?.event.status === 'EM_VENDAS');
  const allShown = shown.length > 0 && shown.every((r) => selected.has(r.event.id));

  function toggle(id: string) {
    setSelected((prev) => {
      const next = new Set(prev);
      if (next.has(id)) next.delete(id);
      else next.add(id);
      return next;
    });
  }

  function exportCsv() {
    downloadCsv('eventos-bolsopass.csv', [
      ['ID', 'Evento', 'Local', 'Data', 'Status', 'Capacidade', 'Vendidos', 'Receita (R$)', 'Ticket médio (R$)'],
      ...list.map((r) => [r.event.id, r.event.title, `${r.event.venue} - ${r.event.city}`, formatDate(r.event.startsAt), STATUS_LABEL[r.event.status], r.event.capacity, r.event.sold, (r.revenueCents / 100).toFixed(2), (r.avgTicketCents / 100).toFixed(2)]),
    ]);
  }

  return (
    <div className="grid gap-6">
      <div className="flex flex-wrap items-end justify-between gap-4">
        <div>
          <p className="text-xs font-semibold"><span className="rounded bg-surface-strong px-1.5 py-0.5 tracking-wider uppercase">Administração</span> <span className="text-brand-600">/ Meus eventos</span></p>
          <h1 className="mt-2 text-4xl font-extrabold tracking-tight">Meus eventos & produções</h1>
          <p className="mt-1 max-w-2xl text-sm text-muted">Acompanhe cada evento do rascunho ao encerramento: abertura das vendas, ocupação e receita.</p>
        </div>
        <div className="grid gap-2">
          <Button variant="soft" onClick={exportCsv}><Download className="size-4" aria-hidden /> Exportar dados (.CSV)</Button>
          <Link to="/organizer/events/new"><Button className="w-full"><Plus className="size-4" aria-hidden /> Criar evento</Button></Link>
        </div>
      </div>

      <section className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
        <Kpi label="Receita gerada" icon={<TrendingUp className="size-4" />}>
          <p className="mt-4 text-3xl font-extrabold tabular">{formatMoney(revenue)}</p>
          <p className="mt-1 text-xs text-muted">Soma de todos os eventos</p>
        </Kpi>
        <Kpi label="Ingressos vendidos" icon={<Ticket className="size-4" />}>
          <p className="mt-4 text-3xl font-extrabold tabular">{formatNumber(sold)} <span className="text-sm font-medium text-muted">/ {formatNumber(capacity)}</span></p>
          <Progress value={pct(sold, capacity)} className="mt-3" />
        </Kpi>
        <Kpi label="Eventos à venda" icon={<Zap className="size-4" />}>
          <p className="mt-4 text-3xl font-extrabold">{data.filter((r) => r.event.status === 'EM_VENDAS').length}</p>
          <p className="mt-1 text-xs text-muted">{data.length} eventos no total</p>
        </Kpi>
        <Kpi label="Conversão do checkout" icon={<ShoppingCart className="size-4" />}>
          <p className="mt-4 text-3xl font-extrabold">{dash.data ? `${dash.data.health.checkoutConversionPct}%` : '—'}</p>
          <p className="mt-1 text-xs text-muted">Reservas de hoje que viraram compra</p>
        </Kpi>
      </section>

      <Card className="grid gap-3 p-4">
        <div className="relative">
          <Search className="absolute top-1/2 left-3.5 size-4 -translate-y-1/2 text-muted" aria-hidden />
          <input value={q} onChange={(e) => { setQ(e.target.value); setPage(0); }} placeholder="Buscar por nome do evento, local, cidade ou código…" aria-label="Buscar eventos"
            className="h-11 w-full rounded-lg bg-surface pr-3 pl-10 text-sm focus:outline-none" />
        </div>
        <div className="flex gap-2 overflow-x-auto">
          {FILTERS.map((f) => {
            const count = f.key === 'TODOS' ? data.length : data.filter((r) => r.event.status === f.key).length;
            return (
              <button key={f.key} onClick={() => { setStatus(f.key); setPage(0); }}
                className={cx('flex shrink-0 items-center gap-1.5 rounded-lg px-3 py-1.5 text-xs font-semibold', status === f.key ? 'bg-navy text-white' : 'bg-surface')}>
                {f.dot && <span className={cx('size-2 rounded-full', f.dot)} />}
                {f.label} <span className="opacity-60">{count}</span>
              </button>
            );
          })}
        </div>
      </Card>

      <Card className="overflow-hidden">
        <div className="overflow-x-auto">
          <table className="w-full min-w-[960px] text-sm">
            <thead className="bg-surface text-left text-[11px] tracking-wider text-muted uppercase">
              <tr>
                <th className="w-10 py-3 pl-5">
                  <input type="checkbox" aria-label="Selecionar todos desta página" checked={allShown}
                    onChange={() => setSelected((prev) => {
                      const next = new Set(prev);
                      shown.forEach((r) => (allShown ? next.delete(r.event.id) : next.add(r.event.id)));
                      return next;
                    })} />
                </th>
                <th className="px-3 py-3">Evento & local</th><th className="px-3 py-3">Data e horário</th><th className="px-3 py-3">Capacidade / ocupação</th><th className="px-3 py-3">Receita gerada</th><th className="px-3 py-3">Status</th><th className="px-5 py-3 text-right">Ação</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-line">
              {shown.length === 0 && <tr><td colSpan={7} className="px-5 py-10 text-center text-muted">Nenhum evento encontrado.</td></tr>}
              {shown.map((r) => {
                const e = r.event;
                const occ = pct(e.sold, e.capacity);
                return (
                  <tr key={e.id} className={cx('hover:bg-surface/50', selected.has(e.id) && 'bg-brand-50/40')}>
                    <td className="py-3 pl-5"><input type="checkbox" aria-label={`Selecionar ${e.title}`} checked={selected.has(e.id)} onChange={() => toggle(e.id)} /></td>
                    <td className="px-3 py-3">
                      <Link to={`/organizer/events/${e.id}`} className="flex items-center gap-3">
                        <EventCover hue={e.hue} className="size-12 shrink-0 rounded-lg" />
                        <span><b className="hover:text-brand-600">{e.title}</b><br /><span className="text-xs text-muted">{e.venue}, {e.city}</span></span>
                      </Link>
                    </td>
                    <td className="px-3 py-3 text-xs"><b>{formatDate(e.startsAt, { month: 'short' })}</b><br /><span className="text-muted">{formatTime(e.startsAt)}</span></td>
                    <td className="w-52 px-3 py-3">
                      <p className="flex justify-between text-xs font-semibold"><span>{formatNumber(e.sold)} / {formatNumber(e.capacity)}</span><span className={occ >= 100 ? 'text-brand-600' : 'text-ok'}>{occ}%</span></p>
                      <Progress value={occ} tone={e.status === 'ENCERRADO' ? 'dark' : occ >= 100 ? 'brand' : 'ok'} className="mt-1" />
                      <p className={cx('mt-1 text-[11px]', occ >= 100 && e.status !== 'ENCERRADO' ? 'text-brand-600' : 'text-muted')}>{r.note}</p>
                    </td>
                    <td className="px-3 py-3"><b className="tabular">{formatMoney(r.revenueCents)}</b><br /><span className="text-xs text-ok">Ticket médio: {formatMoney(r.avgTicketCents)}</span></td>
                    <td className="px-3 py-3"><StatusBadge status={e.status} /></td>
                    <td className="px-5 py-3 text-right"><SalesAction id={e.id} status={e.status} /></td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
        <div className="flex flex-wrap items-center justify-between gap-3 border-t border-line bg-surface/60 px-5 py-3 text-xs">
          <div className="flex items-center gap-2">
            <span className="font-semibold text-muted">Ações em massa:</span>
            <Button size="sm" variant="outline" disabled={pausable.length === 0} loading={pauseMany.isPending}
              onClick={() => window.confirm(`Pausar as vendas de ${pausable.length} evento(s)?`) && pauseMany.mutate(pausable)}>
              <Pause className="size-3.5" aria-hidden /> Pausar selecionados{pausable.length ? ` (${pausable.length})` : ''}
            </Button>
          </div>
          <div className="flex items-center gap-3">
            <span className="text-muted">Mostrando <b className="text-ink">{list.length ? current * PAGE + 1 : 0}–{current * PAGE + shown.length}</b> de <b className="text-ink">{list.length}</b> eventos</span>
            <div className="flex items-center gap-1">
              <button className="rounded p-1 disabled:opacity-30" disabled={current === 0} onClick={() => setPage(current - 1)} aria-label="Página anterior"><ChevronLeft className="size-4" /></button>
              {Array.from({ length: pages }, (_, i) => (
                <button key={i} onClick={() => setPage(i)} className={cx('size-7 rounded-md font-bold', i === current ? 'bg-navy text-white' : 'bg-white')}>{i + 1}</button>
              ))}
              <button className="rounded p-1 disabled:opacity-30" disabled={current >= pages - 1} onClick={() => setPage(current + 1)} aria-label="Próxima página"><ChevronRight className="size-4" /></button>
            </div>
          </div>
        </div>
      </Card>
    </div>
  );
}
