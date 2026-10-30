import { History, Keyboard, QrCode, ShieldCheck, Ticket, WifiOff, X } from 'lucide-react';
import { useCallback, useEffect, useRef, useState, type FormEvent } from 'react';
import { Link, Navigate } from 'react-router-dom';
import { Badge, Button, Card, Progress } from '@/components/ui';
import { cx, formatNumber, formatTime, pct } from '@/lib/format';
import { maskCpf, useOperator, type ScanResult } from './OperatorProvider';
import { QrCamera } from './QrCamera';
import { SCAN_STYLE, feedback, scanDetail } from './scanUi';

const AUTO_NEXT_S = 3;

/** Tela de resultado da leitura (mockup "Detalhes do ingresso"). */
function ResultView({ result, onNext, gateStats }: { result: ScanResult; onNext: () => void; gateStats: { read: number; approvalPct: number } }) {
  const st = SCAN_STYLE[result.status];
  const [left, setLeft] = useState(AUTO_NEXT_S);
  const auto = result.status === 'VALIDO';
  useEffect(() => {
    if (!auto) return;
    const t = setInterval(() => setLeft((s) => s - 1), 1000);
    return () => clearInterval(t);
  }, [auto]);
  useEffect(() => {
    if (auto && left <= 0) onNext();
  }, [auto, left, onNext]);

  return (
    <div className="grid gap-4">
      <div className={cx('rounded-2xl p-6 text-center text-white shadow-lg', st.panel)} role="status" aria-live="assertive">
        <p className="mx-auto w-fit rounded-full bg-black/15 px-3 py-1 text-[11px] font-bold tracking-wider uppercase">
          {result.status === 'VALIDO' ? 'Pode entrar' : 'Não liberar a entrada'}
        </p>
        <span className="mx-auto mt-4 grid size-20 place-items-center rounded-2xl bg-white/95"><st.icon className={cx('size-11', result.status === 'VALIDO' ? 'text-ok' : 'text-brand-600')} aria-hidden /></span>
        <p className="mt-4 text-2xl font-extrabold">{st.title}</p>
        <p className="mt-1 text-sm text-white/85">{scanDetail(result)}</p>
        <p className="mt-4 flex justify-between rounded-xl bg-black/15 px-4 py-2.5 text-xs">
          <span>{result.gate}</span><span className="font-mono font-bold">{formatTime(result.at, true)}</span>
        </p>
      </div>

      {result.status !== 'INVALIDO' && (
        <Card className="p-4">
          <div className="flex items-center justify-between">
            <p className="text-[11px] font-bold tracking-wider text-brand-600 uppercase">Titular do ingresso</p>
            {result.status === 'VALIDO' && <Badge tone="ok">1º acesso</Badge>}
          </div>
          <p className="mt-2 text-2xl font-bold">{result.holderName}</p>
          <p className="text-xs text-muted">CPF: {maskCpf(result.holderCpf)} • confira com o documento se houver dúvida</p>
          <div className="mt-3 grid grid-cols-2 gap-3 rounded-xl bg-surface p-3 text-sm">
            <div><p className="text-[11px] text-muted">Setor</p><b>{result.sectorName ?? '—'}</b></div>
            <div><p className="text-[11px] text-muted">Categoria</p><b>{result.categoryName ?? '—'}</b></div>
          </div>
          {result.ticketId && <p className="mt-3 flex items-center justify-between text-xs"><span className="flex items-center gap-1 font-mono font-bold"><Ticket className="size-3.5" aria-hidden /> {result.ticketId}</span><span className="text-muted">Assinatura digital conferida</span></p>}
        </Card>
      )}

      <Button size="lg" onClick={onNext}><QrCode className="size-5" aria-hidden /> Próxima leitura{auto ? ` (automática em ${Math.max(0, left)}s)` : ''}</Button>
      <p className="flex justify-between text-xs text-muted"><span>Lidos neste portão: <b className="text-ink">{gateStats.read}</b></span><span>{gateStats.approvalPct}% aprovados</span></p>
    </div>
  );
}

