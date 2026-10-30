import type { Role, User } from '@/api/types';

export const ROLE_LABEL: Record<Role, string> = {
  ROLE_CLIENT: 'Cliente',
  ROLE_ORGANIZER: 'Organizador',
  ROLE_OPERATOR: 'Operador de portaria',
  ROLE_ADMIN: 'Administrador',
};

/** Quem pode entrar em cada área (tabela de rotas do doc 07). */
export const AREA_ROLES = {
  client: ['ROLE_CLIENT'],
  organizer: ['ROLE_ORGANIZER', 'ROLE_ADMIN'],
  operator: ['ROLE_OPERATOR', 'ROLE_ADMIN'],
} as const satisfies Record<string, readonly Role[]>;

export type Area = keyof typeof AREA_ROLES;

export function hasAnyRole(user: User | undefined, roles: readonly Role[]): boolean {
  return !!user && user.roles.some((r) => roles.includes(r));
}

export function canAccess(user: User | undefined, area: Area): boolean {
  return hasAnyRole(user, AREA_ROLES[area]);
}

/** Perfil principal para exibir (admin > organizador > operador > cliente). */
export function mainRoleLabel(user: User): string {
  const order: Role[] = ['ROLE_ADMIN', 'ROLE_ORGANIZER', 'ROLE_OPERATOR', 'ROLE_CLIENT'];
  const role = order.find((r) => user.roles.includes(r));
  return role ? ROLE_LABEL[role] : '';
}

/** Página inicial depois do login, de acordo com o perfil. */
export function homeFor(user: User): string {
  if (canAccess(user, 'organizer')) return '/organizer/dashboard';
  if (canAccess(user, 'operator')) return '/operator';
  return '/';
}
