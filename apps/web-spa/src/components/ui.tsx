import { Loader2 } from 'lucide-react';
import type { ButtonHTMLAttributes, HTMLAttributes, ReactNode } from 'react';
import { cx } from '@/lib/format';

type Variant = 'primary' | 'dark' | 'soft' | 'ghost' | 'danger' | 'success' | 'outline';
type Size = 'sm' | 'md' | 'lg';

const variants: Record<Variant, string> = {
  primary: 'bg-brand-600 text-white hover:bg-brand-700 shadow-sm shadow-brand-600/20',
  dark: 'bg-navy text-white hover:bg-ink',
  soft: 'bg-surface text-ink hover:bg-surface-strong',
  ghost: 'text-ink hover:bg-surface',
  danger: 'bg-brand-50 text-brand-700 hover:bg-brand-100',
  success: 'bg-ok text-white hover:bg-emerald-800',
  outline: 'border border-line bg-white text-ink hover:bg-surface',
};

const sizes: Record<Size, string> = {
  sm: 'h-8 px-3 text-xs gap-1.5',
  md: 'h-10 px-4 text-sm gap-2',
  lg: 'h-13 px-6 text-base gap-2.5',
};

export function Button({
  variant = 'primary',
  size = 'md',
  loading,
  className,
  children,
  disabled,
  ...props
}: ButtonHTMLAttributes<HTMLButtonElement> & {
  variant?: Variant;
  size?: Size;
  loading?: boolean;
}) {
  return (
    <button
      className={cx(
        'inline-flex items-center justify-center rounded-lg font-semibold transition-colors disabled:cursor-not-allowed disabled:opacity-50',
        variants[variant],
        sizes[size],
        className,
      )}
      disabled={disabled || loading}
      {...props}
    >
      {loading && <Loader2 className="size-4 animate-spin" aria-hidden />}
      {children}
    </button>
  );
}

type Tone = 'brand' | 'ok' | 'warn' | 'neutral' | 'dark' | 'info';

const tones: Record<Tone, string> = {
  brand: 'bg-brand-50 text-brand-700',
  ok: 'bg-ok-soft text-ok',
  warn: 'bg-warn-soft text-amber-800',
  neutral: 'bg-surface text-muted',
  dark: 'bg-navy text-white',
  info: 'bg-indigo-50 text-indigo-700',
};

export function Badge({
  tone = 'neutral',
  className,
  children,
  icon,
}: {
  tone?: Tone;
  className?: string;
  children: ReactNode;
  icon?: ReactNode;
}) {
  return (
    <span
      className={cx(
        'inline-flex items-center gap-1 rounded-full px-2.5 py-0.5 text-[11px] font-bold tracking-wide uppercase',
        tones[tone],
        className,
      )}
    >
      {icon}
      {children}
    </span>
  );
}

export function Card({ className, ...props }: HTMLAttributes<HTMLDivElement>) {
  return (
    <div
      // Fundo próprio (ex.: bg-navy) substitui o branco; sem isso o branco venceria no CSS.
      className={cx(
        'rounded-2xl border border-line/70 shadow-sm shadow-slate-200/50',
        !/(^|\s)bg-/.test(className ?? '') && 'bg-white',
        className,
      )}
      {...props}
    />
  );
}

export function Progress({
  value,
  tone = 'brand',
  className,
  label,
}: {
  value: number;
  tone?: 'brand' | 'ok' | 'dark' | 'warn';
  className?: string;
  label?: string;
}) {
  const color = { brand: 'bg-brand-600', ok: 'bg-ok', dark: 'bg-navy', warn: 'bg-warn' }[tone];
  const v = Math.max(0, Math.min(100, value));
  return (
    <div
      className={cx('h-1.5 w-full overflow-hidden rounded-full bg-surface-strong', className)}
      role="progressbar"
      aria-valuenow={Math.round(v)}
      aria-valuemin={0}
      aria-valuemax={100}
      aria-label={label}
    >
      <div className={cx('h-full rounded-full transition-[width] duration-500', color)} style={{ width: `${v}%` }} />
    </div>
  );
}

