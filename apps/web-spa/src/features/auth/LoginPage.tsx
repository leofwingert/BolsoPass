import { LogIn, ScanLine, ShieldCheck, UserRound } from 'lucide-react';
import { useState, type FormEvent } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import { errorMessage } from '@/api/errors';
import type { User } from '@/api/types';
import { useAuth } from '@/auth/AuthProvider';
import { homeFor } from '@/auth/roles';
import { Button, Card, Field, inputClass, Logo } from '@/components/ui';
import { cx } from '@/lib/format';

/** Contas do seed do backend. O acesso rápido só preenche e envia o mesmo login normal. */
const QUICK = [
  { key: 'cliente', label: 'Cliente', sub: 'Compra de ingressos', icon: UserRound, id: 'sarah@bolsopass.io', secret: 'bolsopass', operator: false },
  { key: 'admin', label: 'Admin', sub: 'Gestão de eventos', icon: ShieldCheck, id: 'admin@bolsopass.io', secret: 'bolsopass', operator: false },
  { key: 'bilheteria', label: 'Bilheteria', sub: 'Venda e check-in', icon: ScanLine, id: 'OP-88219', secret: '123456', operator: true },
] as const;

/**
 * Login único. Depois de entrar, cada perfil vai para a sua área:
 * admin/organizador -> painel, operador -> bilheteria, cliente -> catálogo.
 */
export default function LoginPage() {
  const { login, loginOperator } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const from = (location.state as { from?: string } | null)?.from;
  const [id, setId] = useState('');
  const [secret, setSecret] = useState('');
  const [error, setError] = useState<string>();
  const [pending, setPending] = useState<string>();

  async function enter(identifier: string, password: string, key: string) {
    setError(undefined);
    setPending(key);
    try {
      // Matrícula de operador (ex.: OP-88219) usa o login da portaria; e-mail usa o login normal.
      const isBadge = !identifier.includes('@');
      const user: User = isBadge ? await loginOperator(identifier, password) : await login(identifier, password);
      const home = homeFor(user);
      // Só volta para a página anterior se ela for da área do próprio perfil.
      navigate(from && (home === '/' || from.startsWith(home.split('/').slice(0, 2).join('/'))) ? from : home, { replace: true });
    } catch (err) {
      setError(errorMessage(err));
    } finally {
      setPending(undefined);
    }
  }

  function submit(e: FormEvent) {
    e.preventDefault();
    void enter(id.trim(), secret, 'form');
  }

  return (
    <div className="mx-auto grid max-w-md gap-4 px-4 py-12">
      <div className="text-center">
        <Logo className="text-3xl" />
        <h1 className="mt-4 text-2xl font-bold">Entrar na sua conta</h1>
        <p className="mt-1 text-sm text-muted">
          {from ? 'Faça login para continuar de onde parou.' : 'Clientes, organizadores e equipe de bilheteria entram por aqui.'}
        </p>
      </div>

      <Card className="p-6">
        <form onSubmit={submit} className="grid gap-4">
          <Field label="E-mail ou matrícula" hint="Bilheteria: use a matrícula">
            <input className={inputClass} value={id} onChange={(e) => setId(e.target.value)} autoComplete="username" placeholder="voce@exemplo.com" required />
          </Field>
          <Field label="Senha ou PIN">
            <input className={inputClass} type="password" value={secret} onChange={(e) => setSecret(e.target.value)} autoComplete="current-password" required />
          </Field>
          {error && <p role="alert" className="rounded-lg bg-brand-50 px-3 py-2 text-sm font-medium text-brand-700">{error}</p>}
          <Button type="submit" size="lg" loading={pending === 'form'} disabled={!!pending}>
            <LogIn className="size-5" aria-hidden /> Entrar
          </Button>
        </form>
      </Card>

      <div>
        <p className="mb-2 text-center text-xs font-bold tracking-wider text-muted uppercase">Acesso rápido (demonstração)</p>
        <div className="grid grid-cols-3 gap-2">
          {QUICK.map((q) => (
            <button
              key={q.key}
              type="button"
              disabled={!!pending}
              onClick={() => void enter(q.id, q.secret, q.key)}
              className={cx(
                'flex flex-col items-center gap-1 rounded-xl border border-line bg-white p-3 text-center transition hover:border-brand-500 hover:shadow-sm disabled:opacity-60',
                pending === q.key && 'border-brand-600',
              )}
            >
              <q.icon className="size-6 text-brand-600" aria-hidden />
              <span className="text-sm font-bold">{pending === q.key ? 'Entrando…' : q.label}</span>
              <span className="text-[11px] leading-tight text-muted">{q.sub}</span>
            </button>
          ))}
        </div>
        <p className="mt-2 text-center text-[11px] text-muted">
          Senhas: <span className="font-mono">bolsopass</span> (cliente e admin) • PIN <span className="font-mono">123456</span> (bilheteria)
        </p>
      </div>
    </div>
  );
}
