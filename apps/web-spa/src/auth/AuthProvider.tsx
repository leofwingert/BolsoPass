import { useQueryClient } from '@tanstack/react-query';
import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useRef,
  useState,
  type ReactNode,
} from 'react';
import { api } from '@/api';
import { onUnauthorized, refreshAccessToken, setAccessToken, setRefresher } from '@/api/session';
import type { Session, User } from '@/api/types';
import { storage } from '@/lib/storage';

interface AuthContextValue {
  user?: User;
  session?: Session;
  login(email: string, password: string): Promise<User>;
  loginOperator(badge: string, pin: string): Promise<User>;
  logout(): Promise<void>;
}

const AuthContext = createContext<AuthContextValue | null>(null);
const KEY = 'bp.session';
/** Renova 1 min antes do access token vencer. */
const REFRESH_EARLY_MS = 60_000;

// A sessão sobrevive a recarregar a aba (sessionStorage), mas não a fechar o navegador.
const initial = storage.get<Session>(KEY, 'session');
setAccessToken(initial?.accessToken);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [session, setSession] = useState<Session | undefined>(initial);
  const sessionRef = useRef(session);
  const queryClient = useQueryClient();

  const apply = useCallback(
    (s: Session | undefined, opts: { keepCache?: boolean } = {}) => {
      sessionRef.current = s;
      setAccessToken(s?.accessToken);
      if (s) storage.set(KEY, s, 'session');
      else storage.remove(KEY, 'session');
      setSession(s);
      if (!opts.keepCache) queryClient.clear();
    },
    [queryClient],
  );

  // Renovação via refresh token (rotação a cada uso, RF-01).
  useEffect(() => {
    setRefresher(async () => {
      const rt = sessionRef.current?.refreshToken;
      if (!rt) return undefined;
      const next = await api.auth.refresh(rt);
      apply(next, { keepCache: true });
      return next.accessToken;
    });
    return () => setRefresher(undefined);
  }, [apply]);

  // Sem como renovar: encerra a sessão.
  useEffect(() => onUnauthorized(() => apply(undefined)), [apply]);

  // Renova antes de vencer, para o usuário não ser derrubado no meio do checkout.
  useEffect(() => {
    if (!session) return;
    const ms = Date.parse(session.expiresAt) - Date.now() - REFRESH_EARLY_MS;
    const t = setTimeout(
      async () => {
        const fresh = await refreshAccessToken();
        if (!fresh && Date.parse(session.expiresAt) <= Date.now()) apply(undefined);
      },
      Math.max(0, ms),
    );
    return () => clearTimeout(t);
  }, [session, apply]);

  const value = useMemo<AuthContextValue>(
    () => ({
      user: session?.user,
      session,
      async login(email, password) {
        const s = await api.auth.login(email, password);
        apply(s);
        return s.user;
      },
      async loginOperator(badge, pin) {
        const s = await api.auth.loginOperator(badge, pin);
        apply(s);
        return s.user;
      },
      async logout() {
        await api.auth.logout(sessionRef.current?.refreshToken).catch(() => undefined);
        apply(undefined);
      },
    }),
    [session, apply],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthContextValue {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth precisa estar dentro de <AuthProvider>');
  return ctx;
}
