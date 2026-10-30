import { useQuery } from '@tanstack/react-query';
import {
  AlertTriangle,
  ArrowRight,
  CalendarDays,
  Clock,
  Headphones,
  MapPin,
  Radio,
  RefreshCw,
  ShieldCheck,
  Users,
} from 'lucide-react';
import { useEffect, useState } from 'react';
import { Link, useLocation, useParams } from 'react-router-dom';
import { api } from '@/api';
import { errorMessage } from '@/api/errors';
import type { EventDetail, Lot } from '@/api/types';
import { useAuth } from '@/auth/AuthProvider';
import { canAccess } from '@/auth/roles';
import { Badge, Button, Card, ErrorState, EventCover, Progress, Spinner } from '@/components/ui';
import { formatDate, formatMoney, formatNumber, formatTime } from '@/lib/format';

function useClock(intervalMs = 1000) {
  const [now, setNow] = useState(Date.now());
  useEffect(() => {
    const id = setInterval(() => setNow(Date.now()), intervalMs);
    return () => clearInterval(id);
  }, [intervalMs]);
  return now;
}

function hms(ms: number) {
  const s = Math.max(0, Math.floor(ms / 1000));
  const p = (n: number) => String(n).padStart(2, '0');
  return `${p(Math.floor(s / 3600))}h : ${p(Math.floor((s % 3600) / 60))}m : ${p(s % 60)}s`;
}

/** Sala de espera virtual: só libera a seleção de assentos quando chega a vez. */
function WaitingRoom({ event }: { event: EventDetail }) {
  const { user } = useAuth();
  const location = useLocation();
  const isClient = canAccess(user, 'client');
  const { data: q, error } = useQuery({
    queryKey: ['queue', event.id],
    queryFn: () => api.catalog.getQueue(event.id),
    enabled: isClient && event.hasVirtualQueue,
    refetchInterval: (query) => (query.state.data?.admitted ? false : 1000),
  });

  const seatsLink = `/events/${event.id}/seats`;
  const admitted = !event.hasVirtualQueue || q?.admitted;

  if (!event.hasVirtualQueue) {
    return (
      <Card className="p-6">
        <h2 className="text-xl font-bold">Escolha seus assentos</h2>
        <p className="mt-1 text-sm text-muted">
          Ao confirmar a seleção, os assentos ficam travados para você por 10 minutos.
        </p>
        <Link to={seatsLink}>
          <Button size="lg" className="mt-5 w-full">
            Entrar na seleção de assentos <ArrowRight className="size-5" aria-hidden />
          </Button>
        </Link>
      </Card>
    );
  }

  return (
    <Card className="overflow-hidden">
      <div className="flex items-center justify-between gap-3 bg-brand-600 px-6 py-4 text-white">
        <div className="flex items-center gap-3">
          <Radio className="size-7" aria-hidden />
          <div>
            <h2 className="text-xl font-bold">Sala de espera virtual</h2>
            <p className="text-xs opacity-90">Controle de tráfego por token de sessão</p>
          </div>
        </div>
        {q && (
          <span className="hidden items-center gap-1.5 rounded-full bg-white/20 px-3 py-1 text-xs font-semibold sm:flex">
            <span className="size-2 rounded-full bg-emerald-300" /> Sincronizado ao vivo
          </span>
        )}
      </div>
      <div className="p-6">
        {!user ? (
          <div className="text-center">
            <p className="text-sm text-muted">Entre na sua conta para pegar seu lugar na fila.</p>
            <Link to="/auth/login" state={{ from: location.pathname }}>
              <Button size="lg" className="mt-4">
                Entrar e pegar meu lugar
              </Button>
            </Link>
          </div>
        ) : !isClient ? (
          <p className="text-sm text-muted">Somente contas de cliente podem comprar ingressos.</p>
        ) : error ? (
          <ErrorState message={errorMessage(error)} />
        ) : !q ? (
          <Spinner label="Entrando na fila…" />
        ) : (
          <>
            <div className="grid gap-3 sm:grid-cols-3">
              <div className="rounded-xl bg-surface p-4">
                <p className="text-xs text-muted">Sua posição na fila</p>
                <p className="mt-1 text-4xl font-extrabold text-brand-600 tabular">
                  #{q.position}
                  <span className="text-xs font-medium text-muted"> de {formatNumber(q.total)}</span>
                </p>
              </div>
              <div className="rounded-xl bg-surface p-4">
                <p className="text-xs text-muted">Tempo estimado</p>
                <p className="mt-1 text-4xl font-extrabold tabular">
                  ~{Math.ceil(q.etaSeconds / 60)}
                  <span className="text-base font-medium"> min</span>
                </p>
              </div>
              <div className="rounded-xl bg-surface p-4">
                <p className="text-xs text-muted">Status da conexão</p>
                <p className="mt-2 flex items-center gap-2 text-lg font-bold text-ok">
                  <Radio className="size-5" aria-hidden /> Ativo & seguro
                </p>
                <p className="mt-1 font-mono text-[11px] text-muted">Sessão: {q.sessionId}</p>
              </div>
            </div>
            <div className="mt-4 rounded-xl bg-surface p-4">
              <div className="flex items-center justify-between text-sm font-semibold">
                <span className="flex items-center gap-2">
                  <RefreshCw className="size-4 text-brand-600" aria-hidden />
                  {admitted ? 'Sua vez chegou!' : 'Processando liberação de assentos…'}
                </span>
                <span className="text-brand-600">{q.progressPct}%</span>
              </div>
              <Progress value={q.progressPct} className="mt-2" />
              {!admitted && (
                <p className="mt-2 flex items-center gap-1.5 text-xs text-muted">
                  <AlertTriangle className="size-3.5" aria-hidden /> Não feche esta aba. Você entra
                  na seleção assim que chegar sua vez.
                </p>
              )}
            </div>
          </>
        )}
        {isClient && (
          <Link to={admitted ? seatsLink : '#'} aria-disabled={!admitted} onClick={(e) => !admitted && e.preventDefault()}>
            <Button size="lg" className="mt-5 w-full" disabled={!admitted}>
              Entrar na seleção de assentos <ArrowRight className="size-5" aria-hidden />
            </Button>
          </Link>
        )}
      </div>
    </Card>
  );
}

