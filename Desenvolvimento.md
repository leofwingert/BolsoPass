# BolsoPass — Situação do Desenvolvimento

> Conferência do que foi feito contra o **enunciado oficial** (Projeto Integrador de Construção de
> Software – PUCRS 2026/2).
> **Data da conferência:** 30/10/2026 · **Branch:** `feat/Mocks`

**Legenda:** ✅ pronto · ⚠️ parcial (funciona, mas não do jeito que o enunciado pede) · ❌ falta

---

## 1. Resumo

| Área do enunciado | Situação |
| :--- | :--- |
| Funcionalidades para os 3 perfis (cliente, admin, bilheteria) | ✅ Funcionando de ponta a ponta, front + back |
| Regras de negócio críticas (zero overselling, reserva de 10 min, máquina de estados, idempotência, outbox, QR assinado) | ✅ Implementadas e testadas |
| Documentação dos Pacotes 1 e 2 | ✅ Entregue · ⚠️ precisa ser atualizada para refletir o código |
| **Infraestrutura exigida** (microsserviços separados, Keycloak, Kafka, Redis) | ❌ Ainda não |
| **DevOps exigido** (Testcontainers, docker-compose completo, CI no GitHub Actions) | ❌ Ainda não |
| Versionamento | ⚠️ **Nada do código foi commitado**; não existe a branch `develop` |

Em uma frase: **o produto funciona**, mas a parte de **arquitetura distribuída e DevOps** que o
enunciado exige ainda está por fazer. Hoje o backend é um único serviço Spring Boot dividido em
pacotes por módulo, com banco relacional fazendo o papel do Redis e uma tabela de outbox no lugar
do Kafka.

---

## 2. Requisitos funcionais (5 frentes do enunciado)

### 2.1 Identidade e Governança (IAM)

| Requisito | Situação | Como está hoje |
| :--- | :---: | :--- |
| Autenticação stateless via JWT | ✅ | JWT RS256 de 15 min (`iam/`) |
| Refresh tokens **em chave-valor** | ⚠️ | Refresh com rotação e detecção de reuso, mas guardado no banco relacional (Redis pendente) |
| RBAC: Cliente, Organizador, Operador, Admin | ✅ | 401 sem login, 403 perfil errado; cada perfil só vê suas telas |
| Auditoria de credenciais | ⚠️ | Bloqueio por 15 min após 5 senhas erradas; falta registrar tentativas de login num log consultável |
| Gestão cadastral de organizadores | ❌ | Não há cadastro/aprovação de organizadores; contas vêm do seed |
| Provedor de identidade (Keycloak ou Cognito) | ❌ | Autenticação própria |

### 2.2 Inventário e Concorrência

| Requisito | Situação | Como está hoje |
| :--- | :---: | :--- |
| Criação de lotes e mapas de assentos | ✅ | Assistente de 5 passos; lotes em sequência com virada automática; bloqueios PCD/obstrução/produção |
| Catálogo de alta leitura | ⚠️ | Catálogo público funciona; falta cache (Redis, cache-aside) |
| Alocação atômica de assentos | ✅ | `SELECT ... FOR UPDATE`; testado com 20 compradores no mesmo assento (só 1 consegue) |
| Devolução atômica em cancelamento ou timeout | ✅ | Expiração em 10 min e desistência após recusa devolvem o assento |
| Locks concorrentes distribuídos | ⚠️ | Trava no banco (permitida pelo RF-06); Redis pendente |
| Publicar eventos de expiração no broker | ❌ | Sem Kafka |
| Fila virtual (sala de espera) | ✅ | Extra dos mockups |

### 2.3 Transações e Checkout

