import { useQuery, useQueryClient } from '@tanstack/react-query';
import { Accessibility, CreditCard, DoorOpen, Lock, Mail, Phone, ShieldCheck, Ticket, UserRound, X } from 'lucide-react';
import { useEffect, useState } from 'react';
import { api } from '@/api';
import { errorMessage } from '@/api/errors';
import type { OrganizerSectorSeats } from '@/api/types';
import { Badge, ErrorState, Spinner } from '@/components/ui';
import { cx, formatDateTime, formatMoney } from '@/lib/format';

type SeatItem = OrganizerSectorSeats['seats'][number];

const STYLE: Record<SeatItem['status'], string> = {
  DISPONIVEL: 'bg-emerald-100 text-emerald-800 hover:bg-emerald-200',
  BLOQUEADO_TEMPORARIO: 'bg-amber-100 text-amber-800',
  VENDIDO: 'bg-brand-600 text-white hover:bg-brand-700',
  UTILIZADO: 'bg-navy text-white hover:bg-slate-700',
};
const STATUS_LABEL: Record<SeatItem['status'], string> = {
  DISPONIVEL: 'Disponível',
  BLOQUEADO_TEMPORARIO: 'Em carrinho',
  VENDIDO: 'Vendido',
  UTILIZADO: 'Vendido e já entrou',
};

function formatCpf(cpf?: string) {
  const d = (cpf ?? '').replace(/\D/g, '');
  return d.length === 11 ? `${d.slice(0, 3)}.${d.slice(3, 6)}.${d.slice(6, 9)}-${d.slice(9)}` : cpf || '—';
}

function Row({ icon, label, value }: { icon: React.ReactNode; label: string; value?: React.ReactNode }) {
  return (
    <div className="flex items-start gap-2.5 py-1.5">
      <span className="mt-0.5 text-muted">{icon}</span>
      <div className="min-w-0">
        <p className="text-[11px] text-muted">{label}</p>
        <p className="text-sm font-semibold break-words">{value || '—'}</p>
      </div>
    </div>
  );
}

