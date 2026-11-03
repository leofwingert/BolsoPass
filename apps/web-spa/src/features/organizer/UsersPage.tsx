import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { ChevronLeft, ChevronRight, Copy, CreditCard, KeyRound, Search, ShieldCheck, ScanLine, UserCog, UserPlus, UserRound } from 'lucide-react';
import { useState, type FormEvent } from 'react';
import { api } from '@/api';
import { errorMessage } from '@/api/errors';
import type { AdminUser, AdminUserFilters, AdminUserResult, NewAdminUser, UserProfile } from '@/api/types';
import { useAuth } from '@/auth/AuthProvider';
import { Modal } from '@/components/Modal';
import { Badge, Button, Card, ErrorState, Field, Spinner, inputClass } from '@/components/ui';
import { ACCOUNT_MESSAGES, MIN_PASSWORD, isStrongEnoughPassword } from '@/domain/account';
import { isValidCpf, maskCpf } from '@/domain/cpf';
import { cx, formatDate, maskPhone } from '@/lib/format';

const PAGE_SIZE = 10;

const PROFILES: { key: UserProfile; label: string; sub: string; icon: typeof UserRound; tone: 'neutral' | 'brand' | 'info' | 'ok' }[] = [
  { key: 'CLIENT', label: 'Cliente', sub: 'Compra ingressos', icon: UserRound, tone: 'neutral' },
  { key: 'ADMIN', label: 'Admin', sub: 'Eventos e usuários', icon: ShieldCheck, tone: 'brand' },
  { key: 'OPERATOR', label: 'Bilheteria', sub: 'Leitor de QR e venda', icon: ScanLine, tone: 'info' },
  { key: 'SELLER', label: 'Venda', sub: 'Só vende no balcão', icon: CreditCard, tone: 'ok' },
];
const profileInfo = (p: UserProfile) => PROFILES.find((x) => x.key === p)!;
const isStaff = (p: UserProfile) => p === 'OPERATOR' || p === 'SELLER';

function ProfileBadge({ profile }: { profile: UserProfile }) {
  const p = profileInfo(profile);
  return <Badge tone={p.tone} icon={<p.icon className="size-3" />}>{p.label}</Badge>;
}

/** Matrícula e PIN gerados: aparecem uma única vez. */
function Credentials({ result, onDone }: { result: AdminUserResult; onDone: () => void }) {
  const [copied, setCopied] = useState(false);
  const c = result.credentials!;
  return (
    <div className="grid gap-4" aria-label="Credenciais da bilheteria">
      <p className="text-sm">
        <b>{result.user.name}</b> agora é <b>{result.user.profileLabel}</b>. Entregue a matrícula e o PIN para o primeiro acesso:
      </p>
      <div className="grid grid-cols-2 gap-3">
        <div className="rounded-xl bg-surface p-4 text-center">
          <p className="text-xs font-bold tracking-wider text-muted uppercase">Matrícula</p>
          <p className="mt-1 font-mono text-2xl font-extrabold">{c.badge}</p>
        </div>
        <div className="rounded-xl bg-surface p-4 text-center">
          <p className="text-xs font-bold tracking-wider text-muted uppercase">PIN</p>
          <p className="mt-1 font-mono text-2xl font-extrabold tracking-widest">{c.pin}</p>
        </div>
      </div>
      <p role="alert" className="flex items-center gap-2 rounded-lg bg-warn-soft px-3 py-2 text-sm font-medium text-amber-800">
        <KeyRound className="size-4 shrink-0" aria-hidden /> Anote agora: o PIN não aparece de novo.
      </p>
      <div className="flex flex-wrap justify-end gap-2">
        <Button
          variant="outline"
          onClick={() => {
            void navigator.clipboard?.writeText(`Matrícula: ${c.badge} | PIN: ${c.pin}`).then(() => setCopied(true));
          }}
        >
          <Copy className="size-4" aria-hidden /> {copied ? 'Copiado' : 'Copiar'}
        </Button>
        <Button onClick={onDone}>Concluir</Button>
      </div>
    </div>
  );
}

function ProfilePicker({ value, onChange, exclude }: { value?: UserProfile; onChange: (p: UserProfile) => void; exclude?: UserProfile }) {
  return (
    <div role="radiogroup" aria-label="Perfil" className="grid grid-cols-2 gap-2">
      {PROFILES.filter((p) => p.key !== exclude).map((p) => (
        <button
          key={p.key}
          type="button"
          role="radio"
          aria-checked={value === p.key}
          onClick={() => onChange(p.key)}
          className={cx('flex items-center gap-2 rounded-xl border-2 p-3 text-left', value === p.key ? 'border-brand-600 bg-brand-50' : 'border-line hover:bg-surface')}
        >
          <p.icon className="size-5 shrink-0 text-brand-600" aria-hidden />
          <span><b className="block text-sm">{p.label}</b><span className="text-[11px] text-muted">{p.sub}</span></span>
        </button>
      ))}
    </div>
  );
}