| Requisito | Situação | Como está hoje |
| :--- | :---: | :--- |
| Máquina de estados do pedido (Criado, Reservado, Aguardando, Pago, Expirado…) | ✅ | Exatamente a do doc 06; 19 testes cobrindo transições válidas e proibidas |
| Idempotency-Key no cabeçalho HTTP | ✅ | Mesma chave devolve o mesmo pedido, sem cobrar de novo (24 h) |
| Transactional Outbox em transação ACID | ✅ | Pedido pago e evento gravados na mesma transação; relay com `SKIP LOCKED` |
| Publicação segura **no Kafka** | ❌ | O relay só registra o evento no log |
| Simulador de gateway financeiro | ✅ | Aprovação, recusa (cartão final `0000`), nova tentativa e desistência |

### 2.4 Mensageria e Despacho

| Requisito | Situação | Como está hoje |
| :--- | :---: | :--- |
| Consumo **assíncrono** de pagamentos confirmados | ❌ | O ingresso é emitido na mesma chamada do pagamento |
| Ingresso em **PDF** com carimbo de tempo | ❌ | Botão visível e desativado |
| QR Code assinado | ✅ | ECDSA P-256, muda a cada 15 s, validado offline na portaria |
| Disparo multicanal de comprovantes (e-mail etc.) | ❌ | Não há envio de e-mail |
| Atualização de tela em tempo real | ✅ | WebSocket: mapa de assentos e status do pedido |

### 2.5 Análise e Operações (Telemetria e DevOps)

| Requisito | Situação | Como está hoje |
| :--- | :---: | :--- |
| Ingestão e agregação contínua de eventos de navegação e compras | ❌ | Não há coleta de eventos de navegação |
| Painel analítico em tempo real | ⚠️ | Painel do produtor com números calculados do banco a cada 15 s (não por streaming) |
| Pipeline de CI/CD automatizada | ❌ | Não há workflow no GitHub Actions |
| Orquestração Docker Compose | ⚠️ | O compose sobe só o PostgreSQL |

---

## 3. Aspectos de arquitetura obrigatórios

| Exigência | Situação | Observação |
| :--- | :---: | :--- |
| Estilo arquitetural de microsserviços | ⚠️ | Um serviço com um pacote por microsserviço do C4 (`iam`, `catalog`, `inventory`, `orders`, `ticketing`, `outbox`, `realtime`…). Os pacotes conversam por serviços e eventos, então separar em contêineres é o próximo passo |
| Keycloak ou Cognito | ❌ | — |
| Kafka como streaming | ❌ | — |
| Dual-Write Dilemma tratado | ✅ | Transactional Outbox |
| Idempotência no **consumo** | ❌ | Não há consumidores ainda; falta a tabela `processed_messages` |
| Processo de desenvolvimento com IA documentado | ✅ | Pacote 1, doc 01 |
| C4 níveis 1, 2 e 3 | ✅ | Pacote 2, doc 01 · ⚠️ descreve a arquitetura-alvo, não a atual |

---

## 4. Testes e reprodutibilidade

| Exigência | Situação | Detalhe |
| :--- | :---: | :--- |
| Testes unitários das máquinas de estado | ✅ | Backend: 19 testes da máquina de estados do pedido. Frontend: 46 testes de domínio |
| Cobertura ≥ 70% do domínio | ✅ frontend · ⚠️ backend | Frontend `src/domain`: **100% linhas, 98,9% ramos** (medido em 30/10). Backend sem medição (falta JaCoCo) |
| Testes de integração com **Testcontainers** (PostgreSQL, Kafka, Redis) | ❌ | Os 11 testes de integração rodam em H2 em memória |
| Testes de carga (bônus) | ⚠️ | Só o teste de concorrência com 20 threads; sem k6/Gatling |
| Processo de GCS documentado | ✅ | Pacote 2, doc 05 |
| GCS aplicado no repositório | ❌ | Código não commitado; branch `develop` não existe |
| docker-compose completo (backend, frontend, bancos, Kafka, UI de mensageria) | ❌ | Só PostgreSQL; faltam Dockerfiles |
| CI rodando linters, testes e build a cada PR / merge na `develop` | ❌ | Lint, testes e build existem e passam localmente, mas não há pipeline |

**Números atuais:** 30 testes no backend, 46 no frontend, lint e checagem de tipos sem erros,
roteiros ponta a ponta no navegador para os 3 perfis.