/** Painel com quem comprou o assento. Cada abertura fica registrada na auditoria. */
function SalePanel({ eventId, seat }: { eventId: string; seat: SeatItem }) {
  const queryClient = useQueryClient();
  const sale = useQuery({
    queryKey: ['organizer', 'seat-sale', eventId, seat.id],
    queryFn: () => api.organizer.getSeatSale(eventId, seat.id),
    staleTime: 60_000,
  });
  // A consulta gera um registro de auditoria: atualiza a lista do evento.
  useEffect(() => {
    if (sale.isSuccess) queryClient.invalidateQueries({ queryKey: ['organizer', 'event', eventId] });
  }, [sale.isSuccess, eventId, queryClient]);

  if (sale.isLoading) return <Spinner label="Buscando a venda…" />;
  if (sale.error || !sale.data) return <ErrorState message={errorMessage(sale.error)} onRetry={() => sale.refetch()} />;
  const s = sale.data;
  return (
    <div className="grid gap-4">
      <div className="flex items-center justify-between gap-2">
        <div>
          <p className="text-xs text-muted">{s.sectorName}</p>
          <p className="text-2xl font-extrabold">Assento {s.seatLabel}</p>
        </div>
        <Badge tone={s.seatStatus === 'UTILIZADO' ? 'dark' : 'brand'}>{STATUS_LABEL[s.seatStatus]}</Badge>
      </div>

      <section className="rounded-xl border border-line p-4">
        <p className="text-[11px] font-bold tracking-wider text-muted uppercase">Comprador (conta que pagou)</p>
        <Row icon={<UserRound className="size-4" />} label="Nome" value={s.buyer?.name} />
        <Row icon={<ShieldCheck className="size-4" />} label="CPF" value={formatCpf(s.buyer?.cpf)} />
        <Row icon={<Mail className="size-4" />} label="E-mail" value={s.buyer?.email && <a className="text-brand-600 hover:underline" href={`mailto:${s.buyer.email}`}>{s.buyer.email}</a>} />
        <Row icon={<Phone className="size-4" />} label="Telefone para contato" value={s.buyer?.phone && <a className="text-brand-600 hover:underline" href={`tel:${s.buyer.phone.replace(/\D/g, '')}`}>{s.buyer.phone}</a>} />
      </section>

      <section className="rounded-xl border border-line p-4">
        <p className="text-[11px] font-bold tracking-wider text-muted uppercase">Titular do ingresso (quem vai entrar)</p>
        <Row icon={<UserRound className="size-4" />} label="Nome" value={s.holder.name} />
        <Row icon={<ShieldCheck className="size-4" />} label="CPF vinculado ao ingresso" value={formatCpf(s.holder.cpf)} />
        {s.ticketId && <Row icon={<Ticket className="size-4" />} label="Ingresso" value={<span className="font-mono">{s.ticketId}</span>} />}
        <Row icon={<DoorOpen className="size-4" />} label="Entrada no evento"
          value={s.checkedInAt ? `${formatDateTime(s.checkedInAt)}${s.checkedInGate ? ` • ${s.checkedInGate}` : ''}` : 'Ainda não entrou'} />
      </section>

      <section className="rounded-xl bg-surface p-4">
        <p className="text-[11px] font-bold tracking-wider text-muted uppercase">Pedido</p>
        <div className="mt-1 grid grid-cols-2 gap-x-3">
          <Row icon={<Ticket className="size-4" />} label="Código" value={<span className="font-mono">{s.orderCode}</span>} />
          <Row icon={<CreditCard className="size-4" />} label="Pagamento"
            value={s.paymentMethod === 'PIX' ? 'PIX' : s.paymentMethod === 'CREDIT_CARD' ? `Cartão${s.cardLast4 ? ` final ${s.cardLast4}` : ''}${s.installments > 1 ? ` em ${s.installments}x` : ''}` : '—'} />
          <Row icon={<CreditCard className="size-4" />} label="Pago em" value={formatDateTime(s.paidAt)} />
          <Row icon={<CreditCard className="size-4" />} label="Valor deste assento" value={formatMoney(s.priceCents)} />
        </div>
        <p className="mt-1 text-xs text-muted">O pedido tem {s.orderSeats} assento(s), total pago {formatMoney(s.orderTotalCents)} com taxa.</p>
      </section>

      <p className="flex gap-2 text-[11px] text-muted">
        <Lock className="size-3.5 shrink-0" aria-hidden /> Dados pessoais protegidos pela LGPD. Esta consulta foi registrada no histórico do evento com o seu usuário.
      </p>
    </div>
  );
}

