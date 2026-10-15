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
updated: 2026-10-06
status: pronto
---

# Matriz de Rastreabilidade e Pacotes de Entrega (PUCRS)

> [!abstract] Resumo de Governanca e Rastreabilidade
> Este documento consolida a rastreabilidade bidirecional entre as determinações formais do enunciado do **Projeto Integrador de Construção de Software (PUCRS 2026/2)** e os artefatos técnicos produzidos no ecossistema do [[00 - Visao Geral e Indice (MOC)|BolsoPass]]. A rastreabilidade assegura que cada requisito possua especificação formal, critério de aceitação, teste automatizado associado e marco de entrega correspondente.

---

## 1. Checklist Estruturado dos Entregaveis por Pacote

### Pacote 1: Processo de Desenvolvimento e Requisitos
> **Objetivo:** Estabelecer a visão do produto, a metodologia suportada por inteligência artificial, o backlog priorizado, os requisitos não-funcionais e a experiência de navegação do usuário.

| Item Exigido no Enunciado | Documento Correspondente no Vault | Status de Atendimento |
| :--- | :--- | :---: |
| **1. Descrição do processo de desenvolvimento suportado por IA** (ciclo, ferramentas, agentes e specs) | [[01 - Processo de Desenvolvimento Suportado por IA]] | Concluído |
| **2. Documento de Visão do Produto** (problema, escopo, proposta de valor e personas) | [[02 - Documento de Visao do Produto]] | Concluído |
| **3. Backlog com Épicos e Histórias no formato Story Mapping** | [[05 - User Story Mapping e Backlog]] | Concluído |
| **4. Requisitos Não-Funcionais** (concorrência, precisão de TTL de 10 min, resiliência) | [[04 - Requisitos Nao-Funcionais (RNFs)]] | Concluído |
| **5. Mockups e Mapa de Navegação do front-end** (wireframes e rotas da SPA) | [[07 - Mockups e Mapa de Navegacao Frontend]] | Concluído |
| **6. Detalhamento dos 5 Módulos Autônomos** (IAM, Inventário, Checkout, Despacho e DevOps) | [[03 - Requisitos Funcionais (5 Modulos)]] | Concluído |
| **7. Máquinas de Estados e Regras de Negócio** (FSM do Pedido, Outbox e Idempotência) | [[06 - Maquinas de Estados e Regras de Negocio]] | Concluído |

---

### Pacote 2: Arquitetura e Infraestrutura de Desenvolvimento
> **Objetivo:** Especificar a arquitetura técnica, o modelo C4, a topologia de mensageria, a modelagem de dados e as políticas de GCS.

| Item Exigido no Enunciado | Seção / Documento Previsto | Planejamento da Próxima Fase |
| :--- | :--- | :--- |
| **Descrição da arquitetura no [Modelo C4](https://c4model.com/)** (Nível 1 - Contexto, Nível 2 - Contêineres, Nível 3 - Componentes) | `Arquitetura C4 Model` (Diagramas Mermaid Níveis 1, 2 e 3) | Próxima iteração |
| **Descrição do Modelo de Dados Conceitual** | `Modelo de Dados & ERD` (Entidades, Agregados DDD e Esquemas Relacionais) | Próxima iteração |
| **Descrição da estrutura de mensageria do Kafka** | `Topologia Kafka` (Tópicos, Partições, Chaves e Schemas Avro/JSON) | Próxima iteração |
| **Descrição esquemática da Pipeline de desenvolvimento** | `Pipeline CI/CD` (Linters, Testes unitários $\ge 70\%$ e Testcontainers) | Próxima iteração |
| **Descrição do Processo de GCS** | `Processo de GCS` (Estratégia Git Flow, Regras de Proteção de Branch e PRs) | Próxima iteração |

---

### Pacote 3: Checkpoint do Desenvolvimento
- Link para o repositório Git com o estágio corrente de desenvolvimento funcional da plataforma.
- Documentação entregue nos pacotes 1 e 2 atualizada.
- Cobertura de testes unitários de domínio e máquinas de estado em evolução contínua.

---

### Pacote 4: Produto Completo
- Link para o repositório Git contendo o produto completo e funcional.
- Ambiente 100% orquestrado via `docker-compose.yml`.
- Testes automatizados executando com sucesso através de [Testcontainers](https://testcontainers.com/) reais para PostgreSQL, Kafka e Redis.
- Documentação técnica consolidada dos pacotes 1 e 2 atualizada.

---

## 2. Matriz Cruzada de Rastreabilidade (RF x RNF x Modulo)

A matriz a seguir cruza cada frente funcional autônoma com seus requisitos funcionais, requisitos não-funcionais associados e mecanismos automatizados de verificação:

| Módulo Autônomo | Requisitos Funcionais (RF) | RNFs Vinculados | Mecanismo de Verificação Automatizado |
| :--- | :--- | :--- | :--- |
| **Identidade e IAM** | RF-01, RF-02, RF-03 | RNF-06 | Testes unitários de validação de assinatura JWT e asserção de permissões RBAC nas rotas. |
| **Inventário e Concorrência** | RF-04, RF-05, RF-06, RF-07 | RNF-01, RNF-02, RNF-03 | Teste de estresse com 1.000 requisições simultâneas disputando uma vaga, aferindo 0% de sobre-venda. |
| **Pedidos e Checkout** | RF-08, RF-09, RF-10, RF-11 | RNF-04, RNF-05, RNF-07 | Testes de domínio da FSM ($\ge 70\%$ cobertura) e Testcontainers com injeção de falha no Kafka. |
| **Mensageria e Despacho** | RF-12, RF-13, RF-14 | RNF-05, RNF-06 | Teste de reprocessamento com `processed_messages` e validação criptográfica da chave pública no QR Code. |
| **Telemetria e DevOps** | RF-15, RF-16, RF-17, RF-18 | RNF-01, RNF-08 | Execução automatizada de `docker compose up` e pipeline de CI no GitHub Actions validando Pull Requests. |
