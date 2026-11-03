import { UserPlus } from 'lucide-react';
import { useState, type FormEvent } from 'react';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import { errorMessage } from '@/api/errors';
import { useAuth } from '@/auth/AuthProvider';
import { Button, Card, Field, inputClass, Logo } from '@/components/ui';
import { isValidCpf, maskCpf } from '@/domain/cpf';
import { maskPhone } from '@/lib/format';

const MIN_PASSWORD = 8;
const EMAIL_RE = /^[^@\s]+@[^@\s]+\.[^@\s]+$/;

type Form = { name: string; email: string; cpf: string; phone: string; password: string; confirm: string; activationCode: string; terms: boolean };
type Issues = Partial<Record<keyof Form, string>>;

function validate(f: Form): Issues {
  const e: Issues = {};
  if (f.name.trim().split(/\s+/).length < 2) e.name = 'Informe nome e sobrenome';
  if (!EMAIL_RE.test(f.email.trim())) e.email = 'E-mail inválido';
  if (!isValidCpf(f.cpf)) e.cpf = 'CPF inválido';
  if (f.phone.replace(/\D/g, '').length < 10) e.phone = 'Informe um telefone com DDD';
  if (f.password.length < MIN_PASSWORD) e.password = `Mínimo de ${MIN_PASSWORD} caracteres`;
  if (f.confirm !== f.password) e.confirm = 'As senhas não conferem';
  if (!f.terms) e.terms = 'Aceite os termos para continuar';
  return e;
}

/**
 * Cadastro público. Sempre cria um **Cliente**: não há escolha de perfil. Admin, bilheteria e
 * venda são atribuídos só pela administração (página Usuários).
 */
export default function RegisterPage() {
  const { register } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const from = (location.state as { from?: string } | null)?.from;
  const [form, setForm] = useState<Form>({ name: '', email: '', cpf: '', phone: '', password: '', confirm: '', activationCode: '', terms: false });
  const [issues, setIssues] = useState<Issues>({});
  const [error, setError] = useState<string>();
  const [pending, setPending] = useState(false);

  const set = <K extends keyof Form>(k: K, v: Form[K]) => setForm((f) => ({ ...f, [k]: v }));

  async function submit(e: FormEvent) {
    e.preventDefault();
    const found = validate(form);
    setIssues(found);
    setError(undefined);
    if (Object.keys(found).length) return;
    setPending(true);
    try {
      await register({
        name: form.name.trim(),
        email: form.email.trim(),
        cpf: form.cpf,
        phone: form.phone,
        password: form.password,
        acceptTerms: form.terms,
        activationCode: form.activationCode.trim() || undefined,
      });
      // Cliente: volta para onde estava (ex.: mapa de assentos) ou vai ao catálogo.
      navigate(from ?? '/', { replace: true });
    } catch (err) {
      setError(errorMessage(err));
    } finally {
      setPending(false);
    }
  }

  return (
    <div className="mx-auto grid max-w-md gap-4 px-4 py-12">
      <div className="text-center">
        <Logo className="text-3xl" />
        <h1 className="mt-4 text-2xl font-bold">Criar sua conta</h1>
        <p className="mt-1 text-sm text-muted">Para comprar ingressos. Seus ingressos ficam vinculados ao seu CPF.</p>
      </div>

      <Card className="p-6">
        <form onSubmit={submit} noValidate className="grid gap-4">
          <Field label="Nome completo" error={issues.name}>
            <input className={inputClass} value={form.name} onChange={(e) => set('name', e.target.value)} autoComplete="name" />
          </Field>
          <Field label="E-mail" error={issues.email}>
            <input className={inputClass} type="email" value={form.email} onChange={(e) => set('email', e.target.value)} autoComplete="email" placeholder="voce@exemplo.com" />
          </Field>
          <div className="grid gap-4 sm:grid-cols-2">
            <Field label="CPF" error={issues.cpf}>
              <input className={inputClass} inputMode="numeric" value={form.cpf} onChange={(e) => set('cpf', maskCpf(e.target.value))} placeholder="000.000.000-00" />
            </Field>
            <Field label="Telefone" error={issues.phone}>
              <input className={inputClass} inputMode="tel" value={form.phone} onChange={(e) => set('phone', maskPhone(e.target.value))} autoComplete="tel" placeholder="(11) 99999-9999" />
            </Field>
          </div>
          <Field label="Código de ativação (se recebido no balcão)" hint="Necessário para vincular uma compra feita na bilheteria à sua conta.">
            <input className={inputClass} inputMode="numeric" value={form.activationCode} onChange={(e) => set('activationCode', e.target.value)} autoComplete="one-time-code" />
          </Field>
          <div className="grid gap-4 sm:grid-cols-2">
            <Field label="Senha" error={issues.password} hint={issues.password ? undefined : `Mínimo de ${MIN_PASSWORD} caracteres`}>
              <input className={inputClass} type="password" value={form.password} onChange={(e) => set('password', e.target.value)} autoComplete="new-password" />
            </Field>
            <Field label="Confirmar senha" error={issues.confirm}>
              <input className={inputClass} type="password" value={form.confirm} onChange={(e) => set('confirm', e.target.value)} autoComplete="new-password" />
            </Field>
          </div>
          <div>
            <label className="flex items-start gap-2 text-sm">
              <input type="checkbox" className="mt-0.5 size-4 accent-brand-600" checked={form.terms} onChange={(e) => set('terms', e.target.checked)} />
              <span>Li e aceito os termos de uso e a política de privacidade.</span>
            </label>
            {issues.terms && <span data-field-error className="mt-1 block text-xs font-medium text-brand-700">{issues.terms}</span>}
          </div>
          {error && <p role="alert" className="rounded-lg bg-brand-50 px-3 py-2 text-sm font-medium text-brand-700">{error}</p>}
          <Button type="submit" size="lg" loading={pending}>
            <UserPlus className="size-5" aria-hidden /> Criar conta
          </Button>
        </form>
        <p className="mt-4 border-t border-line pt-4 text-center text-sm text-muted">
          Já tem conta?{' '}
          <Link to="/auth/login" state={location.state} className="font-bold text-brand-600 hover:underline">
            Entrar
          </Link>
        </p>
      </Card>

      <p className="text-center text-xs text-muted">
        Comprou na bilheteria? Use o mesmo CPF: sua conta é ativada com os ingressos que você já tem.
      </p>
    </div>
  );
}
