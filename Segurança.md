# Relatório de Segurança

## Resumo

Foi identificado um problema de controle de acesso no fluxo de cadastro público
de contas criadas no balcão.

## Achados

| # | Severidade | Arquivo | Linhas | Vulnerabilidade | Confiança |
|---|---|---|---|---|---|
| 1 | 🔴 HIGH | `backend/src/main/java/com/bolsopass/iam/AuthService.java` | 142-161 | Uma conta criada no balcão pode ser assumida informando apenas um CPF válido, sem prova adicional de posse. O atacante pode definir as credenciais e receber tokens imediatamente. | 9/10 |

### 1. Tomada de conta por CPF

**Categoria:** Broken Access Control / Authentication Failure

O cadastro público localiza o proprietário exclusivamente pelo CPF. Quando a
conta é elegível para assunção, o fluxo substitui as credenciais e emite
access/refresh tokens na mesma operação. A criação da conta no balcão usa uma
senha aleatória, portanto o CPF funciona como única barreira para assumir a
conta.

**Impacto:** qualquer pessoa que obtenha o CPF de um cliente pode assumir a
conta e acessar ingressos, pedidos e demais dados associados.

**Evidências:**

- `backend/src/main/java/com/bolsopass/iam/AuthService.java:142-143` localiza o usuário apenas por CPF.
- `backend/src/main/java/com/bolsopass/iam/AuthService.java:150-151` permite a assunção e substitui as credenciais.
- `backend/src/main/java/com/bolsopass/iam/AuthService.java:161` emite a sessão imediatamente.
- `backend/src/main/java/com/bolsopass/boxoffice/BoxOfficeService.java:106-109` cria contas de balcão com senha aleatória.

**Recomendação:** exigir prova adicional de posse antes do claim, como OTP
enviado a telefone/e-mail verificado ou código de ativação entregue pelo
operador. A sessão não deve ser emitida antes da conclusão dessa verificação.

## Remediação aplicada

- O balcão agora gera um código de ativação aleatório de uso único, válido por
  24 horas.
- O banco armazena somente o hash do código e sua expiração.
- O cadastro público exige o código correto antes de assumir uma conta do
  balcão; CPF sem o código resulta em conflito.
- O frontend permite informar o código recebido no balcão.
