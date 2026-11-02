import { useMutation, useQuery } from '@tanstack/react-query';
import { Accessibility, ArrowLeft, Banknote, CalendarSync, CheckCircle2, CreditCard, Loader2, Mail, Nfc, Phone, Printer, QrCode, Search, Ticket, UserPlus, UserRound, XCircle } from 'lucide-react';
import { QRCodeSVG } from 'qrcode.react';
import { useMemo, useRef, useState, type FormEvent } from 'react';
import { Navigate } from 'react-router-dom';
import { api } from '@/api';
import { ApiError, errorMessage } from '@/api/errors';
import type { AssignedEvent, BoxOfficeCustomer, Order, Reservation } from '@/api/types';
import { useAuth } from '@/auth/AuthProvider';
import { isSellerOnly } from '@/auth/roles';
import { Badge, Button, Card, ErrorState, EventCover, Field, Spinner, inputClass } from '@/components/ui';
import { isValidCpf, maskCpf, redactCpf } from '@/domain/cpf';
import { validateHolders } from '@/domain/purchaseRules';
import { isSelectable, type Seat } from '@/domain/seat';
import { cx, formatDateTime, formatMoney, formatTime, maskPhone } from '@/lib/format';
import { useOperator } from './OperatorProvider';

const MAX_SEATS = 6;
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
function SeatsStep({ eventId, customer, onReserved, onBack }: { eventId: string; customer: BoxOfficeCustomer; onReserved: (r: { reservation: Reservation; order: Order }) => void; onBack: () => void }) {
  // Sempre do servidor ao abrir: após uma venda, os lugares vendidos não podem aparecer como vagos.
  const map = useQuery({ queryKey: ['box-office', 'seats', eventId], queryFn: () => api.operator.boxOffice.getSeatMap(eventId), refetchInterval: 10_000, staleTime: 0, refetchOnMount: 'always' });
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
      <Card className="flex items-center gap-2 p-3 text-sm">
        <button type="button" onClick={onBack} className="grid size-8 shrink-0 place-items-center rounded-lg bg-surface hover:bg-surface-strong" aria-label="Voltar para o cliente"><ArrowLeft className="size-4" aria-hidden /></button>
        <span className="flex min-w-0 flex-1 items-center gap-2 font-bold"><UserRound className="size-4 shrink-0 text-brand-600" aria-hidden /><span className="truncate">{customer.name}</span></span>
        <span className="shrink-0 text-xs text-muted">{customer.phone}</span>
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

type Method = 'CREDIT_CARD' | 'PIX' | 'CASH';
type Phase = 'idle' | 'waiting' | 'processing';

const METHOD_INFO: Record<Method, { label: string; icon: typeof CreditCard }> = {
  CREDIT_CARD: { label: 'Crédito', icon: CreditCard },
  PIX: { label: 'PIX', icon: QrCode },
  CASH: { label: 'Dinheiro', icon: Banknote },
};
// Cartões de teste do simulador: final 0000 = recusado por saldo insuficiente.
const APPROVED_CARD = '4111 1111 1111 1111';
const DECLINED_CARD = '4111 1111 1111 0000';
const brl = (cents: number) => formatMoney(cents);
const parseBrl = (v: string) => Math.round(Number(v.replace(/\./g, '').replace(',', '.')) * 100) || 0;

/** Visor da maquininha: valor grande em fonte de display. */
function TerminalScreen({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <div className="rounded-2xl bg-navy p-4 text-white shadow-inner">
      <p className="text-[10px] font-bold tracking-[0.2em] text-emerald-300/80 uppercase">{title}</p>
      {children}
    </div>
  );
}

export interface PaidSale {
  order: Order;
  method: Method;
  installments: number;
  receivedCents?: number;
}

/** Etapa 3: titulares (CPF vinculado a cada ingresso) e cobrança simulada na maquininha. */
function PaymentStep({ customer, reservation, order, onPaid, onCancel }: {
  customer: BoxOfficeCustomer; reservation: Reservation; order: Order; onPaid: (p: PaidSale) => void; onCancel: () => void;
}) {
  const [holders, setHolders] = useState(() => order.items.map((it, i) => ({
    seatId: it.seatId, name: i === 0 ? customer.name : '', cpf: i === 0 ? maskCpf(customer.cpf) : '',
  })));
  const [method, setMethod] = useState<Method>('CREDIT_CARD');
  const [installments, setInstallments] = useState(1);
  const [received, setReceived] = useState('');
  const [phase, setPhase] = useState<Phase>('idle');
  const [declined, setDeclined] = useState<string>();
  const key = useRef(newKey());
  const issues = useMemo(() => validateHolders(holders), [holders]);
  const total = order.totals.totalCents;
  const receivedCents = parseBrl(received);
  const change = receivedCents - total;

  const pay = useMutation({
    mutationFn: (card?: string) => api.operator.boxOffice.pay(customer.id, {
      reservationId: reservation.id,
      method,
      installments: method === 'CREDIT_CARD' ? installments : 1,
      holders,
      card: method === 'CREDIT_CARD' && card ? { number: card, holderName: customer.name.toUpperCase(), expiry: '12/30', cvv: '000' } : undefined,
    }, key.current),
    onSuccess: (o) => {
      setPhase('idle');
      if (o.status === 'PAGO') onPaid({ order: o, method, installments, receivedCents: method === 'CASH' ? receivedCents : undefined });
      else {
        setDeclined(o.declineReason ?? 'Pagamento recusado');
        key.current = newKey(); // nova tentativa = nova chave
      }
    },
    onError: () => setPhase('idle'),
  });
  const giveUp = useMutation({ mutationFn: () => api.operator.boxOffice.giveUp(customer.id, reservation.id), onSuccess: onCancel });

  /** Simula o tempo da adquirente antes de enviar ao servidor. */
  function charge(card?: string) {
    setDeclined(undefined);
    setPhase('processing');
    setTimeout(() => pay.mutate(card), 1200);
  }

  const busy = phase === 'processing' || pay.isPending;
  const holdersOk = issues.length === 0;
  const pixCode = `00020126BR.GOV.BCB.PIX0136bolsopass-${order.code}5204000053039865406${(total / 100).toFixed(2)}5802BR5909BOLSOPASS6009SAOPAULO`;

  return (
    <div className="grid grid-cols-[minmax(0,1fr)] gap-4">
      <Card className="p-4">
        <p className="flex items-center justify-between text-sm"><b>{customer.name}</b><Badge tone="warn">Reservado até {formatTime(reservation.expiresAt)}</Badge></p>
        <ul className="mt-3 grid gap-1 text-sm">
          {order.items.map((it) => <li key={it.seatId} className="flex justify-between"><span>{it.sectorName} • {it.seatLabel}</span><span>{brl(it.priceCents)}</span></li>)}
        </ul>
        <p className="mt-2 flex justify-between border-t border-line pt-2 text-sm text-muted"><span>Taxa de serviço</span><span>{brl(order.totals.feeCents)}</span></p>
      </Card>

      <Card className="grid gap-3 p-4">
        <div>
          <p className="font-bold">CPF vinculado a cada ingresso</p>
          <p className="text-xs text-muted">O ingresso aparece na conta BolsoPass de quem tem este CPF, e a entrada é conferida por ele.</p>
        </div>
        {order.items.map((it, i) => {
          const err = (f: 'name' | 'cpf') => issues.find((x) => x.seatId === it.seatId && x.field === f)?.message;
          return (
            <div key={it.seatId} className="grid gap-2 rounded-xl bg-surface p-3">
              <p className="text-xs font-bold">{it.seatLabel}{i === 0 ? ' • cliente' : ''}</p>
              <Field label="Nome completo" error={holders[i]?.name ? err('name') : undefined}>
                <input className={inputClass} value={holders[i]?.name ?? ''} disabled={busy} onChange={(e) => setHolders((h) => h.map((x, j) => (j === i ? { ...x, name: e.target.value } : x)))} />
              </Field>
              <Field label="CPF" error={holders[i]?.cpf ? err('cpf') : undefined}>
                <input className={inputClass} inputMode="numeric" value={holders[i]?.cpf ?? ''} disabled={busy} onChange={(e) => setHolders((h) => h.map((x, j) => (j === i ? { ...x, cpf: maskCpf(e.target.value) } : x)))} placeholder="000.000.000-00" />
              </Field>
            </div>
          );
        })}
      </Card>

      <TerminalScreen title="Valor a cobrar">
        <p className="mt-1 font-mono text-4xl font-bold tabular text-emerald-300">{brl(total)}</p>
        <p className="mt-1 text-xs text-white/60">
          {method === 'CREDIT_CARD' && installments > 1 ? `${installments}x de ${brl(Math.ceil(total / installments))} sem juros` : `${order.items.length} ingresso(s) • pedido ${order.code}`}
        </p>
      </TerminalScreen>

      <div className="grid grid-cols-3 gap-2" role="radiogroup" aria-label="Forma de pagamento">
        {(Object.keys(METHOD_INFO) as Method[]).map((m) => {
          const { label, icon: Icon } = METHOD_INFO[m];
          return (
            <button key={m} type="button" role="radio" aria-checked={method === m} disabled={busy}
              onClick={() => { setMethod(m); setDeclined(undefined); }}
              className={cx('flex flex-col items-center gap-1 rounded-2xl border-2 py-3 text-sm font-bold transition',
                method === m ? 'border-brand-600 bg-brand-600 text-white' : 'border-transparent bg-white shadow-sm')}>
              <Icon className="size-6" aria-hidden /> {label}
            </button>
          );
        })}
      </div>

      {!holdersOk && <p className="text-xs font-medium text-brand-700">Preencha nome e CPF válido de cada titular para cobrar.</p>}

      {busy ? (
        <TerminalScreen title="Processando">
          <p className="mt-2 flex items-center gap-2 text-lg font-bold"><Loader2 className="size-5 animate-spin" aria-hidden />
            {method === 'CREDIT_CARD' ? 'Aguarde… não remova o cartão' : method === 'PIX' ? 'Confirmando PIX…' : 'Registrando pagamento…'}
          </p>
        </TerminalScreen>
      ) : method === 'CREDIT_CARD' ? (
        <Card className="grid gap-3 p-4">
          <Field label="Parcelas">
            <select className={inputClass} value={installments} onChange={(e) => setInstallments(Number(e.target.value))}>
              {[1, 2, 3, 4, 5, 6].map((n) => <option key={n} value={n}>{n}x de {brl(Math.ceil(total / n))}</option>)}
            </select>
          </Field>
          <div className="flex items-center gap-3 rounded-xl bg-surface p-3">
            <Nfc className="size-8 shrink-0 text-brand-600" aria-hidden />
            <p className="text-sm"><b>Insira, passe ou aproxime o cartão</b><br /><span className="text-xs text-muted">Simulação da maquininha</span></p>
          </div>
          <Button size="lg" disabled={!holdersOk} onClick={() => charge(APPROVED_CARD)}><CreditCard className="size-5" aria-hidden /> Simular cartão aproximado</Button>
          <Button variant="ghost" size="sm" disabled={!holdersOk} onClick={() => charge(DECLINED_CARD)}>Simular cartão sem saldo (recusa)</Button>
        </Card>
      ) : method === 'PIX' ? (
        <Card className="grid justify-items-center gap-3 p-4 text-center">
          <p className="text-sm font-bold">Peça para o cliente ler o QR no app do banco</p>
          <div className="rounded-xl bg-white p-3 shadow-sm"><QRCodeSVG value={pixCode} size={168} level="M" aria-label="QR Code PIX" /></div>
          <p className="flex items-center gap-1.5 text-xs text-muted"><Loader2 className="size-3.5 animate-spin" aria-hidden /> Aguardando pagamento de {brl(total)}…</p>
          <Button size="lg" className="w-full" disabled={!holdersOk} onClick={() => charge()}><QrCode className="size-5" aria-hidden /> Simular PIX recebido</Button>
        </Card>
      ) : (
        <Card className="grid gap-3 p-4">
          <Field label="Valor recebido (R$)">
            <input className={cx(inputClass, 'text-lg font-bold')} inputMode="decimal" value={received} placeholder="0,00"
              onChange={(e) => setReceived(e.target.value.replace(/[^\d,.]/g, ''))} />
          </Field>
          <div className="grid grid-cols-4 gap-2">
            {[['Exato', total], ['R$ 50', 5000], ['R$ 100', 10000], ['R$ 200', 20000]].map(([label, cents]) => (
              <button key={label} type="button" onClick={() => setReceived(((cents as number) / 100).toFixed(2).replace('.', ','))}
                className="rounded-lg bg-surface py-2 text-xs font-bold hover:bg-surface-strong">{label}</button>
            ))}
          </div>
          <TerminalScreen title="Troco">
            <p className={cx('mt-1 font-mono text-3xl font-bold tabular', change >= 0 ? 'text-emerald-300' : 'text-amber-300')}>
              {receivedCents === 0 ? '—' : change >= 0 ? brl(change) : `Faltam ${brl(-change)}`}
            </p>
          </TerminalScreen>
          <Button size="lg" disabled={!holdersOk || change < 0 || receivedCents === 0} onClick={() => charge()}>
            <Banknote className="size-5" aria-hidden /> Confirmar recebimento
          </Button>
        </Card>
      )}

      {declined && (
        <p role="alert" className="flex gap-2 rounded-xl bg-brand-50 p-3 text-sm font-medium text-brand-700">
          <XCircle className="size-5 shrink-0" aria-hidden /> {declined}. Tente outro cartão, mude para PIX ou dinheiro, ou desista para liberar os lugares.
        </p>
      )}
      {pay.error && <p role="alert" className="rounded-lg bg-brand-50 px-3 py-2 text-sm font-medium text-brand-700">{errorMessage(pay.error)}</p>}

      <Button variant="ghost" loading={giveUp.isPending} onClick={() => giveUp.mutate()} disabled={!declined || busy}
        title={declined ? undefined : 'Disponível após uma recusa; sem pagamento a reserva expira sozinha em 10 min'}>
        Desistir da compra
      </Button>
    </div>
  );
}

/** Etapa 4: comprovante, como o impresso pela maquininha. */
function Receipt({ sale, eventTitle, onNew }: { sale: PaidSale; eventTitle: string; onNew: () => void }) {
  const { order } = sale;
  const line = 'border-t border-dashed border-ink/30';
  const methodText =
    sale.method === 'CREDIT_CARD'
      ? `Crédito ${sale.installments}x • final ${order.payment?.last4 ?? '****'}`
      : sale.method === 'PIX' ? 'PIX' : 'Dinheiro';
  return (
    <div className="grid gap-4">
      <div role="status" className="flex items-center gap-3 rounded-2xl bg-ok p-4 text-white">
        <CheckCircle2 className="size-8 shrink-0" aria-hidden />
        <div><p className="text-lg font-extrabold">Venda concluída</p><p className="text-xs text-white/85">Pagamento aprovado • {methodText}</p></div>
      </div>

      <div className="mx-auto w-full max-w-sm bg-white px-5 py-6 font-mono text-[12px] leading-relaxed text-ink shadow-md" aria-label="Comprovante de venda">
        <p className="text-center text-base font-bold">BOLSOPASS</p>
        <p className="text-center">COMPROVANTE DE VENDA • BILHETERIA</p>
        <p className={cx('mt-2 pt-2', line)}>{eventTitle}</p>
        <p>{formatDateTime(order.paidAt ?? new Date().toISOString())}</p>
        <p>PEDIDO {order.code}</p>
        <div className={cx('mt-2 grid gap-2 pt-2', line)}>
          {order.items.map((it) => (
            <div key={it.seatId}>
              <p className="flex justify-between"><span>{it.sectorName} {it.seatLabel}</span><span>{brl(it.priceCents)}</span></p>
              <p className="text-ink/70">TITULAR {it.holderName?.toUpperCase()}</p>
              <p className="text-ink/70">CPF {redactCpf(it.holderCpf ?? '')} • {it.ticketId}</p>
            </div>
          ))}
        </div>
        <div className={cx('mt-2 pt-2', line)}>
          <p className="flex justify-between"><span>SUBTOTAL</span><span>{brl(order.totals.subtotalCents)}</span></p>
          <p className="flex justify-between"><span>TAXA DE SERVIÇO</span><span>{brl(order.totals.feeCents)}</span></p>
          <p className="flex justify-between text-sm font-bold"><span>TOTAL</span><span>{brl(order.totals.totalCents)}</span></p>
          <p className="flex justify-between"><span>PAGAMENTO</span><span>{methodText.toUpperCase()}</span></p>
          {sale.receivedCents !== undefined && (
            <>
              <p className="flex justify-between"><span>RECEBIDO</span><span>{brl(sale.receivedCents)}</span></p>
              <p className="flex justify-between font-bold"><span>TROCO</span><span>{brl(sale.receivedCents - order.totals.totalCents)}</span></p>
            </>
          )}
        </div>
        <p className={cx('mt-2 pt-2 text-center text-ink/70', line)}>Ingressos vinculados ao CPF de cada titular. Eles aparecem na conta BolsoPass desse CPF com QR dinâmico.</p>
      </div>

      <div className="grid grid-cols-2 gap-2">
        <Button variant="outline" onClick={() => window.print()}><Printer className="size-4" aria-hidden /> Imprimir</Button>
        <Button onClick={onNew}>Nova venda</Button>
      </div>
    </div>
  );
}

/** Lista de eventos para vender (a mesma do posto da bilheteria). */
function EventPicker({ currentId, onPick, onClose }: { currentId: string; onPick: (e: AssignedEvent['event']) => void; onClose: () => void }) {
  const events = useQuery({ queryKey: ['operator', 'events'], queryFn: api.operator.listAssignedEvents });
  return (
    <div className="fixed inset-0 z-40 flex items-end justify-center bg-navy/50 sm:items-center" onClick={onClose}>
      <div role="dialog" aria-label="Escolher evento para vender" className="max-h-[80vh] w-full max-w-xl overflow-y-auto rounded-t-2xl bg-white p-4 shadow-2xl sm:rounded-2xl" onClick={(e) => e.stopPropagation()}>
        <div className="flex items-center justify-between">
          <h2 className="text-lg font-bold">Escolher evento para vender</h2>
          <button type="button" onClick={onClose} className="rounded-lg p-1.5 hover:bg-surface" aria-label="Fechar"><XCircle className="size-5" /></button>
        </div>
        {events.isLoading && <Spinner label="Carregando eventos…" />}
        {events.error && <ErrorState message={errorMessage(events.error)} onRetry={() => void events.refetch()} />}
        <div className="mt-3 grid gap-2">
          {events.data?.map(({ event }) => {
            const current = event.id === currentId;
            return (
              <button key={event.id} type="button" onClick={() => onPick(event)}
                className={cx('flex items-center gap-3 rounded-xl border p-3 text-left', current ? 'border-brand-500 bg-brand-50' : 'border-line hover:bg-surface')}>
                <EventCover hue={event.hue} className="size-12 shrink-0 rounded-lg" />
                <span className="min-w-0 flex-1">
                  <b className="block truncate">{event.title}</b>
                  <span className="text-xs text-muted">{formatDateTime(event.startsAt)} • {event.venue}</span>
                </span>
                {current && <Badge tone="brand">Atual</Badge>}
              </button>
            );
          })}
          {events.data?.length === 0 && <p className="py-6 text-center text-sm text-muted">Nenhum evento com vendas abertas.</p>}
        </div>
      </div>
    </div>
  );
}

/** Venda no balcão da bilheteria: terminal tipo maquininha de cartão. */
export default function OperatorSalePage() {
  const { station, configure } = useOperator();
  const { user } = useAuth();
  const seller = isSellerOnly(user);
  // Evento da venda: começa no do posto e pode ser trocado aqui, sem mexer no posto de leitura da portaria.
  const [saleEvent, setSaleEvent] = useState<AssignedEvent['event']>();
  const [picking, setPicking] = useState(false);
  const [customer, setCustomer] = useState<BoxOfficeCustomer>();
  const [reserved, setReserved] = useState<{ reservation: Reservation; order: Order }>();
  const [paid, setPaid] = useState<PaidSale>();
  if (!station) return <Navigate to="/operator/setup" replace />;

  const event = saleEvent ?? station.event;
  const step = paid ? 3 : reserved ? 2 : customer ? 1 : 0;
  const restart = () => { setCustomer(undefined); setReserved(undefined); setPaid(undefined); };
  // Durante o pagamento os lugares estão reservados: troca de evento só antes ou depois.
  const canSwitch = step !== 2;

  function pick(e: AssignedEvent['event']) {
    setPicking(false);
    if (!station || e.id === event.id) return;
    setSaleEvent(e);
    // Terminal de venda: o evento escolhido vira o do terminal (fica salvo no aparelho).
    if (seller) configure(e, station.gate, { ...station.cache, eventId: e.id });
    setReserved(undefined);
    setPaid(undefined);
  }

  return (
    <div className="grid grid-cols-[minmax(0,1fr)] gap-4">
      <div className="flex items-center justify-between gap-2 rounded-2xl bg-navy px-4 py-3 text-white">
        <div className="min-w-0">
          <p className="text-[10px] font-bold tracking-[0.2em] text-emerald-300 uppercase">● Terminal de venda {user?.badge ?? ''}</p>
          <p className="truncate text-sm font-bold">{event.title}</p>
          <p className="truncate text-[11px] text-white/60">{formatDateTime(event.startsAt)} • {event.venue}</p>
        </div>
        <button type="button" onClick={() => setPicking(true)} disabled={!canSwitch}
          title={canSwitch ? undefined : 'Conclua ou desista do pagamento para trocar de evento'}
          className="flex shrink-0 items-center gap-1.5 rounded-xl bg-white/10 px-3 py-2 text-xs font-bold hover:bg-white/20 disabled:opacity-40">
          <CalendarSync className="size-4" aria-hidden /> Trocar evento
        </button>
      </div>
      <Steps step={step} />
      {step === 0 && <CustomerStep onConfirm={setCustomer} />}
      {step === 1 && customer && <SeatsStep eventId={event.id} customer={customer} onReserved={setReserved} onBack={() => setCustomer(undefined)} />}
      {step === 2 && customer && reserved && (
        <PaymentStep customer={customer} reservation={reserved.reservation} order={reserved.order} onPaid={setPaid} onCancel={() => setReserved(undefined)} />
      )}
      {step === 3 && paid && <Receipt sale={paid} eventTitle={event.title} onNew={restart} />}
      {picking && <EventPicker currentId={event.id} onPick={pick} onClose={() => setPicking(false)} />}
    </div>
  );
}
