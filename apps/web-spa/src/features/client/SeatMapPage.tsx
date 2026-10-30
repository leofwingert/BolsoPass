import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Accessibility, AlertTriangle, ArrowLeft, Check, Lock, Timer, Trash2, Zap } from 'lucide-react';
import { useMemo, useState, type KeyboardEvent } from 'react';
import { Link, useNavigate, useParams, useSearchParams } from 'react-router-dom';
import { api } from '@/api';
import { ConflictError, errorMessage } from '@/api/errors';
import { topics, type RealtimeMessage } from '@/api/realtime';
import type { SeatMap } from '@/api/types';
import { Button, Card, ErrorState, Spinner } from '@/components/ui';
import { computeTotals } from '@/domain/pricing';
import { canAddSeat } from '@/domain/purchaseRules';
import { formatCountdown } from '@/domain/reservation';
import { isSelectable, seatView, type Seat, type SeatStatus, type SeatView } from '@/domain/seat';
import { useCountdown } from '@/hooks/useCountdown';
import { useConnectionStatus, useRealtime } from '@/hooks/useRealtime';
import { cx, formatDate, formatMoney, formatTime } from '@/lib/format';
import { useActiveReservation } from './activeReservation';

const VIEW_CLASS: Record<SeatView, string> = {
  DISPONIVEL: 'bg-seat-free text-white hover:ring-2 hover:ring-seat-free/40',
  SELECIONADO: 'bg-brand-600 text-white ring-2 ring-brand-200',
  RESERVADO_OUTRO: 'bg-warn text-white cursor-not-allowed',
  OCUPADO: 'bg-seat-taken text-white cursor-not-allowed',
  INDISPONIVEL: 'bg-surface-strong text-muted cursor-not-allowed',
};

const VIEW_LABEL: Record<SeatView, string> = {
  DISPONIVEL: 'disponível',
  SELECIONADO: 'selecionado',
  RESERVADO_OUTRO: 'reservado por outro comprador',
  OCUPADO: 'ocupado',
  INDISPONIVEL: 'indisponível',
};

/** Aplica uma mensagem do WebSocket ao mapa em cache. */
function applyMessage(map: SeatMap, msg: RealtimeMessage): SeatMap {
  if (msg.type === 'LIVE_BUYERS') return { ...map, liveBuyers: msg.count };
  const update = (status: SeatStatus, heldBy?: string) => (s: Seat) => ({ ...s, status, heldBy });
  const ids = 'seatIds' in msg ? new Set(msg.seatIds) : new Set<string>();
  const next = (fn: (s: Seat) => Seat) => ({
    ...map,
    seats: map.seats.map((s) => (ids.has(s.id) ? fn(s) : s)),
  });
  switch (msg.type) {
    case 'SEAT_LOCKED':
      return next(update('BLOQUEADO_TEMPORARIO', msg.reservationId));
    case 'SEAT_RELEASED':
      return next(update('DISPONIVEL'));
    case 'SEAT_SOLD':
      return next(update('VENDIDO'));
    default:
      return map;
  }
}

/** Setas movem o foco entre assentos (acessibilidade, doc 07). */
function onGridKeyDown(e: KeyboardEvent<HTMLDivElement>) {
  const target = e.target as HTMLElement;
  if (!target.dataset.seat) return;
  const moves: Record<string, [number, number]> = {
    ArrowLeft: [0, -1],
    ArrowRight: [0, 1],
    ArrowUp: [-1, 0],
    ArrowDown: [1, 0],
  };
  const move = moves[e.key];
  if (!move) return;
  e.preventDefault();
  const row = Number(target.dataset.row) + move[0];
  const col = Number(target.dataset.col) + move[1];
  const next = e.currentTarget.querySelector<HTMLElement>(
    `[data-sector="${target.dataset.sector}"][data-row="${row}"][data-col="${col}"]`,
  );
  next?.focus();
}