/** Mapa de assentos de um setor para o organizador. Clique num assento vendido para ver a venda. */
export function SectorSeatsDialog({ eventId, sectorId, onClose }: { eventId: string; sectorId: string; onClose: () => void }) {
  const [selected, setSelected] = useState<SeatItem>();
  const map = useQuery({
    queryKey: ['organizer', 'sector-seats', eventId, sectorId],
    queryFn: () => api.organizer.getSectorSeats(eventId, sectorId),
    refetchInterval: 10_000,
  });

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => e.key === 'Escape' && onClose();
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [onClose]);

  const rows = new Map<string, SeatItem[]>();
  for (const seat of map.data?.seats ?? []) rows.set(seat.row, [...(rows.get(seat.row) ?? []), seat]);
  const count = (st: SeatItem['status']) => map.data?.seats.filter((s) => s.status === st).length ?? 0;

  return (
    <div className="fixed inset-0 z-40 flex items-start justify-center overflow-y-auto bg-navy/50 p-4 backdrop-blur-sm" onClick={onClose}>
      <div role="dialog" aria-modal="true" aria-label="Assentos do setor" className="my-8 w-full max-w-5xl rounded-2xl bg-white shadow-2xl" onClick={(e) => e.stopPropagation()}>
        <div className="flex items-start justify-between gap-3 border-b border-line p-5">
          <div>
            <p className="text-xs font-semibold text-brand-600">Assentos do setor</p>
            <h2 className="text-2xl font-extrabold">{map.data?.sectorName ?? '…'}</h2>
            {map.data?.gate && <p className="text-xs text-muted">Entrada pelo {map.data.gate}</p>}
          </div>
          <button onClick={onClose} className="rounded-lg p-2 hover:bg-surface" aria-label="Fechar"><X className="size-5" /></button>
        </div>

        {map.isLoading && <Spinner />}
        {map.error && <ErrorState message={errorMessage(map.error)} onRetry={() => map.refetch()} />}
        {map.data && (
          <div className="grid gap-5 p-5 lg:grid-cols-[1fr_360px]">
            <div>
              <div className="flex flex-wrap gap-3 text-xs font-semibold">
                <span className="flex items-center gap-1.5"><span className="size-3 rounded bg-emerald-100" /> Disponível ({count('DISPONIVEL')})</span>
                <span className="flex items-center gap-1.5"><span className="size-3 rounded bg-brand-600" /> Vendido ({count('VENDIDO')})</span>
                <span className="flex items-center gap-1.5"><span className="size-3 rounded bg-navy" /> Já entrou ({count('UTILIZADO')})</span>
                <span className="flex items-center gap-1.5"><span className="size-3 rounded bg-amber-100" /> Em carrinho ({count('BLOQUEADO_TEMPORARIO')})</span>
              </div>
              <div className="mt-4 rounded-xl bg-surface p-4">
                <p className="mx-auto mb-4 w-2/3 rounded-md bg-navy py-1.5 text-center text-[11px] font-bold tracking-widest text-white">PALCO</p>
                <div className="grid gap-2 overflow-x-auto">
                  {[...rows.entries()].map(([row, seats]) => (
                    <div key={row} className="flex items-center justify-center gap-1.5">
                      <span className="w-8 text-right text-[11px] font-bold text-muted">{row}</span>
                      {seats.map((seat) => {
                        const sold = seat.status === 'VENDIDO' || seat.status === 'UTILIZADO';
                        const blocked = seat.kind === 'BLOQUEADO_PRODUCAO' || seat.kind === 'OBSTRUCAO';
                        return (
                          <button
                            key={seat.id}
                            type="button"
                            disabled={!sold}
                            onClick={() => setSelected(seat)}
                            title={`${seat.label} • ${blocked ? (seat.kind === 'OBSTRUCAO' ? 'Obstrução' : 'Bloqueado pela produção') : STATUS_LABEL[seat.status]}${sold ? ' • clique para ver o comprador' : ''}`}
                            aria-label={`Assento ${seat.label}, ${STATUS_LABEL[seat.status]}`}
                            className={cx(
                              'grid size-8 shrink-0 place-items-center rounded-md text-[10px] font-bold transition',
                              blocked && !sold ? 'bg-slate-200 text-slate-400' : STYLE[seat.status],
                              sold && 'cursor-pointer',
                              selected?.id === seat.id && 'ring-2 ring-brand-500 ring-offset-2',
                            )}
                          >
                            {seat.kind === 'PCD' ? <Accessibility className="size-3.5" /> : seat.number}
                          </button>
                        );
                      })}
                    </div>
                  ))}
                </div>
              </div>
              <p className="mt-3 text-xs text-muted">Clique num assento vendido (vermelho ou escuro) para ver quem comprou.</p>
            </div>

            <aside className="rounded-xl border border-line p-4">
              {selected ? (
                <SalePanel key={selected.id} eventId={eventId} seat={selected} />
              ) : (
                <div className="grid h-full place-items-center py-10 text-center text-sm text-muted">
                  <div>
                    <UserRound className="mx-auto size-8 text-line" aria-hidden />
                    <p className="mt-2">Selecione um assento vendido para ver o comprador, o titular com CPF e o telefone de contato.</p>
                  </div>
                </div>
              )}
            </aside>
          </div>
        )}
      </div>
    </div>
  );
}
