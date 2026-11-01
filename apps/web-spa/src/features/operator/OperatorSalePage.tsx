import { useMutation, useQuery } from '@tanstack/react-query';
import { Accessibility, CheckCircle2, CreditCard, Mail, Phone, QrCode, Search, ShoppingCart, Ticket, UserPlus, UserRound, XCircle } from 'lucide-react';
import { useMemo, useRef, useState, type FormEvent } from 'react';
import { Navigate } from 'react-router-dom';
import { api } from '@/api';
import { ApiError, errorMessage } from '@/api/errors';
import type { BoxOfficeCustomer, Order, PaymentMethod, Reservation } from '@/api/types';
import { Badge, Button, Card, ErrorState, Field, Spinner, inputClass } from '@/components/ui';
import { isValidCpf, maskCpf } from '@/domain/cpf';
import { validateHolders } from '@/domain/purchaseRules';
import { isSelectable, type Seat } from '@/domain/seat';
import { cx, formatMoney, formatTime } from '@/lib/format';
import { useOperator } from './OperatorProvider';

const MAX_SEATS = 6;
const digits = (v: string) => v.replace(/\D/g, '');
const maskCard = (v: string) => digits(v).slice(0, 19).replace(/(\d{4})(?=\d)/g, '$1 ');
const maskExpiry = (v: string) => digits(v).slice(0, 4).replace(/(\d{2})(\d)/, '$1/$2');
const maskPhone = (v: string) => {
  const d = digits(v).slice(0, 11);
  return d.length <= 10 ? d.replace(/(\d{2})(\d{0,4})(\d{0,4})/, '($1) $2-$3').replace(/[-\s]+$/, '') : d.replace(/(\d{2})(\d{5})(\d{0,4})/, '($1) $2-$3');
};
const newKey = () => crypto.randomUUID?.() ?? `${Date.now()}-${Math.random()}`;

const STEPS = ['Cliente', 'Lugares', 'Pagamento', 'Concluído'];

function Steps({ step }: { step: number }) {
  return (
    <ol className="flex items-center gap-1.5 text-[11px] font-semibold">
      {STEPS.map((s, i) => (
        <li key={s} className="flex flex-1 items-center gap-1.5">
          <span className={cx('grid size-6 shrink-0 place-items-center rounded-full text-xs', i <= step ? 'bg-brand-600 text-white' : 'bg-surface-strong')}>{i + 1}</span>
          <span className={cx('truncate', i === step ? 'text-ink' : 'text-muted')}>{s}</span>
        </li>
      ))}
    </ol>
  );
}

