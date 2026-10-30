import { useMutation, useQuery } from '@tanstack/react-query';
import { Accessibility, Check, CheckCircle2, DoorOpen, HardDriveDownload, IdCard, QrCode, RefreshCw, Star, Ticket, Users } from 'lucide-react';
import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { api } from '@/api';
import { errorMessage } from '@/api/errors';
import type { AssignedEvent, Gate } from '@/api/types';
import { useAuth } from '@/auth/AuthProvider';
import { Badge, Button, Card, ErrorState, EventCover, Spinner } from '@/components/ui';
import { cx, formatDateTime, formatNumber } from '@/lib/format';
import { useOperator } from './OperatorProvider';

const GATE_ICON = { door: DoorOpen, star: Star, accessible: Accessibility } as const;

function Steps({ step }: { step: 1 | 2 }) {
  return (
    <div className="flex items-center gap-2 text-sm font-semibold">
      <span className="grid size-6 place-items-center rounded-full bg-brand-600 text-xs text-white">{step > 1 ? <Check className="size-3.5" /> : 1}</span> Evento
      <span className={cx('h-1 flex-1 rounded-full', step > 1 ? 'bg-brand-600' : 'bg-surface-strong')} />
      <span className={cx('grid size-6 place-items-center rounded-full text-xs', step > 1 ? 'bg-brand-600 text-white' : 'bg-surface-strong')}>2</span> Portão
      <span className="h-1 flex-1 rounded-full bg-surface-strong" />
    </div>
  );
}

