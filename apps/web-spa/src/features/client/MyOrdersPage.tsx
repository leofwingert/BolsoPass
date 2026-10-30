import { useQuery } from '@tanstack/react-query';
import { CalendarDays, CheckCircle2, CreditCard, MapPin, Navigation, QrCode, RefreshCw, ShieldCheck, Ticket } from 'lucide-react';
import { useState } from 'react';
import { Link } from 'react-router-dom';
import { api } from '@/api';
import { errorMessage } from '@/api/errors';
import type { Order } from '@/api/types';
import { useAuth } from '@/auth/AuthProvider';
import { Badge, Button, Card, ErrorState, EventCover, Progress, Spinner } from '@/components/ui';
import { redactCpf } from '@/domain/cpf';
import { REFUND_WINDOW_DAYS } from '@/domain/purchaseRules';
import { cx, daysUntil, formatDate, formatDateTime, formatMoney } from '@/lib/format';

type Tab = 'todos' | 'proximos' | 'passados';
const isPast = (o: Order) => Date.parse(o.event.startsAt) < Date.now();

function OrderCard({ order }: { order: Order }) {
  const past = isPast(order);
  const firstTicket = order.items.find((i) => i.ticketId)?.ticketId;
  return (
    <Card className={cx('p-5', past && 'opacity-80')}>
      <div className="flex flex-wrap items-start justify-between gap-2">
        <div className="flex flex-wrap gap-2">
          {past ? (
            <Badge>Encerrado</Badge>
          ) : (
            <Badge tone="ok" icon={<CheckCircle2 className="size-3" />}>Pago & confirmado</Badge>
          )}
          <Badge tone="brand">{order.items[0]?.sectorName}</Badge>
        </div>
        <p className="text-right text-xs text-muted">
          <span className="font-mono font-semibold text-ink">Pedido #{order.code}</span>
          <br />
          {order.paidAt && formatDate(order.paidAt)}
        </p>
      </div>
      <div className="mt-4 grid gap-4 sm:grid-cols-[180px_1fr]">
        <EventCover hue={order.event.hue} className="h-28 rounded-xl">
          <span className="absolute bottom-2 left-2 rounded bg-brand-600 px-2 py-0.5 text-[11px] font-bold text-white">
            {order.items.length} {order.items.length > 1 ? 'INGRESSOS' : 'INGRESSO'}
          </span>
        </EventCover>
        <div>
          <h3 className={cx('text-xl font-bold', past && 'line-through decoration-1')}>{order.event.title}</h3>
          <p className="mt-1 flex items-center gap-1.5 text-sm"><CalendarDays className="size-4 text-brand-600" aria-hidden /> {formatDateTime(order.event.startsAt)}</p>
          <p className="flex items-center gap-1.5 text-sm text-muted"><MapPin className="size-4" aria-hidden /> {order.event.venue} • {order.event.city}</p>
          <p className="flex items-center gap-1.5 text-sm"><Ticket className="size-4" aria-hidden /> Assentos: {order.items.map((i) => i.seatLabel).join(', ')}</p>
          <div className="mt-3 flex items-center justify-between rounded-lg bg-surface px-3 py-2 text-sm">
            <span className="flex items-center gap-1.5 text-muted">
              <CreditCard className="size-4" aria-hidden />
              {order.payment?.method === 'PIX' ? 'PIX instantâneo' : `Cartão final ${order.payment?.last4 ?? '****'}`} ({order.payment?.installments ?? 1}x)
            </span>
            <b className="text-lg tabular">{formatMoney(order.totals.totalCents)}</b>
          </div>
        </div>
      </div>
      <div className="mt-4 flex flex-wrap gap-2">
        {!past && firstTicket ? (
          <Link to={`/tickets/${firstTicket}`}>
            <Button><QrCode className="size-4" aria-hidden /> Ver ingresso & QR dinâmico</Button>
          </Link>
        ) : (
          <p className="text-xs text-muted italic">Acesso aos portões expirado</p>
        )}
      </div>
    </Card>
  );
}

