import { useQuery } from '@tanstack/react-query';
import { QRCodeSVG } from 'qrcode.react';
import {
  Ban,
  CalendarDays,
  Check,
  Clock,
  Copy,
  CreditCard,
  DoorOpen,
  Download,
  MapPin,
  RefreshCw,
  Send,
  ShieldCheck,
  Wallet,
} from 'lucide-react';
import { useEffect, useState } from 'react';
import { Link, useParams, useSearchParams } from 'react-router-dom';
import { api } from '@/api';
import { errorMessage } from '@/api/errors';
import type { Ticket } from '@/api/types';
import { Badge, Button, Card, ErrorState, EventCover, Progress, Spinner } from '@/components/ui';
import { QR_ROTATION_MS, msUntilRotation } from '@/domain/ticketQr';
import { cx, formatDateTime, formatMoney, formatTime } from '@/lib/format';

/** QR que muda a cada 15 s (print de tela não passa na catraca). */
function DynamicQr({ ticket }: { ticket: Ticket }) {
  const active = ticket.status === 'ATIVO';
  const { data, error } = useQuery({
    queryKey: ['qr', ticket.id],
    queryFn: () => api.tickets.getQr(ticket.id),
    enabled: active,
    staleTime: 0,
    refetchInterval: () => msUntilRotation(Date.now()) + 150,
    refetchIntervalInBackground: false,
  });
  const [left, setLeft] = useState(msUntilRotation(Date.now()));
  useEffect(() => {
    const id = setInterval(() => setLeft(msUntilRotation(Date.now())), 250);
    return () => clearInterval(id);
  }, []);

  if (!active) {
    return (
      <div className="grid h-full place-items-center rounded-2xl bg-surface p-6 text-center text-sm text-muted">
        <div>
          <Ban className="mx-auto size-8" aria-hidden />
          <p className="mt-2 font-semibold">Ingresso {ticket.status.toLowerCase()}</p>
        </div>
      </div>
    );
  }
  return (
    <div className="rounded-2xl bg-surface p-4 text-center">
      <div className="mx-auto w-fit rounded-xl bg-white p-3 shadow-sm">
        {data ? (
          <QRCodeSVG value={data.qr} size={176} level="M" aria-label="QR Code do ingresso" />
        ) : error ? (
          <p className="grid size-44 place-items-center text-xs text-brand-700">{errorMessage(error)}</p>
        ) : (
          <div className="size-44 animate-pulse rounded bg-surface" />
        )}
      </div>
      <p className="mt-3 flex items-center justify-center gap-1.5 text-xs font-semibold">
        <RefreshCw className="size-3.5 text-brand-600" aria-hidden /> Código muda em: {Math.ceil(left / 1000)}s
      </p>
      <Progress value={(left / QR_ROTATION_MS) * 100} className="mx-auto mt-2 max-w-40" />
      <p className="mt-2 text-[11px] text-muted">Proteção anti-print ativa. Apresente esta tela na catraca.</p>
    </div>
  );
}

