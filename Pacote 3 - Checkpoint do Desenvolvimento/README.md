# Pacote 3 — Checkpoint do Desenvolvimento e Testes Automatizados

> **Projeto:** BolsoPass  
> **Disciplina:** Construção de Software — 2026/2 — Escola Politécnica — PUCRS  
> **Status:** Concluído (`v0.3.0`)

---

## 1. Entregaveis do Pacote 3

| # | Artefato / Diretorio | Descricao |
| :---: | :--- | :--- |
| **00** | [`00 - Visao Geral e Indice (MOC).md`](00%20-%20Visao%20Geral%20e%20Indice%20%28MOC%29.md) | Mapa de conteúdo do Pacote 3 e rastreabilidade com o enunciado da PUCRS |
| **01** | [`01 - Implementacao dos Microsservicos e Dominio.md`](01%20-%20Implementacao%20dos%20Microsservicos%20e%20Dominio.md) | Detalhamento técnico dos 5 módulos implementados em Java 21 e Spring Boot 3.3 |
| **02** | [`02 - Suite de Testes e Cobertura de Dominio JaCoCo.md`](02%20-%20Suite%20de%20Testes%20e%20Cobertura%20de%20Dominio%20JaCoCo.md) | Testes unitários da FSM (`OrderStateMachineTest`), concorrência (`SeatInventoryTest`) e cobertura JaCoCo $\ge 70\%$ |
| **03** | [`03 - Testes de Integracao Testcontainers e Pipeline CI.md`](03%20-%20Testes%20de%20Integracao%20Testcontainers%20e%20Pipeline%20CI.md) | Testes de integração com PostgreSQL (Testcontainers) + Kafka (`TransactionalOutboxIntegrationTest`) e GitHub Actions |
| **04** | [`../src/main/java/com/bolsopass/`](../src/main/java/com/bolsopass/) | Código-fonte de produção dos módulos (`iam`, `catalog`, `inventory`, `orders`, `ticketing`, `telemetry`) |
| **05** | [`../src/test/java/com/bolsopass/`](../src/test/java/com/bolsopass/) & [`../tests/`](../tests/) | Suíte de testes unitários, concorrência multithread, integração com Testcontainers e script de carga k6 |
| **06** | [`../.github/workflows/`](../.github/workflows/) | Esteira automatizada de CI/CD no GitHub Actions (`ci.yml` e `pr-checks.yml`) |

---

## 2. Como Compilar, Executar os Testes e Auditar a Cobertura ($\ge 70\%$)

```bash
# Executar compilacao, testes unitarios, testes de integracao e verificacao bloqueante JaCoCo (>= 70%)
./mvnw clean verify
```

Após a execução, o relatório detalhado de cobertura do JaCoCo pode ser visualizado em:
- `target/site/jacoco/index.html`

