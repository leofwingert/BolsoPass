/** Acesso ao storage do navegador que nunca quebra a tela (modo privado, storage bloqueado). */
type Kind = 'local' | 'session';

function backend(kind: Kind): Storage | undefined {
  try {
    return kind === 'local' ? window.localStorage : window.sessionStorage;
  } catch {
    return undefined;
  }
}

export const storage = {
  get<T>(key: string, kind: Kind = 'local'): T | undefined {
    try {
      const raw = backend(kind)?.getItem(key);
      return raw ? (JSON.parse(raw) as T) : undefined;
    } catch {
      return undefined;
    }
  },
  set(key: string, value: unknown, kind: Kind = 'local'): void {
    try {
      backend(kind)?.setItem(key, JSON.stringify(value));
    } catch {
      /* storage indisponível: segue só em memória */
    }
  },
  remove(key: string, kind: Kind = 'local'): void {
    try {
      backend(kind)?.removeItem(key);
    } catch {
      /* ignore */
    }
  },
};
