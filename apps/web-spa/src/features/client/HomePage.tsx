import { useQuery } from '@tanstack/react-query';
import {
  ArrowRight,
  CalendarDays,
  Flame,
  LayoutGrid,
  Lock,
  MapPin,
  QrCode,
  Search,
  ShieldCheck,
  Timer,
  Zap,
} from 'lucide-react';
import { useState, type FormEvent } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { api } from '@/api';
import { errorMessage } from '@/api/errors';
import type { EventCategory, EventSummary } from '@/api/types';
import { Badge, Button, Card, ErrorState, EventCover, Progress, Spinner } from '@/components/ui';
import { cx, formatDate, formatMoney, formatNumber, pct } from '@/lib/format';
import { CATEGORY_LABEL } from './labels';

function Hero({ event }: { event: EventSummary }) {
  const occupancy = pct(event.sold, event.capacity);
  return (
    <EventCover hue={event.hue} className="text-white">
      <div className="relative mx-auto grid max-w-7xl gap-8 px-4 py-12 lg:grid-cols-[1fr_360px] lg:py-16">
        <div>
          <div className="flex flex-wrap gap-2">
            <Badge tone="brand" icon={<Zap className="size-3" />} className="bg-brand-600 text-white">
              {event.highlight ?? 'Lote liberado'}
            </Badge>
            {occupancy >= 80 && (
              <Badge className="bg-white/90 text-brand-700" icon={<Flame className="size-3" />}>
                Alta demanda
              </Badge>
            )}
            {event.hasVirtualQueue && <Badge className="bg-white/15 text-white">Sala de espera ativa</Badge>}
          </div>
          <p className="mt-5 flex items-center gap-2 text-sm font-semibold tracking-wider uppercase opacity-90">
            <CalendarDays className="size-4" aria-hidden />
            {formatDate(event.startsAt)} • {event.venue} • {event.city}
          </p>
          <h1 className="mt-2 text-4xl font-extrabold tracking-tight md:text-5xl">{event.title}</h1>
          <div className="mt-8 flex flex-wrap gap-3">
            <Link to={`/events/${event.id}`}>
              <Button size="lg">
                <Lock className="size-5" aria-hidden /> Garantir ingresso com lock seguro
              </Button>
            </Link>
          </div>
        </div>
        <div className="self-end rounded-2xl border border-white/15 bg-black/30 p-5 backdrop-blur">
          <p className="text-xs font-bold tracking-wider text-brand-200 uppercase">Ocupação em tempo real</p>
          <p className="mt-2 text-3xl font-bold tabular">
            {occupancy}% <span className="text-sm font-medium opacity-80">vendido</span>
          </p>
          <Progress value={occupancy} className="mt-3 bg-white/20" label="Ocupação do evento" />
          <p className="mt-3 text-xs opacity-90">
            Restam {formatNumber(event.capacity - event.sold)} ingressos
          </p>
        </div>
      </div>
    </EventCover>
  );
}

function EventCard({ event }: { event: EventSummary }) {
  const occupancy = pct(event.sold, event.capacity);
  const soldOut = event.status === 'ESGOTADO' || occupancy >= 100;
  return (
    <Card className="flex flex-col overflow-hidden">
      <EventCover hue={event.hue} className="h-40">
        {event.highlight && (
          <Badge className="absolute top-3 left-3 bg-brand-600 text-white">{event.highlight}</Badge>
        )}
        {event.hasVirtualQueue && (
          <span className="absolute right-3 bottom-3 flex items-center gap-1 rounded-md bg-white px-2 py-0.5 text-[11px] font-bold">
            <Timer className="size-3" aria-hidden /> Fila virtual
          </span>
        )}
      </EventCover>
      <div className="flex flex-1 flex-col p-4">
        <p className="flex items-center gap-1.5 text-xs text-muted">
          <CalendarDays className="size-3.5" aria-hidden /> {formatDate(event.startsAt)}
        </p>
        <h3 className="mt-1 text-lg leading-snug font-bold">{event.title}</h3>
        <p className="flex items-center gap-1 text-xs text-muted">
          <MapPin className="size-3.5" aria-hidden /> {event.venue} • {event.city}
        </p>
        <div className="mt-4 flex items-center justify-between text-xs font-semibold">
          <span>Ocupação</span>
          <span className={occupancy >= 80 ? 'text-brand-700' : 'text-ok'}>{occupancy}% vendido</span>
        </div>
        <Progress value={occupancy} tone={occupancy >= 80 ? 'brand' : 'ok'} className="mt-1.5" />
        <div className="mt-auto flex items-end justify-between pt-4">
          <div>
            <p className="text-[11px] text-muted">A partir de</p>
            <p className="text-xl font-bold tabular">{formatMoney(event.fromPriceCents)}</p>
          </div>
          <Link to={`/events/${event.id}`}>
            <Button size="sm" variant={soldOut ? 'soft' : occupancy >= 80 ? 'primary' : 'dark'}>
              {soldOut ? 'Esgotado' : event.hasVirtualQueue ? 'Entrar na fila' : 'Escolher assentos'}
              <ArrowRight className="size-3.5" aria-hidden />
            </Button>
          </Link>
        </div>
      </div>
    </Card>
  );
}

