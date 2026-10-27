# Pacote 2 — Arquitetura e Infraestrutura de Desenvolvimento

> **Projeto:** BolsoPass
> **Disciplina:** Construção de Software — 2026/2 — PUCRS
> **Status:** Em elaboração

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
| **Frontend SPA** | React 18 + Vite + TypeScript + TanStack Query |
| **Backend** | Java 21 + Spring Boot 3.3 (Web, Data JPA, Kafka, Security) |
| **Identidade** | Keycloak 25 (OIDC) |
| **Banco relacional** | PostgreSQL 16 |
| **Cache / locks** | Redis 7 |
| **Mensageria** | Apache Kafka 3.7 (KRaft, sem Zookeeper) |
| **UI Kafka** | AKHQ |
| **Observabilidade** | Prometheus + Grafana + Loki |
| **Orquestração** | Docker Compose |
| **CI/CD** | GitHub Actions |
| **Testes** | JUnit 5 + Testcontainers + REST Assured + Vitest/Playwright |
