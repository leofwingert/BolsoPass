---
name: bolsopass-domain
description: "Invariantes de domínio, máquina de estados finitos do pedido e regras de concorrência com zero sobre-venda para a plataforma BolsoPass."
---

# Invariantes de Dominio e Regras do BolsoPass

Este documento orienta agentes de IA sobre as regras fundamentais de negocio e invariantes arquiteturais do sistema BolsoPass.

## Invariantes Inegociaveis

1. **Zero Sobre-venda (Zero Overselling):**
   - Sob nenhuma circunstancia o mesmo assento ou ingresso pode ser vendido para dois compradores distintos.
   - Qualquer operacao de reserva de assento deve ser atomica (via Redis `SET NX PX` ou `SELECT ... FOR UPDATE` relacional).
   - Tentativas concorrentes rejeitadas devem retornar `HTTP 409 Conflict`.

2. **TTL Estrito de Reserva (10 Minutos):**
   - Toda reserva temporaria tem tempo de vida maximo de exatamente 10 minutos (600 segundos).
   - Ao estourar o tempo sem confirmacao de pagamento, a reserva transiciona para `EXPIRADO` e o assento volta para `DISPONIVEL`.
   - Um evento `reservas.expiradas` e publicado no Kafka para desalocacao assincrona e notificacao do frontend.

3. **Maquina de Estados Finitos do Pedido:**
   - Transicoes validas:
     `CRIADO` -> `RESERVADO` -> `AGUARDANDO_PAGAMENTO` -> `PAGO` | `RECUSADO` | `EXPIRADO` | `CANCELADO`
   - O estado `PAGO` e terminal e imutavel. Tentativas de transicionar um pedido `PAGO` para qualquer outro estado devem disparar excecao de dominio.
   - Um pedido no estado `EXPIRADO` jamais pode transicionar para `PAGO`.

4. **Idempotencia em Pagamentos:**
   - O endpoint de checkout exige cabecalho `Idempotency-Key`.
   - Requisicoes duplicadas dentro da janela de 24 horas recebem o resultado da primeira transacao sem reprocessar cobranca.