export default function OperatorScanPage() {
  const op = useOperator();
  const [result, setResult] = useState<ScanResult>();
  const [manual, setManual] = useState(false);
  const [code, setCode] = useState('');
  const busy = useRef(false);
  const lastRaw = useRef<{ raw: string; at: number }>();

  const handle = useCallback(async (raw: string) => {
    const now = Date.now();
    // A câmera lê o mesmo QR várias vezes por segundo: ignora repetição imediata.
    if (busy.current || (lastRaw.current?.raw === raw && now - lastRaw.current.at < 4000)) return;
    busy.current = true;
    lastRaw.current = { raw, at: now };
    try {
      const r = await op.scan(raw);
      feedback(r.status === 'VALIDO');
      setResult(r);
    } finally {
      busy.current = false;
    }
  }, [op]);

  const next = useCallback(() => setResult(undefined), []);

  if (!op.station) return <Navigate to="/operator/setup" replace />;
  const { cache, gate, event } = op.station;
  const total = Object.keys(cache.tickets).length;
  const localIn = op.scans.filter((s) => s.status === 'VALIDO' && s.ticketId && !cache.used[s.ticketId]).length;
  const entered = Object.keys(cache.used).length + localIn;
  const lastMinute = op.scans.filter((s) => Date.now() - Date.parse(s.at) < 60_000).length;
  const atGate = op.scans.filter((s) => s.gate === gate.name);
  const gateStats = { read: atGate.length, approvalPct: pct(atGate.filter((s) => s.status === 'VALIDO').length, atGate.length || 1) };
  const last = op.scans[0];

  function submitManual(e: FormEvent) {
    e.preventDefault();
    if (!code.trim()) return;
    setManual(false);
    lastRaw.current = undefined;
    void handle(code.trim());
    setCode('');
  }

  if (result) return <ResultView result={result} onNext={next} gateStats={gateStats} />;

  return (
    <div className="grid gap-4">
      {!op.online && (
        <Link to="/operator/offline" className="flex items-center gap-2 rounded-xl bg-brand-600 px-4 py-3 text-sm font-semibold text-white">
          <WifiOff className="size-4" aria-hidden /> Sem internet: validando pela base do aparelho. {op.pending} entrada(s) na fila.
        </Link>
      )}

      <Card className="p-4">
        <p className="flex items-center justify-between gap-2">
          <b className="flex items-center gap-2 truncate"><span className="size-2 rounded-full bg-ok" />{gate.name}</b>
          <span className="truncate text-xs text-muted">{event.title}</span>
        </p>
        <p className="mt-2 text-[11px] font-bold tracking-wider text-muted uppercase">Entradas no evento</p>
        <p className="flex items-end justify-between">
          <span><b className="text-3xl font-extrabold text-brand-600 tabular">{formatNumber(entered)}</b> <span className="text-sm text-muted">/ {formatNumber(total)} ingressos</span></span>
          <span className="text-right text-xs text-muted"><b className="text-ok">{lastMinute} leituras/min</b><br />{pct(entered, total || 1)}% já entraram</span>
        </p>
        <Progress value={pct(entered, total || 1)} className="mt-2" />
      </Card>

      <QrCamera paused={manual} onCode={(raw) => void handle(raw)} />

      <div className="grid grid-cols-2 gap-3">
        <button type="button" onClick={() => setManual(true)} className="flex items-center gap-3 rounded-2xl bg-white p-3 text-left shadow-sm hover:bg-surface">
          <span className="grid size-10 place-items-center rounded-xl bg-surface"><Keyboard className="size-5 text-brand-600" aria-hidden /></span>
          <span><b className="block text-sm">Digitar código</b><span className="text-[11px] text-muted">QR danificado ou sem câmera</span></span>
        </button>
        <Link to="/operator/history" className="flex items-center gap-3 rounded-2xl bg-white p-3 shadow-sm hover:bg-surface">
          <span className="grid size-10 place-items-center rounded-xl bg-surface"><History className="size-5 text-brand-600" aria-hidden /></span>
          <span><b className="block text-sm">Últimas leituras</b><span className="text-[11px] text-muted">{op.scans.length} neste turno</span></span>
        </Link>
      </div>

      {last && (
        <div>
          <p className="text-[11px] font-bold tracking-wider text-muted uppercase">Última validação</p>
          <Card className="mt-1.5 flex items-center gap-3 p-3">
            {(() => { const S = SCAN_STYLE[last.status]; return <span className={cx('grid size-10 place-items-center rounded-xl', S.badge)}><S.icon className="size-5" aria-hidden /></span>; })()}
            <div className="min-w-0 flex-1">
              <p className="truncate font-bold">{last.holderName}</p>
              <p className="text-xs text-muted">{formatTime(last.at, true)} • {last.gate}</p>
            </div>
            <Badge tone={last.status === 'VALIDO' ? 'ok' : 'brand'}>{SCAN_STYLE[last.status].label}</Badge>
          </Card>
        </div>
      )}

      <p className="flex items-center justify-center gap-1.5 text-[11px] text-muted"><ShieldCheck className="size-3.5" aria-hidden /> QR dinâmico conferido pela assinatura digital, mesmo sem internet.</p>

      {manual && (
        <div className="fixed inset-0 z-40 flex items-end justify-center bg-navy/50 p-4 sm:items-center" onClick={() => setManual(false)}>
          <form onSubmit={submitManual} onClick={(e) => e.stopPropagation()} className="w-full max-w-md rounded-2xl bg-white p-5 shadow-2xl" role="dialog" aria-label="Digitar código">
            <div className="flex items-center justify-between">
              <h2 className="text-lg font-bold">Digitar código do ingresso</h2>
              <button type="button" onClick={() => setManual(false)} className="rounded-lg p-1.5 hover:bg-surface" aria-label="Fechar"><X className="size-5" /></button>
            </div>
            <p className="mt-1 text-xs text-muted">Cole ou digite o conteúdo do QR (começa com <span className="font-mono">BP1.</span>). O código muda a cada 15 s no celular do cliente.</p>
            <textarea value={code} onChange={(e) => setCode(e.target.value)} rows={4} autoFocus aria-label="Código do QR"
              className="mt-3 w-full rounded-lg bg-surface p-3 font-mono text-xs focus:outline-none focus:ring-2 focus:ring-brand-500" placeholder="BP1.eyJ0aWQiOi…" />
            <Button type="submit" size="lg" className="mt-3 w-full" disabled={!code.trim()}>Validar</Button>
          </form>
        </div>
      )}
    </div>
  );
}
