import '@testing-library/jest-dom/vitest';
import { cleanup, configure } from '@testing-library/react';
import { afterEach } from 'vitest';

// As telas são carregadas sob demanda (lazy); a primeira compilação no Vitest pode passar de 1 s.
configure({ asyncUtilTimeout: 5000 });

// O roteador cria um Request com o AbortSignal do jsdom, que o Request do Node não aceita.
// Nos testes o sinal é descartado (a navegação não depende dele).
const NodeRequest = globalThis.Request;
globalThis.Request = class extends NodeRequest {
  constructor(input: RequestInfo | URL, init?: RequestInit) {
    const { signal: _signal, ...rest } = init ?? {};
    super(input, rest);
  }
} as typeof Request;

afterEach(() => {
  cleanup();
});