/** Etapa 1: busca o cliente pelo CPF (traz nome e telefone) ou faz o cadastro rápido. */
function CustomerStep({ onConfirm }: { onConfirm: (c: BoxOfficeCustomer) => void }) {
  const [cpf, setCpf] = useState('');
  const [newCustomer, setNewCustomer] = useState({ name: '', phone: '', email: '' });
  const find = useMutation({ mutationFn: (value: string) => api.operator.boxOffice.findCustomer(value) });
  const register = useMutation({
    mutationFn: () => api.operator.boxOffice.registerCustomer({ cpf, ...newCustomer }),
    onSuccess: onConfirm,
  });
  const notFound = find.error instanceof ApiError && find.error.status === 404;

  function search(e: FormEvent) {
    e.preventDefault();
    register.reset();
    find.mutate(cpf);
  }

  return (
    <div className="grid grid-cols-[minmax(0,1fr)] gap-4">
      <Card className="p-4">
        <form onSubmit={search} className="grid gap-3">
          <Field label="CPF do cliente" hint="O ingresso fica na carteira deste CPF">
            <input className={inputClass} inputMode="numeric" value={cpf} onChange={(e) => { setCpf(maskCpf(e.target.value)); find.reset(); }} placeholder="000.000.000-00" autoFocus />
          </Field>
          <Button type="submit" size="lg" disabled={!isValidCpf(cpf)} loading={find.isPending}>
            <Search className="size-5" aria-hidden /> Buscar cliente
          </Button>
          {cpf.length === 14 && !isValidCpf(cpf) && <p className="text-xs font-medium text-brand-700">CPF inválido. Confira os números.</p>}
        </form>
      </Card>

      {find.data && (
        <Card className="p-4">
          <p className="text-[11px] font-bold tracking-wider text-ok uppercase">Cliente encontrado</p>
          <p className="mt-1 text-2xl font-extrabold">{find.data.name}</p>
          <ul className="mt-2 grid gap-1 text-sm">
            <li className="flex items-center gap-2"><Phone className="size-4 text-muted" aria-hidden /> {find.data.phone || 'Sem telefone cadastrado'}</li>
            {find.data.email && <li className="flex items-center gap-2"><Mail className="size-4 text-muted" aria-hidden /> {find.data.email}</li>}
          </ul>
          <p className="mt-2 text-xs text-muted">Confirme nome e telefone com a pessoa antes de seguir.</p>
          <Button size="lg" className="mt-3 w-full" onClick={() => onConfirm(find.data!)}>
            <UserRound className="size-5" aria-hidden /> Confirmar cliente e escolher lugares
          </Button>
        </Card>
      )}

      {notFound && (
        <Card className="p-4">
          <p className="flex items-center gap-2 font-bold"><UserPlus className="size-5 text-brand-600" aria-hidden /> CPF sem cadastro</p>
          <p className="mt-1 text-xs text-muted">Faça o cadastro rápido para vender. O cliente poderá ver o ingresso no app depois.</p>
          <div className="mt-3 grid gap-3">
            <Field label="Nome completo"><input className={inputClass} value={newCustomer.name} onChange={(e) => setNewCustomer({ ...newCustomer, name: e.target.value })} /></Field>
            <Field label="Telefone"><input className={inputClass} inputMode="tel" value={newCustomer.phone} onChange={(e) => setNewCustomer({ ...newCustomer, phone: maskPhone(e.target.value) })} placeholder="(11) 99999-9999" /></Field>
            <Field label="E-mail" hint="Opcional"><input className={inputClass} type="email" value={newCustomer.email} onChange={(e) => setNewCustomer({ ...newCustomer, email: e.target.value })} /></Field>
            {register.error && <p role="alert" className="text-sm font-medium text-brand-700">{errorMessage(register.error)}</p>}
            <Button size="lg" loading={register.isPending} onClick={() => register.mutate()}>Cadastrar e continuar</Button>
          </div>
        </Card>
      )}
      {find.error && !notFound && <p role="alert" className="text-sm font-medium text-brand-700">{errorMessage(find.error)}</p>}
    </div>
  );
}

