import { useQuery } from '@tanstack/react-query';
import { CalendarDays, QrCode, Ticket as TicketIcon } from 'lucide-react';
import { Link } from 'react-router-dom';
import { api } from '@/api';
import { errorMessage } from '@/api/errors';
import type { Ticket } from '@/api/types';
import { Badge, Button, Card, ErrorState, EventCover, Spinner } from '@/components/ui';
import { formatDateTime } from '@/lib/format';

function TicketCard({ t }: { t: Ticket }) {
  const active = t.status === 'ATIVO' && Date.parse(t.event.startsAt) > Date.now();
  return (
    <Card className="flex overflow-hidden">
      <EventCover hue={t.event.hue} className="w-24 shrink-0 sm:w-32" />
      <div className="flex flex-1 flex-wrap items-center gap-3 p-4">
        <div className="flex-1">
          <p className="font-bold">{t.event.title}</p>
          <p className="flex items-center gap-1 text-xs text-muted">
            <CalendarDays className="size-3.5" aria-hidden /> {formatDateTime(t.event.startsAt)}
          </p>
          <p className="mt-1 text-sm">
            {t.sectorName} • <b>Assento {t.seatLabel}</b> • {t.holderName}
          </p>
        </div>
        {active ? (
          <Link to={`/tickets/${t.id}`}>
            <Button>
              <QrCode className="size-4" aria-hidden /> Abrir QR
            </Button>
          </Link>
        ) : (
          <Badge>{t.status === 'ATIVO' ? 'Evento encerrado' : t.status.toLowerCase()}</Badge>
        )}
      </div>
    </Card>
  );
}

export default function WalletPage() {
  const { data, isLoading, error, refetch } = useQuery({ queryKey: ['tickets'], queryFn: () => api.tickets.listMine() });
  if (isLoading) return <Spinner />;
  if (error || !data) return <ErrorState message={errorMessage(error)} onRetry={() => refetch()} />;

  const upcoming = data.filter((t) => t.status === 'ATIVO' && Date.parse(t.event.startsAt) > Date.now());
  const past = data.filter((t) => !upcoming.includes(t));

  return (
    <div className="mx-auto max-w-4xl px-4 py-8">
      <h1 className="flex items-center gap-2 text-3xl font-extrabold tracking-tight">
        <TicketIcon className="size-7 text-brand-600" aria-hidden /> Minha carteira
      </h1>
      <p className="mt-1 text-sm text-muted">Seus ingressos com QR dinâmico. O código só aparece aqui, ao vivo.</p>

      <h2 className="mt-8 mb-3 font-bold">Próximos eventos ({upcoming.length})</h2>
      <div className="grid gap-3">
        {upcoming.length === 0 && (
          <Card className="p-6 text-center text-sm text-muted">
            Nenhum ingresso ativo. <Link to="/" className="font-semibold text-brand-600">Explorar eventos</Link>
          </Card>
        )}
        {upcoming.map((t) => <TicketCard key={t.id} t={t} />)}
      </div>

      {past.length > 0 && (
        <>
          <h2 className="mt-8 mb-3 font-bold text-muted">Anteriores</h2>
          <div className="grid gap-3 opacity-80">{past.map((t) => <TicketCard key={t.id} t={t} />)}</div>
        </>
      )}
    </div>
  );
}
