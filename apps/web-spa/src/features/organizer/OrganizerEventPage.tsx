import { useQuery } from '@tanstack/react-query';
import {
  ArrowLeft,
  ArrowRight,
  CalendarDays,
  DoorOpen,
  ExternalLink,
  History,
  Layers,
  MapPin,
  Settings,
  ShoppingCart,
  Ticket,
  Users,
  Wallet,
  XCircle,
} from 'lucide-react';
import { useState } from 'react';
import { SectorSeatsDialog } from './SectorSeatsDialog';
import { Link, useParams } from 'react-router-dom';
import { api } from '@/api';
import { errorMessage } from '@/api/errors';
import type { AuditLogEntry, LotSummaryStatus, OrganizerEventDetail } from '@/api/types';
import { Badge, Button, Card, ErrorState, EventCover, Progress, Spinner } from '@/components/ui';
import { cx, formatDateTime, formatMoney, formatNumber, pct, relativeTime } from '@/lib/format';
import { SalesAction, StatusBadge } from './shared';

const LOT_TONE: Record<LotSummaryStatus, 'ok' | 'brand' | 'neutral' | 'warn'> = {
  ATIVO: 'ok',
  ESGOTADO: 'brand',
  PROGRAMADO: 'neutral',
  FECHADO: 'warn',
};
const LOT_LABEL: Record<LotSummaryStatus, string> = {
  ATIVO: 'À venda',
  ESGOTADO: 'Esgotado',
  PROGRAMADO: 'Próximo',
  FECHADO: 'Fechado',
};
const LOG_STYLE: Record<AuditLogEntry['kind'], { tone: string; label: string }> = {
  EVENTO: { tone: 'bg-indigo-50 text-indigo-700', label: 'Evento' },
  LOTE: { tone: 'bg-ok-soft text-ok', label: 'Lote' },
  BILHETERIA: { tone: 'bg-brand-50 text-brand-700', label: 'Bilheteria' },
  SEGURANCA: { tone: 'bg-warn-soft text-amber-800', label: 'Segurança' },
};

type Tab = 'resumo' | 'setores' | 'portaria' | 'auditoria';
const TABS: { key: Tab; label: string; icon: typeof Layers }[] = [
  { key: 'resumo', label: 'Resumo e métricas', icon: Layers },
  { key: 'setores', label: 'Setores e lotes', icon: Ticket },
  { key: 'portaria', label: 'Portaria', icon: DoorOpen },
  { key: 'auditoria', label: 'Auditoria', icon: History },
];

/** Linha do ritmo de vendas (ingressos por hora nas últimas 6 h). */
function Sparkline({ values }: { values: number[] }) {
  const max = Math.max(1, ...values);
  const pts = values.map((v, i) => `${(i / (values.length - 1)) * 100},${40 - (v / max) * 36}`).join(' ');
  return (
    <svg viewBox="0 0 100 42" className="h-24 w-full" preserveAspectRatio="none" role="img" aria-label={`Vendas por hora: ${values.join(', ')}`}>
      <polygon points={`0,42 ${pts} 100,42`} className="fill-brand-50" />
      <polyline points={pts} fill="none" className="stroke-brand-600" strokeWidth="1.5" vectorEffect="non-scaling-stroke" />
    </svg>
  );
}

