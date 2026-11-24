---
title: "Pacote 2 - Arquitetura e Infraestrutura de Desenvolvimento (MOC)"
aliases:
  - MOC Pacote 2
  - Arquitetura e Infraestrutura
  - Pacote 2 BolsoPass
tags:
  - pucrs
  - construcao-de-software
  - arquitetura
  - c4-model
  - kafka
  - devops
  - pacotes/pacote-2
created: 2026-10-26
updated: 2026-11-23
status: pronto
---

# Pacote 2: Arquitetura e Infraestrutura de Desenvolvimento

> [!abstract] Resumo Executivo do Pacote 2
> Este Mapa de Conteúdo (MOC) organiza as especificações arquiteturais e de infraestrutura de desenvolvimento do [[00 - Visao Geral e Indice (MOC)|BolsoPass]] correspondentes ao **Pacote 2** da disciplina de Construção de Software (Escola Politécnica – PUCRS, 2026/2). Todos os artefatos deste pacote foram atualizados na versão `v0.3.0` para manter rastreabilidade bidirecional com o código-fonte, entidades relacionais, tópicos Kafka e workflows de CI/CD implementados no [[Pacote 3 - Checkpoint do Desenvolvimento/00 - Visao Geral e Indice (MOC)|Pacote 3]].

---

## 1. Mapa de Conteudo do Pacote 2

| Documento | Foco e Escopo Técnico | Status |
| :--- | :--- | :--- |
| [[01 - Arquitetura C4]] | Diagramas e decisões arquiteturais no [Modelo C4](https://c4model.com/) nos Níveis 1 (Contexto), 2 (Contêineres) e 3 (Componentes), mapeados para as classes de produção em `src/main/java/com/bolsopass/`. | Concluído (`v0.2.0` / atualizado em `v0.3.0`) |
| [[02 - Modelo de Dados Conceitual]] | Modelagem conceitual por contexto delimitado (*Bounded Context*), invariantes de domínio e mapeamento para entidades JPA (`orders`, `outbox_events`, `processed_messages`). | Concluído (`v0.2.0` / atualizado em `v0.3.0`) |
| [[03 - Mensageria Kafka]] | Topologia de tópicos [Apache Kafka](https://kafka.apache.org/) (`pedidos.pagos`, `reservas.expiradas`), padrão [Transactional Outbox](https://microservices.io/patterns/data/transactional-outbox.html) com `FOR UPDATE SKIP LOCKED` e consumo idempotente. | Concluído (`v0.2.0` / atualizado em `v0.3.0`) |
| [[04 - Pipeline de Desenvolvimento]] | Esteira de CI/CD no GitHub Actions (`.github/workflows/ci.yml` e `pr-checks.yml`), validação bloqueante de cobertura [JaCoCo](https://www.jacoco.org/jacoco/) $\ge 70\%$ e [Testcontainers](https://testcontainers.com/). | Concluído (`v0.2.0` / atualizado em `v0.3.0`) |
| [[05 - Processo de GCS]] | Política de Gerenciamento da Configuração de Software: monorepo Maven, GitFlow adaptado, Conventional Commits, SemVer (`v0.1.0`, `v0.2.0`, `v0.3.0`) e critérios de revisão. | Concluído (`v0.2.0` / atualizado em `v0.3.0`) |

---

## 2. Stack Tecnologica Consolidada

| Camada Arquitetural | Tecnologia Adotada | Papel no Ecossistema BolsoPass |
| :--- | :--- | :--- |
| **Frontend SPA** | React 18 + Vite + TypeScript + HTML5/CSS3 | Interface reativa do comprador, portaria e dashboard (protótipo em `mockups-high-res/index.html`). |
| **Backend & Domínio** | Java 21 + [Spring Boot 3.3](https://spring.io/projects/spring-boot) | Implementação dos módulos `iam`, `catalog`, `inventory`, `orders`, `ticketing` e `telemetry`. |
| **Identidade e Segurança** | [Keycloak 25](https://www.keycloak.org/) + JWT RS256 + ECDSA P-256 | Autenticação OIDC/JWT stateless, RBAC e assinatura criptográfica offline de ingressos. |
| **Banco de Dados Relacional** | [PostgreSQL](https://www.postgresql.org/) | Persistência transacional ACID, tabela `outbox_events` (`FOR UPDATE SKIP LOCKED`) e `processed_messages`. |
| **Cache e Travas Distribuídas** | [Redis 7](https://redis.io/) | Cache-aside de catálogo, rotação de Refresh Tokens e exclusão mútua de reservas temporárias. |
| **Mensageria Assíncrona** | [Apache Kafka 3.7](https://kafka.apache.org/) (KRaft) | Desacoplamento temporal entre checkout, inventário, emissão de ingressos e telemetria. |
| **Testes e Qualidade** | JUnit 5 + AssertJ + [Testcontainers](https://testcontainers.com/) + [JaCoCo](https://www.jacoco.org/jacoco/) + k6 | Testes unitários de domínio ($93,38\%$ de cobertura de instruções), testes de integração e carga. |
| **Integração Contínua** | GitHub Actions (`ci.yml`, `pr-checks.yml`) | Build automatizada, execução de testes e enforcement de cobertura mínima de $70\%$. |