function CreateUserDialog({ onClose, onSaved }: { onClose: () => void; onSaved: () => void }) {
  const [form, setForm] = useState<NewAdminUser>({ name: '', email: '', cpf: '', phone: '', password: '', profile: 'CLIENT' });
  const [localError, setLocalError] = useState<string>();
  const create = useMutation({ mutationFn: (input: NewAdminUser) => api.admin.createUser(input), onSuccess: onSaved });
  const set = <K extends keyof NewAdminUser>(k: K, v: NewAdminUser[K]) => setForm((f) => ({ ...f, [k]: v }));
  const staff = isStaff(form.profile);

  function submit(e: FormEvent) {
    e.preventDefault();
    if (form.cpf && !isValidCpf(form.cpf)) return setLocalError('CPF inválido');
    if (!staff && !isStrongEnoughPassword(form.password ?? '')) return setLocalError(ACCOUNT_MESSAGES.password);
    setLocalError(undefined);
    create.mutate({ ...form, cpf: form.cpf || undefined, phone: form.phone || undefined, password: staff ? undefined : form.password });
  }

  if (create.data?.credentials) return <Modal title="Usuário cadastrado" onClose={onClose}><Credentials result={create.data} onDone={onClose} /></Modal>;
  if (create.data) {
    return (
      <Modal title="Usuário cadastrado" onClose={onClose}>
        <p className="text-sm"><b>{create.data.user.name}</b> foi cadastrado como <b>{create.data.user.profileLabel}</b> e já pode entrar com o e-mail e a senha definidos.</p>
        <div className="mt-4 flex justify-end"><Button onClick={onClose}>Concluir</Button></div>
      </Modal>
    );
  }

  return (
    <Modal title="Cadastrar usuário" onClose={onClose}>
      <form onSubmit={submit} className="grid gap-4">
        <ProfilePicker value={form.profile} onChange={(p) => set('profile', p)} />
        <Field label="Nome completo"><input className={inputClass} value={form.name} onChange={(e) => set('name', e.target.value)} required /></Field>
        <Field label="E-mail"><input className={inputClass} type="email" value={form.email} onChange={(e) => set('email', e.target.value)} required /></Field>
        <div className="grid gap-4 sm:grid-cols-2">
          <Field label={form.profile === 'CLIENT' ? 'CPF' : 'CPF (opcional)'}>
            <input className={inputClass} inputMode="numeric" value={form.cpf} onChange={(e) => set('cpf', maskCpf(e.target.value))} placeholder="000.000.000-00" required={form.profile === 'CLIENT'} />
          </Field>
          <Field label="Telefone"><input className={inputClass} inputMode="tel" value={form.phone} onChange={(e) => set('phone', maskPhone(e.target.value))} placeholder="(11) 99999-9999" /></Field>
        </div>
        {staff ? (
          <p className="rounded-lg bg-surface px-3 py-2 text-sm text-muted">A matrícula e o PIN da bilheteria são gerados ao salvar.</p>
        ) : (
          <Field label="Senha inicial" hint={`Mínimo de ${MIN_PASSWORD} caracteres`}><input className={inputClass} type="password" value={form.password} onChange={(e) => set('password', e.target.value)} autoComplete="new-password" /></Field>
        )}
        {(localError || create.error) && <p role="alert" className="rounded-lg bg-brand-50 px-3 py-2 text-sm font-medium text-brand-700">{localError ?? errorMessage(create.error)}</p>}
        <div className="flex justify-end gap-2">
          <Button type="button" variant="ghost" onClick={onClose}>Cancelar</Button>
          <Button type="submit" loading={create.isPending}><UserPlus className="size-4" aria-hidden /> Cadastrar</Button>
        </div>
      </form>
    </Modal>
  );
}

