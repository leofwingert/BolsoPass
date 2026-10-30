# BolsoPass — Versão 3.0 da Implementação

> **Escopo:** reformulação da **área administrativa** (organizador / admin) para seguir os mockups,
> com nomes claros para o produtor e consulta de **para quem cada assento foi vendido**.
> **Data:** 30/10/2026 · **Branch:** `feat/Mocks`
> **Versões anteriores:** [1.0](../Versão%201.0%20-%20Implementação/README.md) (cliente) ·
> [2.0](../Versão%202.0%20-%20Implementação/README.md) (primeira versão do admin e login único).

---

## 1. Resumo

Na 2.0 o admin funcionava, mas usava o cabeçalho do cliente, tinha telas diferentes dos mockups e
vários nomes técnicos. A 3.0 corrige isso e acrescenta o mapa de assentos com os dados do comprador:

```
Painel → Meus eventos → Gestão do evento ─┬─ Resumo e métricas
                                          ├─ Setores e lotes → clique no setor → mapa de assentos
                                          │                       → clique no assento vendido → comprador
                                          ├─ Portaria (entradas por portão)
                                          └─ Auditoria (registro de alterações)
```

| Parte | Pasta | O que mudou |
|---|---|---|
| Frontend | [`features/organizer`](../apps/web-spa/src/features/organizer) | Cabeçalho próprio, 4 telas refeitas, mapa de assentos do setor |
| Frontend | [`components/UserMenu.tsx`](../apps/web-spa/src/components/UserMenu.tsx) | Dados do usuário no cabeçalho (nome, perfil, e-mail) |
| Backend | [`organizer/SeatSalesService`](../backend/src/main/java/com/bolsopass/organizer/SeatSalesService.java) | Mapa do setor e venda de cada assento |
| Backend | [`organizer/OrganizerService`](../backend/src/main/java/com/bolsopass/organizer/OrganizerService.java) | Portão e entradas por setor |
| Backend | [`seed/DataSeeder`](../backend/src/main/java/com/bolsopass/seed/DataSeeder.java) | Compradores de exemplo e histórico inicial de auditoria |

---

## 2. Como rodar

Igual às versões anteriores. Abra `http://localhost:3000/auth/login` e clique em **Admin**
(`admin@bolsopass.io` / `bolsopass`).

---

## 3. Mudanças na área administrativa

### 3.1 Cabeçalho próprio

| Antes | Agora |
|---|---|
| Cabeçalho do cliente (busca de shows, "Explorar Eventos") | Cabeçalho do admin: logo com selo **ADMIN**, Painel, Meus eventos, Criar evento, Bilheteria, "Ver site público" |
| Barra de abas e selo de perfil repetidos | Removidos |
| Perfil exibido como "Organizador" para o admin | Mostra o perfil principal: **Administrador** |
| Menu do usuário só com "Sair" | Nome, e-mail, perfil e "Sair" (campo mantido, embora não esteja nos mockups) |

### 3.2 Nomes corrigidos (jargão técnico → linguagem do produtor)

| Mockup | Agora |
|---|---|
| Produção Enterprise / Dashboard Geral — Visão Executiva | Ao vivo / Painel geral do produtor |
| Trava Emergencial | Pausar todas as vendas |
| Infraestrutura BolsoPass, Redis Locks, Overselling | Saúde das vendas: reservas no último minuto, venda acima da capacidade |
| Wizard de implantação / Trava atômica de reserva | Novo evento / Tempo de reserva no carrinho |
| Cluster Redundante: Lock Atômico Ativo | Venda sem assento duplicado |
| Wallets ativas, Vazão & Anti-carting (Redis) | Em carrinho agora, Reservas expiradas hoje |
| Checkout Conversion, Gross Bookings | Conversão do checkout, Receita gerada |
| Logs de Auditoria / Executor | Registro de alterações / Feito por |

### 3.3 Telas

