# BolsoPass API (backend)

Spring Boot 3.5 + Java 21. Hoje é um único serviço com um pacote por microsserviço do C4
(`iam`, `catalog`, `inventory`, `orders`, `ticketing`, `outbox`, `realtime`). A separação em
contêineres vem depois.

## Rodar

Precisa de um JDK 21+ (o do IntelliJ serve: `~/.jdks/openjdk-25.0.2`).

```bash
# Sem Docker: H2 em memória, dados de demonstração recriados a cada start
./mvnw spring-boot:run

# Com PostgreSQL (docker-compose.yml na raiz)
docker compose up -d
./mvnw spring-boot:run -Dspring-boot.run.profiles=postgres
```

API em `http://localhost:8080/api/v1`, WebSocket em `ws://localhost:8080/ws`.
O front (`apps/web-spa`, `pnpm dev`) faz proxy de `/api` e `/ws` para cá.

Contas: `sarah@bolsopass.io` (cliente) e `admin@bolsopass.io` (organizador/admin), senha
`bolsopass`. Operador: `OP-88219`, PIN `123456`. Cartão terminado em `0000` simula recusa.

## Testes

```bash
./mvnw test
```

FSM do pedido, 20 compradores disputando o mesmo assento (só 1 vence), pagamento
idempotente, recusa + desistência, CPF inválido, fila virtual e expiração em 10 min.

## Endpoints da jornada do cliente

| Método | Caminho | Regra |
|---|---|---|
| POST | `/auth/login`, `/auth/refresh`, `/auth/logout` | JWT 15 min, refresh 7 dias com rotação (RF-01) |
| GET | `/events`, `/events/{id}` | Público |
| GET | `/events/{id}/queue` | Sala de espera virtual |
| GET | `/events/{id}/seats` | `ROLE_CLIENT` |
| POST | `/reservations` | Trava atômica `SELECT ... FOR UPDATE`, 409 em conflito (RF-06) |
| GET | `/reservations/{id}` | Dono da reserva |
| POST | `/reservations/{id}/give-up` | Só após `RECUSADO` |
| POST | `/checkout` | Header `Idempotency-Key` obrigatório (RF-09), Outbox (RF-10) |
| GET | `/orders/me`, `/orders/{id}` | Dono do pedido |
| GET | `/tickets/me`, `/tickets/{id}`, `/tickets/{id}/qr` | QR ECDSA P-256 que muda a cada 15 s |
| GET | `/keys/qr` | Chave pública do QR (validação offline na portaria) |

## Endpoints de gestão e portaria

| Método | Caminho | Regra |
|---|---|---|
| GET | `/organizer/dashboard` | Métricas do organizador/admin |
| GET | `/organizer/events` | Eventos visíveis ao organizador/admin |
| POST | `/organizer/events/{id}/pause` | Alterna vendas entre pausadas e em vendas |
| GET | `/operator/events` | Eventos atribuídos ao operador/admin |
| GET | `/operator/events/{eventId}/cache` | Cache de ingressos para operação offline |
| POST | `/operator/events/{eventId}/scans` | Sincroniza leituras feitas no posto |

## Ainda não implementado

- Kafka: eventos ficam na tabela `outbox_events` e o relay só registra no log (`LoggingEventPublisher`).
- Redis e Keycloak: travas e refresh tokens usam o banco; chaves JWT/QR são geradas ao subir.
- Migrações Flyway (hoje `ddl-auto`), Testcontainers, Dockerfile.
- Reembolso de 7 dias: a FSM do doc 06 diz que `PAGO` é imutável, então o estorno precisa de
  um fluxo próprio (definir antes de implementar).
