---
name: testing-strategy
description: "Estratégia de testes automatizados, cobertura mínima de 70% na FSM e integração com Testcontainers para o BolsoPass."
---

# Estrategia de Testes Automatizados

Este documento orienta os agentes na construcao de testes conformes aos requisitos da PUCRS.

## Requisitos Obrigatorios de Teste

1. **Testes Unitarios com Cobertura de Dominio >= 70%:**
   - Focar na FSM do Pedido (`OrderStateMachineTest`) e no agregador de Reserva (`SeatInventoryTest`).
   - Testar todas as transicoes validas e asserir que transicoes invalidas lancam excecao.
   - Testar cenarios de borda: tentativa de pagar com timeout expirado, concorrencia de reservas, chave de idempotencia duplicada.

2. **Testes de Integracao com Testcontainers:**
   - Utilizar instâncias reais de PostgreSQL, Apache Kafka e Redis instanciadas dinamicamente via Testcontainers.
   - Nao utilizar bancos H2 ou mocks em memoria para testes de integracao transacional.
   - Testar o Transactional Outbox simulando queda e reinicio do broker Kafka.

3. **Cenarios de Resiliencia e Concorrencia:**
   - Teste multithread com `ExecutorService` disparando 100 requisições simultaneas para a mesma vaga, validando que apenas 1 obtém sucesso (zero sobre-venda).