/** Posto de leitura: escolhe evento e portão e baixa a base para validar sem internet. */
export default function OperatorSetupPage() {
  const { user } = useAuth();
  const navigate = useNavigate();
  const op = useOperator();
  const assigned = useQuery({ queryKey: ['operator', 'events'], queryFn: api.operator.listAssignedEvents });
  const [eventId, setEventId] = useState(op.station?.event.id ?? '');
  const [gateId, setGateId] = useState(op.station?.gate.id ?? '');

  const start = useMutation({
    mutationFn: async ({ item, gate }: { item: AssignedEvent; gate: Gate }) => {
      const cache = await api.operator.downloadCache(item.event.id);
      return { item, gate, cache };
    },
    onSuccess: ({ item, gate, cache }) => {
      op.configure(item.event, gate, cache);
      navigate('/operator/scan');
    },
  });

  if (assigned.isLoading) return <Spinner label="Carregando eventos atribuídos…" />;
  if (assigned.error || !assigned.data) return <ErrorState message={errorMessage(assigned.error)} onRetry={() => void assigned.refetch()} />;

  const list = assigned.data;
  const selected = list.find((i) => i.event.id === (eventId || list[0]?.event.id));
  const gate = selected?.gates.find((g) => g.id === gateId);
  const switching = !!op.station && !!selected && op.station.event.id !== selected.event.id;
  const blocked = switching && op.pending > 0;

  return (
    <div className="grid gap-5">
      <Card className="flex items-center gap-3 p-4">
        <span className="grid size-12 place-items-center rounded-xl bg-brand-50 text-brand-600"><IdCard className="size-6" aria-hidden /></span>
        <div className="min-w-0 flex-1">
          <p className="flex items-center gap-2 text-lg font-extrabold">{user?.name} {user?.badge && <Badge>{user.badge}</Badge>}</p>
          <p className="flex items-center gap-1.5 text-xs text-muted">
            <span className={cx('size-2 rounded-full', op.station ? 'bg-ok' : 'bg-amber-500')} />
            {op.station ? `Posto: ${op.station.gate.name} • ${op.station.event.title}` : 'Posto: aguardando escolha do evento e do portão'}
          </p>
        </div>
      </Card>

      <Steps step={gate ? 2 : 1} />

      <section>
        <div className="flex items-center justify-between">
          <h2 className="text-lg font-bold">Eventos atribuídos</h2>
          <button className="rounded-lg p-2 text-muted hover:bg-surface" onClick={() => void assigned.refetch()} aria-label="Atualizar lista"><RefreshCw className="size-4" /></button>
        </div>
        {list.length === 0 && <Card className="mt-2 p-6 text-center text-sm text-muted">Nenhum evento com vendas abertas nas próximas horas.</Card>}
        <div className="mt-2 grid gap-3">
          {list.map((item) => {
            const active = selected?.event.id === item.event.id;
            return (
              <button key={item.event.id} type="button" onClick={() => { setEventId(item.event.id); setGateId(''); }}
                className={cx('rounded-2xl border bg-white p-3 text-left shadow-sm transition', active ? 'border-brand-500 ring-2 ring-brand-500/30' : 'border-line hover:border-brand-200')}>
                <div className="flex gap-3">
                  <EventCover hue={item.event.hue} className="size-20 shrink-0 rounded-xl" />
                  <div className="min-w-0 flex-1">
                    <div className="flex flex-wrap items-center justify-between gap-1">
                      <Badge tone={item.gatesOpen ? 'ok' : 'neutral'}>{item.gatesOpen ? '● Portões abertos' : 'Portões fechados'}</Badge>
                      <span className="text-xs font-semibold text-muted">{formatDateTime(item.event.startsAt)}</span>
                    </div>
                    <p className="mt-1 truncate text-lg font-bold">{item.event.title}</p>
                    <p className="truncate text-xs text-muted">{item.event.venue}</p>
                    <p className="mt-1.5 flex gap-4 text-xs font-semibold">
                      <span className="flex items-center gap-1 text-brand-600"><Ticket className="size-3.5" aria-hidden /> {formatNumber(item.event.sold)} ingressos</span>
                      <span className="flex items-center gap-1 text-ok"><Users className="size-3.5" aria-hidden /> {item.checkedInPct}% já entraram</span>
                    </p>
                  </div>
                </div>
                {active && <p className="mt-3 flex items-center gap-1.5 rounded-lg bg-brand-50 px-3 py-2 text-xs font-bold text-brand-700"><CheckCircle2 className="size-4" aria-hidden /> Evento selecionado</p>}
              </button>
            );
          })}
        </div>
      </section>

      {selected && (
        <section>
          <h2 className="text-lg font-bold">Portão de acesso</h2>
          <p className="text-xs text-muted">Selecione onde você está posicionado.</p>
          <div className="mt-2 grid gap-2">
            {selected.gates.map((g) => {
              const Icon = GATE_ICON[g.icon] ?? DoorOpen;
              const active = gateId === g.id;
              return (
                <button key={g.id} type="button" onClick={() => setGateId(g.id)}
                  className={cx('flex items-center gap-3 rounded-2xl p-3 text-left transition', active ? 'bg-brand-600 text-white shadow-md' : 'bg-white shadow-sm hover:bg-surface')}>
                  <span className={cx('grid size-10 shrink-0 place-items-center rounded-xl', active ? 'bg-white/20' : 'bg-surface')}><Icon className="size-5" aria-hidden /></span>
                  <span className="min-w-0 flex-1">
                    <b className="block truncate">{g.name}</b>
                    <span className={cx('text-xs', active ? 'text-white/80' : 'text-muted')}>{g.turnstiles} • {g.description}</span>
                  </span>
                  <span className={cx('grid size-6 place-items-center rounded-full', active ? 'bg-white text-brand-600' : 'bg-surface-strong')}>{active && <Check className="size-4" />}</span>
                </button>
              );
            })}
          </div>
        </section>
      )}

      <Card className="flex items-center gap-3 p-4">
        <HardDriveDownload className="size-5 shrink-0 text-ok" aria-hidden />
        <p className="text-xs text-muted">Ao confirmar, o aparelho baixa a lista de ingressos e a chave de validação. As leituras continuam funcionando mesmo se a internet cair.</p>
      </Card>

      {blocked && (
        <p role="alert" className="rounded-lg bg-brand-50 px-3 py-2 text-sm font-medium text-brand-700">
          Há {op.pending} entrada(s) do evento atual ainda não enviadas. Conecte-se e aguarde a sincronização antes de trocar de evento.
        </p>
      )}
      {start.error && <p role="alert" className="rounded-lg bg-brand-50 px-3 py-2 text-sm font-medium text-brand-700">{errorMessage(start.error)}</p>}

      <div className="sticky bottom-20 grid gap-1">
        <Button size="lg" disabled={!selected || !gate || blocked} loading={start.isPending} onClick={() => selected && gate && start.mutate({ item: selected, gate })}>
          <QrCode className="size-5" aria-hidden /> {gate ? 'Confirmar posto e iniciar leituras' : 'Escolha o portão'}
        </Button>
      </div>
    </div>
  );
}