function ChangeProfileDialog({ user, onClose, onSaved }: { user: AdminUser; onClose: () => void; onSaved: () => void }) {
  const [profile, setProfile] = useState<UserProfile>();
  const change = useMutation({ mutationFn: (p: UserProfile) => api.admin.changeProfile(user.id, p), onSuccess: onSaved });

  if (change.data?.credentials) return <Modal title="Perfil alterado" onClose={onClose}><Credentials result={change.data} onDone={onClose} /></Modal>;
  if (change.data) {
    return (
      <Modal title="Perfil alterado" onClose={onClose}>
        <p className="text-sm"><b>{change.data.user.name}</b> agora é <b>{change.data.user.profileLabel}</b>.</p>
        <div className="mt-4 flex justify-end"><Button onClick={onClose}>Concluir</Button></div>
      </Modal>
    );
  }

  return (
    <Modal title="Alterar perfil" onClose={onClose}>
      <div className="grid gap-4">
        <div className="flex items-center justify-between gap-2 rounded-xl bg-surface p-3">
          <span className="min-w-0"><b className="block truncate">{user.name}</b><span className="text-xs text-muted">{user.email}</span></span>
          <ProfileBadge profile={user.profile} />
        </div>
        <p className="text-sm font-semibold">Novo perfil</p>
        <ProfilePicker value={profile} onChange={setProfile} exclude={user.profile} />
        <ul className="grid gap-1 rounded-lg bg-warn-soft px-4 py-3 text-xs text-amber-900">
          <li>• Cada conta tem <b>um perfil só</b>: o perfil atual é substituído.</li>
          {user.profile === 'CLIENT' && <li>• Deixa de comprar como cliente; os ingressos já comprados continuam válidos.</li>}
          {profile && isStaff(profile) && <li>• Uma nova matrícula e um novo PIN serão gerados.</li>}
          {isStaff(user.profile) && <li>• A matrícula atual ({user.badge}) deixa de funcionar.</li>}
          <li>• As sessões abertas da pessoa são encerradas.</li>
        </ul>
        {change.error && <p role="alert" className="rounded-lg bg-brand-50 px-3 py-2 text-sm font-medium text-brand-700">{errorMessage(change.error)}</p>}
        <div className="flex justify-end gap-2">
          <Button type="button" variant="ghost" onClick={onClose}>Cancelar</Button>
          <Button disabled={!profile} loading={change.isPending} onClick={() => profile && change.mutate(profile)}>
            <UserCog className="size-4" aria-hidden /> {profile ? `Tornar ${profileInfo(profile).label}` : 'Escolha o perfil'}
          </Button>
        </div>
      </div>
    </Modal>
  );
}

