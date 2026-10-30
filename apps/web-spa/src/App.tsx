import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import type { ReactNode } from 'react';
import { RouterProvider } from 'react-router-dom';
import { ApiError } from '@/api/errors';
import { AuthProvider, useAuth } from '@/auth/AuthProvider';
import { ActiveReservationProvider } from '@/features/client/activeReservation';
import { router } from './router';

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      staleTime: 15_000,
      refetchOnWindowFocus: false,
      // Erros de regra (4xx) não adiantam repetir.
      retry: (count, err) => !(err instanceof ApiError && err.status < 500) && count < 2,
    },
  },
});

/** Remonta a reserva ativa a cada troca de usuário. */
function PerUser({ children }: { children: ReactNode }) {
  const { user } = useAuth();
  return (
    <ActiveReservationProvider key={user?.id ?? 'anon'} userId={user?.id}>
      {children}
    </ActiveReservationProvider>
  );
}

export function App() {
  return (
    <QueryClientProvider client={queryClient}>
      <AuthProvider>
        <PerUser>
          <RouterProvider router={router} />
        </PerUser>
      </AuthProvider>
    </QueryClientProvider>
  );
}
