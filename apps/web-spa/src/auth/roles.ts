import type { Role, User } from '@/api/types';

export const ROLE_LABEL: Record<Role, string> = {
  ROLE_CLIENT: 'Cliente',
  ROLE_ORGANIZER: 'Organizador',
  ROLE_OPERATOR: 'Operador de portaria',
  ROLE_ADMIN: 'Administrador',
  ROLE_SELLER: 'Terminal de venda',
};

/**
 * Quem pode entrar em cada área (tabela de rotas do doc 07).
 * A bilheteria é só da equipe de bilheteria: o admin não vende nem lê QR.
 * - boxOffice: app da bilheteria (portaria e terminal de venda);
 * - operator: leitor de QR, histórico e modo offline (só portaria).
 */
export const AREA_ROLES = {
  client: ['ROLE_CLIENT'],
  organizer: ['ROLE_ORGANIZER', 'ROLE_ADMIN'],
  boxOffice: ['ROLE_OPERATOR', 'ROLE_SELLER'],
  operator: ['ROLE_OPERATOR'],
} as const satisfies Record<string, readonly Role[]>;

export type Area = keyof typeof AREA_ROLES;

export function hasAnyRole(user: User | undefined, roles: readonly Role[]): boolean {
  return !!user && user.roles.some((r) => roles.includes(r));
}

export function canAccess(user: User | undefined, area: Area): boolean {
  return hasAnyRole(user, AREA_ROLES[area]);
}

/** Terminal de venda: só vende (sem leitor de QR). */
export function isSellerOnly(user: User | undefined): boolean {
  return canAccess(user, 'boxOffice') && !canAccess(user, 'operator');
}

/** Perfil principal para exibir (admin > organizador > operador > terminal de venda > cliente). */
export function mainRoleLabel(user: User): string {
  const order: Role[] = ['ROLE_ADMIN', 'ROLE_ORGANIZER', 'ROLE_OPERATOR', 'ROLE_SELLER', 'ROLE_CLIENT'];
  const role = order.find((r) => user.roles.includes(r));
  return role ? ROLE_LABEL[role] : '';
}

/** Página inicial depois do login, de acordo com o perfil. */
export function homeFor(user: User): string {
  if (canAccess(user, 'organizer')) return '/organizer/dashboard';
  if (canAccess(user, 'operator')) return '/operator';
  if (canAccess(user, 'boxOffice')) return '/operator/sell';
  return '/';
}
