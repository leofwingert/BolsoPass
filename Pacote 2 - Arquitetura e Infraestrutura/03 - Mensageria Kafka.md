# 03 — Estrutura de Mensageria Kafka

> Topologia de tópicos, particionamento, retenção, schemas e padrões de produção/consumo adotados no BolsoPass.
> O contrato formal (AsyncAPI 2.6) está em [`especificacoes-ia/specs/asyncapi-kafka.yaml`](../Pacote%201%20-%20Processo%20de%20Desenvolvimento%20e%20Requisitos/especificacoes-ia/specs/asyncapi-kafka.yaml).

---

## 1. Cluster

| Item | Decisão |
|---|---|
| Versão | Apache Kafka 3.8 (imagem `apache/kafka:3.8.0`) |
| Modo | **KRaft** (sem Zookeeper) |
| Brokers | 1 em dev / 3 em produção simulada |
| Replication factor | `1` em dev / `3` em prod |
| `min.insync.replicas` | `1` em dev / `2` em prod |
| `acks` do producer | `all` para tópicos transacionais |
| Schema Registry | Confluent Schema Registry (opcional — JSON Schema no Pacote 3) |
| UI | AKHQ em `http://localhost:8089` |

---

## 2. Catálogo de tópicos

| Tópico | Produtor | Consumidores | Partições | Retenção | Chave de particionamento |
|---|---|---|---:|---|---|
| `eventos.publicados` | catalog-service | inventory (cria os assentos), telemetry | 6 | 30d | `event_id` |
| `lotes.virados` | catalog-service | notification, telemetry | 6 | 7d | `event_id` |
| `reservas.criadas` ✅ | inventory-service | **orders (cria o pedido)**, notification, telemetry | 12 | 7d | `event_id` |
| `reservas.expiradas` ✅ | inventory-service | orders (pedido → EXPIRADO), notification, telemetry | 12 | 7d | `reservation_id` |
| `reservas.liberadas` | inventory-service | notification, telemetry | 12 | 7d | `reservation_id` |
| `pedidos.criados` | orders-service | telemetry | 12 | 7d | `order_id` |
| `pedidos.pagos` ✅ | orders-service | ticketing (emite), inventory (assento → VENDIDO), notification, telemetry | 12 | 30d | `order_id` |
| `pedidos.cancelados` ✅ | orders-service | inventory (devolve o assento), notification, telemetry | 6 | 7d | `order_id` |
| `pagamentos.recusados` | orders-service | notification, telemetry | 6 | 7d | `order_id` |
| `ingressos.emitidos` | ticketing-service | notification (e-mail + PDF + push), telemetry | 12 | 30d | `order_id` |
| `ingressos.validados` | ticketing-service (portaria) | telemetry (entradas no painel) | 6 | 30d | `ticket_id` |
| `notificacoes.enviadas` | notification-service | telemetry | 3 | 7d | `order_id` |
| `auditoria.eventos` | todos | iam (grava o log), telemetry | 6 | 90d | `aggregate_id` |
| `telemetria.navegacao` | telemetry-service (`POST /telemetry/events`) | telemetry | 6 | 7d | `session_id` |
| `_dlq.<tópico-origem>` | (consumidor que falhou) | ops (manual) | 3 | 30d | igual ao original |

✅ = já publicado pelo backend hoje (relay do Outbox → Kafka), com as partições acima criadas na
subida da aplicação.

### Convenções de nomenclatura

- `<agregado-no-plural>.<ação-no-passado>`, em **português**: `reservas.criadas`, `pedidos.pagos`
  (eventos, não comandos). Segue o RNF-03, que nomeia o tópico `reservas.expiradas`.
- DLQ: prefixo `_dlq.` seguido do tópico de origem.
- Tudo em minúsculas, sem acentos.

---

## 3. Schema de evento (envelope padrão)

Todo evento publicado segue o envelope abaixo (JSON Schema simplificado):

```json
{
  "eventId": "e1153fd0-21b5-410c-8d24-2cc1386a11c9", // id da linha do Outbox: igual em todo reenvio
  "eventType": "PedidoPagoEvent",
  "eventVersion": "1.0",
  "occurredAt": "2026-11-01T23:55:11.475Z",
  "producer": "bolsopass-api",                       // depois da separação: "orders-service"
  "aggregateId": "ord-f5b7d2d1-5401-40a6-987b-0762936951dd",
  "payload": { "orderId": "...", "eventId": "evt-symphonic", "totalCents": 33000, "paidAt": "..." }
}
```

Exemplo real lido do tópico `pedidos.pagos` numa venda feita na bilheteria. O `eventId` é o id da
linha do Outbox: um reenvio carrega o mesmo id, e o consumidor descarta a duplicata.
`traceId` e `aggregateType` entram com a observabilidade (versão 5.0).

### Headers Kafka obrigatórios

| Header | Significado |
|---|---|
| `event-id` | duplicata do `eventId` do envelope (facilita o consumer filtrar sem desserializar) |
| `event-type` | duplicata do `eventType` |
| `trace-id` | propagação de traço (W3C) — planejado |
| `content-type` | `application/json` |

---

## 4. Produção segura — Transactional Outbox

Nenhum serviço produz direto do fluxo síncrono. O padrão aplicado é:

