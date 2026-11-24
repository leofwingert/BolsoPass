# Suite de Testes Automatizados e Verificacao de Qualidade (Pacote 3)

Este diretorio complementa a suite principal de testes automatizados JUnit 5, Mockito, AssertJ e Testcontainers localizada em [`../src/test/java/com/bolsopass/`](../src/test/java/com/bolsopass/).

## 1. Estrutura da Suite de Testes no Repositorio

| Classe / Script de Teste | Localizacao | Escopo e Requisitos Verificados |
| :--- | :--- | :--- |
| **`OrderStateMachineTest`** | `src/test/java/com/bolsopass/orders/domain/OrderStateMachineTest.java` | Testa todas as transicoes validas e invalidas da FSM do Pedido (`CRIADO -> RESERVADO -> AGUARDANDO_PAGAMENTO -> PAGO \| RECUSADO \| EXPIRADO \| CANCELADO`), imutabilidade do estado `PAGO`, proibicao de transicao `EXPIRADO -> PAGO` e deduplicacao em janela de 24h do `IdempotencyStore` (`RF-08`, `RF-09`, `RNF-05`, `RNF-07`). |
| **`SeatInventoryTest`** | `src/test/java/com/bolsopass/inventory/domain/SeatInventoryTest.java` | Testa alocacao atomica concorrente com 200 threads disputando o mesmo assento (`A-12`) garantindo `0%` de sobre-venda, expiracao estrita em 10 minutos ($t = 600\text{ s}$) com publicacao de `ReservaExpiradaEvent` e invariante de conservacao de estoque (`RF-06`, `RF-07`, `RNF-02`, `RNF-03`). |
| **`DomainModulesUnitTest`** | `src/test/java/com/bolsopass/domain/DomainModulesUnitTest.java` | Testa autenticacao RBAC, bloqueio por rate limiting apos 5 tentativas incorretas, rotacao de Refresh Token com invalidacao de familia por reuso malicioso, invariantes de capacidade de lotes no catalogo, assinatura criptografica ECDSA P-256 offline em $< 300\text{ ms}$, prevencao de dupla entrada na catraca e telemetria (`RF-01`..`RF-04`, `RF-13`, `RF-16`, `RNF-06`). |
| **`TransactionalOutboxIntegrationTest`** | `src/test/java/com/bolsopass/integration/TransactionalOutboxIntegrationTest.java` | Teste de integracao com PostgreSQL via Testcontainers e broker Apache Kafka validando rotas REST, concorrencia de 100 chamadas HTTP simultaneas, `Idempotency-Key` com 3 chamadas consecutivas gerando apenas 1 debito financeiro, resiliencia do Transactional Outbox durante queda e recuperacao do Kafka, consumo idempotente com `processed_messages` e geracao real de ingresso em PDF (`RF-10`, `RF-12`, `RNF-04`, `RNF-05`). |
| **`k6-flash-crowd-load-test.js`** | `tests/k6-flash-crowd-load-test.js` | Script de carga e estresse k6 simulando rampa de usuarios virtuais disputando reservas e checkout com `Idempotency-Key` (`RNF-01`, `RNF-02`). |

## 2. Como Executar os Testes e Validar Cobertura JaCoCo ($\ge 70\%$)

```bash
# Executar testes unitarios, testes de integracao e verificacao de cobertura JaCoCo (>= 70% no dominio)
./mvnw clean verify
```

O relatorio HTML detalhado do JaCoCo e gerado em `target/site/jacoco/index.html`.

