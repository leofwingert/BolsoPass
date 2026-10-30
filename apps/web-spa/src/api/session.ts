/** Token de acesso atual, compartilhado entre o cliente HTTP, o WebSocket e o mock. */
let accessToken: string | undefined;
let unauthorizedHandler: () => void = () => {};
let refresher: (() => Promise<string | undefined>) | undefined;
let inFlight: Promise<string | undefined> | undefined;

export function getAccessToken(): string | undefined {
  return accessToken;
}

export function setAccessToken(token: string | undefined): void {
  accessToken = token;
}

/** Chamado quando a sessão não pode ser recuperada: o AuthProvider encerra e manda para o login. */
export function onUnauthorized(handler: () => void): void {
  unauthorizedHandler = handler;
}

export function notifyUnauthorized(): void {
  unauthorizedHandler();
}

/** Registrado pelo AuthProvider: troca o refresh token por um novo access token. */
export function setRefresher(fn: (() => Promise<string | undefined>) | undefined): void {
  refresher = fn;
}

/** Várias requisições com 401 ao mesmo tempo disparam uma única renovação. */
export function refreshAccessToken(): Promise<string | undefined> {
  if (!refresher) return Promise.resolve(undefined);
  inFlight ??= refresher()
    .catch(() => undefined)
    .finally(() => {
      inFlight = undefined;
    });
  return inFlight;
}
