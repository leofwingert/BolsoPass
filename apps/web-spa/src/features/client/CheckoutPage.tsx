import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  AlertTriangle,
  CalendarDays,
  CreditCard,
  Lock,
  MapPin,
  QrCode,
  RotateCcw,
  ShieldCheck,
  Timer,
  XCircle,
} from 'lucide-react';
import { useEffect, useMemo, useRef, useState, type FormEvent } from 'react';
import { Link, Navigate, useNavigate, useParams } from 'react-router-dom';
import { api } from '@/api';
import { ApiError, errorMessage } from '@/api/errors';
import { topics } from '@/api/realtime';
import type { Order, PaymentMethod } from '@/api/types';
import { useAuth } from '@/auth/AuthProvider';
import { Badge, Button, Card, ErrorState, EventCover, Field, Spinner, inputClass } from '@/components/ui';
import { maskCpf } from '@/domain/cpf';
import { ORDER_STATUS_LABEL, canSubmitPayment } from '@/domain/order';
import { installmentCents } from '@/domain/pricing';
import { validateHolders, type Holder, type HolderIssue } from '@/domain/purchaseRules';
import { clockOffset, formatCountdown } from '@/domain/reservation';
import { useCountdown } from '@/hooks/useCountdown';
import { useRealtime } from '@/hooks/useRealtime';
import { beep } from '@/lib/beep';
import { cx, formatDateTime, formatMoney } from '@/lib/format';
import { clearIdempotencyKey, getOrCreateIdempotencyKey } from '@/lib/idempotency';
import { useActiveReservation } from './activeReservation';

const digits = (v: string) => v.replace(/\D/g, '');
const maskCard = (v: string) => digits(v).slice(0, 19).replace(/(\d{4})(?=\d)/g, '$1 ');
const maskExpiry = (v: string) => digits(v).slice(0, 4).replace(/(\d{2})(\d)/, '$1/$2');

function cardErrors(card: { number: string; name: string; expiry: string; cvv: string }) {
  const errs: Record<string, string> = {};
  const n = digits(card.number);
  if (n.length < 13 || n.length > 19) errs.number = 'Número do cartão inválido';
  if (card.name.trim().length < 3) errs.name = 'Informe o nome impresso';
  const [mm, yy] = card.expiry.split('/').map(Number);
  const now = new Date();
  const expired = !mm || !yy || mm > 12 || 2000 + yy < now.getFullYear() || (2000 + yy === now.getFullYear() && mm < now.getMonth() + 1);
  if (expired) errs.expiry = 'Validade inválida';
  if (!/^\d{3,4}$/.test(card.cvv)) errs.cvv = 'CVV inválido';
  return errs;
}

/** Tela final quando a reserva não pode mais ser paga. */
function Closed({ order, expired }: { order: Order; expired: boolean }) {
  return (
    <div className="grid min-h-[60vh] place-items-center px-4">
      <Card className="max-w-md p-8 text-center">
        <XCircle className="mx-auto size-12 text-brand-600" aria-hidden />
        <h1 className="mt-3 text-xl font-bold">{expired ? 'Sua reserva expirou' : `Pedido ${ORDER_STATUS_LABEL[order.status].toLowerCase()}`}</h1>
        <p className="mt-2 text-sm text-muted">
          {expired
            ? 'Os 10 minutos acabaram e os assentos voltaram ao catálogo. Nenhuma cobrança foi feita.'
            : 'Os assentos foram liberados. Nenhuma cobrança foi feita.'}
        </p>
        <Link to={`/events/${order.event.id}/seats`}>
          <Button className="mt-5">
            <RotateCcw className="size-4" aria-hidden /> Escolher assentos novamente
          </Button>
        </Link>
      </Card>
    </div>
  );
}