function LotRow({ lot, canSelect, eventId }: { lot: Lot; canSelect: boolean; eventId: string }) {
  const remaining = lot.quantity - lot.sold;
  const soldOut = lot.status === 'ESGOTADO' || remaining <= 0;
  const lastOnes = !soldOut && remaining <= Math.max(3, lot.quantity * 0.25);
  return (
    <div className={`grid gap-4 border-b border-line py-5 last:border-0 sm:grid-cols-[1fr_auto] ${soldOut ? 'opacity-60' : ''}`}>
      <div>
        <div className="flex flex-wrap items-center gap-2">
          <h3 className="text-lg font-bold">{lot.name}</h3>
          {soldOut && <Badge>Esgotado</Badge>}
          {lastOnes && <Badge tone="brand">Últimos {remaining} ingressos!</Badge>}
        </div>
        <p className="mt-1 text-sm text-muted">{lot.description}</p>
        <ul className="mt-2 flex flex-wrap gap-x-4 gap-y-1 text-xs font-medium">
          <li>• {lot.gate}</li>
          <li>• Até {lot.limitPerCpf} por CPF</li>
          {lot.perks.map((p) => (
            <li key={p}>• {p}</li>
          ))}
        </ul>
      </div>
      <div className="flex items-center gap-4 sm:flex-col sm:items-end">
        <div className="text-right">
          {lot.originalPriceCents && (
            <p className="text-xs text-muted line-through">{formatMoney(lot.originalPriceCents)}</p>
          )}
          <p className="text-2xl font-extrabold text-brand-600 tabular">{formatMoney(lot.priceCents)}</p>
          <p className="text-[11px] text-muted">em até {lot.maxInstallments}x</p>
        </div>
        {soldOut ? (
          <Button variant="soft" disabled>
            Indisponível
          </Button>
        ) : (
          <Link
            to={canSelect ? `/events/${eventId}/seats?setor=${encodeURIComponent(lot.sectorId)}` : '#'}
            onClick={(e) => !canSelect && e.preventDefault()}
          >
            <Button disabled={!canSelect} title={canSelect ? undefined : 'Aguarde sua vez na fila'}>
              Selecionar
            </Button>
          </Link>
        )}
      </div>
    </div>
  );
}

