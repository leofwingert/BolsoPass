---
name: transactional-outbox
description: "Padrão Transactional Outbox para eliminar o Dual-Write Dilemma com PostgreSQL e Apache Kafka no BolsoPass."
---

# Padrao Transactional Outbox

Este documento detalha o padrao mandatorio para prevencao da escrita dupla (Dual-Write Dilemma).

## Problema
Gravar a mudanca de estado no banco de dados e enviar evento ao Kafka em operacoes separadas causa inconsistencia em caso de falha de rede ou queda de processo.

## Solucao Implementada

1. **Gravacao Atomica na Transacao Relacional:**
   - Na mesma transacao do banco:
     ```sql
     UPDATE orders SET status = 'PAID' WHERE id = :order_id;
     INSERT INTO outbox_events (id, aggregate_type, aggregate_id, event_type, payload, status, created_at)
     VALUES (gen_random_uuid(), 'Order', :order_id, 'OrderPaid', :payloadJson, 'PENDING', NOW());
     ```
   - Commit ACID local garante que a escrita e atômica.

2. **Relay Worker Desacoplado:**
   - Worker independente faz polling ou escuta log de transacoes (CDC):
     ```sql
     SELECT * FROM outbox_events 
     WHERE status = 'PENDING' 
     ORDER BY created_at ASC 
     LIMIT 100 
     FOR UPDATE SKIP LOCKED;
     ```
   - Publica no topico `pedidos.pagos` no Kafka.
   - Ao receber o ACK do Kafka, atualiza `status = 'PUBLISHED'`.
