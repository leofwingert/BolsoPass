# Pacote 2 — Arquitetura e Infraestrutura de Desenvolvimento

> **Projeto:** BolsoPass
> **Disciplina:** Construção de Software — 2026/2 — PUCRS
> **Status:** ✅ Entregue · revisado em 03/11/2026 para refletir a implementação (versões 1.0 a 5.1)

---

## Entregáveis do Pacote 2

| # | Documento | Descrição |
|---|---|---|
| 01 | [`01 - Arquitetura C4.md`](01%20-%20Arquitetura%20C4.md) | Modelo C4 nos níveis 1 (Contexto), 2 (Contêineres) e 3 (Componentes) |
| 02 | [`02 - Modelo de Dados Conceitual.md`](02%20-%20Modelo%20de%20Dados%20Conceitual.md) | ER conceitual por módulo + visão integrada |
| 03 | [`03 - Mensageria Kafka.md`](03%20-%20Mensageria%20Kafka.md) | Tópicos, partições, retenção, schemas e padrões de consumo |
| 04 | [`04 - Pipeline de Desenvolvimento.md`](04%20-%20Pipeline%20de%20Desenvolvimento.md) | CI/CD esquemática em GitHub Actions |
| 05 | [`05 - Processo de GCS.md`](05%20-%20Processo%20de%20GCS.md) | Git flow, branches, PRs, versionamento, commits |

---

## Stack adotada

| Camada | Tecnologia |
|---|---|
| **Frontend SPA** | React 18 + Vite 6 + TypeScript + TanStack Query 5 + Tailwind 4 |
| **Backend** | Java 25 (LTS) + Spring Boot 3.5 (Web, Data JPA, Kafka, Security, WebSocket) |
| **Identidade** | Keycloak 25 (OIDC) |
| **Banco relacional** | PostgreSQL 16 |
| **Cache / locks** | Redis 7 |
| **Mensageria** | Apache Kafka 3.8 (KRaft, sem Zookeeper) |
| **UI Kafka** | AKHQ |
| **Observabilidade** | Prometheus + Grafana + Loki |
| **Orquestração** | Docker Compose |
| **CI/CD** | GitHub Actions |
| **Testes** | JUnit 5 + Testcontainers + MockMvc + Vitest/Testing Library + Playwright |

---

## Arquitetura-alvo × estado atual

Os documentos descrevem a **arquitetura-alvo** (microsserviços). A implementação chega lá por
etapas. O plano está em [`Versão 5.0`](../Versão%205.0%20-%20Implementação/versao5.0.md) e o que já
existe, em [`Desenvolvimento.md`](../Desenvolvimento.md).

| Item | Estado atual (03/11/2026) |
|---|---|
| Serviços | Um backend Spring Boot com **um pacote por serviço** do C4 (`iam`, `catalog`, `inventory`, `orders`, `ticketing`, `realtime`, `boxoffice`…), em separação por etapas |
| Kafka | ✅ Outbox publica no Kafka real (envelope e cabeçalhos do doc 03); testado com o Kafka **pausado** |
| Infra no `docker compose` | ✅ PostgreSQL, Kafka, AKHQ, Redis, Mailpit, backend e frontend (HTTP 3000 e HTTPS 3443) |
| Testcontainers | ✅ PostgreSQL e Kafka reais (outbox, Kafka fora do ar, zero overselling) |
| Keycloak, Redis em uso, PDF, e-mail, CI | ⏳ Próximos passos da Versão 5.0 |
