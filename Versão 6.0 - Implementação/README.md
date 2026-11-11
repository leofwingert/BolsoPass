# BolsoPass — Versão 6.0: Check-in por Reconhecimento Facial

> **Escopo:** o cliente cadastra o rosto para o ingresso e entra no evento só com o rosto; a
> portaria escolhe entre QR Code e reconhecimento facial. Primeiro microsserviço separado do
> backend: `facial-checkin-service` (Java 25, Spring Boot 3.5, AWS Rekognition).
> **Branch:** `feature/facial-checkin-service` · **Requisitos:** RF-13 (entrada na portaria), RF-02
> (RBAC), LGPD art. 11 (dado biométrico)
> **Versões anteriores:** [5.0](../Versão%205.0%20-%20Implementação/versao5.0.md) (microsserviços) ·
> [5.1](../Versão%205.1%20-%20Implementação/README.md) (cadastro e usuários)

## Andamento

| Parte | Situação | O que entra |
| :-: | :-: | :--- |
| 1 | ✅ 09/11 | Serviço, contrato `checkin.realizado`, Rekognition simulado e real, testes, Docker |
| 2 | ⏳ | Telas: modal de cadastro do rosto em Meus Pedidos e modo facial na portaria |
| 3 | ⏳ | Rekognition real com a conta AWS do grupo; C4 e Kafka do Pacote 2 |

---

## 1. Como funciona

```
Cliente (Meus Pedidos)                          Portaria (celular do operador)
  aceita o termo LGPD                              escolhe "Reconhecimento facial"
  envia a selfie ──► facial-checkin-service ◄── envia a foto
                       │ IndexFaces              │ SearchFacesByImage (≥ 90%)
                       ▼                         ▼
                   AWS Rekognition          achou o ingresso?
                   (collection do evento)        │ sim
                                                 ▼
                                    backend: registra a entrada (mesma regra do QR)
                                    + checkin.realizado no Outbox (mesma transação) ──► Kafka
```

| Regra | Onde |
| :--- | :--- |
| Sem **consentimento** ativo (versão atual do termo), nenhum rosto é indexado | `ConsentService` |
| **Só o titular** do ingresso cadastra o próprio rosto; o comprador não consente pelo acompanhante | `EnrollmentService` + `GET /tickets/{id}/biometric-eligibility` |
| **A imagem nunca é gravada**: só FaceId, ingresso, evento, usuário e datas (testado no PostgreSQL real) | `FaceEnrollment` |
| Uma collection por evento, criada sob demanda; `ExternalImageId` = id do ingresso | `AwsRekognitionFaceRecognition` |
| Similaridade mínima de **90%** | `facial.similarity-threshold` |
| Foto sem rosto ou com mais de um rosto: 422 com orientação | Rekognition / simulado |
| **Revogar** o consentimento apaga os rostos em todas as collections (`DeleteFaces`) | `ConsentService.revoke` |
| **Retenção:** 7 dias depois do evento, a collection é apagada | `RetentionJob` |
| O backend é a **fonte única** de "ingresso já usado": QR e rosto nunca liberam o mesmo ingresso (linha travada com `FOR UPDATE`) | `CheckinService` (backend) |
| `checkin.realizado` é gravado **na mesma transação** da entrada (sem Dual-Write); mesmo contrato para QR e FACIAL | `CheckinService` (backend) |
| Rekognition ou backend fora do ar: 503 "use o QR Code" (o QR continua funcionando offline) | `HttpTicketing`, adaptador AWS |
| Leitura facial só para a portaria (`ROLE_OPERATOR`); cadastro só para o cliente | `SecurityConfig` |

### Por que o serviço facial não publica o evento sozinho

O enunciado exige tratar o **Dual-Write**. Se o serviço facial gravasse a entrada num banco e
publicasse no Kafka por conta própria, a entrada e o evento poderiam divergir, e QR e rosto
poderiam liberar o mesmo ingresso. Por isso quem registra a entrada é o backend, que já é dono do
ingresso, com o Outbox na mesma transação. O contrato `checkin.realizado` é o mesmo para os dois
métodos (campo `metodo`).

## 2. API

