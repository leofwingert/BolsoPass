import { ChevronDown, CloudOff, QrCode, Search, ShieldAlert, ShieldCheck } from 'lucide-react';
import { useState } from 'react';
import { Navigate } from 'react-router-dom';
import type { ScanStatus } from '@/domain/ticketQr';
import { Card } from '@/components/ui';
import { cx, formatTime, pct } from '@/lib/format';
import { maskCpf, useOperator } from './OperatorProvider';
import { SCAN_STYLE, scanDetail } from './scanUi';

type Filter = 'TODAS' | 'VALIDAS' | 'RECUSADAS';

/** Histórico do turno neste aparelho (mockup "Histórico de leituras"). */
export default function OperatorHistoryPage() {
  const { station, scans, pending } = useOperator();
  const [q, setQ] = useState('');
  const [filter, setFilter] = useState<Filter>('TODAS');
  const [open, setOpen] = useState<string>();
  if (!station) return <Navigate to="/operator/setup" replace />;

  const ok = scans.filter((s) => s.status === 'VALIDO').length;
  const refused = scans.length - ok;
  const lastMinutes = scans.filter((s) => Date.now() - Date.parse(s.at) < 5 * 60_000).length;
  const needle = q.toLowerCase().trim();
  const list = scans
    .filter((s) => filter === 'TODAS' || (filter === 'VALIDAS' ? s.status === 'VALIDO' : s.status !== 'VALIDO'))
    .filter((s) => !needle || `${s.ticketId ?? ''} ${s.holderName}`.toLowerCase().includes(needle));

  return (
    <div className="grid gap-4">
      <Card className="p-4">
        <p className="flex items-center justify-between font-bold">Resumo do turno <span className="rounded-full bg-ok-soft px-2 py-0.5 text-[11px] text-ok">{station.gate.name}</span></p>
        <div className="mt-3 grid grid-cols-2 gap-2">
          <div className="rounded-xl bg-surface p-3"><p className="text-[11px] font-bold text-muted uppercase">Total lido</p><p className="flex items-center justify-between text-3xl font-extrabold">{scans.length}<QrCode className="size-5 text-muted" aria-hidden /></p></div>
          <div className="rounded-xl bg-surface p-3"><p className="text-[11px] font-bold text-muted uppercase">Ritmo atual</p><p className="text-3xl font-extrabold text-ok">{(lastMinutes / 5).toFixed(1)}<span className="text-xs font-semibold"> leituras/min</span></p></div>
          <div className="rounded-xl bg-ok-soft p-3 text-ok"><p className="flex justify-between text-[11px] font-bold uppercase">Aprovados <ShieldCheck className="size-4" aria-hidden /></p><p className="text-3xl font-extrabold">{ok} <span className="rounded bg-ok px-1.5 text-xs text-white">{pct(ok, scans.length || 1)}%</span></p></div>
          <div className="rounded-xl bg-brand-50 p-3 text-brand-700"><p className="flex justify-between text-[11px] font-bold uppercase">Recusados <ShieldAlert className="size-4" aria-hidden /></p><p className="text-3xl font-extrabold">{refused} <span className="rounded bg-brand-600 px-1.5 text-xs text-white">{pct(refused, scans.length || 1)}%</span></p></div>
        </div>
        {pending > 0 && <p className="mt-2 flex items-center gap-1.5 text-xs font-semibold text-brand-700"><CloudOff className="size-3.5" aria-hidden /> {pending} entrada(s) aguardando envio ao servidor</p>}
      </Card>

      <div className="relative">
        <Search className="absolute top-1/2 left-3 size-4 -translate-y-1/2 text-muted" aria-hidden />
        <input value={q} onChange={(e) => setQ(e.target.value)} placeholder="Buscar código do ingresso ou titular…" aria-label="Buscar leituras"
          className="h-11 w-full rounded-xl bg-white pr-3 pl-9 text-sm shadow-sm focus:outline-none focus:ring-2 focus:ring-brand-500" />
      </div>
      <div className="flex gap-2 overflow-x-auto">
        {([['TODAS', `Todas (${scans.length})`], ['VALIDAS', `Válidas (${ok})`], ['RECUSADAS', `Recusadas (${refused})`]] as [Filter, string][]).map(([k, label]) => (
          <button key={k} onClick={() => setFilter(k)} className={cx('shrink-0 rounded-full px-3.5 py-1.5 text-xs font-semibold', filter === k ? 'bg-navy text-white' : 'bg-white shadow-sm')}>{label}</button>
        ))}
      </div>

      {list.length === 0 && <Card className="p-8 text-center text-sm text-muted">{scans.length ? 'Nenhuma leitura neste filtro.' : 'Nenhuma leitura neste turno ainda.'}</Card>}
      <ul className="grid gap-3">
        {list.map((s) => {
          const st = SCAN_STYLE[s.status as ScanStatus];
          const expanded = open === s.id;
          return (
            <li key={s.id}>
              <Card className="p-4">
                <div className="flex items-start justify-between gap-2">
                  <div className="min-w-0">
                    <p className="flex flex-wrap items-center gap-2">
                      <b className="font-mono text-lg">{formatTime(s.at, true)}</b>
                      <span className={cx('rounded-full px-2 py-0.5 text-[11px] font-bold', st.badge)}>{st.label}</span>
                      {!s.synced && <span className="rounded-full bg-amber-100 px-2 py-0.5 text-[11px] font-bold text-amber-800">na fila</span>}
                    </p>
                    <p className="mt-1 truncate text-lg font-bold">{s.holderName}</p>
                    {s.sectorName && <p className="text-xs text-muted">Setor: {s.sectorName}</p>}
                  </div>
                  <span className="shrink-0 rounded-md bg-surface px-2 py-1 font-mono text-[10px] font-bold">{s.ticketId ?? 'Sem código'}</span>
                </div>
                {s.status !== 'VALIDO' && <p className={cx('mt-2 rounded-lg px-3 py-1.5 text-xs', st.badge)}>{scanDetail(s)}</p>}
                <button onClick={() => setOpen(expanded ? undefined : s.id)} className="mt-2 flex w-full items-center justify-between rounded-lg bg-surface px-3 py-1.5 text-xs">
                  <span>{s.gate}{s.offline ? ' • lido sem internet' : ''}</span>
                  <span className="flex items-center gap-0.5 font-semibold text-brand-600">Detalhes <ChevronDown className={cx('size-3.5 transition', expanded && 'rotate-180')} aria-hidden /></span>
                </button>
                {expanded && (
                  <dl className="mt-2 grid grid-cols-2 gap-2 text-xs">
                    <div><dt className="text-muted">CPF do titular</dt><dd className="font-semibold">{maskCpf(s.holderCpf)}</dd></div>
                    <div><dt className="text-muted">Categoria</dt><dd className="font-semibold">{s.categoryName ?? '—'}</dd></div>
                    <div><dt className="text-muted">Catraca</dt><dd className="font-semibold">{s.turnstile}</dd></div>
                    <div><dt className="text-muted">Envio ao servidor</dt><dd className="font-semibold">{s.status !== 'VALIDO' ? 'Não se aplica' : s.synced ? 'Enviado' : 'Pendente'}</dd></div>
                  </dl>
                )}
              </Card>
            </li>
          );
        })}
      </ul>
    </div>
  );
}