/** Administração › Usuários: todos os usuários, filtros, cadastro e troca de perfil (só ROLE_ADMIN). */
export default function UsersPage() {
  const { user: me } = useAuth();
  const queryClient = useQueryClient();
  const [filters, setFilters] = useState<AdminUserFilters>({ page: 0, size: PAGE_SIZE });
  const [creating, setCreating] = useState(false);
  const [editing, setEditing] = useState<AdminUser>();
  const { data, isLoading, error, refetch, isFetching } = useQuery({
    queryKey: ['admin', 'users', filters],
    queryFn: () => api.admin.listUsers(filters),
    placeholderData: keepPreviousData,
  });

  const update = (patch: Partial<AdminUserFilters>) => setFilters((f) => ({ ...f, ...patch, page: 0 }));
  const saved = () => void queryClient.invalidateQueries({ queryKey: ['admin', 'users'] });
  const pages = data ? Math.max(1, Math.ceil(data.total / data.size)) : 1;
  const page = filters.page ?? 0;
  const hasFilters = !!(filters.q || filters.profile || filters.from || filters.to);

  return (
    <div className="grid gap-6">
      <div className="flex flex-wrap items-end justify-between gap-4">
        <div>
          <p className="text-xs font-semibold"><span className="rounded bg-surface-strong px-1.5 py-0.5 tracking-wider uppercase">Administração</span> <span className="text-brand-600">/ Usuários</span></p>
          <h1 className="mt-2 text-4xl font-extrabold tracking-tight">Usuários e perfis de acesso</h1>
          <p className="mt-1 max-w-2xl text-sm text-muted">Clientes se cadastram sozinhos. Aqui você cadastra a equipe e promove contas para Admin, Bilheteria ou Venda.</p>
        </div>
        <Button onClick={() => setCreating(true)}><UserPlus className="size-4" aria-hidden /> Cadastrar usuário</Button>
      </div>

      <Card className="grid gap-3 p-4">
        <div className="relative">
          <Search className="absolute top-1/2 left-3.5 size-4 -translate-y-1/2 text-muted" aria-hidden />
          <input value={filters.q ?? ''} onChange={(e) => update({ q: e.target.value || undefined })} placeholder="Buscar por nome, e-mail, CPF ou matrícula…" aria-label="Buscar usuários"
            className="h-11 w-full rounded-lg bg-surface pr-3 pl-10 text-sm focus:outline-none" />
        </div>
        <div className="flex flex-wrap items-center gap-2">
          {[{ key: undefined, label: 'Todos' }, ...PROFILES].map((p) => (
            <button key={p.label} type="button" aria-pressed={filters.profile === p.key} onClick={() => update({ profile: p.key })}
              className={cx('rounded-full px-3.5 py-1.5 text-sm font-semibold', filters.profile === p.key ? 'bg-navy text-white' : 'bg-surface hover:bg-surface-strong')}>
              {p.label}
            </button>
          ))}
          <span className="ml-auto flex flex-wrap items-center gap-2 text-sm">
            <label className="flex items-center gap-1.5">Cadastro de <input type="date" aria-label="Cadastrado a partir de" value={filters.from ?? ''} onChange={(e) => update({ from: e.target.value || undefined })} className="h-9 rounded-lg bg-surface px-2" /></label>
            <label className="flex items-center gap-1.5">até <input type="date" aria-label="Cadastrado até" value={filters.to ?? ''} onChange={(e) => update({ to: e.target.value || undefined })} className="h-9 rounded-lg bg-surface px-2" /></label>
            {hasFilters && <Button variant="ghost" size="sm" onClick={() => setFilters({ page: 0, size: PAGE_SIZE })}>Limpar filtros</Button>}
          </span>
        </div>
      </Card>

      {isLoading ? (
        <Spinner label="Carregando usuários…" />
      ) : error || !data ? (
        <ErrorState message={errorMessage(error)} onRetry={() => void refetch()} />
      ) : (
        <Card className={cx('overflow-hidden', isFetching && 'opacity-70')}>
          <div className="flex items-center justify-between border-b border-line px-4 py-3 text-sm">
            <b>{data.total} usuário(s)</b>
            <span className="text-muted">Página {page + 1} de {pages}</span>
          </div>
          {data.items.length === 0 ? (
            <p className="px-4 py-10 text-center text-sm text-muted">Nenhum usuário com esses filtros.</p>
          ) : (
            <ul className="divide-y divide-line" aria-label="Usuários">
              {data.items.map((u) => {
                const self = u.id === me?.id;
                return (
                  <li key={u.id} className="grid gap-2 px-4 py-3 sm:grid-cols-[minmax(0,2fr)_minmax(0,1.2fr)_auto_auto] sm:items-center sm:gap-4">
                    <div className="min-w-0">
                      <p className="truncate font-bold">{u.name} {self && <span className="text-xs font-medium text-muted">(você)</span>}</p>
                      <p className="truncate text-xs text-muted">{u.email}</p>
                    </div>
                    <div className="text-xs text-muted">
                      <p>CPF {u.cpf ?? '—'} • {u.phone ?? 'sem telefone'}</p>
                      <p>Cadastro: {u.createdAt ? formatDate(u.createdAt) : 'conta de demonstração'}</p>
                    </div>
                    <div className="flex flex-wrap items-center gap-1.5">
                      <ProfileBadge profile={u.profile} />
                      {u.badge && <Badge tone="dark">{u.badge}</Badge>}
                      {u.counterAccount && <Badge tone="warn">Criada no balcão</Badge>}
                    </div>
                    <Button variant="outline" size="sm" disabled={self} title={self ? 'Você não pode alterar o próprio perfil' : undefined}
                      onClick={() => setEditing(u)} aria-label={`Alterar perfil de ${u.name}`}>
                      <UserCog className="size-4" aria-hidden /> Alterar perfil
                    </Button>
                  </li>
                );
              })}
            </ul>
          )}
          <div className="flex items-center justify-end gap-2 border-t border-line px-4 py-3">
            <Button variant="soft" size="sm" disabled={page === 0} onClick={() => setFilters((f) => ({ ...f, page: page - 1 }))} aria-label="Página anterior"><ChevronLeft className="size-4" /></Button>
            <Button variant="soft" size="sm" disabled={page + 1 >= pages} onClick={() => setFilters((f) => ({ ...f, page: page + 1 }))} aria-label="Próxima página"><ChevronRight className="size-4" /></Button>
          </div>
        </Card>
      )}

      {creating && <CreateUserDialog onClose={() => setCreating(false)} onSaved={saved} />}
      {editing && <ChangeProfileDialog user={editing} onClose={() => setEditing(undefined)} onSaved={saved} />}
    </div>
  );
}