function SectorsCard({ data, compact, onOpen }: { data: OrganizerEventDetail; compact?: boolean; onOpen: (sectorId: string) => void }) {
  return (
    <Card className="p-6">
      <div className="flex items-start justify-between gap-2">
        <div>
          <h2 className="flex items-center gap-2 text-xl font-bold"><Layers className="size-5 text-brand-600" aria-hidden /> Setores e lotes</h2>
          <p className="text-xs text-muted">Estoque por setor e faixa de preço. Clique num setor para ver os assentos e quem comprou cada um.</p>
        </div>
      </div>
      <div className="mt-4 grid gap-3">
        {data.sectors.map((s) => {
          const p = pct(s.sold, s.capacity);
          const free = s.capacity - s.sold - s.locked;
          const active = s.lots.find((l) => l.status === 'ATIVO');
          return (
            <button type="button" key={s.id} onClick={() => onOpen(s.id)} title="Ver assentos e compradores" className="w-full rounded-xl bg-surface p-4 text-left ring-brand-500 transition hover:bg-surface-strong hover:ring-2 focus-visible:ring-2 focus-visible:outline-none">
              <div className="flex flex-wrap items-start justify-between gap-2">
                <div>
                  <p className="flex items-center gap-2 font-bold"><span className={cx('size-2.5 rounded-full', p >= 100 || s.closed ? 'bg-brand-600' : 'bg-ok')} />{s.name}</p>
                  <p className="mt-0.5 text-xs text-muted">
                    {active ? `${active.name} à venda • ${formatMoney(active.priceCents)}` : s.closed ? 'Setor fechado para venda' : 'Todos os lotes esgotados'}
                  </p>
                </div>
                <Badge tone={p >= 100 || s.closed ? 'brand' : 'ok'}>{s.closed ? 'Setor fechado' : p >= 100 ? '100% esgotado' : `${p}% vendido`}</Badge>
              </div>
              <p className="mt-3 flex justify-between text-xs font-semibold">
                <span>{formatNumber(s.sold)} / {formatNumber(s.capacity)} ingressos emitidos{s.locked ? ` • ${s.locked} em carrinho` : ''}</span>
                <span className={cx('font-mono', free <= 50 ? 'text-brand-600' : 'text-ok')}>{free <= 0 ? '0 restantes' : `${free} restantes`}</span>
              </p>
              <Progress value={p} tone={p >= 100 ? 'brand' : 'ok'} className="mt-1.5" />
              {!compact && (
                <ul className="mt-3 flex flex-wrap gap-2">
                  {s.lots.map((l) => (
                    <li key={l.name} className="flex items-center gap-1.5 rounded-lg bg-white px-3 py-1.5 text-xs">
                      <b>{l.name}</b> {formatMoney(l.priceCents)} • {l.sold}/{l.quantity}
                      <Badge tone={LOT_TONE[l.status]}>{LOT_LABEL[l.status]}</Badge>
                    </li>
                  ))}
                </ul>
              )}
            </button>
          );
        })}
      </div>
      <div className="mt-5 rounded-xl border border-line p-4">
        <p className="flex justify-between text-xs font-semibold"><span>Ritmo de vendas nas últimas 6 horas</span><span className="font-mono text-ok">pico de {Math.max(...data.salesLast6h)} ingressos/h</span></p>
        <Sparkline values={data.salesLast6h} />
      </div>
    </Card>
  );
}

function GatesCard({ data }: { data: OrganizerEventDetail }) {
  // Agrupa setores pelo portão de entrada configurado no cadastro.
  const gates = new Map<string, { sectors: string[]; sold: number; checkedIn: number }>();
  for (const s of data.sectors) {
    const key = s.gate || 'Portão principal';
    const g = gates.get(key) ?? { sectors: [], sold: 0, checkedIn: 0 };
    g.sectors.push(s.name);
    g.sold += s.sold;
    g.checkedIn += s.checkedIn;
    gates.set(key, g);
  }
  const totalIn = data.sectors.reduce((s, x) => s + x.checkedIn, 0);
  return (
    <Card className="p-6">
      <h2 className="flex items-center gap-2 text-xl font-bold"><DoorOpen className="size-5 text-ok" aria-hidden /> Portaria</h2>
      <p className="text-xs text-muted">Entradas registradas pela leitura do QR em cada portão.</p>
      <p className="mt-4 flex items-center justify-between rounded-xl bg-surface px-4 py-3 text-sm font-semibold">
        <span className="flex items-center gap-2"><Users className="size-4" aria-hidden /> Público que já entrou</span>
        <span className="font-mono">{formatNumber(totalIn)} / {formatNumber(data.event.sold)}</span>
      </p>
      <ul className="mt-3 divide-y divide-line">
        {[...gates.entries()].map(([name, g], i) => (
          <li key={name} className="flex items-center gap-3 py-3">
            <span className="grid size-9 shrink-0 place-items-center rounded-lg bg-indigo-50 text-xs font-bold text-indigo-700">{String(i + 1).padStart(2, '0')}</span>
            <div className="min-w-0 flex-1">
              <p className="text-sm font-bold">{name}</p>
              <p className="truncate text-xs text-muted">{g.sectors.join(' • ')}</p>
              <Progress value={pct(g.checkedIn, g.sold)} tone="ok" className="mt-1.5" />
            </div>
            <span className="text-right text-xs"><b>{g.checkedIn}</b><br /><span className="text-muted">de {g.sold}</span></span>
          </li>
        ))}
      </ul>
      <p className="mt-2 text-xs text-muted">Os leitores da bilheteria baixam a lista de ingressos e funcionam mesmo sem internet.</p>
    </Card>
  );
}