Especificação completa: [`.agents/specs/openapi-facial-checkin.yaml`](../.agents/specs/openapi-facial-checkin.yaml).
Evento: canal `checkin.realizado` em [`asyncapi-kafka.yaml`](../.agents/specs/asyncapi-kafka.yaml).
Regras para agentes: [`.agents/skills/facial-checkin/SKILL.md`](../.agents/skills/facial-checkin/SKILL.md).

| Método | Caminho (`/api/v1/facial`) | Quem |
| :--- | :--- | :--- |
| `GET` / `POST` / `DELETE` | `/consent` | Cliente: ver, aceitar, revogar |
| `GET` / `POST` | `/enrollments` | Cliente: listar, cadastrar (multipart: `ticketId` + `image`) |
| `DELETE` | `/enrollments/{ticketId}` | Cliente |
| `POST` | `/events/{eventId}/checkins` | Portaria (multipart: `image` + `gate`) |

No backend entraram:
- `GET /api/v1/auth/jwks`: chave pública dos tokens. A chave ganha um nome novo a cada subida, para
  os serviços perceberem a troca.
- `GET /api/v1/tickets/{id}/biometric-eligibility`;
- `POST /api/v1/operator/events/{eventId}/checkins`.

## 3. Como rodar

```bash
docker compose up --build -d          # sobe também o facial-checkin (porta 8090)
```

- **Testar já (antes das telas da parte 2):**
  ```bash
  node services/facial-checkin-service/scripts/testar-checkin-facial.mjs selfie.jpg [foto-portaria.jpg]
  ```
  O script cria um cliente, vende um ingresso, aceita o termo, cadastra o rosto, faz duas leituras
  na portaria e revoga, mostrando cada resposta.
- Sem AWS, o Rekognition é **simulado**: só reconhece a **mesma imagem** cadastrada. Serve para os
  testes e o fluxo, não para rostos de verdade.
- Com a conta AWS do grupo:
  ```bash
  FACIAL_REKOGNITION_MODE=aws AWS_REGION=us-east-1 \
  AWS_ACCESS_KEY_ID=... AWS_SECRET_ACCESS_KEY=... docker compose up -d facial-checkin
  ```
  O usuário IAM precisa só de `rekognition:CreateCollection`, `DeleteCollection`, `IndexFaces`,
  `SearchFacesByImage` e `DeleteFaces`.
- Confiram o plano gratuito: contas criadas a partir de julho de 2025 recebem créditos, e não o
  "free tier de 12 meses" antigo.

## 4. Testes

| Onde | Testes | Cobertura |
| :--- | :--- | :--- |
| `facial-checkin-service` | 19: domínio, simulador, API completa, contrato HTTP contra servidor real, PostgreSQL real (Testcontainers) | 78% das linhas (meta 70%) |
| Backend | 63 (4 novos em `CheckinIntegrationTest`: JWKS, titular, check-in facial com `checkin.realizado`, permissões) | ≥ 90% |

Também foi conferido no Docker, de ponta a ponta pelo nginx:
1. venda no balcão;
2. cadastro sem termo recusado (403);
3. aceite e cadastro do rosto;
4. primeira leitura `LIBERADO`; segunda `INGRESSO_JA_UTILIZADO`; rosto desconhecido
   `ROSTO_NAO_RECONHECIDO`; cliente na portaria recusado (403);
5. revogação apaga o cadastro;
6. **uma única** mensagem `checkin.realizado` no Kafka, com `metodo: FACIAL`, `operadorId` e portão.

## 5. Riscos e limites conhecidos

| Item | Situação |
| :--- | :--- |
| **Foto de foto** (alguém mostra uma foto do titular) | O Rekognition comum não detecta. A verificação de pessoa real (*Face Liveness*) é paga e exige outro fluxo. Mitigação: a tela da portaria mostra nome e lugar para o operador conferir |
| Sem internet na portaria | O facial depende da AWS: a portaria volta para o QR, que valida offline |
| Simulado não reconhece rostos reais | Demonstração com rosto de verdade exige a conta AWS (parte 3) |
| Mesma pessoa com dois ingressos no mesmo evento | Cada ingresso tem o seu rosto cadastrado; a leitura libera um por vez |
