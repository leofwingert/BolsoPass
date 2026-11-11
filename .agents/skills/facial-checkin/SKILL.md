---
name: facial-checkin
description: "Regras do check-in por reconhecimento facial (facial-checkin-service, AWS Rekognition): LGPD para dados biométricos, contrato com o backend de ingressos e o evento checkin.realizado."
---

# Check-in por Reconhecimento Facial

Este documento orienta agentes de IA ao mexer no `services/facial-checkin-service`.

## Arquitetura (camadas)

| Camada | Pacote | O que mora ali |
| :--- | :--- | :--- |
| Apresentação | `presentation` | Controllers HTTP e tradução de erros (`{"message": ...}`) |
| Aplicação | `application` | Casos de uso: consentimento, cadastro, check-in, retenção |
| Domínio | `domain.model`, `domain.port` | Regras, `CheckinOutcome` e as portas `FaceRecognition`, `Ticketing`, repositórios |
| Infraestrutura | `infrastructure` | JPA, Rekognition (real e simulado), cliente HTTP do backend, segurança JWT |

O domínio não conhece AWS, HTTP nem Spring Web: trocar o Rekognition por outro provedor é escrever
outro adaptador de `FaceRecognition`.

## Invariantes Inegociaveis (LGPD, art. 11: dado biometrico e dado sensivel)

1. **Sem consentimento ativo, nenhum rosto é indexado.** O aceite é da versão atual do termo
   (`facial.consent-term-version`); termo novo exige novo aceite.
2. **Só o titular cadastra o próprio rosto** (CPF da conta = CPF do ingresso). Quem comprou não
   consente pelo acompanhante.
3. **A imagem nunca é gravada** (nem em disco, nem em banco, nem em S3). Guardar só FaceId,
   ticketId, eventId, userId e datas.
4. **Revogar o consentimento apaga** os rostos da pessoa em todas as collections (`DeleteFaces`) e
   os registros locais.
5. **Retenção mínima:** `RetentionJob` apaga a collection do evento `facial.retention-days` dias
   depois do início.

## Rekognition

- Uma collection por evento, criada sob demanda: `bolsopass-<eventId>`.
- Cadastro: `IndexFaces` com `ExternalImageId = ticketId`, `MaxFaces = 1`. Foto com mais de um rosto
  é recusada (422).
- Portaria: `SearchFacesByImage` com `FaceMatchThreshold = 90`.
- `facial.rekognition.mode=simulated` (padrão) roda sem AWS: reconhece só a mesma imagem (hash).
  `aws` usa credenciais da cadeia padrão do SDK.

## Contrato com o Backend de Ingressos

- O backend é a **fonte única da verdade** de "ingresso já usado". O serviço facial **não** marca
  entrada no próprio banco: chama `POST /api/v1/operator/events/{eventId}/checkins` com
  `method = FACIAL`, repassando o token do operador.
- O backend grava a entrada e o `checkin.realizado` no Outbox **na mesma transação** (sem
  Dual-Write). O QR usa o mesmo caminho com `method = QR`.
- Respostas: `LIBERADO`, ou `NEGADO` com `ROSTO_NAO_RECONHECIDO`, `INGRESSO_JA_UTILIZADO` ou
  `INGRESSO_INVALIDO`.
- Backend ou AWS fora do ar: 503 com a orientação de usar o QR (o QR continua funcionando offline).

## Testes Obrigatorios

- Domínio (consentimento), simulador, API completa com `Ticketing` dublê, contrato HTTP contra um
  servidor real e PostgreSQL real (Testcontainers) confirmando que não existe coluna de imagem.
- Cobertura de linhas ≥ 70% (`./mvnw verify`).
