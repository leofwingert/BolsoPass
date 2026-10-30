import { AlertTriangle, Ban, CalendarX, CheckCircle2 } from 'lucide-react';
import type { ScanRecord } from '@/api/types';
import { INVALID_REASON_LABEL, type ScanStatus } from '@/domain/ticketQr';
import { formatTime } from '@/lib/format';

export const SCAN_STYLE: Record<ScanStatus, { label: string; badge: string; panel: string; icon: typeof CheckCircle2; title: string }> = {
  VALIDO: { label: 'Válido', badge: 'bg-ok-soft text-ok', panel: 'bg-gradient-to-br from-emerald-600 to-emerald-700', icon: CheckCircle2, title: 'Acesso liberado' },
  JA_UTILIZADO: { label: 'Já utilizado', badge: 'bg-brand-50 text-brand-700', panel: 'bg-gradient-to-br from-amber-500 to-orange-600', icon: AlertTriangle, title: 'Ingresso já utilizado' },
  INVALIDO: { label: 'Inválido', badge: 'bg-brand-50 text-brand-700', panel: 'bg-gradient-to-br from-brand-600 to-brand-700', icon: Ban, title: 'Acesso negado' },
  EVENTO_INCORRETO: { label: 'Evento incorreto', badge: 'bg-indigo-50 text-indigo-700', panel: 'bg-gradient-to-br from-indigo-600 to-indigo-800', icon: CalendarX, title: 'Ingresso de outro evento' },
};

/** Explicação curta do resultado, para o operador agir. */
export function scanDetail(s: ScanRecord): string {
  if (s.status === 'VALIDO') return s.offline ? 'Entrada registrada no aparelho; será enviada quando a rede voltar.' : 'Entrada registrada.';
  if (s.status === 'JA_UTILIZADO') {
    return s.previousUse
      ? `Entrada anterior às ${formatTime(s.previousUse.at)}${s.previousUse.gate ? ` no ${s.previousUse.gate}` : ''}. Possível cópia: peça documento.`
      : 'Este ingresso já entrou. Possível cópia: peça documento.';
  }
  if (s.status === 'EVENTO_INCORRETO') return 'O ingresso é válido, mas de outro evento. Oriente a pessoa a procurar a bilheteria.';
  return s.reason ? INVALID_REASON_LABEL[s.reason] : 'Código não reconhecido.';
}

/** Bipe + vibração: o operador sabe o resultado sem olhar a tela. */
export function feedback(ok: boolean) {
  try {
    navigator.vibrate?.(ok ? 80 : [120, 80, 120]);
    const Ctx = window.AudioContext ?? (window as unknown as { webkitAudioContext?: typeof AudioContext }).webkitAudioContext;
    if (!Ctx) return;
    const ctx = new Ctx();
    const osc = ctx.createOscillator();
    const gain = ctx.createGain();
    osc.frequency.value = ok ? 1200 : 300;
    gain.gain.value = 0.15;
    osc.connect(gain).connect(ctx.destination);
    osc.start();
    osc.stop(ctx.currentTime + (ok ? 0.12 : 0.35));
    osc.onended = () => void ctx.close();
  } catch {
    /* sem áudio: segue só com a tela */
  }
}
