/// <reference types="vitest/config" />
import { fileURLToPath, URL } from 'node:url';
import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import tailwindcss from '@tailwindcss/vite';

// Porta 3000 e proxy para o backend/gateway (8080), conforme "01 - Arquitetura C4".
export default defineConfig({
  plugins: [react(), tailwindcss()],
  resolve: {
    alias: { '@': fileURLToPath(new URL('./src', import.meta.url)) },
  },
  server: {
    port: 3000,
    proxy: {
      '/api': { target: 'http://localhost:8080', changeOrigin: true },
      '/ws': { target: 'ws://localhost:8080', ws: true },
    },
  },
  preview: { port: 3000 },
  test: {
    globals: true,
    environment: 'jsdom',
    setupFiles: ['./src/test/setup.ts'],
    css: false,
    // Testes de fluxo percorrem várias telas (carregadas sob demanda).
    testTimeout: 20_000,
    coverage: {
      provider: 'v8',
      // Sistema inteiro (telas, API, domínio). Fica de fora só o que não é código de produto.
      include: ['src/**'],
      exclude: ['src/**/*.test.*', 'src/test/**', 'src/main.tsx', 'src/vite-env.d.ts', 'src/api/contract.ts', 'src/api/types.ts'],
      // Enunciado: >= 70% de cobertura. O comando falha se cair abaixo.
      thresholds: { lines: 70, functions: 70, branches: 70, statements: 70 },
    },
  },
});
