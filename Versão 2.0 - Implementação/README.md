# BolsoPass — Versão 2.0 da Implementação

> **Escopo:** painel do **Admin/organizador**, login único com direcionamento por perfil e revisão
> do código da **bilheteria** (feito em paralelo por outra IA).
> **Data:** 29/10/2026 · **Branch:** `feat/Mocks`
> **Versão anterior:** [Versão 1.0](../Versão%201.0%20-%20Implementação/README.md) (jornada do cliente).

---

## 1. Resumo

A versão 2.0 entrega o lado de quem **organiza** os eventos e une os três perfis num só login:

```
Login único ─┬─ Cliente     → Catálogo e compra (versão 1.0)
             ├─ Admin       → Painel → Meus eventos → Criar evento (5 passos) → Gestão do evento
             └─ Bilheteria  → Preparar leitor (eventos atribuídos + cache offline)
```

| Parte | Pasta | O que mudou |
|---|---|---|
| Frontend (SPA) | [`apps/web-spa/src/features/organizer`](../apps/web-spa/src/features/organizer) | Telas do Admin |
| Frontend (SPA) | [`apps/web-spa/src/features/auth`](../apps/web-spa/src/features/auth) | Login único + acesso rápido |
| Backend (API) | [`backend/.../organizer`](../backend/src/main/java/com/bolsopass/organizer) | Painel, criação e controle de eventos |
| Backend (API) | [`backend/.../audit`](../backend/src/main/java/com/bolsopass/audit) | Log de auditoria por evento |
| Backend (API) | [`backend/.../catalog`](../backend/src/main/java/com/bolsopass/catalog) | Lotes em sequência por setor, dono do evento, status de venda |

---

## 2. Como rodar

Igual à versão 1.0:

```bash
cd backend && ./mvnw spring-boot:run          # API na porta 8080 (H2 em memória)
corepack pnpm --filter web-spa dev            # site na porta 3000
```

Abra `http://localhost:3000/auth/login`.

**Login**

- Digite e-mail e senha (ou matrícula e PIN, para a bilheteria) e clique em **Entrar**. O sistema
  descobre o perfil e abre a área certa.
- Embaixo do formulário há o **Acesso rápido (demonstração)**: um botão por perfil que entra
  direto com a conta de teste.

| Perfil | Login | Senha / PIN | Abre em |
|---|---|---|---|
| Cliente | `sarah@bolsopass.io` | `bolsopass` | `/` (catálogo) |
| Admin (organizador + administrador) | `admin@bolsopass.io` | `bolsopass` | `/organizer/dashboard` |
| Bilheteria (operador) | `OP-88219` | `123456` | `/operator/setup` |

---

## 3. Regras de negócio implementadas

| Regra | Requisito | Onde |
|---|---|---|
| Login único: e-mail usa login normal, matrícula (`OP-…`) usa login da portaria; cada perfil vai para a sua área | RF-02 | `LoginPage.tsx`, `auth/roles.ts` |
| Volta para a página anterior só se ela for da área do próprio perfil | RF-02 | `LoginPage.tsx` |
| Painel do Admin só para `ROLE_ORGANIZER`/`ROLE_ADMIN`: cliente e operador recebem 403 | RF-02 | `iam/SecurityConfig` |
| **Dono do evento:** organizador vê e altera só os próprios eventos; admin vê todos | RF-04 | `organizer/OrganizerService` |
| Evento nasce como **rascunho** e só aparece no catálogo depois de publicado | RF-04 | `catalog/Event` |
| **A soma dos lotes de cada setor tem que fechar** com os assentos vendáveis (capacidade − bloqueados), senão 400 | RF-04 | `organizer/OrganizerService` |
| **Lotes em sequência por setor**: o próximo lote entra sozinho quando a cota do atual esgota (`ESGOTADO → ATIVO → PROGRAMADO`) | RF-04 | `catalog/Sector`, `orders/CheckoutService` |
| Virada de lote registrada na auditoria | RF-04 | `audit/AuditService` |
| Assentos bloqueados no mapa (obstrução, PCD, reservado à produção) não são vendidos | RF-04 | `organizer/CreateEventRequest` |
| **Pausar vendas**: novas reservas recebem 409; retomar libera de novo | RF-04 | `organizer/OrganizerController` |
| Toda ação do organizador (criar, publicar, pausar, retomar) entra no log de auditoria | RF-03 | `audit/AuditLog` |
| Painel com números do banco: receita bruta, receita da semana, ocupação, virada de lote iminente, incidentes de sobre-venda | RF-16 | `organizer/OrganizerService` |

---

## 4. Telas entregues (frontend)

| Rota | Tela | Destaques |
|---|---|---|
| `/auth/login` | Login único | Um formulário para todos os perfis + acesso rápido Cliente / Admin / Bilheteria |
| `/organizer/dashboard` | Painel | Receita bruta e da semana, ocupação, alerta de virada de lote, saúde do sistema |
| `/organizer/events` | Meus eventos | Lista com status (rascunho, à venda, pausado) e vendidos por evento |
| `/organizer/events/new` | Criar evento | 5 passos: Dados gerais → Local & capacidade → Setores & lotes → Mapa & assentos → Revisão final |
| `/organizer/events/:id` | Gestão do evento | Lotes por setor com status, publicar / pausar / retomar, log de auditoria |
| `/operator/*` | Preparar leitor | Feita pela outra IA (ver seção 6) |
| Qualquer rota de outro perfil | 403 | Botão "Voltar para minha área" |