export default function SeatMapPage() {
  const { id: eventId = '' } = useParams();
  const [params] = useSearchParams();
  const focusSector = params.get('setor');
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const connection = useConnectionStatus();
  const { active, start } = useActiveReservation();
  const activeHere = active?.eventId === eventId ? active : undefined;
  const countdown = useCountdown(activeHere?.expiresAt, activeHere?.offsetMs);

  const [selected, setSelected] = useState<Set<string>>(new Set());
  const [alert, setAlert] = useState<string>();
  const [pcdFocus, setPcdFocus] = useState(false);

  const mapKey = ['seatmap', eventId];
  const { data: map, isLoading, error, refetch } = useQuery({
    queryKey: mapKey,
    queryFn: () => api.catalog.getSeatMap(eventId),
    staleTime: 0,
  });
  const { data: event } = useQuery({ queryKey: ['event', eventId], queryFn: () => api.catalog.getEvent(eventId) });

  // Tempo real: travas, liberações (reservas.expiradas) e vendas de outros compradores.
  useRealtime(topics.eventSeats(eventId), (msg) => {
    queryClient.setQueryData<SeatMap>(mapKey, (old) => (old ? applyMessage(old, msg) : old));
    if (msg.type === 'SEAT_LOCKED' && msg.reservationId !== activeHere?.id) {
      const lost = msg.seatIds.filter((s) => selected.has(s));
      if (lost.length) {
        setSelected((prev) => new Set([...prev].filter((s) => !lost.includes(s))));
        const labels = map?.seats.filter((s) => lost.includes(s.id)).map((s) => s.label).join(', ');
        setAlert(`Alerta de concorrência: o assento ${labels} acabou de ser reservado por outro comprador.`);
      }
    }
  });

  const seatsById = useMemo(() => new Map(map?.seats.map((s) => [s.id, s])), [map]);
  const lotBySector = useMemo(() => new Map(event?.lots.map((l) => [l.sectorId, l])), [event]);
  const selectedSeats = [...selected].map((id) => seatsById.get(id)).filter((s): s is Seat => !!s);
  const priceOf = (s: Seat) => lotBySector.get(s.sectorId)?.priceCents ?? 0;
  const totals = computeTotals(selectedSeats.map((s) => ({ priceCents: priceOf(s) })));

  const reserve = useMutation({
    mutationFn: () => api.inventory.reserveSeats(eventId, [...selected]),
    onSuccess: (reservation) => {
      start(reservation);
      navigate(`/checkout/${reservation.id}`);
    },
    onError: (err) => {
      if (err instanceof ConflictError && err.seatIds.length) {
        setSelected((prev) => new Set([...prev].filter((s) => !err.seatIds.includes(s))));
        refetch();
      }
      setAlert(errorMessage(err));
    },
  });

  function toggle(seat: Seat) {
    setAlert(undefined);
    if (selected.has(seat.id)) {
      setSelected((prev) => {
        const next = new Set(prev);
        next.delete(seat.id);
        return next;
      });
      return;
    }
    if (!isSelectable(seat)) return;
    const limit = lotBySector.get(seat.sectorId)?.limitPerCpf ?? 4;
    const inSector = selectedSeats.filter((s) => s.sectorId === seat.sectorId).length;
    if (!canAddSeat(inSector, limit)) {
      setAlert(`Limite de ${limit} ingressos por CPF neste setor.`);
      return;
    }
    setSelected((prev) => new Set(prev).add(seat.id));
  }

  if (isLoading) return <Spinner label="Carregando mapa de assentos…" />;
  if (error || !map) return <ErrorState message={errorMessage(error)} onRetry={() => refetch()} />;

  const sectors = [...map.sectors].sort((a, b) => (a.id === focusSector ? -1 : b.id === focusSector ? 1 : 0));

  return (
    <div className="mx-auto max-w-7xl px-4 py-6">
      <Link to={`/events/${eventId}`} className="mb-3 inline-flex items-center gap-1 text-sm font-semibold text-muted hover:text-ink">
        <ArrowLeft className="size-4" aria-hidden /> Voltar aos detalhes do evento
      </Link>

      <Card className="mb-5 flex flex-wrap items-center gap-x-6 gap-y-2 px-5 py-3 text-sm">
        <span className="flex items-center gap-2 font-semibold">
          <span className={cx('size-2.5 rounded-full', connection === 'connected' ? 'bg-ok' : 'animate-pulse bg-warn')} />
          Tempo real: {connection === 'connected' ? 'ativo & sincronizado' : 'reconectando…'}
        </span>
        <span className="text-muted">Compradores nesta tela: <b className="text-ink">{map.liveBuyers}</b></span>
        <span className="flex flex-wrap gap-4 text-xs font-semibold">
          {(['DISPONIVEL', 'SELECIONADO', 'RESERVADO_OUTRO', 'OCUPADO'] as SeatView[]).map((v) => (
            <span key={v} className="flex items-center gap-1.5">
              <span className={cx('size-3 rounded-full', VIEW_CLASS[v].split(' ')[0])} />
              {{ DISPONIVEL: 'Disponível', SELECIONADO: 'Seu assento', RESERVADO_OUTRO: 'Reservado (outro)', OCUPADO: 'Ocupado', INDISPONIVEL: '' }[v]}
            </span>
          ))}
        </span>
      </Card>

      <div className="grid gap-6 lg:grid-cols-[1fr_380px]">
        <Card className="p-5 md:p-6">
          <div className="flex flex-wrap items-center justify-between gap-3">
            <div>
              <p className="text-xs font-bold tracking-wider text-muted uppercase">Instalação oficial</p>
              <h1 className="text-2xl font-bold">{event?.venue ?? 'Mapa de assentos'}</h1>
            </div>
            <Button variant={pcdFocus ? 'dark' : 'outline'} size="sm" onClick={() => setPcdFocus((v) => !v)} aria-pressed={pcdFocus}>
              <Accessibility className="size-4" aria-hidden /> Foco PCD
            </Button>
          </div>
          <div className="mx-auto mt-5 max-w-lg rounded-lg bg-navy py-2.5 text-center text-sm font-bold tracking-[0.2em] text-white">
            PALCO PRINCIPAL
          </div>

          {alert && (
            <p role="alert" className="mx-auto mt-4 flex max-w-xl items-start gap-2 rounded-lg bg-brand-50 px-4 py-2.5 text-sm font-semibold text-brand-700">
              <AlertTriangle className="mt-0.5 size-4 shrink-0" aria-hidden /> {alert}
            </p>
          )}

          <div className="mt-5 grid gap-4 md:grid-cols-2" onKeyDown={onGridKeyDown}>
            {sectors.map((sector) => {
              const seats = map.seats.filter((s) => s.sectorId === sector.id);
              const lot = lotBySector.get(sector.id);
              const wide = seats.length > 6;
              return (
                <section
                  key={sector.id}
                  aria-label={sector.name}
                  className={cx('rounded-xl bg-surface p-4', wide && 'md:col-span-2', sector.id === focusSector && 'ring-2 ring-brand-200')}
                >
                  <div className="mb-3 flex items-center justify-between gap-2">
                    <span className="rounded-full bg-white px-2.5 py-0.5 text-xs font-bold">{sector.name}</span>
                    <span className="text-sm font-semibold tabular">{formatMoney(lot?.priceCents ?? sector.priceCents)}</span>
                  </div>
                  <div className="grid gap-2">
                    {sector.rows.map((row, r) => (
                      <div key={row} className="flex flex-wrap justify-center gap-1.5">
                        {seats
                          .filter((s) => s.row === row)
                          .map((seat, c) => {
                            const view = seatView(seat, selected, activeHere?.id);
                            const dim = pcdFocus && seat.kind !== 'PCD';
                            return (
                              <button
                                key={seat.id}
                                data-seat
                                data-sector={sector.id}
                                data-row={r}
                                data-col={c}
                                onClick={() => toggle(seat)}
                                disabled={view !== 'DISPONIVEL' && view !== 'SELECIONADO'}
                                aria-pressed={view === 'SELECIONADO'}
                                aria-label={`Assento ${seat.label}, ${VIEW_LABEL[view]}${seat.kind === 'PCD' ? ', PCD' : ''}`}
                                className={cx(
                                  'grid size-9 place-items-center rounded-md text-[11px] font-bold transition',
                                  VIEW_CLASS[view],
                                  dim && 'opacity-30',
                                )}
                              >
                                {view === 'SELECIONADO' ? (
                                  <Check className="size-4" aria-hidden />
                                ) : view === 'RESERVADO_OUTRO' ? (
                                  <Lock className="size-3.5" aria-hidden />
                                ) : seat.kind === 'PCD' ? (
                                  <Accessibility className="size-4" aria-hidden />
                                ) : (
                                  seat.label.slice(-2)
                                )}
                              </button>
                            );
                          })}
                      </div>
                    ))}
                  </div>
                </section>
              );
            })}
          </div>
          <p className="mt-5 border-t border-line pt-4 text-xs text-muted">
            Clique no assento para selecionar ou liberar. Use as setas do teclado para navegar.
          </p>
        </Card>

        <aside className="grid content-start gap-4">
          <Card className="p-5">
            {activeHere ? (
              <div className="rounded-xl bg-brand-50 p-4">
                <p className="flex items-center gap-2 text-xs font-bold tracking-wider text-brand-700 uppercase">
                  <Timer className="size-4" aria-hidden /> Retenção temporária
                </p>
                <p className="mt-1 text-2xl font-extrabold text-brand-700 tabular">{formatCountdown(countdown.ms)} restantes</p>
                <p className="mt-1 text-xs text-brand-700">Você já tem assentos travados neste evento.</p>
                <Link to={`/checkout/${activeHere.id}`}>
                  <Button className="mt-3 w-full">Voltar ao pagamento</Button>
                </Link>
              </div>
            ) : (
              <div className="rounded-xl bg-surface p-4 text-xs text-muted">
                <p className="flex items-center gap-1.5 font-bold text-ink">
                  <Zap className="size-4 text-brand-600" aria-hidden /> Trava de 10 minutos
                </p>
                Ao confirmar, os assentos ficam travados só para você por 10 minutos. Se o tempo
                acabar sem pagamento, eles voltam ao catálogo.
              </div>
            )}

            {event && (
              <div className="mt-4 border-b border-line pb-4">
                <p className="font-bold">{event.title}</p>
                <p className="text-xs text-muted">
                  {formatDate(event.startsAt)} • {formatTime(event.startsAt)}
                </p>
              </div>
            )}

            <div className="mt-4 flex items-center justify-between">
              <h2 className="font-bold">Assentos selecionados ({selectedSeats.length})</h2>
              {selectedSeats.length > 0 && (
                <button className="text-xs font-semibold text-brand-600" onClick={() => setSelected(new Set())}>
                  Limpar todos
                </button>
              )}
            </div>
            <ul className="mt-3 grid gap-2">
              {selectedSeats.length === 0 && <li className="text-sm text-muted">Nenhum assento selecionado.</li>}
              {selectedSeats.map((s) => (
                <li key={s.id} className="flex items-center gap-3 rounded-xl bg-surface p-3">
                  <span className="grid size-10 place-items-center rounded-lg bg-brand-600 text-xs font-bold text-white">{s.label}</span>
                  <div className="flex-1 text-sm">
                    <p className="font-semibold">{map.sectors.find((x) => x.id === s.sectorId)?.name}</p>
                    <p className="text-xs text-muted">Assento {s.label}{s.kind === 'PCD' ? ' • PCD' : ''}</p>
                  </div>
                  <div className="text-right text-sm">
                    <p className="font-bold tabular">{formatMoney(priceOf(s))}</p>
                    <button aria-label={`Remover ${s.label}`} onClick={() => toggle(s)} className="text-muted hover:text-brand-600">
                      <Trash2 className="ml-auto size-3.5" />
                    </button>
                  </div>
                </li>
              ))}
            </ul>

            <dl className="mt-4 grid gap-1.5 rounded-xl bg-surface p-4 text-sm">
              <div className="flex justify-between"><dt className="text-muted">Subtotal</dt><dd className="tabular">{formatMoney(totals.subtotalCents)}</dd></div>
              <div className="flex justify-between"><dt className="text-muted">Taxa de conveniência (10%)</dt><dd className="tabular">{formatMoney(totals.feeCents)}</dd></div>
              <div className="mt-2 flex items-end justify-between border-t border-line pt-2">
                <dt className="font-bold">Total</dt>
                <dd className="text-2xl font-extrabold text-brand-600 tabular">{formatMoney(totals.totalCents)}</dd>
              </div>
            </dl>

            <Button
              size="lg"
              className="mt-4 w-full"
              disabled={selectedSeats.length === 0 || !!activeHere}
              loading={reserve.isPending}
              onClick={() => reserve.mutate()}
            >
              <Lock className="size-5" aria-hidden /> Garantir reserva e ir para o pagamento
            </Button>
          </Card>
        </aside>
      </div>
    </div>
  );
}