| Rota | Tela | Novidades |
|---|---|---|
| `/organizer/dashboard` | Painel | "Pausar todas as vendas" com confirmação; dia de maior faturamento destacado; card "Equipe de portaria"; corrigido o card escuro que aparecia em branco |
| `/organizer/events` | Meus eventos | Caixas de seleção e "Pausar selecionados"; paginação; filtros por todos os status; conversão do checkout |
| `/organizer/events/new` | Criar evento | "Salvar como rascunho" em todos os passos; textos sem jargão |
| `/organizer/events/:id` | Gestão do evento | Abas (Resumo, Setores e lotes, Portaria, Auditoria); card de portaria com entradas por portão; card do local com link para o mapa |
| (diálogo) | Assentos do setor | Mapa do setor; clique no assento vendido mostra o comprador |

### 3.4 Para quem vendi (mapa de assentos do setor)

Ao clicar num setor, abre o mapa com as cores: verde disponível, vermelho vendido, escuro
"vendido e já entrou", amarelo em carrinho. Ao clicar num assento vendido:

| Bloco | Dados |
|---|---|
| Comprador (conta que pagou) | Nome, CPF, e-mail e telefone para contato (clicáveis) |
| Titular do ingresso (quem vai entrar) | Nome, CPF vinculado ao ingresso, código do ingresso, se já entrou (horário e portão) |
| Pedido | Código, forma de pagamento, data, valor do assento e total pago |

---

## 4. Regras de negócio

| Regra | Requisito | Onde |
|---|---|---|
| Dados do comprador só para o dono do evento ou o admin; outro organizador recebe 403 | RF-02 | `SeatSalesService` |
| **Toda consulta de dados do comprador fica na auditoria** (quem viu, qual assento, qual pedido) | RF-03 / LGPD | `SeatSalesService` |
| Assento não vendido não tem dados de venda (409) | RF-04 | `SeatSalesService` |
| "Pausar todas as vendas" e "Pausar selecionados" usam a mesma pausa auditada por evento | RF-04 | `OrganizerDashboardPage`, `OrganizerEventsPage` |
| Entradas por portão calculadas dos ingressos com check-in | RF-16 | `OrganizerService.detail` |

---

## 5. Endpoints novos (`/api/v1`)

| Método | Caminho | Acesso |
|---|---|---|
| GET | `/organizer/events/{id}/sectors/{sectorId}/seats` | Dono do evento / Admin |
| GET | `/organizer/events/{id}/seats/{seatId}/sale` | Dono do evento / Admin (consulta auditada) |

O detalhe do evento (`GET /organizer/events/{id}`) passou a trazer `gate` e `checkedIn` por setor.

---

## 6. Dados de exemplo

- Os assentos que já nasciam vendidos não tinham pedido. Agora cada um recebe um **pedido pago**
  de 8 compradores fictícios, com **CPF válido** e telefone, e titulares com CPF.
- Por isso a receita do painel ficou maior que na 2.0.
- Cada evento já começa com histórico na auditoria (publicação, abertura da bilheteria e uma
  virada de lote no Festival Synthwave).

---

## 7. Testes e verificação

| Verificação | Resultado |
|---|---|
| Backend — `./mvnw test` | **30 testes passando** (eram 29) |
| ↳ Todo assento vendido tem pedido, comprador com telefone e titular com CPF; consulta vai para a auditoria; assento livre e outro organizador são barrados | ✔ |
| Frontend — `pnpm test:unit`, lint e checagem de tipos | 46 testes, sem erros |
| Navegador (Chrome) | As 4 telas comparadas com os mockups; fluxo setor → assento → comprador conferido |
| Ponta a ponta do admin | 17 checagens OK |

---

## 8. Pendências

| Item | Situação |
|---|---|
| Cancelar evento e editar evento publicado | Botões visíveis e desativados ("em breve"); cancelar depende da regra de reembolso |
| Dados do mockup que o sistema não mede ("32 coletores ativos", latência p99, "Duplicar lotes") | Fora, para não mostrar números inventados |
| Reembolso de 7 dias (CDC) | Ainda precisa de definição |
| Commit | Nada foi commitado ainda |
