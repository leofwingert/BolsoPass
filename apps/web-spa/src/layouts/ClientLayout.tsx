import { Bell, Search, ShoppingBag, Timer, UserRound } from 'lucide-react';
import { useEffect, useState, type FormEvent } from 'react';
import { Link, NavLink, Outlet, useNavigate } from 'react-router-dom';
import { useAuth } from '@/auth/AuthProvider';
import { canAccess } from '@/auth/roles';
import { UserMenu } from '@/components/UserMenu';
import { Logo } from '@/components/ui';
import { useActiveReservation } from '@/features/client/activeReservation';
import { formatCountdown } from '@/domain/reservation';
import { useCountdown } from '@/hooks/useCountdown';
import { cx } from '@/lib/format';

const navClass = ({ isActive }: { isActive: boolean }) =>
  cx(
    'rounded-lg px-3.5 py-2 text-sm font-semibold whitespace-nowrap transition-colors',
    isActive ? 'bg-brand-600 text-white' : 'text-ink/80 hover:bg-surface',
  );

function ReservationPill() {
  const { active, clear } = useActiveReservation();
  const { ms, phase } = useCountdown(active?.expiresAt, active?.offsetMs);
  // Reserva expirada: o assento já voltou ao catálogo, o selo some.
  useEffect(() => {
    if (active && phase === 'expired') clear();
  }, [active, phase, clear]);
  if (!active || phase === 'expired') return null;
  return (
    <Link
      to={`/checkout/${active.id}`}
      className={cx(
        'flex items-center gap-1.5 rounded-full border px-2.5 py-1 text-xs font-bold tabular',
        phase === 'warning'
          ? 'animate-pulse border-brand-600 bg-brand-600 text-white'
          : 'border-brand-200 bg-brand-50 text-brand-700',
      )}
      title="Reserva ativa: voltar ao checkout"
    >
      <Timer className="size-3.5" aria-hidden />
      {formatCountdown(ms)}
      <span className="opacity-40">•</span>
      <ShoppingBag className="size-3.5" aria-hidden />
      {active.seatCount}
    </Link>
  );
}

export function ClientLayout() {
  const { user } = useAuth();
  const navigate = useNavigate();
  const [q, setQ] = useState('');

  const onSearch = (e: FormEvent) => {
    e.preventDefault();
    navigate(q.trim() ? `/?q=${encodeURIComponent(q.trim())}` : '/');
  };

  return (
    <div className="flex min-h-dvh flex-col">
      <header className="sticky top-0 z-20 border-b border-line bg-white/95 backdrop-blur">
        <div className="mx-auto flex h-16 max-w-7xl items-center gap-3 px-4 lg:gap-6">
          <Link to="/" aria-label="BolsoPass - início">
            <Logo />
          </Link>
          <form onSubmit={onSearch} className="relative hidden w-64 md:block">
            <Search className="absolute top-1/2 left-3 size-4 -translate-y-1/2 text-muted" aria-hidden />
            <input
              value={q}
              onChange={(e) => setQ(e.target.value)}
              placeholder="Buscar eventos, arenas, shows…"
              aria-label="Buscar eventos"
              className="h-9 w-full rounded-lg bg-surface pr-3 pl-9 text-sm placeholder:text-muted focus:outline-none"
            />
          </form>
          <nav className="ml-auto hidden items-center gap-1 lg:flex" aria-label="Principal">
            <NavLink to="/" end className={navClass}>
              Explorar Eventos
            </NavLink>
            {canAccess(user, 'client') && (
              <>
                <NavLink to="/wallet" className={navClass}>
                  Minha Carteira
                </NavLink>
                <NavLink to="/my-orders" className={navClass}>
                  Meus Pedidos
                </NavLink>
              </>
            )}
            {canAccess(user, 'organizer') && (
              <NavLink to="/organizer/dashboard" className={navClass}>
                Painel administrativo
              </NavLink>
            )}
            {canAccess(user, 'operator') && (
              <NavLink to="/operator/setup" className={navClass}>
                Operação Portaria
              </NavLink>
            )}
          </nav>
          <div className="ml-auto flex items-center gap-2 lg:ml-0">
            {canAccess(user, 'client') && <ReservationPill />}
            {user && (
              <button className="relative rounded-lg p-2 hover:bg-surface" aria-label="Notificações">
                <Bell className="size-5" />
                <span className="absolute top-1.5 right-1.5 size-2 rounded-full bg-brand-600" />
              </button>
            )}
            <UserMenu />
          </div>
        </div>
        {/* Navegação compacta para celular */}
        <nav className="flex gap-1 overflow-x-auto px-4 pb-2 lg:hidden" aria-label="Principal (celular)">
          <NavLink to="/" end className={navClass}>
            Eventos
          </NavLink>
          {canAccess(user, 'client') && (
            <>
              <NavLink to="/wallet" className={navClass}>
                Carteira
              </NavLink>
              <NavLink to="/my-orders" className={navClass}>
                Pedidos
              </NavLink>
            </>
          )}
          {canAccess(user, 'organizer') && (
            <NavLink to="/organizer/dashboard" className={navClass}>
              Painel
            </NavLink>
          )}
        </nav>
      </header>

      <main className="flex-1">
        <Outlet />
      </main>

      <footer className="border-t border-line bg-white">
        <div className="mx-auto flex max-w-7xl flex-col gap-3 px-4 py-6 text-xs text-muted md:flex-row md:items-center">
          <div className="flex items-center gap-3">
            <Logo className="text-base" />
            <span>© 2026 BolsoPass. Alta concorrência & bilhetagem segura.</span>
          </div>
          <div className="flex gap-5 md:ml-auto">
            <span>Status da rede</span>
            <span>Privacidade</span>
            <span>Termos de serviço</span>
            <span className="flex items-center gap-1">
              <UserRound className="size-3.5" aria-hidden /> Central de suporte
            </span>
          </div>
        </div>
      </footer>
    </div>
  );
}
