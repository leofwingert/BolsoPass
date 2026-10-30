import { Bell, CalendarPlus, ExternalLink, LayoutDashboard, ListChecks, ScanLine } from 'lucide-react';
import { Link, NavLink, Outlet } from 'react-router-dom';
import { useAuth } from '@/auth/AuthProvider';
import { canAccess, hasAnyRole } from '@/auth/roles';
import { UserMenu } from '@/components/UserMenu';
import { Logo } from '@/components/ui';
import { cx } from '@/lib/format';

const navClass = ({ isActive }: { isActive: boolean }) =>
  cx(
    'flex items-center gap-2 rounded-lg px-3.5 py-2 text-sm font-semibold whitespace-nowrap transition-colors',
    isActive ? 'bg-brand-600 text-white' : 'text-ink/80 hover:bg-surface',
  );

/**
 * Moldura da área administrativa (organizador / admin): cabeçalho próprio,
 * sem a busca e os atalhos de compra do cliente.
 */
export default function OrganizerLayout() {
  const { user } = useAuth();
  const isAdmin = hasAnyRole(user, ['ROLE_ADMIN']);

  const links = (
    <>
      <NavLink to="/organizer/dashboard" className={navClass}>
        <LayoutDashboard className="size-4" aria-hidden /> Painel
      </NavLink>
      <NavLink to="/organizer/events" end className={navClass}>
        <ListChecks className="size-4" aria-hidden /> Meus eventos
      </NavLink>
      <NavLink to="/organizer/events/new" className={navClass}>
        <CalendarPlus className="size-4" aria-hidden /> Criar evento
      </NavLink>
      {canAccess(user, 'operator') && (
        <NavLink to="/operator/setup" className={navClass}>
          <ScanLine className="size-4" aria-hidden /> Bilheteria
        </NavLink>
      )}
    </>
  );

  return (
    <div className="flex min-h-dvh flex-col bg-surface/40">
      <header className="sticky top-0 z-20 border-b border-line bg-white/95 backdrop-blur">
        <div className="mx-auto flex h-16 max-w-7xl items-center gap-3 px-4 lg:gap-6">
          <Link to="/organizer/dashboard" aria-label="BolsoPass - painel" className="flex items-center gap-2">
            <Logo />
            <span className="hidden rounded-md bg-navy px-2 py-0.5 text-[10px] font-bold tracking-wider text-white uppercase sm:block">
              {isAdmin ? 'Admin' : 'Produtor'}
            </span>
          </Link>
          <nav className="ml-auto hidden items-center gap-1 lg:flex" aria-label="Administração">
            {links}
          </nav>
          <div className="ml-auto flex items-center gap-2 lg:ml-0">
            <Link to="/" target="_blank" className="hidden items-center gap-1.5 rounded-lg px-3 py-2 text-xs font-semibold text-muted hover:bg-surface md:flex" title="Abrir o catálogo como o comprador vê">
              <ExternalLink className="size-3.5" aria-hidden /> Ver site público
            </Link>
            <button className="relative rounded-lg p-2 hover:bg-surface" aria-label="Notificações">
              <Bell className="size-5" />
              <span className="absolute top-1.5 right-1.5 size-2 rounded-full bg-brand-600" />
            </button>
            <UserMenu />
          </div>
        </div>
        <nav className="flex gap-1 overflow-x-auto px-4 pb-2 lg:hidden" aria-label="Administração (celular)">
          {links}
        </nav>
      </header>

      <main className="mx-auto w-full max-w-7xl flex-1 px-4 py-6">
        <Outlet />
      </main>

      <footer className="border-t border-line bg-white">
        <div className="mx-auto flex max-w-7xl flex-wrap items-center gap-x-6 gap-y-2 px-4 py-4 text-xs text-muted">
          <span className="flex items-center gap-1.5"><span className="size-2 rounded-full bg-ok" /> Pagamentos: operando</span>
          <span className="flex items-center gap-1.5"><span className="size-2 rounded-full bg-ok" /> Proteção contra venda duplicada: ativa</span>
          <span className="ml-auto">{isAdmin ? 'Você vê os eventos de todos os produtores.' : 'Você vê apenas os seus eventos.'}</span>
        </div>
      </footer>
    </div>
  );
}
