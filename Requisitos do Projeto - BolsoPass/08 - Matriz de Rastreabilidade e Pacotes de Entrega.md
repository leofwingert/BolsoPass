---
title: "Matriz de Rastreabilidade e Pacotes de Entrega (PUCRS)"
aliases:
  - Rastreabilidade
  - Pacotes de Entrega
  - Checklist PUCRS
tags:
  - pucrs
  - entregaveis
  - rastreabilidade
  - governanca
  - pacotes/pacote-1
  - pacotes/pacote-2
  - pacotes/pacote-3
created: 2026-10-05
updated: 2026-11-23
status: pronto
---

# Matriz de Rastreabilidade e Pacotes de Entrega (PUCRS)

> [!abstract] Resumo de Governanca e Rastreabilidade
> Este documento consolida a rastreabilidade bidirecional entre as determinações formais do enunciado do **Projeto Integrador de Construção de Software (PUCRS 2026/2)** e os artefatos técnicos produzidos no ecossistema do [[00 - Visao Geral e Indice (MOC)|BolsoPass]]. A rastreabilidade assegura que cada requisito possua especificação formal, critério de aceitação, teste automatizado associado e marco de entrega correspondente.

---

## 1. Checklist Estruturado dos Entregaveis por Pacote

### Pacote 1: Processo de Desenvolvimento e Requisitos
> **Objetivo:** Estabelecer a visão do produto, a metodologia suportada por inteligência artificial com especificações formais, o backlog priorizado em Story Mapping com BDD, os requisitos não-funcionais mensuráveis e a experiência completa de navegação do front-end com mockups de alta fidelidade.

| Item Exigido no Enunciado | Artefatos e Documentos Correspondentes | Status de Atendimento |
| :--- | :--- | :---: |
| **1. Descrição do processo de desenvolvimento suportado por IA** | [[01 - Processo de Desenvolvimento Suportado por IA]]<br/>- Agent Skills sob `.agents/skills/`<br/>- Contrato OpenAPI: `.agents/specs/openapi-checkout.yaml`<br/>- Contrato AsyncAPI: `.agents/specs/asyncapi-kafka.yaml` | Concluído e auditado |
| **2. Documento de Visão do Produto** | [[02 - Documento de Visao do Produto]]<br/>- Problema de negócio, posicionamento e oportunidade<br/>- 4 Personas mapeadas (Comprador, Organizadora, Operador, SRE)<br/>- Limites de escopo, simulador financeiro e matriz de riscos | Concluído |
| **3. Backlog com Épicos e Histórias no formato Story Mapping** | [[05 - User Story Mapping e Backlog]]<br/>- Backbone com 5 macroatividades<br/>- 18 Histórias de Usuário completas (US-01 a US-18)<br/>- Critérios de aceitação BDD (*Given/When/Then*) | Concluído |
| **4. Requisitos Não-Funcionais** | [[04 - Requisitos Nao-Funcionais (RNFs)]]<br/>- 8 RNFs mensuráveis (ISO 25010)<br/>- Métricas para rajada, Zero Overselling, TTL 10 min, Outbox, etc.<br/>- Métodos de verificação automatizada definidos | Concluído |
| **5. Mockups e Mapa de Navegação do front-end** | [[07 - Mockups e Mapa de Navegacao Frontend]]<br/>- Mapa global de rotas com guardas RBAC<br/>- Wireframes das 6 telas críticas<br/>- Protótipo interativo em [`mockups-high-res/index.html`](mockups-high-res/index.html) | Concluído |
| **6. Detalhamento dos 5 Módulos Autônomos** | [[03 - Requisitos Funcionais (5 Modulos)]]<br/>- IAM, Inventário, Checkout, Despacho e DevOps | Concluído |
| **7. Máquinas de Estados e Regras de Negócio** | [[06 - Maquinas de Estados e Regras de Negocio]]<br/>- FSM do Pedido, ciclo de vida do assento, Outbox e `Idempotency-Key` | Concluído |

---

### Pacote 2: Arquitetura e Infraestrutura de Desenvolvimento
> **Objetivo:** Especificar a arquitetura técnica, o modelo C4, a topologia de mensageria, a modelagem de dados e as políticas de GCS.

