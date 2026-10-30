import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  AlertTriangle,
  ArrowRight,
  CalendarCheck,
  CheckCircle2,
  Download,
  Lock,
  PlusCircle,
  ScanLine,
  ShieldCheck,
  Ticket,
  TrendingUp,
  Wallet,
} from 'lucide-react';
import { useState } from 'react';
import { Link } from 'react-router-dom';
import { api } from '@/api';
import { errorMessage } from '@/api/errors';
import { useAuth } from '@/auth/AuthProvider';
import { canAccess } from '@/auth/roles';
import { Badge, Button, Card, ErrorState, EventCover, Progress, Spinner } from '@/components/ui';
import { cx, daysUntil, formatDate, formatMoney, formatNumber, pct } from '@/lib/format';
import { STATUS_LABEL, StatusBadge, downloadCsv } from './shared';

type Filter = 'todos' | 'vendas' | 'esgotados';

function Kpi({ label, icon, tone = 'neutral', children }: { label: string; icon: React.ReactNode; tone?: 'neutral' | 'ok' | 'brand' | 'info'; children: React.ReactNode }) {
  const iconTone = { neutral: 'bg-brand-50 text-brand-600', ok: 'bg-ok-soft text-ok', brand: 'bg-brand-50 text-brand-600', info: 'bg-indigo-50 text-indigo-700' }[tone];
  return (
    <Card className={cx('relative overflow-hidden p-5', tone === 'brand' && 'bg-gradient-to-br from-white to-brand-50')}>
      <div className="flex items-start justify-between gap-2">
        <p className={cx('text-[11px] font-bold tracking-wider uppercase', tone === 'brand' ? 'text-brand-600' : 'text-muted')}>{label}</p>
        <span className={cx('grid size-8 place-items-center rounded-lg', iconTone)}>{icon}</span>
      </div>
      {children}
    </Card>
  );
}