export default function CheckoutPage() {
  const { reservationId = '' } = useParams();
  const { user } = useAuth();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const { clear } = useActiveReservation();
  const key = ['reservation', reservationId];

  const { data, isLoading, error, refetch, dataUpdatedAt } = useQuery({
    queryKey: key,
    queryFn: () => api.orders.getReservation(reservationId),
    staleTime: 0,
  });
  const order = data?.order;
  const { data: event } = useQuery({
    queryKey: ['event', order?.event.id],
    queryFn: () => api.catalog.getEvent(order!.event.id),
    enabled: !!order,
  });

  // Cronômetro pelo relógio do servidor.
  const offset = useMemo(
    () => (data ? clockOffset(data.reservation.serverNow, dataUpdatedAt) : 0),
    [data, dataUpdatedAt],
  );
  const { ms, phase } = useCountdown(data?.reservation.expiresAt, offset);

  // Status vindo do servidor (ex.: EXPIRADO pelo job de expiração).
  useRealtime(order ? topics.order(order.id) : undefined, () => {
    queryClient.invalidateQueries({ queryKey: key });
  });

  // Aviso sonoro ao entrar nos 2 minutos finais.
  const warned = useRef(false);
  useEffect(() => {
    if (phase === 'warning' && !warned.current) {
      warned.current = true;
      beep();
    }
  }, [phase]);

  const [holders, setHolders] = useState<Holder[]>([]);
  useEffect(() => {
    if (!order || holders.length) return;
    setHolders(
      order.items.map((it, i) => ({
        seatId: it.seatId,
        name: it.holderName ?? (i === 0 ? (user?.name ?? '') : ''),
        cpf: maskCpf(it.holderCpf ?? (i === 0 ? (user?.cpf ?? '') : '')),
      })),
    );
  }, [order, user, holders.length]);

  const [method, setMethod] = useState<PaymentMethod>('CREDIT_CARD');
  const [card, setCard] = useState({ number: '', name: '', expiry: '', cvv: '' });
  const [installments, setInstallments] = useState(1);
  const [holderIssues, setHolderIssues] = useState<HolderIssue[]>([]);
  const [cardIssues, setCardIssues] = useState<Record<string, string>>({});

  const maxInstallments = useMemo(() => {
    if (!event || !order) return 1;
    const sectorIds = new Set(event.sectors.filter((s) => order.items.some((i) => i.sectorName === s.name)).map((s) => s.id));
    const lots = event.lots.filter((l) => sectorIds.has(l.sectorId));
    return lots.length ? Math.min(...lots.map((l) => l.maxInstallments)) : 1;
  }, [event, order]);

  const scope = `checkout:${reservationId}`;
  const pay = useMutation({
    mutationFn: () =>
      api.orders.pay(
        {
          reservationId,
          method,
          installments: method === 'PIX' ? 1 : installments,
          holders: holders.map((h) => ({ ...h, name: h.name.trim() })),
          card:
            method === 'CREDIT_CARD'
              ? { number: digits(card.number), holderName: card.name, expiry: card.expiry, cvv: card.cvv }
              : undefined,
        },
        // Mesma chave em caso de queda de rede: o servidor não cobra duas vezes.
        getOrCreateIdempotencyKey(scope),
      ),
    onSuccess: (o) => {
      clearIdempotencyKey(scope);
      queryClient.setQueryData(key, (old: typeof data) => (old ? { ...old, order: o } : old));
      if (o.status === 'PAGO') {
        clear();
        queryClient.invalidateQueries({ queryKey: ['orders'] });
        queryClient.invalidateQueries({ queryKey: ['tickets'] });
        const first = o.items.find((i) => i.ticketId)?.ticketId;
        navigate(first ? `/tickets/${first}?novo=1` : '/my-orders', { replace: true });
      }
    },
    onError: (err) => {
      // Resposta do servidor = tentativa encerrada. Erro de rede = mantém a chave para repetir.
      if (err instanceof ApiError) clearIdempotencyKey(scope);
      if (err instanceof ApiError && err.status === 410) refetch();
    },
  });

  const giveUp = useMutation({
    mutationFn: () => api.orders.giveUp(reservationId),
    onSuccess: () => {
      clear();
      refetch();
    },
  });

  if (isLoading) return <Spinner label="Carregando sua reserva…" />;
  if (error || !data || !order) return <ErrorState message={errorMessage(error)} onRetry={() => refetch()} />;
  if (order.status === 'PAGO') {
    const first = order.items.find((i) => i.ticketId)?.ticketId;
    return <Navigate to={first ? `/tickets/${first}` : '/my-orders'} replace />;
  }
  if (order.status === 'CANCELADO') return <Closed order={order} expired={false} />;
  if (order.status === 'EXPIRADO' || phase === 'expired') return <Closed order={order} expired />;

  const canPay = canSubmitPayment(order.status, ms);
  const issueFor = (seatId: string, field: 'name' | 'cpf') =>
    holderIssues.find((i) => i.seatId === seatId && i.field === field)?.message;

  function submit(e: FormEvent) {
    e.preventDefault();
    const hi = validateHolders(holders);
    const ci = method === 'CREDIT_CARD' ? cardErrors(card) : {};
    setHolderIssues(hi);
    setCardIssues(ci);
    if (hi.length || Object.keys(ci).length) return;
    pay.mutate();
  }

  function setHolder(i: number, patch: Partial<Holder>) {
    setHolders((prev) => prev.map((h, idx) => (idx === i ? { ...h, ...patch } : h)));
  }

  return (
    <div>
      <div
        className={cx(
          'border-b-2',
          phase === 'warning' ? 'border-brand-600 bg-brand-600 text-white' : 'border-brand-600 bg-brand-50',
        )}
        role="timer"
        aria-live={phase === 'warning' ? 'assertive' : 'off'}
      >
        <div className="mx-auto flex max-w-7xl flex-wrap items-center justify-between gap-3 px-4 py-3">
          <p className="text-sm">
            <b className={phase === 'warning' ? '' : 'text-brand-700'}>
              {phase === 'warning' ? 'ÚLTIMOS MINUTOS: ' : 'AVISO DE BLOQUEIO ATIVO: '}
            </b>
            seus assentos estão travados para você até o cronômetro zerar.
          </p>
          <span className={cx('flex items-center gap-2 rounded-lg px-3 py-1.5 text-2xl font-extrabold tabular', phase === 'warning' ? 'bg-white text-brand-700' : 'bg-white text-brand-600 shadow-sm')}>
            <Timer className="size-5" aria-hidden /> {formatCountdown(ms)}
            <span className="text-xs font-medium text-muted">min restantes</span>
          </span>
        </div>
      </div>

      <form onSubmit={submit} className="mx-auto grid max-w-7xl gap-6 px-4 py-8 lg:grid-cols-[1fr_400px]">
        <div className="grid content-start gap-6">
          {order.status === 'RECUSADO' && (
            <Card role="alert" className="border-brand-200 bg-brand-50 p-5">
              <p className="flex items-center gap-2 font-bold text-brand-700">
                <AlertTriangle className="size-5" aria-hidden /> {order.declineReason ?? 'Pagamento recusado'}
              </p>
              <p className="mt-1 text-sm text-brand-800">
                Seus assentos continuam travados até o fim do cronômetro. Tente outro cartão ou PIX.
              </p>
              <Button type="button" variant="outline" size="sm" className="mt-3" loading={giveUp.isPending} onClick={() => giveUp.mutate()}>
                Desistir da compra e liberar os assentos
              </Button>
            </Card>
          )}

          <Card className="p-6">
            <h2 className="flex items-center gap-3 text-xl font-bold">
              <span className="grid size-7 place-items-center rounded-full bg-brand-600 text-sm text-white">1</span>
              Dados do comprador
            </h2>
            <dl className="mt-4 grid gap-3 text-sm sm:grid-cols-2">
              <div className="rounded-lg bg-surface px-4 py-3"><dt className="text-xs text-muted">Nome</dt><dd className="font-semibold">{user?.name}</dd></div>
              <div className="rounded-lg bg-surface px-4 py-3"><dt className="text-xs text-muted">E-mail para os ingressos</dt><dd className="font-semibold">{user?.email}</dd></div>
            </dl>
          </Card>

          <Card className="p-6">
            <div className="flex flex-wrap items-center justify-between gap-2">
              <h2 className="flex items-center gap-3 text-xl font-bold">
                <span className="grid size-7 place-items-center rounded-full bg-brand-600 text-sm text-white">2</span>
                Identificação nominal dos ingressos
              </h2>
              <Badge tone="brand" icon={<Lock className="size-3" />}>Bloqueio {order.items.map((i) => i.seatLabel).join(' / ')}</Badge>
            </div>
            <p className="mt-2 text-sm text-muted">
              Por regra antifraude, cada ingresso é vinculado ao nome e CPF de quem vai entrar.
            </p>
            <div className="mt-4 grid gap-3">
              {order.items.map((item, i) => (
                <div key={item.seatId} className="rounded-xl bg-surface p-4">
                  <div className="mb-3 flex items-center justify-between gap-2 text-sm">
                    <span>
                      <Badge tone="brand">Assento {item.seatLabel}</Badge>{' '}
                      <span className="font-semibold">{item.sectorName}</span>
                    </span>
                    {i > 0 && user?.cpf && (
                      <span className="text-xs text-muted">Acompanhante</span>
                    )}
                  </div>
                  <div className="grid gap-3 sm:grid-cols-2">
                    <Field label="Nome completo" error={issueFor(item.seatId, 'name')}>
                      <input className={inputClass} value={holders[i]?.name ?? ''} onChange={(e) => setHolder(i, { name: e.target.value })} autoComplete="name" />
                    </Field>
                    <Field label="CPF" error={issueFor(item.seatId, 'cpf')}>
                      <input className={inputClass} value={holders[i]?.cpf ?? ''} inputMode="numeric" onChange={(e) => setHolder(i, { cpf: maskCpf(e.target.value) })} placeholder="000.000.000-00" />
                    </Field>
                  </div>
                </div>
              ))}
            </div>
          </Card>

          <Card className="p-6">
            <h2 className="flex items-center gap-3 text-xl font-bold">
              <span className="grid size-7 place-items-center rounded-full bg-brand-600 text-sm text-white">3</span>
              Forma de pagamento
            </h2>
            <div className="mt-4 grid gap-3 sm:grid-cols-2" role="radiogroup" aria-label="Forma de pagamento">
              {([
                ['CREDIT_CARD', CreditCard, 'Cartão de crédito', `Até ${maxInstallments}x`],
                ['PIX', QrCode, 'PIX instantâneo', 'Aprovação imediata'],
              ] as const).map(([value, Icon, title, sub]) => (
                <button
                  type="button"
                  role="radio"
                  aria-checked={method === value}
                  key={value}
                  onClick={() => setMethod(value)}
                  className={cx('flex items-center justify-center gap-3 rounded-xl border-2 p-4 text-left', method === value ? 'border-brand-600 bg-white' : 'border-transparent bg-surface')}
                >
                  <Icon className="size-6 text-brand-600" aria-hidden />
                  <span><b className="block">{title}</b><span className="text-xs text-muted">{sub}</span></span>
                </button>
              ))}
            </div>

            {method === 'CREDIT_CARD' ? (
              <div className="mt-5 grid gap-4 sm:grid-cols-3">
                <Field label="Número do cartão" className="sm:col-span-3" error={cardIssues.number} hint="Ambiente de testes: final 0000 simula recusa">
                  <input className={inputClass} inputMode="numeric" autoComplete="cc-number" value={card.number} onChange={(e) => setCard({ ...card, number: maskCard(e.target.value) })} placeholder="0000 0000 0000 0000" />
                </Field>
                <Field label="Validade" error={cardIssues.expiry}>
                  <input className={inputClass} inputMode="numeric" autoComplete="cc-exp" value={card.expiry} onChange={(e) => setCard({ ...card, expiry: maskExpiry(e.target.value) })} placeholder="MM/AA" />
                </Field>
                <Field label="CVV" error={cardIssues.cvv}>
                  <input className={inputClass} inputMode="numeric" autoComplete="cc-csc" value={card.cvv} onChange={(e) => setCard({ ...card, cvv: digits(e.target.value).slice(0, 4) })} placeholder="123" />
                </Field>
                <Field label="Parcelas">
                  <select className={inputClass} value={installments} onChange={(e) => setInstallments(Number(e.target.value))}>
                    {Array.from({ length: maxInstallments }, (_, i) => i + 1).map((n) => (
                      <option key={n} value={n}>
                        {n}x de {formatMoney(installmentCents(order.totals.totalCents, n))} sem juros
                      </option>
                    ))}
                  </select>
                </Field>
                <Field label="Nome impresso no cartão" className="sm:col-span-3" error={cardIssues.name}>
                  <input className={inputClass} autoComplete="cc-name" value={card.name} onChange={(e) => setCard({ ...card, name: e.target.value.toUpperCase() })} />
                </Field>
              </div>
            ) : (
              <p className="mt-5 rounded-xl bg-surface p-4 text-sm text-muted">
                O pagamento via PIX é confirmado na hora pelo simulador do gateway.
              </p>
            )}
          </Card>
        </div>

        <aside className="grid content-start gap-4">
          <Card className="overflow-hidden lg:sticky lg:top-24">
            <EventCover hue={order.event.hue} className="h-28">
              <Badge className="absolute top-3 right-3 bg-white text-amber-700">● Reservado</Badge>
            </EventCover>
            <div className="p-5">
              <h2 className="text-xl font-bold">{order.event.title}</h2>
              <p className="mt-1 flex items-center gap-1.5 text-xs text-muted"><CalendarDays className="size-3.5" aria-hidden /> {formatDateTime(order.event.startsAt)}</p>
              <p className="flex items-center gap-1.5 text-xs text-muted"><MapPin className="size-3.5" aria-hidden /> {order.event.venue}, {order.event.city}</p>

              <ul className="mt-4 grid gap-1.5 rounded-xl bg-surface p-3 text-sm">
                {order.items.map((i) => (
                  <li key={i.seatId} className="flex justify-between">
                    <span>{i.sectorName} • <b>{i.seatLabel}</b></span>
                    <span className="tabular">{formatMoney(i.priceCents)}</span>
                  </li>
                ))}
              </ul>
              <dl className="mt-4 grid gap-1.5 text-sm">
                <div className="flex justify-between"><dt className="text-muted">Subtotal</dt><dd className="tabular">{formatMoney(order.totals.subtotalCents)}</dd></div>
                <div className="flex justify-between"><dt className="text-muted">Taxa de conveniência (10%)</dt><dd className="tabular">{formatMoney(order.totals.feeCents)}</dd></div>
                <div className="mt-2 flex items-end justify-between border-t border-line pt-3">
                  <dt className="text-lg font-bold">Total final</dt>
                  <dd className="text-3xl font-extrabold text-brand-600 tabular">{formatMoney(order.totals.totalCents)}</dd>
                </div>
              </dl>

              {pay.error && (
                <p role="alert" className="mt-3 rounded-lg bg-brand-50 px-3 py-2 text-sm font-medium text-brand-700">
                  {errorMessage(pay.error)}
                </p>
              )}
              <Button type="submit" size="lg" className="mt-4 w-full" disabled={!canPay} loading={pay.isPending}>
                <Lock className="size-5" aria-hidden /> Confirmar e pagar {formatMoney(order.totals.totalCents)}
              </Button>
              <div className="mt-4 grid grid-cols-3 gap-2 text-center text-[11px]">
                <span className="rounded-lg bg-surface p-2"><ShieldCheck className="mx-auto size-4 text-ok" aria-hidden /> Conexão segura</span>
                <span className="rounded-lg bg-surface p-2"><Lock className="mx-auto size-4 text-brand-600" aria-hidden /> Anti-fraude</span>
                <span className="rounded-lg bg-surface p-2"><RotateCcw className="mx-auto size-4" aria-hidden /> Cancela em 7 dias</span>
              </div>
            </div>
          </Card>
        </aside>
      </form>
    </div>
  );
}