---

## 5. Entregáveis por pacote

| Pacote | O que pede | Situação |
| :--- | :--- | :--- |
| **1** – Processo e Requisitos | Processo com IA, Visão, Story Mapping, RNFs, Mockups e navegação | ✅ Entregue |
| **2** – Arquitetura e Infra | C4, modelo de dados, Kafka, pipeline, GCS | ✅ Entregue (docs) |
| **3** – Checkpoint | Link do repositório com o estágio atual + docs 1 e 2 atualizadas | ⚠️ Código pronto localmente, **falta commit/push** e atualizar as docs |
| **4** – Produto completo | Repositório com o produto completo + docs atualizadas | ❌ Depende dos itens da seção 6 |

---

## 6. O que falta — ordem sugerida

### Para o Pacote 3 (checkpoint)

1. **Commitar e publicar o código** (`backend/`, `apps/`, READMEs de versão, `.gitignore`), criar a
   branch `develop` e seguir o fluxo do doc de GCS.
2. **Atualizar a documentação dos Pacotes 1 e 2** com o que mudou na implementação (ex.: estado
   atual × arquitetura-alvo no C4, decisões das versões 1.0–4.0).
3. **Pipeline de CI no GitHub Actions**: lint + testes + build do frontend e do backend em cada PR
   e merge na `develop`.
4. **JaCoCo** no backend para provar a cobertura ≥ 70% do domínio.

### Para o Pacote 4 (produto completo)

5. **Kafka**: relay do outbox publicando nos tópicos do doc 03 do Pacote 2; consumidor de
   pagamentos confirmados emitindo o ingresso; tabela `processed_messages` para idempotência no consumo.
6. **Redis**: travas de assento, refresh tokens e cache do catálogo.
7. **Keycloak**: substituir a autenticação própria (papéis já mapeados).
8. **Separar em microsserviços** (um contêiner por módulo) e **Dockerfiles**.
9. **docker-compose completo**: backend(s), frontend, PostgreSQL, Redis, Kafka, UI do Kafka, Keycloak.
10. **Testcontainers** com PostgreSQL, Kafka e Redis reais nos testes de integração; **Flyway** para o esquema.
11. **PDF do ingresso** com carimbo de tempo e **envio por e-mail** (multicanal).
12. **Telemetria**: ingestão de eventos de navegação e compras pelo Kafka alimentando o painel.
13. **Cadastro e aprovação de organizadores**; log de tentativas de login.
14. Bônus: teste de carga (k6 ou Gatling) na abertura de vendas.

### Funcionalidades de produto ainda abertas

| Item | Observação |
| :--- | :--- |
| Reembolso de 7 dias (CDC) | **Precisa de decisão**: o doc 06 diz que `PAGO` é final, então o estorno exige um fluxo próprio |
| Editar e cancelar evento publicado | Botões existem desativados no admin |
| Wallet e transferência de ingresso | Botões desativados |
| Venda no balcão da bilheteria | Não iniciada |
| App da bilheteria instalável (abrir sem internet) e HTTPS de desenvolvimento | Necessário para usar a câmera num celular pela rede |

---

## 7. Observações

- O código foi construído por versões, cada uma com seu README: [1.0](Versão%201.0%20-%20Implementação/README.md)
  (cliente), [2.0](Versão%202.0%20-%20Implementação/README.md) (admin e login),
  [3.0](Versão%203.0%20-%20Implementação/README.md) (admin pelos mockups),
  [4.0](Versão%204.0%20-%20Implementação/README.md) (bilheteria). O que cada perfil pode fazer
  está na seção 3 do [README principal](README.md).
- Os arquivos `.zip` dos mockups em `Pacote 1/Mocks-User/` aparecem como apagados no git e as
  pastas extraídas como novas. Confirmar se foi intencional antes do commit.
- Os docs `04 - Pipeline de Desenvolvimento.md` e `README.md` do Pacote 2 têm alterações não
  commitadas que não foram feitas nesta implementação; revisar antes do commit.
