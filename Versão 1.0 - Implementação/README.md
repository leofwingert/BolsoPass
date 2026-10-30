# BolsoPass — Versão 1.0 da Implementação

> **Escopo:** jornada completa do **cliente** (comprador), com frontend e backend integrados.
> **Data:** 29/10/2026 · **Branch:** `feat/Mocks`
> **Próximas versões:** painel do organizador/admin (1.1) e app mobile da portaria (1.2).

---

## 1. Resumo

A versão 1.0 tira o BolsoPass do papel para a jornada do comprador:

```
Catálogo → Detalhe do evento → Sala de espera → Mapa de assentos → Reserva (10 min)
        → Checkout → Pagamento → Ingresso com QR dinâmico → Carteira / Meus pedidos
```

Tudo segue os mockups do Figma (`Pacote 1/Mocks-User`) e as regras de negócio dos documentos
`03 - Requisitos Funcionais`, `06 - Máquinas de Estados` e `07 - Mockups e Mapa de Navegação`.

| Parte | Pasta | Tecnologia |
|---|---|---|
| Frontend (SPA) | [`apps/web-spa`](../apps/web-spa) | React 18 + Vite + TypeScript + TanStack Query + Tailwind |
| Backend (API) | [`backend`](../backend) | Java 21 + Spring Boot 3.5 + Spring Security + JPA |
| Banco | [`docker-compose.yml`](../docker-compose.yml) | PostgreSQL 16 (ou H2 em memória no modo `dev`) |

---

## 2. Como rodar

**Backend** (porta 8080):

```bash
cd backend
./mvnw spring-boot:run            # H2 em memória, sem Docker
# ou, com PostgreSQL:
docker compose up -d
./mvnw spring-boot:run -Dspring-boot.run.profiles=postgres
```

**Frontend** (porta 3000):

```bash
corepack pnpm install
corepack pnpm --filter web-spa dev        # usa o backend real
corepack pnpm --filter web-spa dev:mock   # roda sem backend (API simulada)
```

Abra `http://localhost:3000`.

**Contas de demonstração**

| Perfil | Login | Senha / PIN |
|---|---|---|
| Cliente | `sarah@bolsopass.io` | `bolsopass` |
| Organizador / Admin | `admin@bolsopass.io` | `bolsopass` |
| Operador de portaria | `OP-88219` | `123456` |

Cartão terminado em `0000` simula pagamento recusado.

---

## 3. Regras de negócio implementadas

| Regra | Requisito | Onde |
|---|---|---|
| Autenticação com JWT de 15 min e refresh token de 7 dias com rotação; reuso de token antigo derruba as sessões | RF-01 | `iam/AuthService` |
| Bloqueio após 5 senhas erradas (15 min) | RF-03 | `iam/AuthService` |
| Perfis com acesso separado (cliente, organizador, operador, admin): 401 sem login, 403 perfil errado | RF-02 | `iam/SecurityConfig`, `auth/RequireArea` |
| Catálogo público com vendidos/disponíveis calculados do estoque real | RF-05 | `catalog/CatalogService` |
| Sala de espera virtual: só reserva quem já foi admitido na fila | Mockup | `catalog/VirtualQueue` |
| **Zero overselling:** trava atômica (`SELECT ... FOR UPDATE`); quem chega depois recebe 409 | RF-06 | `inventory/ReservationService` |
| **Reserva de 10 minutos** com cronômetro pelo relógio do servidor; ao expirar, o pedido vira `EXPIRADO` e o assento volta ao mapa | RF-07 | `inventory/ReservationExpiryJob` |
| Uma reserva aberta por comprador e evento; limite de ingressos por CPF no lote | Mockup | `inventory/ReservationService` |
| Máquina de estados do pedido exatamente como no doc 06 (`PAGO` é final; `EXPIRADO` nunca vira `PAGO`) | RF-08 | `orders/OrderStatus`, `domain/order.ts` |
| **Pagamento idempotente:** header `Idempotency-Key` obrigatório; mesma chave devolve o mesmo pedido, sem cobrar de novo; chave válida por 24 h | RF-09 | `orders/CheckoutService` |
| Pedido pago e evento `pedidos.pagos` gravados na mesma transação (Transactional Outbox, relay com `SKIP LOCKED`) | RF-10 | `outbox/*` |
| Simulador de gateway: aprovação, recusa e nova tentativa ou desistência dentro do prazo | RF-11 | `orders/PaymentGateway` |
| Ingresso nominal: nome + CPF válido por ingresso, sem CPF repetido no pedido | Mockup | `orders/CheckoutService`, `domain/purchaseRules.ts` |
| **QR dinâmico** assinado com ECDSA P-256, trocado a cada 15 s (print de tela não passa) | RF-13 | `ticketing/QrSigner`, `domain/ticketQr.ts` |
| Atualização em tempo real por WebSocket: assento travado, liberado ou vendido; status do pedido | RF-07, RF-14 | `realtime/*`, `hooks/useRealtime.ts` |
| Taxa de conveniência de 10% mostrada antes de pagar | Mockup | `domain/pricing.ts` |

---

## 4. Telas entregues (frontend)

