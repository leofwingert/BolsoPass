import { LogOut, Mail, ShieldCheck } from 'lucide-react';
import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useAuth } from '@/auth/AuthProvider';
import { mainRoleLabel } from '@/auth/roles';

/** Dados do usuário logado no cabeçalho (avatar, nome, perfil) com menu de saída. */
export function UserMenu() {
  const { user, logout } = useAuth();
  const navigate = useNavigate();
  const [open, setOpen] = useState(false);

  if (!user) {
    return (
      <Link to="/auth/login" className="rounded-lg bg-navy px-4 py-2 text-sm font-semibold text-white">
        Entrar
      </Link>
    );
  }
  const initials = user.name
    .split(' ')
    .slice(0, 2)
    .map((p) => p[0])
    .join('');
  const role = mainRoleLabel(user);
  return (
    <div className="relative">
      <button
        className="flex items-center gap-2.5 rounded-lg p-1 pr-2 hover:bg-surface"
        onClick={() => setOpen((v) => !v)}
        aria-expanded={open}
        aria-haspopup="menu"
      >
        <span className="grid size-9 place-items-center rounded-full bg-gradient-to-br from-brand-500 to-navy text-xs font-bold text-white">
          {initials}
        </span>
        <span className="hidden text-left leading-tight lg:block">
          <span className="block text-sm font-bold">{user.name.split(' ').slice(0, 2).join(' ')}</span>
          <span className="block text-[11px] text-muted">{role}</span>
        </span>
      </button>
      {open && (
        <div
          role="menu"
          className="absolute right-0 z-30 mt-2 w-64 rounded-xl border border-line bg-white p-1.5 shadow-lg"
          onMouseLeave={() => setOpen(false)}
        >
          <div className="border-b border-line px-3 py-2.5">
            <p className="text-sm font-bold">{user.name}</p>
            <p className="mt-0.5 flex items-center gap-1.5 text-xs text-muted">
              <Mail className="size-3.5" aria-hidden /> {user.email}
            </p>
            <p className="mt-0.5 flex items-center gap-1.5 text-xs text-muted">
              <ShieldCheck className="size-3.5" aria-hidden /> {role}
            </p>
          </div>
          <button
            role="menuitem"
            className="mt-1 flex w-full items-center gap-2 rounded-lg px-3 py-2 text-sm hover:bg-surface"
            onClick={async () => {
              await logout();
              navigate('/auth/login');
            }}
          >
            <LogOut className="size-4" aria-hidden /> Sair
          </button>
        </div>
      )}
    </div>
  );
}