/** Etapa 2: lugares vagos do evento do posto. */
function SeatsStep({ eventId, customer, onReserved }: { eventId: string; customer: BoxOfficeCustomer; onReserved: (r: { reservation: Reservation; order: Order }) => void }) {
  const map = useQuery({ queryKey: ['box-office', 'seats', eventId], queryFn: () => api.operator.boxOffice.getSeatMap(eventId), refetchInterval: 10_000 });
  const [sectorId, setSectorId] = useState<string>();
  const [selected, setSelected] = useState<string[]>([]);
  const reserve = useMutation({
    mutationFn: () => api.operator.boxOffice.reserve(eventId, customer.id, selected),
    onSuccess: onReserved,
    onError: () => void map.refetch(),
  });

  if (map.isLoading) return <Spinner label="Carregando lugares vagos…" />;
  if (map.error || !map.data) return <ErrorState message={errorMessage(map.error)} onRetry={() => void map.refetch()} />;
  const sectors = map.data.sectors;
  const sector = sectors.find((s) => s.id === (sectorId ?? sectors[0]?.id));
  const seats = map.data.seats.filter((s) => s.sectorId === sector?.id);
  const free = (s: Seat) => isSelectable(s);
  const rows = new Map<string, Seat[]>();
  for (const s of seats) rows.set(s.row, [...(rows.get(s.row) ?? []), s]);
  const chosen = map.data.seats.filter((s) => selected.includes(s.id));
  const estimate = chosen.reduce((sum, s) => sum + (sectors.find((x) => x.id === s.sectorId)?.priceCents ?? 0), 0);

  function toggle(s: Seat) {
    setSelected((prev) => (prev.includes(s.id) ? prev.filter((x) => x !== s.id) : prev.length >= MAX_SEATS ? prev : [...prev, s.id]));
  }

  return (
    <div className="grid grid-cols-[minmax(0,1fr)] gap-4">
      <Card className="flex items-center justify-between gap-2 p-3 text-sm">
        <span className="flex items-center gap-2 font-bold"><UserRound className="size-4 text-brand-600" aria-hidden /> {customer.name}</span>
        <span className="text-xs text-muted">{customer.phone}</span>
      </Card>

      <div className="flex gap-2 overflow-x-auto" role="tablist" aria-label="Setores">
        {sectors.map((s) => {
          const vagos = map.data.seats.filter((x) => x.sectorId === s.id && free(x)).length;
          return (
            <button key={s.id} role="tab" aria-selected={s.id === sector?.id} onClick={() => setSectorId(s.id)}
              className={cx('shrink-0 rounded-xl px-3 py-2 text-left text-xs', s.id === sector?.id ? 'bg-navy text-white' : 'bg-white shadow-sm')}>
              <b className="block text-sm">{s.name}</b>
              {formatMoney(s.priceCents)} • {vagos} vagos
            </button>
          );
        })}
      </div>

      <Card className="p-3">
        <div className="flex flex-wrap gap-3 text-[11px] font-semibold">
          <span className="flex items-center gap-1"><span className="size-3 rounded bg-emerald-100" /> Vago</span>
          <span className="flex items-center gap-1"><span className="size-3 rounded bg-brand-600" /> Selecionado</span>
          <span className="flex items-center gap-1"><span className="size-3 rounded bg-surface-strong" /> Ocupado</span>
        </div>
        <div className="mt-3 grid gap-1.5 overflow-x-auto">
          {[...rows.entries()].map(([row, list]) => (
            <div key={row} className="flex items-center gap-1">
              <span className="w-7 shrink-0 text-right text-[10px] font-bold text-muted">{row}</span>
              {list.map((s) => {
                const isSel = selected.includes(s.id);
                const ok = free(s);
                return (
                  <button key={s.id} type="button" disabled={!ok && !isSel} onClick={() => toggle(s)}
                    aria-label={`Assento ${s.label}, ${isSel ? 'selecionado' : ok ? 'vago' : 'ocupado'}`} aria-pressed={isSel}
                    className={cx('grid size-8 shrink-0 place-items-center rounded-md text-[10px] font-bold',
                      isSel ? 'bg-brand-600 text-white' : ok ? 'bg-emerald-100 text-emerald-800' : 'bg-surface-strong text-muted/60')}>
                    {s.kind === 'PCD' ? <Accessibility className="size-3.5" /> : s.number}
                  </button>
                );
              })}
            </div>
          ))}
        </div>
      </Card>

      {reserve.error && <p role="alert" className="rounded-lg bg-brand-50 px-3 py-2 text-sm font-medium text-brand-700">{errorMessage(reserve.error)}</p>}
      <div className="sticky bottom-20 rounded-2xl bg-white p-3 shadow-lg">
        <p className="flex justify-between text-sm"><span>{chosen.length ? chosen.map((s) => s.label).join(', ') : 'Nenhum lugar escolhido'}</span><b>{formatMoney(estimate)}</b></p>
        <p className="text-[11px] text-muted">Até {MAX_SEATS} lugares por venda. Taxa de serviço calculada na próxima etapa.</p>
        <Button size="lg" className="mt-2 w-full" disabled={!chosen.length} loading={reserve.isPending} onClick={() => reserve.mutate()}>
          <Ticket className="size-5" aria-hidden /> Reservar {chosen.length || ''} lugar(es) por 10 min
        </Button>
      </div>
    </div>
  );
}