export default function OrganizerDashboardPage() {
  const { user } = useAuth();
  const queryClient = useQueryClient();
  const [filter, setFilter] = useState<Filter>('todos');
  const dash = useQuery({ queryKey: ['organizer', 'dashboard'], queryFn: () => api.organizer.getDashboard(), refetchInterval: 15_000 });
  const rows = useQuery({ queryKey: ['organizer', 'events'], queryFn: () => api.organizer.listEvents() });

  // "Pausar todas as vendas": interrompe novas reservas de todos os eventos à venda.
  const pauseAll = useMutation({
    mutationFn: async (ids: string[]) => {
      for (const id of ids) await api.organizer.pause(id);
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['organizer'] });
      queryClient.invalidateQueries({ queryKey: ['events'] });
    },
    onError: (err) => window.alert(errorMessage(err)),
  });

  if (dash.isLoading || rows.isLoading) return <Spinner label="Carregando painel…" />;
  if (dash.error || rows.error || !dash.data || !rows.data) {
    return <ErrorState message={errorMessage(dash.error ?? rows.error)} onRetry={() => { dash.refetch(); rows.refetch(); }} />;
  }
  const m = dash.data;
  const onSale = rows.data.filter((r) => r.event.status === 'EM_VENDAS');
  const upcomingAll = rows.data
    .filter((r) => Date.parse(r.event.startsAt) > Date.now())
    .filter((r) => filter === 'todos' || (filter === 'vendas' ? r.event.status === 'EM_VENDAS' : pct(r.event.sold, r.event.capacity) >= 100));
  const upcoming = upcomingAll.slice(0, 5);
  const maxDay = Math.max(1, ...m.weeklyRevenue.map((d) => d.cents));
  const bestDay = m.weeklyRevenue.findIndex((d) => d.cents === maxDay);
  const weekTotal = m.weeklyRevenue.reduce((s, d) => s + d.cents, 0);
  const secondsAgo = Math.max(0, Math.round((Date.now() - dash.dataUpdatedAt) / 1000));

  function exportReport() {
    downloadCsv('relatorio-bolsopass.csv', [
      ['Evento', 'Status', 'Data', 'Capacidade', 'Vendidos', 'Receita (R$)'],
      ...rows.data!.map((r) => [r.event.title, STATUS_LABEL[r.event.status], formatDate(r.event.startsAt), r.event.capacity, r.event.sold, (r.revenueCents / 100).toFixed(2)]),
    ]);
  }

  function confirmPauseAll() {
    if (onSale.length === 0) return window.alert('Nenhum evento está com vendas abertas.');
    if (window.confirm(`Pausar as vendas de ${onSale.length} evento(s) agora? Novas reservas ficam bloqueadas até você retomar cada evento.`)) {
      pauseAll.mutate(onSale.map((r) => r.event.id));
    }
  }

  return (
    <div className="grid gap-6">
      <Card className="flex flex-wrap items-center justify-between gap-5 p-6">
        <div className="max-w-xl">
          <p className="flex flex-wrap items-center gap-2 text-xs text-muted">
            <Badge tone="brand">Ao vivo</Badge> Atualizado há {secondsAgo}s · renova a cada 15 s
          </p>
          <h1 className="mt-2 text-3xl font-extrabold tracking-tight">Painel geral do produtor</h1>
          <p className="mt-1 text-sm text-muted">Venda de ingressos, faturamento e situação da bilheteria dos seus eventos.</p>
        </div>
        <div className="grid gap-2 sm:grid-cols-2">
          <Button variant="soft" onClick={exportReport}>
            <Download className="size-4" aria-hidden /> Relatório consolidado
          </Button>
          <Button variant="outline" className="border-brand-200 bg-brand-50 text-brand-700" loading={pauseAll.isPending} onClick={confirmPauseAll}>
            <Lock className="size-4" aria-hidden /> Pausar todas as vendas
          </Button>
          <Link to="/organizer/events/new" className="sm:col-span-2">
            <Button className="w-full">
              <PlusCircle className="size-4" aria-hidden /> Criar novo evento
            </Button>
          </Link>
        </div>
      </Card>

      <section className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
        <Kpi label="Eventos à venda" icon={<CalendarCheck className="size-4" />}>
          <p className="mt-3 text-4xl font-extrabold">{m.eventsActive}</p>
          <p className="mt-1 text-xs text-muted">{rows.data.length} eventos cadastrados</p>
          <p className="mt-3 text-xs"><span className="rounded-md bg-ok-soft px-1.5 py-0.5 font-bold text-ok">+{m.eventsNewThisMonth} este mês</span></p>
        </Kpi>
        <Kpi label="Ingressos vendidos hoje" icon={<Ticket className="size-4" />} tone="ok">
          <p className="mt-3 text-4xl font-extrabold tabular">{formatNumber(m.ticketsToday)}</p>
          <p className="mt-1 text-xs font-bold text-ok">{formatMoney(m.revenueTodayCents)} vendidos hoje</p>
          <p className="mt-3 text-xs text-muted">
            <span className={cx('rounded-md px-1.5 py-0.5 font-bold', m.ticketsTodayGrowthPct >= 0 ? 'bg-ok-soft text-ok' : 'bg-brand-50 text-brand-700')}>
              {m.ticketsTodayGrowthPct >= 0 ? '↑' : '↓'} {Math.abs(m.ticketsTodayGrowthPct)}%
            </span>{' '}
            em relação a ontem
          </p>
        </Kpi>
        <Kpi label="Receita bruta total" icon={<Wallet className="size-4" />} tone="info">
          <p className="mt-3 text-3xl font-extrabold tabular">{formatMoney(m.grossRevenueCents)}</p>
          <p className="mt-1 text-xs text-muted">Líquido do produtor: <b className="text-ink">{formatMoney(m.netRevenueCents)}</b></p>
          <p className="mt-3 text-xs text-muted">Taxa da plataforma: {m.platformFeePct}%</p>
        </Kpi>
        {m.critical ? (
          <Kpi label="Atenção" icon={<AlertTriangle className="size-4" />} tone="brand">
            <Link to={`/organizer/events/${m.critical.eventId}`} className="mt-3 block text-xl font-extrabold leading-tight hover:text-brand-600">{m.critical.title}</Link>
            <p className="mt-1 text-xs text-muted">Em {m.critical.daysLeft} dias • {m.critical.venue}</p>
            <p className="mt-3 flex justify-between text-xs font-semibold"><span>Ocupação</span><span className="text-brand-600">{m.critical.occupancyPct}% vendido</span></p>
            <Progress value={m.critical.occupancyPct} className="mt-1" />
          </Kpi>
        ) : (
          <Kpi label="Atenção" icon={<AlertTriangle className="size-4" />} tone="brand">
            <p className="mt-3 text-sm text-muted">Nenhum evento perto de esgotar.</p>
          </Kpi>
        )}
      </section>

      <div className="grid gap-6 lg:grid-cols-[1fr_340px]">
        <div className="grid content-start gap-6">
          <Card className="overflow-hidden">
            <div className="flex flex-wrap items-start justify-between gap-3 px-6 pt-6 pb-4">
              <div>
                <h2 className="flex items-center gap-2 text-xl font-bold">
                  Próximos eventos <Badge tone="info">{upcoming.length} listados</Badge>
                </h2>
                <p className="mt-0.5 text-xs text-muted">Ocupação e situação das vendas de cada evento.</p>
              </div>
              <div className="flex gap-1 rounded-lg bg-surface p-1 text-xs font-semibold">
                {(['todos', 'vendas', 'esgotados'] as Filter[]).map((f) => (
                  <button key={f} onClick={() => setFilter(f)} className={cx('rounded-md px-3 py-1.5', filter === f && 'bg-white shadow-sm')}>
                    {{ todos: 'Todos', vendas: 'Em vendas', esgotados: 'Esgotados' }[f]}
                  </button>
                ))}
              </div>
            </div>
            <div className="overflow-x-auto">
              <table className="w-full min-w-[620px] text-sm">
                <thead className="bg-surface text-left text-[11px] tracking-wider text-muted uppercase">
                  <tr><th className="px-6 py-3">Evento & local</th><th className="px-3 py-3">Data</th><th className="px-3 py-3">Capacidade vendida</th><th className="px-3 py-3">Status</th></tr>
                </thead>
                <tbody className="divide-y divide-line">
                  {upcoming.length === 0 && <tr><td colSpan={4} className="px-6 py-8 text-center text-muted">Nenhum evento neste filtro.</td></tr>}
                  {upcoming.map(({ event: e }) => {
                    const occ = pct(e.sold, e.capacity);
                    return (
                      <tr key={e.id} className="hover:bg-surface/50">
                        <td className="px-6 py-3">
                          <Link to={`/organizer/events/${e.id}`} className="flex items-center gap-3">
                            <EventCover hue={e.hue} className="size-10 shrink-0 rounded-lg" />
                            <span><b className="hover:text-brand-600">{e.title}</b><br /><span className="text-xs text-muted">{e.venue}</span></span>
                          </Link>
                        </td>
                        <td className="px-3 py-3 text-xs"><b>{formatDate(e.startsAt, { month: 'short' })}</b><br /><span className="text-muted">em {daysUntil(e.startsAt)} dias</span></td>
                        <td className="w-48 px-3 py-3">
                          <p className="text-xs font-semibold"><span className={occ >= 90 ? 'text-brand-600' : ''}>{occ}%</span> <span className="text-muted">{formatNumber(e.sold)} / {formatNumber(e.capacity)}</span></p>
                          <Progress value={occ} tone={occ >= 100 ? 'ok' : occ >= 90 ? 'brand' : 'dark'} className="mt-1" />
                        </td>
                        <td className="px-3 py-3"><StatusBadge status={e.status} /></td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </div>
            <div className="flex justify-between bg-surface px-6 py-3 text-xs">
              <span className="text-muted">Mostrando {upcoming.length} de {upcomingAll.length} próximos eventos</span>
              <Link to="/organizer/events" className="flex items-center gap-1 font-semibold text-brand-600">Ver todos os eventos <ArrowRight className="size-3.5" aria-hidden /></Link>
            </div>
          </Card>

          <Card className="p-6">
            <div className="flex flex-wrap items-start justify-between gap-2">
              <div>
                <h2 className="text-xl font-bold">Faturamento da semana</h2>
                <p className="text-xs text-muted">Vendas dos últimos 7 dias via PIX e cartão.</p>
              </div>
              <p className="font-bold text-ok">{formatMoney(weekTotal)} total</p>
            </div>
            <div className="mt-6 flex h-48 items-end gap-3" role="img" aria-label="Faturamento por dia nos últimos 7 dias">
              {m.weeklyRevenue.map((d, i) => {
                const top = i === bestDay && d.cents > 0;
                return (
                  <div key={i} className="flex h-full flex-1 flex-col items-center justify-end gap-1.5">
                    {top && <span className="rounded bg-navy px-1.5 py-0.5 text-[10px] font-bold text-white tabular">{formatMoney(d.cents).replace(/,\d+$/, '')}</span>}
                    <div className={cx('w-full rounded-t-md', top ? 'bg-brand-600' : 'bg-indigo-100')} style={{ height: `${Math.max(4, (d.cents / maxDay) * 100)}%` }} title={formatMoney(d.cents)} />
                    <span className={cx('text-xs', top ? 'font-bold text-brand-600' : 'text-muted')}>{d.day}</span>
                  </div>
                );
              })}
            </div>
          </Card>
        </div>

        <aside className="grid content-start gap-6">
          <Card className="p-5">
            <div className="flex items-center justify-between gap-2">
              <h2 className="flex items-center gap-2 font-bold"><span className="size-2 rounded-full bg-brand-600" /> Virada de lote próxima</h2>
              <Badge tone="brand">Automática</Badge>
            </div>
            {m.lotTurn ? (
              <>
                <Link to={`/organizer/events/${m.lotTurn.eventId}`} className="mt-4 block rounded-xl bg-surface p-4 hover:bg-surface-strong">
                  <p className="flex justify-between gap-2 text-sm font-bold">{m.lotTurn.eventTitle}<span className="text-right text-xs text-brand-600">{m.lotTurn.lotName} ({m.lotTurn.sectorName})</span></p>
                  <p className="mt-3 flex items-baseline justify-between text-xs text-muted">Ingressos restantes <b className="text-3xl text-brand-600">{m.lotTurn.remaining}</b></p>
                  <div className="mt-3 grid grid-cols-2 gap-2 text-xs">
                    <p><span className="text-muted">Preço atual</span><br /><b>{formatMoney(m.lotTurn.currentPriceCents)}</b></p>
                    <p><span className="text-muted">Próximo lote</span><br /><b>{formatMoney(m.lotTurn.nextPriceCents)}</b> <span className="text-brand-600">(+{pct(m.lotTurn.nextPriceCents - m.lotTurn.currentPriceCents, m.lotTurn.currentPriceCents)}%)</span></p>
                  </div>
                </Link>
                <p className="mt-3 flex gap-2 text-xs text-muted"><CheckCircle2 className="size-4 shrink-0 text-ok" aria-hidden /> Quando o lote esgotar, o próximo entra à venda sozinho com o novo preço.</p>
              </>
            ) : (
              <p className="mt-4 text-sm text-muted">Nenhum setor com próximo lote programado.</p>
            )}
          </Card>

          <Card className="bg-navy p-5 text-white">
            <div className="flex items-start justify-between gap-2">
              <div>
                <h2 className="flex items-center gap-2 font-bold"><ShieldCheck className="size-5 text-emerald-400" aria-hidden /> Saúde das vendas</h2>
                <p className="text-xs text-white/60">Medido agora nas reservas e pedidos</p>
              </div>
              <span className={cx('rounded-full px-2 py-0.5 text-[10px] font-bold', m.health.oversellingIncidents === 0 ? 'bg-emerald-400/15 text-emerald-300' : 'bg-brand-600 text-white')}>
                {m.health.oversellingIncidents === 0 ? 'Tudo certo' : 'Verificar'}
              </span>
            </div>
            <div className="mt-4 grid grid-cols-2 gap-3">
              {([
                ['Reservas no último minuto', formatNumber(m.health.locksLastMinute), `${m.health.activeLocks} assentos em carrinho agora`, false],
                ['Venda acima da capacidade', String(m.health.oversellingIncidents), m.health.oversellingIncidents === 0 ? 'Nenhum assento vendido duas vezes' : 'Investigar agora', true],
                ['Conversão do checkout', `${m.health.checkoutConversionPct}%`, 'das reservas de hoje viraram compra', false],
                ['Reservas expiradas hoje', formatNumber(m.health.expiredReservationsToday), 'assentos voltaram à venda', false],
              ] as const).map(([label, value, hint, isOversell]) => (
                <div key={label} className="rounded-xl bg-white/5 p-3">
                  <p className="text-[11px] text-white/70">{label}</p>
                  <p className={cx('mt-1 font-mono text-2xl font-bold', isOversell && (m.health.oversellingIncidents === 0 ? 'text-emerald-400' : 'text-brand-500'))}>{value}</p>
                  <p className="text-[10px] text-white/50">{hint}</p>
                </div>
              ))}
            </div>
            <p className="mt-3 flex items-center justify-between border-t border-white/10 pt-3 text-xs text-white/70">
              Confirmações aguardando envio <b className="font-mono text-white">{m.health.outboxPending}</b>
            </p>
          </Card>

          <Card className="p-5">
            <h2 className="flex items-center justify-between font-bold">Equipe de portaria <ScanLine className="size-5 text-muted" aria-hidden /></h2>
            <p className="mt-1 text-xs text-muted">Os leitores baixam a lista de ingressos do evento e validam o QR mesmo sem internet.</p>
            <div className="mt-3 rounded-xl bg-surface p-3 text-xs">
              <p className="font-bold">{onSale.length} evento(s) com portaria a preparar</p>
              <p className="mt-0.5 text-muted">Acompanhe as entradas por portão na página de cada evento.</p>
            </div>
            {canAccess(user, 'operator') && (
              <Link to="/operator/setup"><Button variant="soft" size="sm" className="mt-3 w-full">Abrir bilheteria</Button></Link>
            )}
          </Card>

          <p className="flex gap-2 px-1 text-xs text-muted">
            <TrendingUp className="size-4 shrink-0 text-brand-600" aria-hidden /> A taxa de conveniência (10%) fica com a plataforma; o valor líquido é repassado ao produtor.
          </p>
        </aside>
      </div>
    </div>
  );
}
