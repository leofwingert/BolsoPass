# BolsoPass — Versão 5.1: Cadastro e gestão de usuários

> **Escopo:** cadastro público na tela de login (sempre Cliente) e página **Usuários** na
> administração, para listar, filtrar, cadastrar e promover contas para Admin, Bilheteria ou Venda.
> **Data:** 02/11/2026 · **Branch:** `feat/Mocks` · **Requisitos:** RF-02 (RBAC), RF-03 (gestão
> cadastral e auditoria)
> **Versões anteriores:** [4.0](../Versão%204.0%20-%20Implementação/README.md) (bilheteria) ·
> [5.0](../Versão%205.0%20-%20Implementação/versao5.0.md) (microsserviços, passo 1)

---

## 1. Regras de negócio

| Regra | Onde |
| :--- | :--- |
| **Cadastro público cria sempre um Cliente.** Não existe campo de perfil; se alguém tentar mandar um, ele é ignorado | `AuthService.register` |
| Cliente não entra na administração nem na bilheteria (403) | `RequireArea`, `@PreAuthorize` |
| Cadastro pede nome e sobrenome, e-mail, CPF válido, telefone com DDD, senha de 8+ caracteres e aceite dos termos. E-mail e CPF são únicos | `AuthService.register`, `RegisterPage` |
| **CPF de conta criada no balcão:** quem se cadastra com esse CPF **assume a conta** (define e-mail e senha) e vê os ingressos que já comprou na bilheteria. Contas do balcão anteriores a esta versão (sem a marca) são reconhecidas pelo e-mail automático `cpf…@balcao.bolsopass.io` | `User.claim`, `User.isCounterAccount` |
| CPF de conta normal (já tem senha): o cadastro é recusado com "CPF já cadastrado. Entre com seu e-mail e senha." | `AuthService.register` |
| **Um perfil por conta.** Promover troca o perfil. Quem vira equipe deixa de comprar como cliente; os ingressos já comprados continuam válidos | `UserAdminService.changeProfile` |
| Bilheteria (`OP-xxxxx`) e Venda (`VD-xxxxx`) recebem **matrícula e PIN de 6 dígitos**, mostrados ao admin **uma única vez**. Rebaixar tira a matrícula | `UserAdminService` |
| O **PIN tem campo próprio**, separado da senha: promover não apaga a senha do e-mail. Contas antigas, que guardavam o PIN como senha, continuam entrando | `User.pinHash`, `AuthService.pinOf` |
| Ao trocar o perfil, as sessões abertas da pessoa são encerradas (o novo perfil vale no próximo login) | `revokeAllForUser` |
| O admin **não muda o próprio perfil**, e o sistema nunca fica sem admin | `UserAdminService` |
| Só **`ROLE_ADMIN`** gerencia usuários (o organizador sem admin recebe 403) | `UserAdminController`, área `admin` |
| Toda criação de conta, conta assumida e troca de perfil vai para a **auditoria** (categoria Segurança, com quem fez) | `AuditService` |
| Na lista, o CPF aparece parcial (`***.982.247-**`); a busca aceita o CPF completo | LGPD |
| Bilheteria não cria um segundo cadastro para o CPF de alguém da equipe | `BoxOfficeService.register` |

## 2. Telas

| Rota | Tela |
| :--- | :--- |
| `/auth/login` | Link **Criar conta**. Os botões de acesso rápido continuam (demonstração) |
| `/auth/register` | Cadastro do cliente. Ao concluir, já entra logado e volta para onde estava (ex.: mapa de assentos) |
| `/organizer/users` | **Usuários**: busca (nome, e-mail, CPF, matrícula), filtro por perfil e por período de cadastro, paginação, **Cadastrar usuário** e **Alterar perfil** em cada linha |

## 3. API (`/api/v1`)

| Método | Caminho | Quem |
| :--- | :--- | :--- |
| `POST` | `/auth/register` | Público. Devolve a sessão (já logado) |
| `GET` | `/admin/users?q=&profile=&from=&to=&page=&size=` | `ROLE_ADMIN` |
| `POST` | `/admin/users` | `ROLE_ADMIN`. Devolve o usuário e, se for equipe, `credentials` (matrícula e PIN) |
| `PATCH` | `/admin/users/{id}/profile` | `ROLE_ADMIN`. Corpo `{ "profile": "CLIENT" \| "ADMIN" \| "OPERATOR" \| "SELLER" }` |

## 4. Testes

- **Backend:** `UserAdminIntegrationTest` com 6 cenários:
  - cadastro sempre Cliente;
  - validações e duplicados;
  - conta do balcão assumida;
  - só admin vê a lista;
  - promover e rebaixar, com login pelo PIN e sessão antiga derrubada;
  - travas do admin.

  São 58 testes no total, com 93,5% das linhas cobertas.
- **Frontend:** `usersAdmin.test.tsx` com 5 jornadas nas telas reais:
  - cadastro e 403 na administração;
  - CPF repetido;
  - lista com filtros e cadastro de Venda;
  - login com a matrícula e o PIN gerados;
  - promoção da cliente para Bilheteria.

  São 84 testes no total, com 92,6% das linhas cobertas.
- **No navegador, contra o Docker:**
  1. cadastro de uma cliente nova;
  2. o admin a promove para Venda (matrícula `VD-…` e PIN);
  3. ela entra com essas credenciais;
  4. o login antigo `OP-88219` continua funcionando.

## 5. Correção encontrada no caminho

O **código do pedido** (`PLS-12345-BR`) era sorteado sem conferir se já existia. Com mais pedidos no
banco, uma compra podia falhar com erro interno. Agora o sorteio confere antes, e o id do
ingresso, que usa os mesmos 5 dígitos, também fica único (`ReservationService.newCode`).

## 6. Pendências

| Item | Observação |
| :--- | :--- |
| Gerar novo PIN sem trocar o perfil (PIN esquecido) | Hoje: rebaixar e promover de novo |
| Bloquear ou desativar uma conta | Não pedido nesta versão |
| Recuperação de senha por e-mail | Depende do envio de e-mail (versão 5.0, passo 3) |
| Contas criadas antes desta versão aparecem como "conta de demonstração" na data de cadastro | Não havia data gravada |
