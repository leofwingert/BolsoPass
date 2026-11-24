---
title: "Implementação dos Microsserviços, Agregados de Domínio e Contratos"
aliases:
  - Implementação Pacote 3
  - Microsserviços Implementados
  - Domínio BolsoPass
tags:
  - pucrs
  - construcao-de-software
  - ddd
  - spring-boot
  - java-21
  - outbox-pattern
  - pacotes/pacote-3
created: 2026-11-23
updated: 2026-11-23
status: pronto
---

# Implementacao dos Microsservicos e Agregados de Dominio

> [!abstract] Resumo da Implementação Técnica
> Este documento descreve a materialização em código dos requisitos funcionais estabelecidos em [[03 - Requisitos Funcionais (5 Modulos)]] e das regras de negócio modeladas em [[06 - Maquinas de Estados e Regras de Negocio]]. A implementação adota Java 21, [Spring Boot 3.3](https://spring.io/projects/spring-boot) e [Domain-Driven Design (DDD)](https://martinfowler.com/tags/domain%20driven%20design.html), isolando rigorosamente as regras e invariantes em pacotes `domain` puros, livres de acoplamento com frameworks de infraestrutura.

---

## 1. Estrutura de Pacotes e Bounded Contexts

Para permitir tanto a evolução modular independente quanto a execução unificada e verificação imediata na esteira de integração contínua do [[00 - Visao Geral e Indice (MOC)|BolsoPass]], o código-fonte em `src/main/java/com/bolsopass/` está organizado por contextos delimitados (*Bounded Contexts*), cada qual subdividido nas camadas `domain`, `service`, `infrastructure` e `api`:

```text
src/main/java/com/bolsopass/
├── BolsoPassApplication.java
├── config/
│   └── SecurityConfig.java
├── iam/
│   ├── api/AuthController.java
│   ├── domain/ (Role, UserAccount, RefreshTokenFamily, AuditEvent, Exceptions)
│   └── service/IamService.java
├── catalog/
│   └── domain/EventCatalogItem.java
├── inventory/
│   ├── api/ReservationController.java
│   ├── domain/ (Seat, SeatStatus, Reservation, ReservationStatus, SeatInventory, ReservaExpiradaEvent)
│   └── service/InventoryService.java
├── orders/
│   ├── api/CheckoutController.java
│   ├── domain/ (Order, OrderStatus, OrderStateMachine, IdempotencyStore, PedidoPagoEvent)
│   ├── infrastructure/ (OrderEntity, OrderJpaRepository, OutboxEventEntity, OutboxEventRepository)
│   └── service/ (CheckoutService, OutboxRelayWorker, PaymentGatewaySimulator)
├── ticketing/
│   ├── domain/ (DigitalTicket, TicketCryptoSigner, ScanResultStatus)
│   ├── infrastructure/ (ProcessedMessageEntity, ProcessedMessageRepository)
│   └── service/TicketingService.java
└── telemetry/
    └── domain/TelemetrySnapshot.java
```

---

## 2. Modulo 1: Identidade, Autenticacao e Governanca (`com.bolsopass.iam`)

### Por que o Refresh Token utiliza Rotação com Detecção de Reuso por Família
Em sistemas sujeitos a tráfego massivo, validar sessões em banco relacional a cada chamada HTTP criaria um gargalo centralizado. Por isso, o `IamService` emite Access Tokens stateless no padrão [RFC 7519 (JWT)](https://datatracker.ietf.org/doc/html/rfc7519) assinados com chave assimétrica RSA-2048 (`RS256`) e validade estrita de 15 minutos ($900\text{ segundos}$).

Para renovar a sessão sem exigir nova senha (`US-02`), o agregado `RefreshTokenFamily` implementa a rotação segura de Refresh Tokens:
- Cada login inaugura uma família de tokens identificada por `familyId`.
- Ao chamar `POST /api/v1/auth/refresh`, o token atual é marcado como rotacionado e revogado, e um novo token filho é emitido na mesma família.
- Caso um invasor intercepte um Refresh Token antigo e tente reutilizá-lo após a rotação legítima, o método `RefreshTokenFamily.rotate(...)` detecta que o nó já foi rotacionado, **revoga instantaneamente todos os tokens ativos daquela família** e lança `RefreshTokenReuseException` (mapeada para `HTTP 401 Unauthorized`).

Adicionalmente, o agregado `UserAccount` protege a plataforma contra ataques de dicionário e força bruta (`US-03`): após 5 tentativas consecutivas de autenticação com senha incorreta, a conta é temporariamente bloqueada por 1 minuto, retornando `HTTP 429 Too Many Requests` e gravando um registro imutável `AuditEvent`.

---

## 3. Modulo 2: Catalogo, Assentos e Concorrencia (`com.bolsopass.catalog` & `com.bolsopass.inventory`)

### Invariantes do Catálogo (`EventCatalogItem`)
O agregado `EventCatalogItem` valida na construção e na adição de lotes (`TicketLot`) duas invariantes fundamentais do modelo conceitual definido no Pacote 2:
1. A data de encerramento de cada lote deve ser estritamente posterior à data de abertura (`endsAt.isAfter(startsAt)`).
2. A soma das cotas de todos os lotes vinculados a um evento jamais pode ultrapassar a capacidade física total do estádio ou arena (`sum(lot.quota) <= venueCapacityTotal`).

### Alocação Atômica com Tolerância Zero à Sobre-venda (`SeatInventory` e `Seat`)
Para cumprir o requisito inegociável de **Zero Overselling** (`RNF-02` e `US-05`), a alocação de assentos no `SeatInventory` opera com sincronização de granularidade fina por assento físico (`Seat`):
- Assentos diferentes (ex.: `A-01` e `B-04`) não competem pelo mesmo monitor, permitindo alta vazão paralela.
- Requisições concorrentes disputando exatamente o mesmo assento (ex.: `A-12`) são serializadas atomicamente no método `Seat.allocateAtomically(...)`. A primeira thread transiciona o estado de `DISPONIVEL` para `RESERVADO_TEMPORARIO` com `ttlSeconds = 600` ($10\text{ minutos}$) e retorna `HTTP 201 Created`; todas as demais recebem imediatamente `SeatAlreadyReservedException`, traduzida pelo `ReservationController` em `HTTP 409 Conflict` com o código `"SEAT_ALREADY_RESERVED"`, em total conformidade com o contrato OpenAPI 3.0.
- A cada mutação, o método `SeatInventory.assertConservationInvariant()` verifica que a soma de assentos disponíveis, reservados temporariamente, vendidos e utilizados permanece estritamente igual ao total cadastrado.

---

## 4. Modulo 3: Pedidos, FSM, Idempotencia e Transactional Outbox (`com.bolsopass.orders`)

### Máquina de Estados Finitos (`OrderStateMachine` e `Order`)
A classe `OrderStateMachine` codifica a matriz formal de transições de estado do pedido especificada em [[06 - Maquinas de Estados e Regras de Negocio]]:

$$\text{CRIADO} \longrightarrow \text{RESERVADO} \longrightarrow \text{AGUARDANDO\_PAGAMENTO} \longrightarrow \text{PAGO} \mid \text{RECUSADO} \mid \text{EXPIRADO} \mid \text{CANCELADO}$$

- **Imutabilidade do estado `PAGO`:** Uma vez confirmado o pagamento, o pedido atinge estado terminal imutável. Qualquer tentativa de transicionar de `PAGO` para `CANCELADO`, `EXPIRADO` ou `RECUSADO` dispara `InvalidOrderStateTransitionException`.
- **Bloqueio de pagamento pós-expiração:** Um pedido em estado `EXPIRADO` (ou cujo relógio `now` tenha atingido `reservationExpiresAt`) jamais pode transicionar para `PAGO`. Caso o pagamento chegue após os 600 segundos da reserva, o pedido transiciona automaticamente para `EXPIRADO` e lança `ReservationExpiredException` (`HTTP 409 Conflict`).
- **Recuperação de falha de cartão (`US-11`):** Um pedido em estado `RECUSADO` pode retornar para `AGUARDANDO_PAGAMENTO` caso o comprador submeta outro meio de pagamento válido dentro da janela restante dos 10 minutos.

### Idempotência HTTP (`IdempotencyStore`)
Em aderência à especificação [IETF Idempotency-Key](https://datatracker.ietf.org/doc/draft-ietf-httpapi-idempotency-key-header/) (`RF-09` e `US-10`), o `CheckoutService` intercepta todas as chamadas a `POST /api/v1/checkout`:
- Armazena o hash do payload (`reservationId:paymentMethod:installments`) junto ao estado da execução (`PROCESSING` ou `COMPLETED`) por 24 horas.
- Chamadas repetidas com a mesma chave e o mesmo payload retornam a resposta `CheckoutResponse` original sem invocar novamente o `PaymentGatewaySimulator`.
- Chamadas que reutilizem uma `Idempotency-Key` existente com parâmetros divergentes (ou enquanto a primeira chamada ainda está em `PROCESSING`) são barradas com `IdempotencyConflictException` (`HTTP 409 Conflict`).

### Eliminação do Dual-Write Dilemma (`Transactional Outbox Pattern`)
No método `CheckoutService.processCheckout(...)`, anotado com `@Transactional`, a persistência da entidade `OrderEntity` (tabela `orders`) e da entidade `OutboxEventEntity` (tabela `outbox_events`, com `status = 'PENDING'` e tópico `pedidos.pagos`) ocorre na **mesma transação ACID relacional**.

O componente `OutboxRelayWorker` executa de forma desacoplada a query nativa com bloqueio não-bloqueante do [PostgreSQL](https://www.postgresql.org/docs/current/explicit-locking.html#LOCKING-ROWS):

```sql
SELECT * FROM outbox_events
WHERE status = 'PENDING'
ORDER BY created_at ASC
LIMIT 100
FOR UPDATE SKIP LOCKED
```

Ao publicar cada evento no cluster [Apache Kafka](https://kafka.apache.org/) e confirmar o recebimento do ACK, o worker marca o registro como `PUBLISHED` e grava o timestamp `published_at`.

---

## 5. Modulo 4: Emissao Criptografica de Ingressos e Despacho (`com.bolsopass.ticketing`)

1. **Deduplicação Mandatória no Consumidor (`ProcessedMessageEntity`):**
   Ao consumir eventos do tópico `pedidos.pagos`, o `TicketingService` verifica e insere o identificador da mensagem na tabela relacional `processed_messages(message_id, consumer_group, processed_at)`. Retransmissões decorrentes da semântica *At-Least-Once* do Kafka são identificadas pela chave primária e descartadas como *no-op* (`RF-12` / `US-12`).
2. **Assinatura Assimétrica ECDSA P-256 e Compilação PDF (`TicketCryptoSigner` e `DigitalTicket`):**
   Para cada ingresso emitido, o sistema gera um hash SHA-256 de auditoria temporal, monta o payload canônico do QR Code e o assina com chave privada ECDSA sobre a curva `secp256r1` (`SHA256withECDSA`). Em seguida, renderiza o arquivo PDF oficial com a imagem matricial do QR Code embutida (`RF-13` / `US-13`).
3. **Validação Offline na Catraca e Prevenção de Dupla Entrada (`US-15`):**
   Na portaria, o método `DigitalTicket.validateAndScanAtGate(...)` confere a assinatura digital utilizando exclusivamente a chave pública ECDSA em menos de $5\text{ ms}$ (muito abaixo do limite de $300\text{ ms}$ definido em `RNF-06`). A primeira leitura válida retorna `AUTORIZADO`; qualquer tentativa subsequente com o mesmo ingresso retorna `DUPLICATA`, e qualquer adulteração no payload ou assinatura retorna `INVALIDO`.