export default function MyOrdersPage() {
  const { user } = useAuth();
  const [tab, setTab] = useState<Tab>('todos');
  const orders = useQuery({ queryKey: ['orders'], queryFn: () => api.orders.listMine() });
  const tickets = useQuery({ queryKey: ['tickets'], queryFn: () => api.tickets.listMine() });

  if (orders.isLoading) return <Spinner />;
  if (orders.error || !orders.data) return <ErrorState message={errorMessage(orders.error)} onRetry={() => orders.refetch()} />;

  const all = orders.data;
  const upcoming = all.filter((o) => !isPast(o)).sort((a, b) => Date.parse(a.event.startsAt) - Date.parse(b.event.startsAt));
  const list = tab === 'proximos' ? upcoming : tab === 'passados' ? all.filter(isPast) : all;
  const activeTickets = tickets.data?.filter((t) => t.status === 'ATIVO' && Date.parse(t.event.startsAt) > Date.now()).length ?? 0;
  const next = upcoming[0];

  return (
    <div className="mx-auto max-w-7xl px-4 py-8">
      <Card className="mb-6 flex flex-wrap items-center justify-between gap-2 bg-surface px-5 py-3 text-sm">
        <span>Início / <b>Meus pedidos & ingressos</b></span>
        {user && (
          <span className="flex items-center gap-1.5 font-semibold">
            <ShieldCheck className="size-4 text-ok" aria-hidden /> Titular: {user.name.split(' ')[0]}
            {user.cpf && <Badge>CPF {redactCpf(user.cpf)}</Badge>}
          </span>
        )}
      </Card>

      <div className="grid gap-6 lg:grid-cols-[1fr_auto]">
        <div>
          <Badge tone="brand">Carteira de ingressos</Badge>
          <h1 className="mt-2 text-4xl font-extrabold tracking-tight">Meus pedidos & ingressos</h1>
          <p className="mt-2 max-w-xl text-sm text-muted">Acesse QR codes dinâmicos à prova de fraude e acompanhe seus eventos.</p>
        </div>
        <div className="grid grid-cols-3 gap-3">
          <Card className="w-36 p-4"><p className="text-xs text-muted">Pedidos pagos</p><p className="text-2xl font-bold">{all.length}</p><Progress value={100} tone="ok" className="mt-2" /></Card>
          <Card className="w-36 p-4"><p className="text-xs text-muted">Ingressos ativos</p><p className="text-2xl font-bold">{activeTickets}</p><Progress value={100} className="mt-2" /></Card>
          <Card className="w-36 p-4">
            <p className="text-xs text-muted">Próximo evento</p>
            <p className="text-lg font-bold text-brand-600">{next ? `em ${daysUntil(next.event.startsAt)} dias` : '—'}</p>
            <p className="truncate text-[11px] text-muted">{next?.event.title}</p>
          </Card>
        </div>
      </div>

      <Card className="my-6 flex flex-wrap gap-2 p-3" role="tablist">
        {([
          ['todos', `Todos os eventos (${all.length})`],
          ['proximos', `Próximos (${upcoming.length})`],
          ['passados', `Passados (${all.length - upcoming.length})`],
        ] as const).map(([value, label]) => (
          <button key={value} role="tab" aria-selected={tab === value} onClick={() => setTab(value)}
            className={cx('rounded-lg px-4 py-2 text-sm font-semibold', tab === value ? 'bg-brand-600 text-white' : 'bg-surface')}>
            {label}
          </button>
        ))}
      </Card>

      <div className="grid gap-6 lg:grid-cols-[1fr_360px]">
        <div className="grid content-start gap-4">
          {list.length === 0 && <Card className="p-8 text-center text-sm text-muted">Nada por aqui ainda.</Card>}
          {list.map((o) => <OrderCard key={o.id} order={o} />)}
        </div>
        <aside className="grid content-start gap-4">
          <Card className="p-5">
            <h2 className="text-lg font-bold">Segurança de acesso</h2>
            <ul className="mt-3 grid gap-3 text-sm">
              <li className="rounded-lg bg-surface p-3"><b className="flex items-center gap-1.5"><RefreshCw className="size-4 text-brand-600" aria-hidden /> QR rotativo e dinâmico</b><span className="text-xs text-muted">Muda a cada 15 segundos. Prints não passam na catraca.</span></li>
              <li className="rounded-lg bg-surface p-3"><b>Ingresso nominal</b><br /><span className="text-xs text-muted">Cada ingresso é vinculado ao CPF do titular.</span></li>
              <li className="rounded-lg bg-surface p-3"><b>Garantia CDC</b><br /><span className="text-xs text-muted">Direito de arrependimento em até {REFUND_WINDOW_DAYS} dias corridos após a compra.</span></li>
            </ul>
          </Card>
          <Card className="flex items-center gap-3 p-5 text-sm">
            <Navigation className="size-5 text-brand-600" aria-hidden />
            <span><b>Como chegar aos eventos</b><br /><span className="text-xs text-muted">Abra o ingresso para ver portão e rota.</span></span>
          </Card>
        </aside>
      </div>
    </div>
  );
}
