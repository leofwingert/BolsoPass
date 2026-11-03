/**
 * Regras de cadastro (as mesmas do backend, AccountRules): usadas no cadastro público, na página
 * Usuários da administração, no cadastro rápido da bilheteria, nos titulares do ingresso e na API
 * simulada.
 */
export const MIN_PASSWORD = 8;

const EMAIL_RE = /^[^@\s]+@[^@\s]+\.[^@\s]+$/;

/** Nome e sobrenome (duas palavras ou mais). */
export function isFullName(name: string): boolean {
  return name.trim().split(/\s+/).length >= 2;
}

export function isEmail(email: string): boolean {
  return EMAIL_RE.test(email.trim());
}

/** Telefone com DDD: 10 ou 11 dígitos. */
export function isPhoneWithDdd(phone: string): boolean {
  return phone.replace(/\D/g, '').length >= 10;
}

export function isStrongEnoughPassword(password: string): boolean {
  return password.length >= MIN_PASSWORD;
}

export const ACCOUNT_MESSAGES = {
  name: 'Informe nome e sobrenome',
  email: 'E-mail inválido',
  phone: 'Informe um telefone com DDD',
  password: `A senha precisa ter pelo menos ${MIN_PASSWORD} caracteres`,
} as const;
