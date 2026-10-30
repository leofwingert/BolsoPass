import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import type { ReactNode } from 'react';
import { RouterProvider, createMemoryRouter } from 'react-router-dom';
import { setAccessToken } from '@/api/session';
import { AuthProvider, useAuth } from '@/auth/AuthProvider';
import { ActiveReservationProvider } from '@/features/client/activeReservation';
import { routes } from '@/router';

function PerUser({ children }: { children: ReactNode }) {
  const { user } = useAuth();
  return (
    <ActiveReservationProvider key={user?.id ?? 'anon'} userId={user?.id}>
      {children}
    </ActiveReservationProvider>
  );
}

/**
 * Monta a aplicação inteira (rotas reais + API simulada em memória) a partir de uma URL.
 * É o mesmo App de produção, só que com roteador em memória e sem novas tentativas de rede.
 */
export function renderApp(path = '/') {
  sessionStorage.clear();
  localStorage.clear();
  setAccessToken(undefined);
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false, refetchOnWindowFocus: false } } });
  const router = createMemoryRouter(routes, { initialEntries: [path] });
  const user = userEvent.setup();
  render(
    <QueryClientProvider client={queryClient}>
      <AuthProvider>
        <PerUser>
          <RouterProvider router={router} />
        </PerUser>
      </AuthProvider>
    </QueryClientProvider>,
  );
  return { user, router };
}

export type Profile = 'Cliente' | 'Admin' | 'Bilheteria';

/** Abre a URL; como ela é protegida, cai no login e entra pelo botão de acesso rápido do perfil. */
export async function openAs(profile: Profile, path: string) {
  const app = renderApp(path);
  const quick = await screen.findByRole('button', { name: new RegExp(profile) });
  await app.user.click(quick);
  return app;
}
