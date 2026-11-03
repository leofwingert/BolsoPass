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
created: 2026-10-05
updated: 2026-11-03
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
| **2. Documento de Visão do Produto** | [[02 - Documento de Visao do Produto]]<br/>- Problema de negócio, posicionamento e oportunidade<br/>- 5 Personas mapeadas (Comprador, Organizadora, Operador, Vendedora, SRE)<br/>- Limites de escopo, simulador financeiro e matriz de riscos | Concluído |
| **3. Backlog com Épicos e Histórias no formato Story Mapping** | [[05 - User Story Mapping e Backlog]]<br/>- Backbone com 5 macroatividades<br/>- 21 Histórias de Usuário completas (US-01 a US-21)<br/>- Critérios de aceitação BDD (*Given/When/Then*) | Concluído |
| **4. Requisitos Não-Funcionais** | [[04 - Requisitos Nao-Funcionais (RNFs)]]<br/>- 8 RNFs mensuráveis (ISO 25010)<br/>- Métricas para rajada, Zero Overselling, TTL 10 min, Outbox, etc.<br/>- Métodos de verificação automatizada definidos | Concluído |
| **5. Mockups e Mapa de Navegação do front-end** | [[07 - Mockups e Mapa de Navegacao Frontend]]<br/>- Mapa global de rotas com guardas RBAC<br/>- Wireframes das 6 telas críticas<br/>- Protótipo interativo em [`mockups-high-res/index.html`](mockups-high-res/index.html) | Concluído |
| **6. Detalhamento dos 5 Módulos Autônomos** | [[03 - Requisitos Funcionais (5 Modulos)]]<br/>- IAM, Inventário, Checkout, Despacho e DevOps | Concluído |
| **7. Máquinas de Estados e Regras de Negócio** | [[06 - Maquinas de Estados e Regras de Negocio]]<br/>- FSM do Pedido, ciclo de vida do assento, Outbox e `Idempotency-Key` | Concluído |

---

### Pacote 2: Arquitetura e Infraestrutura de Desenvolvimento
> **Objetivo:** Especificar a arquitetura técnica, o modelo C4, a topologia de mensageria, a modelagem de dados e as políticas de GCS.

| Item Exigido no Enunciado | Documento | Status de Atendimento |
| :--- | :--- | :---: |
| **Descrição da arquitetura no [Modelo C4](https://c4model.com/)** (Nível 1 - Contexto, Nível 2 - Contêineres, Nível 3 - Componentes) | [01 - Arquitetura C4](../Pacote%202%20-%20Arquitetura%20e%20Infraestrutura/01%20-%20Arquitetura%20C4.md) | Concluído |
| **Descrição do Modelo de Dados Conceitual** | [02 - Modelo de Dados Conceitual](../Pacote%202%20-%20Arquitetura%20e%20Infraestrutura/02%20-%20Modelo%20de%20Dados%20Conceitual.md) | Concluído |
| **Descrição da estrutura de mensageria do Kafka** | [03 - Mensageria Kafka](../Pacote%202%20-%20Arquitetura%20e%20Infraestrutura/03%20-%20Mensageria%20Kafka.md) | Concluído |
| **Descrição esquemática da Pipeline de desenvolvimento** | [04 - Pipeline de Desenvolvimento](../Pacote%202%20-%20Arquitetura%20e%20Infraestrutura/04%20-%20Pipeline%20de%20Desenvolvimento.md) | Concluído |
| **Descrição do Processo de GCS** | [05 - Processo de GCS](../Pacote%202%20-%20Arquitetura%20e%20Infraestrutura/05%20-%20Processo%20de%20GCS.md) | Concluído |

---

### Pacote 3: Checkpoint do Desenvolvimento
- Link para o repositório Git com o estágio corrente de desenvolvimento funcional da plataforma.
- Documentação entregue nos pacotes 1 e 2 atualizada. ✅ Revisada em 03/11/2026.
- Cobertura de testes unitários de domínio e máquinas de estado em evolução contínua. ✅ Backend 93% e frontend 92% das linhas (mínimo exigido: 70%).

---

### Pacote 4: Produto Completo
- Link para o repositório Git contendo o produto completo e funcional.
- Ambiente 100% orquestrado via `docker-compose.yml`.
- Testes automatizados executando com sucesso através de [Testcontainers](https://testcontainers.com/) reais para PostgreSQL, Kafka e Redis.
- Documentação técnica consolidada dos pacotes 1 e 2 atualizada.

---

## 2. Matriz Cruzada de Rastreabilidade (RF x RNF x User Stories x Modulo)

A matriz a seguir cruza cada frente funcional autônoma com seus requisitos funcionais, requisitos não-funcionais associados, histórias de usuário mapeadas e mecanismos automatizados de verificação:

| Módulo Autônomo | Requisitos Funcionais (RF) | Histórias de Usuário | RNFs Vinculados | Mecanismo de Verificação Automatizado |
| :--- | :--- | :--- | :--- | :--- |
| **Identidade e IAM** | RF-01, RF-02, RF-03 | US-01, US-02, US-03, US-20, US-21 | RNF-06 | Testes de integração de login, refresh com detecção de reuso, 401/403 por perfil, cadastro público, código de ativação e promoção de perfil (`ApiIntegrationTest`, `UserAdminIntegrationTest`). |
| **Inventário e Concorrência** | RF-04, RF-05, RF-06, RF-07 | US-04, US-05, US-06, US-07 | RNF-01, RNF-02, RNF-03 | 20 compradores simultâneos disputando o mesmo assento no PostgreSQL real (Testcontainers): só 1 consegue. Expiração da reserva testada com TTL encurtado. Planejado: carga com k6. |
| **Pedidos e Checkout** | RF-08, RF-09, RF-10, RF-11, RF-19 | US-08, US-09, US-10, US-11, US-19 | RNF-04, RNF-05, RNF-07 | Testes de domínio da FSM (19 casos), `Idempotency-Key` sem cobrança dupla, Testcontainers com o **Kafka pausado** durante o pagamento e venda no balcão (`BoxOfficeIntegrationTest`). |
| **Mensageria e Despacho** | RF-12, RF-13, RF-14 | US-12, US-13, US-14, US-15 | RNF-05, RNF-06 | Teste de reprocessamento com `processed_messages` e validação criptográfica da chave pública no QR Code. |
| **Telemetria e DevOps** | RF-15, RF-16, RF-17, RF-18 | US-16, US-17, US-18 | RNF-01, RNF-08 | Execução automatizada de `docker compose up` e pipeline de CI no GitHub Actions validando Pull Requests. |