| Rota | Tela | Destaques |
|---|---|---|
| `/` | Home e catálogo | Busca, filtro por categoria, ocupação por evento |
| `/events/:id` | Detalhe do evento | Sala de espera ao vivo, lotes, contagem para a virada de lote |
| `/events/:id/seats` | Mapa de assentos | Atualização em tempo real, alerta de concorrência, navegação por teclado, foco PCD |
| `/checkout/:id` | Checkout | Cronômetro de 10 min, aviso sonoro nos 2 min finais, titular por ingresso, cartão/PIX |
| `/tickets/:id` | Ingresso | QR que muda a cada 15 s, resumo da compra, instruções de acesso |
| `/wallet` | Minha carteira | Ingressos ativos e anteriores |
| `/my-orders` | Meus pedidos | Abas próximos/passados, indicadores |
| `/auth/login` | Login | Contas de demonstração |
| `/organizer/*`, `/operator/*` | Em construção | Já protegidas por perfil |

---

## 5. Endpoints da API (`/api/v1`)

| Método | Caminho | Acesso |
|---|---|---|
| POST | `/auth/login` · `/auth/refresh` · `/auth/logout` · `/auth/operator/login` | Público |
| GET | `/events` · `/events/{id}` | Público |
| GET | `/events/{id}/queue` | Logado |
| GET | `/events/{id}/seats` | Cliente |
| POST | `/reservations` | Cliente |
| GET / POST | `/reservations/{id}` · `/reservations/{id}/give-up` | Dono da reserva |
| POST | `/checkout` (header `Idempotency-Key`) | Cliente |
| GET | `/orders/me` · `/orders/{id}` | Dono do pedido |
| GET | `/tickets/me` · `/tickets/{id}` · `/tickets/{id}/qr` | Dono do ingresso |
| GET | `/keys/qr` | Público (chave pública para a portaria) |
| WS | `/ws` | Tópicos `events/{id}/seats` (público) e `orders/{id}` (dono) |

---

## 6. Testes e verificação

| Verificação | Resultado |
|---|---|
| Backend — `./mvnw test` | **25 testes passando** |
| ↳ Máquina de estados do pedido (todas as transições válidas e proibidas) | ✔ |
| ↳ 20 compradores disputando o mesmo assento ao mesmo tempo: só 1 consegue | ✔ |
| ↳ Pagamento repetido com a mesma chave: um pedido, um evento no outbox | ✔ |
| ↳ Recusa + desistência devolve o assento; CPF inválido é barrado; fila virtual exigida | ✔ |
| ↳ Reserva expirada bloqueia pagamento (410) e devolve o assento | ✔ |
| Frontend — `pnpm test:unit` | **46 testes passando** (regras de domínio) |
| Frontend — lint e build de produção | Sem erros |
| Ponta a ponta contra o servidor rodando | **18 checagens OK**, incluindo QR assinado em Java validado pelo WebCrypto do navegador |
| Tempo real pelo proxy do Vite | Reserva de um comprador chega ao mapa do outro |

> As telas não foram percorridas num navegador durante esta versão (API e WebSocket foram
> testados por script). Recomenda-se validar a jornada manualmente.

---

## 7. Decisões tomadas

1. **Um único serviço no backend por enquanto**, com um pacote por microsserviço do C4
   (`iam`, `catalog`, `inventory`, `orders`, `ticketing`, `outbox`, `realtime`). Os pacotes só se
   comunicam por serviços e eventos, então a divisão em contêineres é mecânica.
2. **Spring Boot 3.5 em vez de 3.3:** o 3.3 está sem suporte e não roda nos JDKs atuais.
3. **Trava com `SELECT ... FOR UPDATE`** (permitido pelo RF-06) até o Redis entrar no compose.
4. **Visual segue os mockups do Figma** (tema claro, vermelho BolsoPass), não a paleta escura do doc 07.
5. **Reserva só trava no botão "Garantir reserva"**, não a cada clique no assento: menos travas inúteis.
6. **Pedido `RESERVADO` não pode ser cancelado direto** (a FSM do doc 06 não permite): ele expira
   ou segue para pagamento. Desistir só é possível após um pagamento recusado.
7. **"Admin" = organizador + administrador; "checkout" = app mobile do operador de portaria.**

---

## 8. Pendências para as próximas versões

| Item | Situação |
|---|---|
| Painel do organizador/admin | Versão 1.1 |
| App mobile da portaria (scanner, histórico, modo offline) | Versão 1.2 (chave pública já exposta em `/keys/qr`) |
| Kafka | Eventos ficam na tabela `outbox_events`; o relay só registra no log |
| Redis e Keycloak | Travas e refresh tokens usam o banco; chaves JWT/QR são geradas ao iniciar |
| Migrações Flyway, Testcontainers, Dockerfiles, pipeline CI | Não iniciados |
| PDF do ingresso, Wallet e transferência | Botões visíveis, desabilitados |
| **Reembolso de 7 dias (CDC)** | **Precisa de definição:** o doc 06 diz que `PAGO` é imutável, então o estorno exige um fluxo próprio |

---

## 9. Arquivos criados

```
apps/web-spa/                 Frontend (SPA)
  src/domain/                 Regras de negócio puras + testes
  src/api/                    Contrato, cliente HTTP, WebSocket, API real
  src/mocks/                  API simulada (modo sem backend)
  src/features/client/        Telas do cliente
backend/                      API Spring Boot
  src/main/java/com/bolsopass/{iam,catalog,inventory,orders,ticketing,outbox,realtime,seed,shared}
  src/test/java/              Testes unitários e de integração
docker-compose.yml            PostgreSQL
pnpm-workspace.yaml, package.json   Workspace do monorepo
```
