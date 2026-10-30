import { CheckCircle2, Database, QrCode, RefreshCw, ShieldCheck, Upload, WifiOff } from 'lucide-react';
import { Link, Navigate } from 'react-router-dom';
import { Button, Card, Progress } from '@/components/ui';
import { cx, formatNumber, formatTime } from '@/lib/format';
import { useOperator } from './OperatorProvider';

const RULES = [
  'Continue a fila normalmente: o aparelho valida os QR codes sem internet.',
  'A assinatura digital do QR é conferida no próprio aparelho, com a chave baixada ao abrir o posto.',
  'As entradas ficam guardadas no aparelho e são enviadas sozinhas quando a rede voltar.',
  'Se o aparelho acusar "já utilizado", peça um documento com foto antes de qualquer liberação manual.',
];

/** Situação da conexão e modo contingência (mockup "Liberação manual de acesso"). */
export default function OperatorOfflinePage() {
  const op = useOperator();
  if (!op.station) return <Navigate to="/operator/setup" replace />;
  const { cache } = op.station;

  return (
    <div className="grid gap-4">
      <div className={cx('rounded-2xl px-4 py-3 text-white', op.online ? 'bg-ok' : 'bg-brand-600')}>
        <p className="flex items-center gap-2 text-lg font-extrabold">
          {op.online ? <CheckCircle2 className="size-5" aria-hidden /> : <WifiOff className="size-5" aria-hidden />}
          {op.online ? 'Conectado ao servidor' : 'Sem conexão com o servidor'}
        </p>
        <p className="text-xs text-white/80">{op.online ? 'Leituras enviadas em tempo real.' : 'Modo contingência ativo: validando pela base do aparelho.'}</p>
      </div>

      <Card className="p-4">
        <p className="flex items-center justify-between text-sm font-semibold">
          <span className="flex items-center gap-2"><span className={cx('size-2 rounded-full', op.online ? 'bg-ok' : 'bg-brand-600')} />
            {op.syncing ? 'Sincronizando…' : `Próxima sincronização em ${op.nextRetryInS}s`}</span>
          {op.lastSyncAt && <span className="text-xs text-muted">última às {formatTime(op.lastSyncAt)}</span>}
        </p>
        <Progress value={((10 - op.nextRetryInS) / 10) * 100} tone={op.online ? 'ok' : 'brand'} className="mt-2" />
        {op.lastSyncError && <p className="mt-2 text-xs text-brand-700">Falha na última tentativa: {op.lastSyncError}</p>}
      </Card>

      <Card className="bg-ok p-4 text-white">
        <p className="flex items-center gap-2 font-bold"><ShieldCheck className="size-5" aria-hidden /> Validação no próprio aparelho</p>
        <p className="mt-1 text-xs text-white/85">A assinatura de cada QR é conferida com a chave pública baixada. Funciona sem internet, e QR copiado ou print de tela é recusado.</p>
      </Card>

      <div className="grid grid-cols-2 gap-3">
        <Card className="p-4">
          <p className="flex justify-between text-[11px] font-bold text-muted uppercase">Base no aparelho <Database className="size-4" aria-hidden /></p>
          <p className="mt-1 text-2xl font-extrabold">{formatNumber(Object.keys(cache.tickets).length)}</p>
          <p className="text-xs text-muted">ingressos • baixada às {formatTime(cache.syncedAt)}</p>
        </Card>
        <Card className="p-4">
          <p className="flex justify-between text-[11px] font-bold text-muted uppercase">Fila de envio <Upload className="size-4" aria-hidden /></p>
          <p className={cx('mt-1 text-2xl font-extrabold', op.pending ? 'text-brand-600' : 'text-ok')}>{op.pending} entradas</p>
          <p className="text-xs text-muted">{op.pending ? 'guardadas, nenhuma se perde' : 'tudo enviado'}</p>
        </Card>
      </div>

      <Button size="lg" loading={op.syncing} onClick={() => void op.syncNow()}><RefreshCw className="size-5" aria-hidden /> Tentar reconectar agora</Button>
      <Link to="/operator/scan"><Button size="lg" variant="dark" className="w-full"><QrCode className="size-5" aria-hidden /> Continuar validando</Button></Link>

      <Card className="p-4">
        <p className="font-bold">Regras para o modo sem internet</p>
        <ol className="mt-3 grid gap-3">
          {RULES.map((r, i) => (
            <li key={r} className="flex gap-3 text-sm"><span className="grid size-6 shrink-0 place-items-center rounded-full bg-surface text-xs font-bold">{i + 1}</span>{r}</li>
          ))}
        </ol>
      </Card>
    </div>
  );
}