| Item Exigido no Enunciado | Artefatos e Documentos Correspondentes (`Pacote 2 - Arquitetura e Infraestrutura/`) | Status de Atendimento |
| :--- | :--- | :---: |
| **Descrição da arquitetura no [Modelo C4](https://c4model.com/)** (Nível 1 - Contexto, Nível 2 - Contêineres, Nível 3 - Componentes) | [[01 - Arquitetura C4]] (Diagramas Mermaid Níveis 1, 2 e 3 e mapeamento de componentes) | Concluído (`v0.2.0`) |
| **Descrição do Modelo de Dados Conceitual** | [[02 - Modelo de Dados Conceitual]] (Entidades, Agregados DDD, Invariantes e Esquemas Relacionais) | Concluído (`v0.2.0`) |
| **Descrição da estrutura de mensageria do Kafka** | [[03 - Mensageria Kafka]] (Tópicos, Partições, Chaves, Envelope de Eventos, Outbox e DLQ) | Concluído (`v0.2.0`) |
| **Descrição esquemática da Pipeline de desenvolvimento** | [[04 - Pipeline de Desenvolvimento]] (Stages de Lint, Build, Testes Unitários $\ge 70\%$ JaCoCo e Testcontainers) | Concluído (`v0.2.0`) |
| **Descrição do Processo de GCS** | [[05 - Processo de GCS]] (Estratégia GitFlow adaptado, Conventional Commits, SemVer e Branch Protection) | Concluído (`v0.2.0`) |

---

### Pacote 3: Checkpoint do Desenvolvimento
> **Objetivo:** Entregar o estágio funcional dos microsserviços e agregados de domínio, atualizar a documentação dos pacotes 1 e 2 e comprovar cobertura automatizada $\ge 70\%$ nas máquinas de estado e regras de domínio com integração contínua.

| Item Exigido no Enunciado | Artefatos e Implementação Correspondente | Status de Atendimento |
| :--- | :--- | :---: |
| **1. Estágio corrente de desenvolvimento funcional da plataforma** | Código-fonte em `src/main/java/com/bolsopass/` (`iam`, `catalog`, `inventory`, `orders`, `ticketing`, `telemetry`) e documentação em [[01 - Implementacao dos Microsservicos e Dominio]] | Concluído (`v0.3.0`) |
| **2. Documentação entregue nos pacotes 1 e 2 atualizada** | Atualização transversal das notas do Pacote 1 e do Pacote 2 com rastreabilidade direta para as classes, entidades JPA, tópicos Kafka e workflows implementados | Concluído (`v0.3.0`) |
| **3. Cobertura de testes unitários de domínio e máquinas de estado ($\ge 70\%$)** | `OrderStateMachineTest`, `SeatInventoryTest`, `DomainModulesUnitTest` e `TransactionalOutboxIntegrationTest` (**$93,38\%$ instruções e $73,98\%$ branches no domínio** via JaCoCo), documentados em [[02 - Suite de Testes e Cobertura de Dominio JaCoCo]] e [[03 - Testes de Integracao Testcontainers e Pipeline CI]] | Concluído (`v0.3.0`) |

---

### Pacote 4: Produto Completo
- Link para o repositório Git contendo o produto completo e funcional.
- Ambiente 100% orquestrado via `docker-compose.yml`.
- Testes automatizados executando com sucesso através de [Testcontainers](https://testcontainers.com/) reais para PostgreSQL, Kafka e Redis.
- Documentação técnica consolidada dos pacotes 1 e 2 atualizada.

---

## 2. Matriz Cruzada de Rastreabilidade (RF x RNF x User Stories x Modulo)

A matriz a seguir cruza cada frente funcional autônoma com seus requisitos funcionais, requisitos não-funcionais associados, histórias de usuário mapeadas e mecanismos automatizados de verificação:

| Módulo Autônomo | Requisitos Funcionais (RF) | Histórias de Usuário | RNFs Vinculados | Mecanismo de Verificação Automatizado (Implementado no Pacote 3) |
| :--- | :--- | :--- | :--- | :--- |
| **Identidade e IAM** | RF-01, RF-02, RF-03 | US-01, US-02, US-03 | RNF-06 | `DomainModulesUnitTest` e `TransactionalOutboxIntegrationTest`: login JWT RS256, RBAC, bloqueio HTTP 429 após 5 falhas e rotação de Refresh Token com invalidação de família por replay. |
| **Inventário e Concorrência** | RF-04, RF-05, RF-06, RF-07 | US-04, US-05, US-06, US-07 | RNF-01, RNF-02, RNF-03 | `SeatInventoryTest` (200 threads concorrentes $\to$ 1 reserva e 199 conflitos, TTL estrito de $600\text{ s}$) e `TransactionalOutboxIntegrationTest` (100 chamadas HTTP simultâneas $\to$ 0% sobre-venda). |
| **Pedidos e Checkout** | RF-08, RF-09, RF-10, RF-11 | US-08, US-09, US-10, US-11 | RNF-04, RNF-05, RNF-07 | `OrderStateMachineTest` ($93,4\%$ de cobertura na FSM + `IdempotencyStore` 24h) e `TransactionalOutboxIntegrationTest` (PostgreSQL via Testcontainers + queda e recuperação do Kafka). |
| **Mensageria e Despacho** | RF-12, RF-13, RF-14 | US-12, US-13, US-14, US-15 | RNF-05, RNF-06 | `TransactionalOutboxIntegrationTest` e `DomainModulesUnitTest`: deduplicação via `processed_messages`, geração de PDF (`%PDF-`), assinatura ECDSA P-256 ($< 5\text{ ms}$) e prevenção de dupla entrada. |
| **Telemetria e DevOps** | RF-15, RF-16, RF-17, RF-18 | US-16, US-17, US-18 | RNF-01, RNF-08 | `DomainModulesUnitTest` (`TelemetrySnapshot`), script `tests/k6-flash-crowd-load-test.js` e workflows GitHub Actions (`.github/workflows/ci.yml` e `pr-checks.yml`) com regra bloqueante JaCoCo $\ge 70\%$. |