/** Etapa 3: titular de cada ingresso e pagamento por cartão ou PIX. */
function PaymentStep({ customer, reservation, order, onPaid, onCancel }: {
  customer: BoxOfficeCustomer; reservation: Reservation; order: Order; onPaid: (o: Order) => void; onCancel: () => void;
}) {
  const [holders, setHolders] = useState(() => order.items.map((it, i) => ({
    seatId: it.seatId, name: i === 0 ? customer.name : '', cpf: i === 0 ? maskCpf(customer.cpf) : '',
  })));
  const [method, setMethod] = useState<PaymentMethod>('PIX');
  const [card, setCard] = useState({ number: '', holderName: '', expiry: '', cvv: '' });
  const [declined, setDeclined] = useState<string>();
  const key = useRef(newKey());
  const issues = useMemo(() => validateHolders(holders), [holders]);
  const cardOk = method === 'PIX' || (digits(card.number).length >= 13 && card.expiry.length === 5 && card.cvv.length >= 3 && card.holderName.trim().length > 2);

  const pay = useMutation({
    mutationFn: () => api.operator.boxOffice.pay(customer.id, {
      reservationId: reservation.id, method, installments: 1, holders,
      card: method === 'CREDIT_CARD' ? card : undefined,
    }, key.current),
    onSuccess: (o) => {
      if (o.status === 'PAGO') onPaid(o);
      else {
        setDeclined(o.declineReason ?? 'Pagamento recusado');
        key.current = newKey(); // nova tentativa = nova chave
      }
    },
  });
  const giveUp = useMutation({ mutationFn: () => api.operator.boxOffice.giveUp(customer.id, reservation.id), onSuccess: onCancel });

  return (
    <div className="grid grid-cols-[minmax(0,1fr)] gap-4">
      <Card className="p-4">
        <p className="flex items-center justify-between text-sm"><b>{customer.name}</b><Badge tone="warn">Reservado até {formatTime(reservation.expiresAt)}</Badge></p>
        <ul className="mt-3 grid gap-1 text-sm">
          {order.items.map((it) => <li key={it.seatId} className="flex justify-between"><span>{it.sectorName} • {it.seatLabel}</span><span>{formatMoney(it.priceCents)}</span></li>)}
        </ul>
        <dl className="mt-2 grid gap-0.5 border-t border-line pt-2 text-sm">
          <div className="flex justify-between text-muted"><dt>Taxa de serviço</dt><dd>{formatMoney(order.totals.feeCents)}</dd></div>
          <div className="flex justify-between text-lg font-extrabold"><dt>Total</dt><dd>{formatMoney(order.totals.totalCents)}</dd></div>
        </dl>
      </Card>

      <Card className="grid gap-3 p-4">
        <p className="font-bold">Titulares dos ingressos</p>
        {order.items.map((it, i) => {
          const err = (f: 'name' | 'cpf') => issues.find((x) => x.seatId === it.seatId && x.field === f)?.message;
          return (
            <div key={it.seatId} className="grid gap-2 rounded-xl bg-surface p-3">
              <p className="text-xs font-bold">{it.seatLabel}{i === 0 ? ' • cliente' : ''}</p>
              <Field label="Nome completo" error={holders[i]?.name ? err('name') : undefined}>
                <input className={inputClass} value={holders[i]?.name ?? ''} onChange={(e) => setHolders((h) => h.map((x, j) => (j === i ? { ...x, name: e.target.value } : x)))} />
              </Field>
              <Field label="CPF" error={holders[i]?.cpf ? err('cpf') : undefined}>
                <input className={inputClass} inputMode="numeric" value={holders[i]?.cpf ?? ''} onChange={(e) => setHolders((h) => h.map((x, j) => (j === i ? { ...x, cpf: maskCpf(e.target.value) } : x)))} placeholder="000.000.000-00" />
              </Field>
            </div>
          );
        })}
      </Card>

      <Card className="grid gap-3 p-4">
        <p className="font-bold">Forma de pagamento</p>
        <div className="grid grid-cols-2 gap-2" role="radiogroup" aria-label="Forma de pagamento">
          {([['PIX', QrCode, 'PIX'], ['CREDIT_CARD', CreditCard, 'Cartão']] as const).map(([value, Icon, label]) => (
            <button key={value} type="button" role="radio" aria-checked={method === value} onClick={() => setMethod(value)}
              className={cx('flex items-center justify-center gap-2 rounded-xl border-2 p-3 font-bold', method === value ? 'border-brand-600 bg-white' : 'border-transparent bg-surface')}>
              <Icon className="size-5 text-brand-600" aria-hidden /> {label}
            </button>
          ))}
        </div>
        {method === 'PIX' ? (
          <p className="rounded-xl bg-surface p-3 text-xs text-muted">O cliente paga o PIX no próprio celular; a confirmação é automática (ambiente simulado).</p>
        ) : (
          <div className="grid grid-cols-2 gap-2">
            <Field label="Número do cartão" className="col-span-2" hint="Final 0000 simula recusa">
              <input className={inputClass} inputMode="numeric" value={card.number} onChange={(e) => setCard({ ...card, number: maskCard(e.target.value) })} placeholder="0000 0000 0000 0000" />
            </Field>
            <Field label="Validade"><input className={inputClass} inputMode="numeric" value={card.expiry} onChange={(e) => setCard({ ...card, expiry: maskExpiry(e.target.value) })} placeholder="MM/AA" /></Field>
            <Field label="CVV"><input className={inputClass} inputMode="numeric" value={card.cvv} onChange={(e) => setCard({ ...card, cvv: digits(e.target.value).slice(0, 4) })} /></Field>
            <Field label="Nome no cartão" className="col-span-2"><input className={inputClass} value={card.holderName} onChange={(e) => setCard({ ...card, holderName: e.target.value.toUpperCase() })} /></Field>
          </div>
        )}
      </Card>

      {declined && (
        <p role="alert" className="flex gap-2 rounded-xl bg-brand-50 p-3 text-sm font-medium text-brand-700">
          <XCircle className="size-5 shrink-0" aria-hidden /> {declined}. Tente outro cartão, mude para PIX ou desista para liberar os lugares.
        </p>
      )}
      {pay.error && <p role="alert" className="rounded-lg bg-brand-50 px-3 py-2 text-sm font-medium text-brand-700">{errorMessage(pay.error)}</p>}

      <div className="grid gap-2">
        <Button size="lg" disabled={issues.length > 0 || !cardOk} loading={pay.isPending} onClick={() => pay.mutate()}>
          <ShoppingCart className="size-5" aria-hidden /> Cobrar {formatMoney(order.totals.totalCents)}
        </Button>
        <Button variant="ghost" loading={giveUp.isPending} onClick={() => giveUp.mutate()} disabled={!declined}
          title={declined ? undefined : 'Disponível após uma recusa; sem pagamento a reserva expira sozinha em 10 min'}>
          Desistir da compra
        </Button>
      </div>
    </div>
  );
}

