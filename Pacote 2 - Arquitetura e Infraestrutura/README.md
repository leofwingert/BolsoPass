# Pacote 2 — Arquitetura e Infraestrutura de Desenvolvimento

> **Projeto:** BolsoPass  
> **Disciplina:** Construção de Software — 2026/2 — Escola Politécnica — PUCRS  
> **Status:** Concluído (`v0.2.0` — atualizado e sincronizado com a implementação do Pacote 3 `v0.3.0`)

---

## 1. Entregaveis do Pacote 2

| # | Documento | Descrição | Status |
| :---: | :--- | :--- | :---: |
| **00** | [`00 - Visao Geral e Indice (MOC).md`](00%20-%20Visao%20Geral%20e%20Indice%20%28MOC%29.md) | Mapa de conteúdo (MOC) da arquitetura e infraestrutura do BolsoPass | Concluído |
| **01** | [`01 - Arquitetura C4.md`](01%20-%20Arquitetura%20C4.md) | Modelo C4 nos níveis 1 (Contexto), 2 (Contêineres) e 3 (Componentes) mapeado para as classes do Pacote 3 | Concluído |
| **02** | [`02 - Modelo de Dados Conceitual.md`](02%20-%20Modelo%20de%20Dados%20Conceitual.md) | Modelo conceitual e relacional por contexto delimitado + mapeamento para entidades JPA (`orders`, `outbox_events`, `processed_messages`) | Concluído |
| **03** | [`03 - Mensageria Kafka.md`](03%20-%20Mensageria%20Kafka.md) | Tópicos (`pedidos.pagos`, `reservas.expiradas`), Transactional Outbox (`FOR UPDATE SKIP LOCKED`) e consumo idempotente | Concluído |
| **04** | [`04 - Pipeline de Desenvolvimento.md`](04%20-%20Pipeline%20de%20Desenvolvimento.md) | Pipeline CI/CD em GitHub Actions (`.github/workflows/ci.yml` e `pr-checks.yml`) com gate JaCoCo $\ge 70\%$ e Testcontainers | Concluído |
| **05** | [`05 - Processo de GCS.md`](05%20-%20Processo%20de%20GCS.md) | GitFlow adaptado, Conventional Commits, SemVer (`v0.1.0`, `v0.2.0`, `v0.3.0`) e governança de código | Concluído |

---

## 2. Stack Adotada e Implementada

| Camada | Tecnologia |
| :--- | :--- |
| **Frontend SPA** | React 18 + Vite + TypeScript + Protótipo High-Res HTML5/CSS3 (`mockups-high-res/index.html`) |
| **Backend & Domínio** | Java 21 + Spring Boot 3.3.5 (Web, Data JPA, Kafka, Data Redis, Security, WebSocket, Actuator) |
| **Identidade & Criptografia** | Keycloak 25 (OIDC) + JWT RS256 (`jjwt 0.12.6`) + QR Code ECDSA P-256 (`secp256r1` / OpenPDF / ZXing) |
| **Banco Relacional** | PostgreSQL 15/16 (`orders`, `outbox_events`, `processed_messages`) |
| **Cache / Locks** | Redis 7 |
| **Mensageria** | Apache Kafka 3.7 (KRaft, sem ZooKeeper) |
| **UI Kafka & Observabilidade** | AKHQ + Prometheus + Grafana |
| **CI/CD** | GitHub Actions (`.github/workflows/ci.yml` e `.github/workflows/pr-checks.yml`) |
| **Testes e Cobertura** | JUnit 5 + AssertJ + Testcontainers + JaCoCo (**$93,38\%$ instruções e $73,98\%$ branches no domínio**) + k6 |