export default function TicketPage() {
  const { ticketId = '' } = useParams();
  const [params] = useSearchParams();
  const justPaid = params.get('novo') === '1';
  const [copied, setCopied] = useState(false);

  const { data: ticket, isLoading, error, refetch } = useQuery({
    queryKey: ['ticket', ticketId],
    queryFn: () => api.tickets.get(ticketId),
  });
  const { data: order } = useQuery({
    queryKey: ['order', ticket?.orderId],
    queryFn: () => api.orders.getOrder(ticket!.orderId),
    enabled: !!ticket,
  });

  if (isLoading) return <Spinner />;
  if (error || !ticket) return <ErrorState message={errorMessage(error)} onRetry={() => refetch()} />;

  const siblings = order?.items.filter((i) => i.ticketId) ?? [];
  const opensAt = new Date(Date.parse(ticket.event.startsAt) - 2 * 3_600_000).toISOString();

  return (
    <div className="mx-auto max-w-7xl px-4 py-8">
      {justPaid && order && (
        <Card className="mb-6 flex flex-wrap items-center gap-4 p-5">
          <span className="grid size-12 place-items-center rounded-xl bg-ok-soft text-ok">
            <Check className="size-7" aria-hidden />
          </span>
          <div>
            <h1 className="flex items-center gap-2 text-xl font-bold">
              Pagamento aprovado com sucesso! <Badge tone="ok">Pago</Badge>
            </h1>
            <p className="text-sm text-muted">
              Pedido <b className="text-ink">#{order.code}</b> • Ingressos emitidos e enviados para {ticket.holderEmail}.
            </p>
          </div>
        </Card>
      )}

      <div className="grid gap-6 lg:grid-cols-[1fr_360px]">
        <div className="grid content-start gap-6">
          <Card className="overflow-hidden">
            <EventCover hue={ticket.event.hue} className="text-white">
              <div className="relative flex flex-wrap items-end justify-between gap-3 px-6 pt-16 pb-5">
                <div>
                  <Badge className="bg-brand-600 text-white">{ticket.sectorName}</Badge>
                  <h2 className="mt-2 text-2xl font-bold">{ticket.event.title}</h2>
                  <p className="flex items-center gap-1.5 text-sm opacity-90">
                    <CalendarDays className="size-4" aria-hidden /> {formatDateTime(ticket.event.startsAt)}
                  </p>
                </div>
                <p className="text-right text-sm">
                  <span className="block text-[11px] tracking-wider uppercase opacity-80">Arena</span>
                  <b>{ticket.event.venue}</b>
                </p>
              </div>
            </EventCover>

            {siblings.length > 1 && (
              <div className="flex gap-2 overflow-x-auto border-b border-line px-6 py-3">
                {siblings.map((s) => (
                  <Link
                    key={s.ticketId}
                    to={`/tickets/${s.ticketId}${justPaid ? '?novo=1' : ''}`}
                    className={cx('rounded-lg px-3 py-1.5 text-sm font-semibold', s.ticketId === ticket.id ? 'bg-brand-600 text-white' : 'bg-surface')}
                  >
                    Assento {s.seatLabel}
                  </Link>
                ))}
              </div>
            )}

            <div className="grid gap-6 p-6 md:grid-cols-[1fr_230px]">
              <div className="grid content-start gap-3">
                <div className="grid gap-3 sm:grid-cols-2">
                  <div className="rounded-xl bg-surface p-4">
                    <p className="text-[11px] font-bold tracking-wider text-muted uppercase">Titular do ingresso</p>
                    <p className="font-bold">{ticket.holderName}</p>
                    <p className="text-xs text-muted">{ticket.holderEmail}</p>
                  </div>
                  <div className="rounded-xl bg-surface p-4">
                    <p className="text-[11px] font-bold tracking-wider text-muted uppercase">Setor & categoria</p>
                    <p className="font-bold text-brand-600">{ticket.sectorName}</p>
                  </div>
                </div>
                <div className="grid grid-cols-2 gap-3">
                  <div className="rounded-xl bg-surface p-4 text-center">
                    <p className="text-xs text-muted">Assento</p>
                    <p className="text-2xl font-extrabold">{ticket.seatLabel}</p>
                  </div>
                  <div className="rounded-xl bg-surface p-4 text-center">
                    <p className="text-xs text-muted">Portão</p>
                    <p className="text-lg font-extrabold text-brand-600">{ticket.gate}</p>
                  </div>
                </div>
                <div className="flex items-center justify-between text-xs">
                  <span className="font-mono text-muted">ID: {ticket.id}</span>
                  <button
                    className="flex items-center gap-1 font-semibold text-brand-600"
                    onClick={async () => {
                      await navigator.clipboard?.writeText(ticket.id).catch(() => undefined);
                      setCopied(true);
                      setTimeout(() => setCopied(false), 1500);
                    }}
                  >
                    <Copy className="size-3.5" aria-hidden /> {copied ? 'Copiado' : 'Copiar'}
                  </button>
                </div>
              </div>
              <DynamicQr ticket={ticket} />
            </div>

            <div className="flex flex-wrap items-center gap-2 bg-surface px-6 py-4">
              <p className="mr-auto flex items-center gap-1.5 text-xs text-muted">
                <ShieldCheck className="size-4" aria-hidden /> Assinatura digital ECDSA P-256
              </p>
              <Button variant="outline" size="sm" disabled title="Disponível quando o serviço de PDF entrar">
                <Download className="size-4" aria-hidden /> Baixar PDF
              </Button>
              <Button variant="outline" size="sm" disabled title="Em breve">
                <Wallet className="size-4" aria-hidden /> Apple / Google Wallet
              </Button>
              <Button size="sm" disabled title="Em breve">
                <Send className="size-4" aria-hidden /> Transferir ingresso
              </Button>
            </div>
          </Card>

          <Card className="p-6">
            <h2 className="text-xl font-bold">Instruções de acesso</h2>
            <div className="mt-4 grid gap-3 sm:grid-cols-3">
              <div className="rounded-xl bg-surface p-4">
                <p className="flex items-center gap-1.5 font-bold text-brand-600"><DoorOpen className="size-4" aria-hidden /> {ticket.gate}</p>
                <p className="mt-1 text-xs text-muted">Use a entrada indicada para o seu setor.</p>
              </div>
              <div className="rounded-xl bg-surface p-4">
                <p className="flex items-center gap-1.5 font-bold text-brand-600"><Clock className="size-4" aria-hidden /> Abertura: {formatTime(opensAt)}</p>
                <p className="mt-1 text-xs text-muted">Portões abrem 2 horas antes do início.</p>
              </div>
              <div className="rounded-xl bg-surface p-4">
                <p className="flex items-center gap-1.5 font-bold text-brand-600"><ShieldCheck className="size-4" aria-hidden /> Documento</p>
                <p className="mt-1 text-xs text-muted">Leve um documento com o CPF do titular.</p>
              </div>
            </div>
          </Card>
        </div>

        <aside className="grid content-start gap-6">
          {order && (
            <Card className="p-5">
              <p className="text-xs font-bold tracking-wider text-muted uppercase">Resumo da compra</p>
              <h2 className="text-lg font-bold">Pedido #{order.code}</h2>
              <ul className="mt-3 grid gap-1.5 text-sm">
                {order.items.map((i) => (
                  <li key={i.seatId} className="flex justify-between"><span>{i.sectorName} • {i.seatLabel}</span><span className="tabular">{formatMoney(i.priceCents)}</span></li>
                ))}
                <li className="flex justify-between text-muted"><span>Taxa de conveniência (10%)</span><span className="tabular">{formatMoney(order.totals.feeCents)}</span></li>
              </ul>
              <div className="mt-3 flex items-end justify-between border-t border-line pt-3">
                <span className="font-bold">Total pago</span>
                <span className="text-2xl font-extrabold text-brand-600 tabular">{formatMoney(order.totals.totalCents)}</span>
              </div>
              {order.payment && (
                <p className="mt-3 flex items-center gap-2 rounded-lg bg-surface px-3 py-2 text-xs">
                  <CreditCard className="size-4" aria-hidden />
                  {order.payment.method === 'PIX' ? 'PIX' : `Cartão **** ${order.payment.last4}`} • {order.payment.installments}x
                  {order.paidAt && ` • ${formatTime(order.paidAt)}`}
                </p>
              )}
            </Card>
          )}
          <Card className="p-5">
            <h2 className="flex items-center gap-2 text-lg font-bold"><MapPin className="size-5 text-brand-600" aria-hidden /> Local do evento</h2>
            <p className="mt-2 text-sm font-semibold">{ticket.event.venue}</p>
            <p className="text-sm text-muted">{ticket.event.city}</p>
            <a href={`https://www.google.com/maps/search/?api=1&query=${encodeURIComponent(`${ticket.event.venue} ${ticket.event.city}`)}`} target="_blank" rel="noreferrer">
              <Button variant="soft" size="sm" className="mt-3 w-full">Abrir rota no Google Maps</Button>
            </a>
          </Card>
          <Link to="/my-orders">
            <Button variant="outline" className="w-full">Ver histórico de pedidos</Button>
          </Link>
        </aside>
      </div>
    </div>
  );
}