/** Venda no balcão da bilheteria. */
export default function OperatorSalePage() {
  const { station } = useOperator();
  const [customer, setCustomer] = useState<BoxOfficeCustomer>();
  const [reserved, setReserved] = useState<{ reservation: Reservation; order: Order }>();
  const [paid, setPaid] = useState<Order>();
  if (!station) return <Navigate to="/operator/setup" replace />;

  const step = paid ? 3 : reserved ? 2 : customer ? 1 : 0;
  const restart = () => { setCustomer(undefined); setReserved(undefined); setPaid(undefined); };

  return (
    <div className="grid grid-cols-[minmax(0,1fr)] gap-4">
      <p className="text-xs text-muted">Venda para <b className="text-ink">{station.event.title}</b></p>
      <Steps step={step} />
      {step === 0 && <CustomerStep onConfirm={setCustomer} />}
      {step === 1 && customer && <SeatsStep eventId={station.event.id} customer={customer} onReserved={setReserved} />}
      {step === 2 && customer && reserved && (
        <PaymentStep customer={customer} reservation={reserved.reservation} order={reserved.order} onPaid={setPaid} onCancel={() => setReserved(undefined)} />
      )}
      {step === 3 && paid && customer && (
        <Card className="p-5 text-center" role="status">
          <CheckCircle2 className="mx-auto size-14 text-ok" aria-hidden />
          <p className="mt-2 text-2xl font-extrabold">Venda concluída</p>
          <p className="text-sm text-muted">Pedido <span className="font-mono">{paid.code}</span> • {formatMoney(paid.totals.totalCents)}</p>
          <ul className="mt-3 grid gap-1 text-left text-sm">
            {paid.items.map((it) => <li key={it.seatId} className="flex justify-between rounded-lg bg-surface px-3 py-2"><span>{it.seatLabel} • {it.holderName}</span><span className="font-mono text-xs">{it.ticketId}</span></li>)}
          </ul>
          <p className="mt-3 text-xs text-muted">Os ingressos já estão na carteira de {customer.name} no app BolsoPass, com QR dinâmico.</p>
          <Button size="lg" className="mt-4 w-full" onClick={restart}>Nova venda</Button>
        </Card>
      )}
    </div>
  );
}