export default function HomePage() {
  const [params, setParams] = useSearchParams();
  const q = params.get('q') ?? '';
  const category = (params.get('categoria') as EventCategory | null) ?? undefined;
  const [text, setText] = useState(q);

  const { data, isLoading, error, refetch } = useQuery({
    queryKey: ['events', q, category],
    queryFn: () => api.catalog.listEvents({ q, category }),
  });

  const featured =
    data && [...data].sort((a, b) => pct(b.sold, b.capacity) - pct(a.sold, a.capacity))[0];

  function setFilter(next: { q?: string; categoria?: EventCategory }) {
    const p = new URLSearchParams();
    const nq = next.q ?? q;
    if (nq) p.set('q', nq);
    if (next.categoria) p.set('categoria', next.categoria);
    setParams(p);
  }

  function onSearch(e: FormEvent) {
    e.preventDefault();
    setFilter({ q: text.trim(), categoria: category });
  }

  return (
    <>
      {featured && !q && !category && <Hero event={featured} />}

      <section className="border-b border-line bg-white">
        <div className="mx-auto max-w-7xl px-4 py-5">
          <form onSubmit={onSearch} className="flex gap-2">
            <div className="relative flex-1">
              <Search className="absolute top-1/2 left-3.5 size-4 -translate-y-1/2 text-muted" aria-hidden />
              <input
                value={text}
                onChange={(e) => setText(e.target.value)}
                placeholder="Buscar por show, festival, artista ou arena…"
                aria-label="Buscar eventos"
                className="h-11 w-full rounded-lg bg-surface pr-3 pl-10 text-sm focus:outline-none"
              />
            </div>
            <Button type="submit" variant="dark" size="lg" className="h-11">
              Buscar
            </Button>
          </form>
          <div className="mt-3 flex gap-2 overflow-x-auto pb-1">
            <button
              onClick={() => setFilter({ q })}
              className={cx(
                'flex shrink-0 items-center gap-1.5 rounded-lg px-3 py-1.5 text-xs font-semibold',
                !category ? 'bg-brand-600 text-white' : 'bg-surface',
              )}
            >
              <LayoutGrid className="size-3.5" aria-hidden /> Todos os eventos
            </button>
            {(Object.keys(CATEGORY_LABEL) as EventCategory[]).map((c) => (
              <button
                key={c}
                onClick={() => setFilter({ q, categoria: c })}
                className={cx(
                  'shrink-0 rounded-lg px-3 py-1.5 text-xs font-semibold',
                  category === c ? 'bg-brand-600 text-white' : 'bg-surface hover:bg-surface-strong',
                )}
              >
                {CATEGORY_LABEL[c]}
              </button>
            ))}
          </div>
        </div>
      </section>

      <section className="mx-auto max-w-7xl px-4 py-10">
        <p className="flex items-center gap-2 text-xs font-bold tracking-wider text-brand-600 uppercase">
          <span className="size-2 rounded-full bg-brand-600" /> Alta concorrência & demanda imediata
        </p>
        <div className="mt-1 flex flex-wrap items-end justify-between gap-2">
          <h2 className="text-3xl font-extrabold tracking-tight">
            {q ? `Resultados para “${q}”` : 'Eventos em destaque'}
          </h2>
          {data && <p className="text-sm text-muted">{data.length} eventos confirmados</p>}
        </div>

        {isLoading && <Spinner />}
        {error && <ErrorState message={errorMessage(error)} onRetry={() => refetch()} />}
        {data?.length === 0 && (
          <p className="py-16 text-center text-muted">Nenhum evento encontrado para esse filtro.</p>
        )}
        <div className="mt-6 grid gap-5 sm:grid-cols-2 lg:grid-cols-4">
          {data?.map((e) => <EventCard key={e.id} event={e} />)}
        </div>
      </section>

      <section className="mx-auto max-w-7xl px-4 pb-14">
        <div className="grid gap-6 rounded-3xl bg-navy p-8 text-white lg:grid-cols-[1.2fr_2fr] lg:p-10">
          <div>
            <Badge className="bg-brand-600 text-white">Engenharia de missão crítica</Badge>
            <h2 className="mt-4 text-3xl font-extrabold tracking-tight">
              Por que a BolsoPass elimina cambistas e quedas de servidor?
            </h2>
            <p className="mt-3 text-sm text-white/75">
              Trava atômica de assentos e QR Code criptográfico que muda a cada 15 segundos na sua
              carteira digital.
            </p>
          </div>
          <div className="grid gap-4 sm:grid-cols-3">
            {[
              { icon: Lock, title: 'Zero overselling', text: 'Cada assento é travado para um único comprador por 10 minutos. Nunca vendemos o mesmo lugar duas vezes.' },
              { icon: QrCode, title: 'QR dinâmico', text: 'Print de tela não funciona: o código muda a cada 15 s com assinatura digital.' },
              { icon: ShieldCheck, title: 'Transparência total', text: 'Taxa de 10% visível antes de pagar e arrependimento em até 7 dias.' },
            ].map(({ icon: Icon, title, text }) => (
              <div key={title} className="rounded-2xl bg-navy-light p-5">
                <Icon className="size-6 text-brand-500" aria-hidden />
                <p className="mt-3 text-lg font-bold">{title}</p>
                <p className="mt-1 text-xs leading-relaxed text-white/70">{text}</p>
              </div>
            ))}
          </div>
        </div>
      </section>
    </>
  );
}
