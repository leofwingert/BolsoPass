import { DoorOpen, History, QrCode, Wifi, WifiOff } from 'lucide-react';
import { useEffect } from 'react';
import { Link, NavLink, Outlet, useLocation } from 'react-router-dom';
import { UserMenu } from '@/components/UserMenu';
import { Logo } from '@/components/ui';
import { cx } from '@/lib/format';
import { OperatorProvider, useOperator } from './OperatorProvider';

const TITLES: Record<string, string> = {
  '/operator/scan': 'Leitor QR',
  '/operator/history': 'Histórico de leituras',
  '/operator/setup': 'Posto de leitura',
  '/operator/offline': 'Modo contingência',
};

const tab = ({ isActive }: { isActive: boolean }) =>
  cx('flex flex-1 flex-col items-center gap-0.5 py-2 text-xs font-semibold', isActive ? 'text-brand-600' : 'text-muted');

function Shell() {
  const { station, online, pending } = useOperator();
  const { pathname } = useLocation();
  return (
    <div className="mx-auto flex min-h-dvh max-w-xl flex-col bg-surface/40">
      <header className="sticky top-0 z-20 border-b border-line bg-white/95 px-4 py-2.5 backdrop-blur">
        <div className="flex items-center gap-3">
          <Link to="/operator/scan" aria-label="BolsoPass - portaria"><Logo className="text-lg" /></Link>
          <Link
            to="/operator/offline"
            className={cx('flex items-center gap-1.5 rounded-full px-2.5 py-1 text-[11px] font-bold', online ? 'bg-ok-soft text-ok' : 'bg-brand-600 text-white')}
            title="Situação da conexão"
          >
            {online ? <Wifi className="size-3.5" aria-hidden /> : <WifiOff className="size-3.5" aria-hidden />}
            {online ? 'Online' : 'Offline'}
            {pending > 0 && <span className="rounded-full bg-white/80 px-1.5 text-brand-700">{pending} na fila</span>}
          </Link>
          <div className="ml-auto"><UserMenu /></div>
        </div>
        <div className="mt-1.5 flex items-baseline justify-between gap-2">
          <h1 className="truncate text-xl font-extrabold">{TITLES[pathname] ?? 'Portaria'}</h1>
          {station && <span className="truncate text-xs font-semibold text-muted">{station.gate.name}</span>}
        </div>
      </header>

      <main className="flex-1 px-4 py-4 pb-24">
        <Outlet />
      </main>

      <nav className="fixed inset-x-0 bottom-0 z-20 mx-auto flex max-w-xl border-t border-line bg-white pb-[env(safe-area-inset-bottom)]" aria-label="Portaria">
        <NavLink to="/operator/scan" className={tab}><QrCode className="size-5" aria-hidden /> Leitor QR</NavLink>
        <NavLink to="/operator/history" className={tab}><History className="size-5" aria-hidden /> Histórico</NavLink>
        <NavLink to="/operator/setup" className={tab}><DoorOpen className="size-5" aria-hidden /> Portão</NavLink>
      </nav>
    </div>
  );
}

/** App da portaria: tela de celular (também funciona no computador), com navegação inferior. */
export default function OperatorLayout() {
  // Baixa todas as telas da portaria já na entrada: se a rede cair, a navegação continua.
  useEffect(() => {
    void import('./OperatorScanPage');
    void import('./OperatorHistoryPage');
    void import('./OperatorOfflinePage');
    void import('./OperatorSetupPage');
    void import('jsqr');
  }, []);
  return (
    <OperatorProvider>
      <Shell />
    </OperatorProvider>
  );
}