function LogsCard({ data }: { data: OrganizerEventDetail }) {
  return (
    <Card className="p-6">
      <h2 className="flex items-center gap-2 text-xl font-bold"><History className="size-5 text-brand-600" aria-hidden /> Registro de alterações</h2>
      <p className="text-xs text-muted">Quem fez o quê e quando (rastreabilidade exigida pela LGPD).</p>
      <ol className="mt-4 grid gap-4">
        {data.logs.length === 0 && <li className="text-sm text-muted">Nenhuma ação registrada ainda.</li>}
        {data.logs.map((l) => (
          <li key={l.id} className="flex gap-3">
            <span className={cx('mt-0.5 h-fit rounded-md px-2 py-1 text-[10px] font-bold', LOG_STYLE[l.kind].tone)}>{LOG_STYLE[l.kind].label}</span>
            <div className="min-w-0 flex-1">
              <p className="flex justify-between gap-2 text-sm font-bold">{l.title}<span className="shrink-0 text-xs font-normal text-muted">{relativeTime(l.at)}</span></p>
              <p className="text-xs text-muted">{l.description}</p>
              <p className="mt-0.5 font-mono text-[10px] text-muted">Feito por: {l.actor}</p>
            </div>
          </li>
        ))}
      </ol>
    </Card>
  );
}