export function SectionLabel({ children, className }: { children: ReactNode; className?: string }) {
  return (
    <p className={cx('text-[11px] font-bold tracking-[0.12em] text-muted uppercase', className)}>{children}</p>
  );
}

export function StatCard({
  label,
  value,
  hint,
  icon,
  className,
  children,
}: {
  label: string;
  value: ReactNode;
  hint?: ReactNode;
  icon?: ReactNode;
  className?: string;
  children?: ReactNode;
}) {
  return (
    <Card className={cx('p-5', className)}>
      <div className="flex items-start justify-between gap-2">
        <SectionLabel>{label}</SectionLabel>
        {icon && <span className="grid size-8 shrink-0 place-items-center rounded-lg bg-surface text-brand-600">{icon}</span>}
      </div>
      <div className="mt-3 text-3xl font-bold tracking-tight tabular">{value}</div>
      {hint && <div className="mt-1 text-xs text-muted">{hint}</div>}
      {children}
    </Card>
  );
}

export function Field({
  label,
  hint,
  error,
  children,
  className,
}: {
  label: string;
  hint?: ReactNode;
  error?: string;
  children: ReactNode;
  className?: string;
}) {
  return (
    <label className={cx('block', className)}>
      <span className="mb-1.5 flex items-center justify-between text-sm font-semibold">
        {label}
        {hint && <span className="text-xs font-normal text-muted">{hint}</span>}
      </span>
      {children}
      {error && <span className="mt-1 block text-xs font-medium text-brand-700">{error}</span>}
    </label>
  );
}

export const inputClass =
  'h-11 w-full rounded-lg border border-transparent bg-surface px-3.5 text-sm text-ink placeholder:text-muted/70 focus:border-brand-500 focus:bg-white focus:outline-none';

export function Spinner({ label = 'Carregando…' }: { label?: string }) {
  return (
    <div className="flex items-center justify-center gap-2 py-16 text-sm text-muted" role="status">
      <Loader2 className="size-5 animate-spin" aria-hidden />
      {label}
    </div>
  );
}

export function ErrorState({ message, onRetry }: { message: string; onRetry?: () => void }) {
  return (
    <Card className="mx-auto my-10 max-w-md p-6 text-center">
      <p className="font-semibold">Não foi possível carregar</p>
      <p className="mt-1 text-sm text-muted">{message}</p>
      {onRetry && (
        <Button variant="soft" className="mt-4" onClick={onRetry}>
          Tentar de novo
        </Button>
      )}
    </Card>
  );
}

/** Capa de evento: gradiente no lugar da foto até existir upload de imagens. */
export function EventCover({
  hue,
  className,
  children,
}: {
  hue: number;
  className?: string;
  children?: ReactNode;
}) {
  return (
    <div
      className={cx('relative overflow-hidden', className)}
      style={{
        background: `radial-gradient(120% 90% at 20% 10%, hsl(${hue} 90% 60% / .9), transparent 60%),
          radial-gradient(90% 80% at 85% 90%, hsl(${(hue + 60) % 360} 85% 55% / .8), transparent 60%),
          linear-gradient(160deg, hsl(${hue} 60% 18%), hsl(${(hue + 40) % 360} 55% 10%))`,
      }}
    >
      <div
        className="absolute inset-0 opacity-25"
        style={{
          backgroundImage:
            'linear-gradient(rgba(255,255,255,.18) 1px, transparent 1px), linear-gradient(90deg, rgba(255,255,255,.18) 1px, transparent 1px)',
          backgroundSize: '28px 28px',
          maskImage: 'linear-gradient(to top, black, transparent 75%)',
        }}
        aria-hidden
      />
      {children}
    </div>
  );
}

export function Logo({ className }: { className?: string }) {
  return (
    <span className={cx('text-xl font-extrabold tracking-tight', className)}>
      Bolso<span className="text-brand-600">Pass</span>
    </span>
  );
}