---

## 5. Endpoints novos da API (`/api/v1`)

| Método | Caminho | Acesso |
|---|---|---|
| GET | `/organizer/dashboard` | Organizador / Admin |
| GET | `/organizer/events` · `/organizer/events/{id}` | Organizador (só os seus) / Admin (todos) |
| POST | `/organizer/events` | Organizador / Admin (cria como rascunho ou já publicado) |
| POST | `/organizer/events/{id}/publish` · `/pause` · `/resume` | Dono do evento / Admin |
| GET | `/operator/events` | Operador (eventos atribuídos) |
| GET | `/operator/events/{id}/cache` | Operador (cache offline com chave pública) |
| POST | `/operator/events/{id}/scans` | Operador (grava leituras do check-in) |

---

## 6. Revisão do código da bilheteria (outra IA)

A outra IA está construindo a bilheteria (`/operator/**`, `management/ManagementController`,
`features/operator/`). Na revisão foram encontrados e **corrigidos**:

| Problema | Efeito | Correção |
|---|---|---|
| Compatibilidade de `Sector.lot` com parâmetros trocados | Setores apareciam esgotados para o cliente | Ordem dos parâmetros corrigida |
| Pausar vendas sem checar o dono | Um organizador podia pausar evento de outro | Checagem de dono adicionada |
| Cache offline com chave pública vazia e lista de usados vazia | Leitor offline não conseguia validar nenhum QR | Cache traz a chave P-256 real e os ingressos já usados |
| Leituras (scans) não eram gravadas | Check-in se perdia | Leitura salva no ingresso (`Ticket.checkIn`) |
| Lista do operador mostrava todos os eventos | Vazamento de dados de outros organizadores | Só eventos publicados atribuídos |

**Ainda falta na bilheteria** (a outra IA segue nessa parte): scanner de QR, venda no balcão,
histórico de leituras e sincronização offline.

---

## 7. Testes e verificação

| Verificação | Resultado |
|---|---|
| Backend — `./mvnw test` | **29 testes passando** (eram 25) |
| ↳ Soma dos lotes precisa fechar com os assentos vendáveis | ✔ |
| ↳ Virada automática de lote ao esgotar a cota | ✔ |
| ↳ Organizador não mexe no evento de outro | ✔ |
| ↳ Painel calcula os números a partir do banco | ✔ |
| Frontend — `pnpm test:unit` | **46 testes passando** |
| Frontend — lint e build de produção | Sem erros |
| Ponta a ponta do Admin contra o servidor rodando | **17 checagens OK**: 403 para cliente e bilheteria, rascunho fora do catálogo, publicar, lotes `ATIVO,PROGRAMADO`, auditoria, assento bloqueado no mapa, reserva com vendas pausadas → 409 |
| Ponta a ponta do cliente (versão 1.0) | Continua passando (18 checagens) |

> As telas ainda não foram percorridas num navegador por ferramenta automática (API testada por
> script). Recomenda-se validar o fluxo do Admin manualmente.

---

## 8. Decisões tomadas

1. **Um login para todos.** A tela decide pelo formato do identificador: com `@` é e-mail, sem
   `@` é matrícula de operador. Os botões de acesso rápido usam o mesmo caminho do formulário.
2. **Admin = organizador + administrador** numa conta só. O organizador comum vê só os próprios
   eventos; o administrador vê todos.
3. **Lotes por setor, em sequência**, com virada automática por quantidade vendida (e não por data).
4. **Evento começa como rascunho** para o organizador revisar antes de abrir as vendas.
5. **Pausa bloqueia novas reservas**, mas quem já tem reserva aberta pode terminar de pagar.

---

## 9. Pendências para as próximas versões

| Item | Situação |
|---|---|
| Bilheteria: scanner, venda no balcão, histórico, offline | Em andamento pela outra IA |
| Editar e cancelar evento já publicado | Não iniciado |
| Webhook assíncrono do PIX | Pagamento ainda confirmado na hora pelo simulador |
| Kafka, Redis, Keycloak | Mesma situação da versão 1.0 |
| Flyway, Testcontainers, Dockerfiles, compose completo, CI | Não iniciados |
| **Reembolso de 7 dias (CDC)** | **Ainda precisa de definição** (conflito com `PAGO` imutável no doc 06) |
| Commit | Nada foi commitado ainda |

---

## 10. Arquivos criados ou alterados

```
apps/web-spa/src/
  features/auth/LoginPage.tsx           Login único + acesso rápido
  features/organizer/                   Layout, Painel, Meus eventos, Criar evento, Gestão do evento
  auth/roles.ts, auth/RequireArea.tsx   Área inicial por perfil e tela 403
  api/, mocks/                          Contrato e API simulada do organizador
  router.tsx                            Rotas /organizer/*
backend/src/main/java/com/bolsopass/
  organizer/                            OrganizerController, OrganizerService, CreateEventRequest, OrganizerViews
  audit/                                AuditLog, AuditLogRepository, AuditService
  catalog/                              Event (dono, status), Sector (lotes em sequência)
  orders/CheckoutService                Virada de lote + auditoria
  ticketing/Ticket                      checkIn(data, portão)
  management/ManagementController       Correções na bilheteria
  seed/DataSeeder                       Conta admin e eventos do organizador
backend/src/test/java/com/bolsopass/OrganizerIntegrationTest.java
```