1. O caso de uso grava a mutação de domínio **+ linha em `outbox_messages`** na **mesma transação ACID**.
2. O `OutboxRelayWorker` (`@Scheduled(fixedDelay=500ms)`) consulta as linhas `PENDING` em ordem de criação, lote de 100, com `FOR UPDATE SKIP LOCKED`.
3. Para cada linha: publica no Kafka com `acks=all` e produtor idempotente, espera o ACK e só então marca `PUBLISHED`.
4. Kafka fora do ar: o produtor desiste em ~3 s, a linha continua `PENDING` e sai na próxima rodada, na mesma ordem.
5. Em caso de crash entre publicar e marcar, o mesmo `eventId` será reenviado — e deduplicado no consumidor.

**Comprovado em teste** (`ContainersIntegrationTest`, Testcontainers com Kafka 3.8 e PostgreSQL 16):
o Kafka é **pausado** durante um pagamento, o pagamento é aprovado, o evento fica `PENDING` e chega
ao tópico quando o broker volta.

**Garantia**: *at-least-once* na saída + *effectively-once* na recepção.

---

## 5. Consumo seguro — Idempotência

Todo consumidor:

1. Inicia transação no banco local.
2. `INSERT INTO processed_messages (message_id, consumer_group) VALUES (?, ?) ON CONFLICT DO NOTHING RETURNING *`.
3. Se **nenhuma linha retornada** → já processado → `commit` e `ack`.
4. Senão → executa a lógica de negócio na **mesma transação** → `commit`.
5. Faz `commit` do offset Kafka **só após** o commit do banco.

### Configurações de consumer

| Config | Valor |
|---|---|
| `enable.auto.commit` | `false` |
| `isolation.level` | `read_committed` |
| `max.poll.records` | `50` |
| `session.timeout.ms` | `30000` |
| `retries do processamento` | 3 (backoff exponencial 1s/5s/25s) → DLQ |

---

## 6. Fluxo coreográfico do checkout

```mermaid
sequenceDiagram
  participant SPA
  participant OR as orders-service
  participant IN as inventory-service
  participant K as Kafka
  participant TK as ticketing-service
  participant NT as notification-service

  SPA->>IN: POST /reservations (trava os assentos por 10 min)
  IN->>IN: Redis SETNX + SELECT … FOR UPDATE; id do pedido nasce aqui
  IN-->>K: reservas.criadas (via outbox)
  IN-->>SPA: 201 Created + reserva + pedido RESERVADO
  K-->>OR: reservas.criadas
  OR->>OR: cria o pedido (idempotente) e grava pedidos.criados
  K-->>NT: reservas.criadas (mapa ao vivo: assento ocupado)

  SPA->>OR: POST /checkout (Idempotency-Key)
  OR->>OR: gateway aprova → PAGO + pedidos.pagos (mesma transação)
  OR-->>K: pedidos.pagos (via outbox)

  K-->>TK: pedidos.pagos
  TK->>TK: gera PDF + QR assinado
  TK-->>K: ingressos.emitidos

  K-->>IN: pedidos.pagos (marca VENDIDO)
  K-->>NT: ingressos.emitidos
  NT->>NT: envia e-mail + SMS + WebSocket push
  NT-->>K: notificacoes.enviadas
```

**Reserva por evento, não por chamada direta.** O enunciado pede que Pedidos "consome reservas do
Inventário". Por isso o orders-service não chama o inventory: ele reage a `reservas.criadas`. Se o
tempo acaba sem pagamento, o inventory publica `reservas.expiradas` e o orders leva o pedido a
`EXPIRADO`.

- Se o checkout chegar antes do evento ser consumido (milissegundos), o orders responde `409 pedido
  em criação` e a SPA tenta de novo.
- **Venda no balcão:** mesmo fluxo. A reserva entra pela rota da bilheteria (sem sala de espera) e o
  pagamento aceita dinheiro.

---

## 7. Dead Letter Queue (DLQ)

Toda falha após 3 tentativas vai para `_dlq.<tópico-origem>` com headers extras:

- `original-topic`, `original-partition`, `original-offset`
- `failure-reason` (classe da exceção)
- `failure-stacktrace` (truncada em 4KB)
- `failed-at` (ISO 8601)

O time de ops inspeciona via AKHQ e usa um utilitário `dlq-replay` (CLI Spring) para reinjetar após correção.

---

## 8. Grupos de consumidores

| Consumer group | Serviço | Tópicos |
|---|---|---|
| `orders-reservas` | orders-service | `reservas.criadas`, `reservas.expiradas` |
| `orders-payment` | orders-service | (webhook, não Kafka) |
| `inventory-pedidos` | inventory-service | `eventos.publicados`, `pedidos.pagos`, `pedidos.cancelados` |
| `ticketing-pedidos` | ticketing-service | `pedidos.pagos` |
| `notification-all` | notification-service | `reservas.*`, `pedidos.*`, `lotes.virados`, `ingressos.emitidos` |
| `iam-auditoria` | iam-service | `auditoria.eventos` |
| `telemetry-all` | telemetry-service | assinatura por padrão `*` (regex) |

---

## 9. Observabilidade

- **Métricas**: `kafka_producer_record_send_total`, `kafka_consumer_fetch_latency_avg`, `kafka_consumer_records_lag` por tópico (Prometheus).
- **Alertas**: lag > 10k mensagens, DLQ > 0, idade do outbox > 5s.
- **Dashboards Grafana**: throughput por tópico, latência fim-a-fim do checkout, taxa de duplicatas filtradas pelo `processed_messages`.
