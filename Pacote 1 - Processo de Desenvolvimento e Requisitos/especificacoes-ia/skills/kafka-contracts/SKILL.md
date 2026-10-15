---
name: kafka-contracts
description: "Contratos de eventos, topologias de tópicos Kafka e garantia de idempotência no consumo para o BolsoPass."
---

# Contratos de Eventos e Topologia Kafka

Este documento orienta os agentes sobre os topicos do Apache Kafka e a semantica de entrega na plataforma BolsoPass.

## Topicos Padronizados

| Topico | Chave de Particionamento | Evento | Produtor | Consumidor |
| :--- | :--- | :--- | :--- | :--- |
| `reservas.criadas` | `event_id` | `ReservaCriadaEvent` | Modulo Inventario | Modulo Pedidos |
| `reservas.expiradas` | `event_id` | `ReservaExpiradaEvent` | Modulo Inventario | Modulo Pedidos, Frontend (WS) |
| `pedidos.pagos` | `order_id` | `PedidoPagoEvent` | Modulo Checkout (Outbox) | Modulo Despacho (Ingressos) |
| `pedidos.cancelados` | `order_id` | `PedidoCanceladoEvent` | Modulo Checkout (Outbox) | Modulo Inventario |
| `telemetria.eventos` | `session_id` | `UserNavigationEvent` | Gateway / Servicos | Modulo Telemetria |

## Semantica de Entrega e Idempotencia

- **Garantia de Entrega:** At-Least-Once.
- **Deduplicacao Mandatoria:** Todo consumidor Kafka deve manter controle de deduplicacao via tabela `processed_messages(message_id, consumer_group, processed_at)` em banco relacional, garantindo que retransmissoes sejam tratadas como no-op.