export default function EventDetailPage() {
  const { id = '' } = useParams();
  const { user } = useAuth();
  const now = useClock();
  const { data: event, isLoading, error, refetch } = useQuery({
    queryKey: ['event', id],
    queryFn: () => api.catalog.getEvent(id),
  });
  const { data: queue } = useQuery({
    queryKey: ['queue', id],
    queryFn: () => api.catalog.getQueue(id),
    enabled: false,
  });

  if (isLoading) return <Spinner />;
  if (error || !event) return <ErrorState message={errorMessage(error)} onRetry={() => refetch()} />;

  const onSale = event.status === 'EM_VENDAS';
  const canSelect =
    onSale && canAccess(user, 'client') && (!event.hasVirtualQueue || !!queue?.admitted);

  return (
    <>
      <EventCover hue={event.hue} className="text-white">
        <div className="relative mx-auto max-w-7xl px-4 pt-32 pb-10">
          <div className="flex flex-wrap gap-2">
            {event.highlight && <Badge className="bg-brand-600 text-white">{event.highlight}</Badge>}
            <Badge className="bg-white/15 text-white">Classificação: {event.ageRating}</Badge>
            {!onSale && <Badge className="bg-white text-ink">{event.status.replace('_', ' ')}</Badge>}
          </div>
          <h1 className="mt-3 text-4xl font-extrabold tracking-tight md:text-5xl">{event.title}</h1>
          <p className="mt-3 flex flex-wrap gap-x-5 gap-y-1 text-sm opacity-90">
            <span className="flex items-center gap-1.5">
              <CalendarDays className="size-4" aria-hidden /> {formatDate(event.startsAt)} às {formatTime(event.startsAt)}
            </span>
            <span className="flex items-center gap-1.5">
              <MapPin className="size-4" aria-hidden /> {event.venue} • {event.city}
            </span>
            <span className="flex items-center gap-1.5">
              <Users className="size-4" aria-hidden /> Capacidade: {formatNumber(event.capacity)} lugares
            </span>
          </p>
        </div>
      </EventCover>

      <div className="mx-auto grid max-w-7xl gap-6 px-4 py-8 lg:grid-cols-[1fr_340px]">
        <div className="grid content-start gap-6">
          {onSale ? (
            <WaitingRoom event={event} />
          ) : (
            <Card className="p-6 text-sm text-muted">As vendas deste evento não estão abertas.</Card>
          )}

          <Card className="p-6">
            <div className="flex flex-wrap items-start justify-between gap-2">
              <div>
                <h2 className="text-2xl font-bold">Lotes disponíveis</h2>
                <p className="text-xs text-muted">Valores sem a taxa de conveniência de 10%, mostrada antes de pagar.</p>
              </div>
              {event.nextLotAt && Date.parse(event.nextLotAt) > now && (
                <span className="flex items-center gap-1.5 rounded-full bg-brand-50 px-3 py-1 text-xs font-semibold text-brand-700 tabular">
                  <Clock className="size-3.5" aria-hidden /> Próximo lote vira em: {hms(Date.parse(event.nextLotAt) - now)}
                </span>
              )}
            </div>
            <div className="mt-2">
              {event.lots.map((lot) => (
                <LotRow key={lot.id} lot={lot} canSelect={canSelect} eventId={event.id} />
              ))}
            </div>
          </Card>

          {event.lineup.length > 0 && (
            <Card className="p-6">
              <h2 className="text-2xl font-bold">Line-up & experiências</h2>
              <div className="mt-4 grid gap-4 sm:grid-cols-3">
                {event.lineup.map((l) => (
                  <div key={l.stage} className="rounded-xl bg-surface p-4">
                    <p className="text-[11px] font-bold tracking-wider text-brand-600 uppercase">{l.stage}</p>
                    <p className="mt-1 text-lg leading-tight font-bold">{l.artists}</p>
                    <p className="mt-2 text-xs text-muted">{l.description}</p>
                  </div>
                ))}
              </div>
            </Card>
          )}

          <Card className="bg-surface p-6">
            <h2 className="flex items-center gap-2 text-lg font-bold">
              <ShieldCheck className="size-5 text-brand-600" aria-hidden /> Segurança anti-cambista & anti-fraude
            </h2>
            <div className="mt-3 grid gap-4 text-sm sm:grid-cols-3">
              <p><b>QR dinâmico</b><br /><span className="text-xs text-muted">Muda a cada 15 segundos na sua carteira BolsoPass.</span></p>
              <p><b>Reembolso garantido</b><br /><span className="text-xs text-muted">Cancelamento em até 7 dias após a compra (CDC).</span></p>
              <p><b>Ingresso nominal</b><br /><span className="text-xs text-muted">Cada ingresso é vinculado ao CPF do titular.</span></p>
            </div>
          </Card>
        </div>

        <aside className="grid content-start gap-6">
          <Card className="p-5">
            <h2 className="flex items-center gap-2 text-lg font-bold">
              <MapPin className="size-5 text-brand-600" aria-hidden /> Local do evento
            </h2>
            <p className="mt-2 text-sm font-semibold">{event.venue}</p>
            <p className="text-sm text-muted">{event.address}</p>
            <a
              href={`https://www.google.com/maps/search/?api=1&query=${encodeURIComponent(`${event.venue} ${event.address}`)}`}
              target="_blank"
              rel="noreferrer"
            >
              <Button variant="soft" size="sm" className="mt-3 w-full">
                Abrir no Google Maps
              </Button>
            </a>
          </Card>

          <Card className="p-5">
            <h2 className="text-lg font-bold">Mapa de setores</h2>
            <div className="mt-3 grid gap-2">
              <div className="rounded-md bg-navy py-1.5 text-center text-[11px] font-bold tracking-widest text-white">PALCO PRINCIPAL</div>
              {event.lots.map((lot) => {
                const remaining = lot.quantity - lot.sold;
                return (
                  <div key={lot.id} className="flex items-center justify-between text-sm">
                    <span className="flex items-center gap-2">
                      <span className="size-3 rounded-sm" style={{ background: event.sectors.find((s) => s.id === lot.sectorId)?.color }} />
                      {event.sectors.find((s) => s.id === lot.sectorId)?.name}
                    </span>
                    <span className="font-semibold">{remaining > 0 && lot.status !== 'ESGOTADO' ? `${remaining} vagas` : 'Esgotado'}</span>
                  </div>
                );
              })}
            </div>
          </Card>

          <Card className="bg-surface p-5">
            <h2 className="flex items-center gap-2 text-lg font-bold">
              <Headphones className="size-5 text-brand-600" aria-hidden /> Central de ajuda
            </h2>
            <p className="mt-1 text-sm text-muted">Dúvidas sobre pagamento, parcelamento ou meia-entrada?</p>
          </Card>
        </aside>
      </div>
    </>
  );
}
