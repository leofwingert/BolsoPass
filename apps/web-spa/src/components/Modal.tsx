import { X } from 'lucide-react';
import type { ReactNode } from 'react';
import { cx } from '@/lib/format';

/**
 * Janela sobre a tela: no celular sobe de baixo, no computador fica no centro.
 * Clicar fora ou no X fecha. {@code label} é o nome acessível (padrão: o título).
 */
export function Modal({
  title,
  label,
  onClose,
  children,
  className,
}: {
  title: string;
  label?: string;
  onClose: () => void;
  children: ReactNode;
  className?: string;
}) {
  return (
    <div className="fixed inset-0 z-40 flex items-end justify-center bg-navy/50 sm:items-center sm:p-4" onClick={onClose}>
      <div
        role="dialog"
        aria-modal="true"
        aria-label={label ?? title}
        className={cx('max-h-[90vh] w-full max-w-lg overflow-y-auto rounded-t-2xl bg-white p-5 shadow-2xl sm:rounded-2xl', className)}
        onClick={(e) => e.stopPropagation()}
      >
        <div className="mb-3 flex items-center justify-between gap-2">
          <h2 className="text-lg font-bold">{title}</h2>
          <button type="button" onClick={onClose} className="rounded-lg p-1.5 hover:bg-surface" aria-label="Fechar">
            <X className="size-5" />
          </button>
        </div>
        {children}
      </div>
    </div>
  );
}
