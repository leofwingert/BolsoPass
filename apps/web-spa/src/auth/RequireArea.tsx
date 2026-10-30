import { ShieldX } from 'lucide-react';
import type { ReactNode } from 'react';
import { Link, Navigate, useLocation } from 'react-router-dom';
import { Button, Card } from '@/components/ui';
import { useAuth } from './AuthProvider';
import { canAccess, homeFor, type Area } from './roles';

/**
 * Guarda de rota por perfil (RBAC).
 * Sem sessão -> login (operador tem login próprio). Perfil errado -> tela 403.
 */
export function RequireArea({ area, children }: { area: Area; children: ReactNode }) {
  const { user } = useAuth();
  const location = useLocation();

  if (!user) {
    // Login único para todos os perfis.
    return <Navigate to="/auth/login" replace state={{ from: location.pathname + location.search }} />;
  }
  if (!canAccess(user, area)) return <Forbidden home={homeFor(user)} />;
  return <>{children}</>;
}

export function Forbidden({ home = '/' }: { home?: string }) {
  return (
    <div className="grid min-h-[60vh] place-items-center px-4">
      <Card className="max-w-md p-8 text-center">
        <ShieldX className="mx-auto size-10 text-brand-600" aria-hidden />
        <h1 className="mt-3 text-xl font-bold">403 · Acesso negado</h1>
        <p className="mt-2 text-sm text-muted">
          Seu perfil não tem permissão para esta área. Cada perfil (cliente, organizador e operador
          de portaria) só vê as telas da sua jornada.
        </p>
        <Link to={home}>
          <Button className="mt-5">Voltar para minha área</Button>
        </Link>
      </Card>
    </div>
  );
}