export default function OrganizerEventPage() {
  const { id = '' } = useParams();
  const [tab, setTab] = useState<Tab>('resumo');
  const [sector, setSector] = useState<string>();
  const { data, isLoading, error, refetch } = useQuery({
    queryKey: ['organizer', 'event', id],
    queryFn: () => api.organizer.getEvent(id),
    refetchInterval: 10_000,
  });

  if (isLoading) return <Spinner />;
  if (error || !data) return <ErrorState message={errorMessage(error)} onRetry={() => refetch()} />;
  const e = data.event;
  const occ = pct(e.sold, e.capacity);
  const fee = data.revenueCents - data.netRevenueCents;
  const mapsUrl = `https://www.google.com/maps/search/?api=1&query=${encodeURIComponent(e.address || `${e.venue}, ${e.city}`)}`;

  return (
    <div className="grid gap-6">
      <nav className="flex items-center gap-1 text-sm font-semibold text-muted" aria-label="Trilha">
        <Link to="/organizer/events" className="flex items-center gap-1 hover:text-ink"><ArrowLeft className="size-4" aria-hidden /> Meus eventos</Link>
        <span>/</span> <span className="text-ink">{e.title}</span>
        <span className="font-mono text-xs">#{e.id}</span>
      </nav>

      <Card className="flex flex-wrap items-center gap-5 p-5">
        <EventCover hue={e.hue} className="size-28 shrink-0 rounded-xl" />
        <div className="min-w-0 flex-1">
          <div className="flex flex-wrap gap-2">
            <StatusBadge status={e.status} />
            <Badge tone="info">{formatNumber(e.sold)} / {formatNumber(e.capacity)} vendidos ({occ}%)</Badge>
            {e.hasVirtualQueue && <Badge>Sala de espera virtual</Badge>}
          </div>
          <h1 className="mt-2 text-3xl font-extrabold tracking-tight">{e.title}</h1>
          <p className="mt-1 flex flex-wrap gap-x-4 gap-y-1 text-sm text-muted">
            <span className="flex items-center gap-1"><CalendarDays className="size-4" aria-hidden /> {formatDateTime(e.startsAt)}</span>
            <span className="flex items-center gap-1"><MapPin className="size-4" aria-hidden /> {e.venue}, {e.city}</span>
          </p>
        </div>
        <div className="flex flex-wrap gap-2">
          <SalesAction id={e.id} status={e.status} size="md" />
          <Button variant="outline" disabled title="Em breve: cancelamento com reembolso aos compradores">
            <XCircle className="size-4" aria-hidden /> Cancelar evento
          </Button>
          <Button variant="outline" disabled title="Em breve: edição de evento publicado">
            <Settings className="size-4" aria-hidden /> Editar configurações
          </Button>
          {e.status !== 'RASCUNHO' && (
            <Link to={`/events/${e.id}`} target="_blank"><Button variant="soft"><ExternalLink className="size-4" aria-hidden /> Página pública</Button></Link>
          )}
        </div>
      </Card>

      <nav className="flex gap-1 overflow-x-auto rounded-xl bg-surface p-1" aria-label="Seções do evento">
        {TABS.map((t) => (
          <button key={t.key} onClick={() => setTab(t.key)}
            className={cx('flex shrink-0 items-center gap-2 rounded-lg px-3.5 py-2 text-sm font-semibold', tab === t.key ? 'bg-white text-brand-600 shadow-sm' : 'text-muted hover:text-ink')}>
            <t.icon className="size-4" aria-hidden /> {t.label}
          </button>
        ))}
      </nav>

      {tab === 'resumo' && (
        <>
          <section className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
            <Card className="p-5">
              <p className="flex justify-between text-[11px] font-bold tracking-wider text-muted uppercase">Volume financeiro <Wallet className="size-4 text-ok" aria-hidden /></p>
              <p className="mt-3 text-2xl font-extrabold tabular">{formatMoney(data.revenueCents)}</p>
              <dl className="mt-3 grid gap-1 rounded-lg bg-surface p-2.5 text-xs">
                <div className="flex justify-between"><dt className="text-muted">Líquido do produtor</dt><dd className="font-mono font-bold text-ok">{formatMoney(data.netRevenueCents)}</dd></div>
                <div className="flex justify-between"><dt className="text-muted">Taxa BolsoPass (10%)</dt><dd className="font-mono">{formatMoney(fee)}</dd></div>
              </dl>
            </Card>
            <Card className="p-5">
              <p className="flex justify-between text-[11px] font-bold tracking-wider text-muted uppercase">Ingressos vendidos <Ticket className="size-4 text-brand-600" aria-hidden /></p>
              <p className="mt-3 text-3xl font-extrabold">{formatNumber(e.sold)} <span className="text-sm text-ok">{occ}%</span></p>
              <p className="mt-3 flex justify-between text-xs"><span>Capacidade: {formatNumber(e.capacity)}</span><b className="text-brand-600">Restantes: {formatNumber(e.capacity - e.sold)}</b></p>
              <Progress value={occ} className="mt-1" />
            </Card>
            <Card className="p-5">
              <p className="flex justify-between text-[11px] font-bold tracking-wider text-muted uppercase">Reservas expiradas hoje <History className="size-4 text-indigo-700" aria-hidden /></p>
              <p className="mt-3 text-3xl font-extrabold">{data.expiredToday}</p>
              <p className="mt-3 text-xs text-muted">Compradores que não pagaram em 10 min. Os assentos voltaram à venda.</p>
            </Card>
            <Card className="bg-navy p-5 text-white">
              <p className="flex justify-between text-[11px] font-bold tracking-wider text-white/70 uppercase">Em carrinho agora <ShoppingCart className="size-4 text-emerald-400" aria-hidden /></p>
              <p className="mt-3 font-mono text-3xl font-bold">{data.activeLocks}</p>
              <p className="mt-3 text-xs text-white/60">Assentos reservados aguardando pagamento. Nenhum pode ser vendido a outra pessoa.</p>
            </Card>
          </section>

          <div className="grid gap-6 lg:grid-cols-[1fr_380px]">
            <div className="grid content-start gap-6">
              <SectorsCard data={data} compact onOpen={setSector} />
              <Card className="overflow-hidden">
                <div className="flex flex-wrap items-start justify-between gap-2 p-6 pb-4">
                  <div>
                    <h2 className="flex items-center gap-2 text-xl font-bold"><MapPin className="size-5 text-brand-600" aria-hidden /> Local do evento</h2>
                    <p className="text-xs text-muted">{e.venue} • {e.city}</p>
                  </div>
                  <a href={mapsUrl} target="_blank" rel="noreferrer"><Button size="sm" variant="soft">Abrir no mapa <ArrowRight className="size-3.5" aria-hidden /></Button></a>
                </div>
                <div className="relative h-40 bg-gradient-to-br from-navy to-indigo-900 px-6 py-5 text-white">
                  <p className="text-2xl font-extrabold">{e.venue}</p>
                  <p className="mt-1 text-sm text-white/70">{e.address || e.city}</p>
                  <p className="absolute bottom-4 text-xs text-white/60">Entradas: {[...new Set(data.sectors.map((s) => s.gate || 'Portão principal'))].join(', ')}</p>
                </div>
              </Card>
            </div>
            <div className="grid content-start gap-6">
              <GatesCard data={data} />
              <LogsCard data={{ ...data, logs: data.logs.slice(0, 3) }} />
              {data.logs.length > 3 && (
                <button onClick={() => setTab('auditoria')} className="-mt-4 text-sm font-semibold text-brand-600">Ver todos os {data.logs.length} registros</button>
              )}
            </div>
          </div>
        </>
      )}

      {tab === 'setores' && <SectorsCard data={data} onOpen={setSector} />}
      {tab === 'portaria' && <GatesCard data={data} />}
      {tab === 'auditoria' && <LogsCard data={data} />}
      {sector && <SectorSeatsDialog eventId={e.id} sectorId={sector} onClose={() => setSector(undefined)} />}
    </div>
  );
}
