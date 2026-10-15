---
title: "User Story Mapping e Backlog de Histórias de Usuário"
aliases:
  - Story Mapping
  - Backlog do Produto
  - User Stories
tags:
  - requisitos
  - story-mapping
  - scrum
  - backlog
  - user-stories
  - pacotes/pacote-1
created: 2026-10-05
updated: 2026-10-06
status: pronto
---

# User Story Mapping e Backlog do Produto

> [!abstract] Metodologia de Mapeamento da Jornada do Usuário
> O backlog do [[00 - Visao Geral e Indice (MOC)|BolsoPass]] adota a metodologia de [User Story Mapping de Jeff Patton](https://jpattonassociates.com/user-story-mapping/). Esse modelo organiza o fluxo de requisitos em duas dimensões complementares:
> 1. **Eixo Horizontal (Backbone):** A sequência cronológica das atividades e passos que os usuários executam na jornada de compra e controle de acesso.
> 2. **Eixo Vertical (Fatias de Release):** A priorização das histórias em fatias arquiteturais de valor (*vertical slices*), conectando diretamente os requisitos de negócio aos pacotes de entrega da disciplina.

---

## 1. Fundamentos do User Story Mapping e o Backbone da Jornada

Em projetos distribuídos complexos, a divisão tradicional de requisitos em listas lineares isoladas com frequência induz equipes a construir componentes desconectados (como uma camada de banco sem APIs, ou uma tela sem backend integrado). O Story Mapping previne essa armadilha ao assegurar que cada entrega represente uma fatia vertical executável de ponta a ponta.

A jornada do usuário na plataforma BolsoPass é dividida em cinco macroatividades sequenciais:

```mermaid
journey
    title Jornada de Compra e Acesso ao Evento
    section 1. Descoberta e Acesso
      Criar conta ou Login JWT: 5: Cliente, Keycloak
      Explorar Eventos e Lotes: 5: Cliente, Catalogo
    section 2. Reserva Concorrente
      Abrir Mapa de Assentos: 4: Cliente, WebSocket
      Bloquear Assento com Zero Overselling: 5: Cliente, Redis Lock
      Iniciar Cronometro de 10 minutos: 5: Cliente, Sistema
    section 3. Checkout e Transacao
      Preencher Pagamento e Idempotency-Key: 5: Cliente, Checkout
      Processar Gateway Simulado: 4: Checkout, Simulador
      Comitar Transacao ACID com Outbox: 5: Checkout, Kafka
    section 4. Emissao e Despacho
      Consumir Evento PedidoPago: 5: Mensageria, Kafka
      Gerar PDF com QR Code Assinado: 5: Mensageria, PDF Engine
      Notificar Interface via WebSocket: 5: Cliente, SPA
    section 5. Portaria e Telemetria
      Validar QR Code na Catraca: 5: Operador, Validador
      Monitorar Indicadores no Painel: 4: Organizador, Dashboard
```

---

## 2. Matriz de Story Mapping por Fatias de Entrega (Releases)

A matriz abaixo distribui as histórias de usuário de acordo com o plano de evolução dos pacotes da PUCRS:

| Atividades do Usuário $\to$ | 1. Identificar e Autenticar | 2. Explorar e Reservar Assento | 3. Efetuar Checkout Resiliente | 4. Emitir e Despachar Ingresso | 5. Portaria e Operações |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **Fatia 1: MVP Core (Pacotes 1 e 2)** | US-01: Login JWT com papéis RBAC | US-03: Catálogo em cache Redis<br/>US-04: Alocação atômica de assento | US-06: Máquina de estados do pedido<br/>US-07: Publicação com Transactional Outbox | US-09: Consumo Kafka de eventos de pagamento | US-12: Orquestração com Docker Compose |
| **Fatia 2: Checkpoint (Pacote 3)** | US-02: Rotação de Refresh Token no Redis | US-05: Expiração estrita de reserva em 10 min | US-08: Suporte ao cabeçalho `Idempotency-Key` | US-10: Geração de PDF e assinatura do QR Code<br/>US-11: Push reativo via WebSocket | US-13: Pipeline de CI/CD com Testcontainers |
| **Fatia 3: Produto Completo (Pacote 4)** | US-14: Auditoria de tentativas de login | US-15: Mapa de assentos interativo | US-16: Tratamento de recusas financeiras | US-17: Validação offline na portaria | US-18: Dashboard analítico em tempo real |

---

## 3. Detalhamento das Historias de Usuario e Criterios BDD

Todas as histórias de usuário são formalizadas utilizando o padrão [Gherkin/BDD (Cucumber)](https://cucumber.io/docs/gherkin/reference/), viabilizando a conversão direta dos cenários de teste em especificações executáveis automatizadas.

### Epico 1: Identidade e Governanca (IAM)

#### [US-01] Autenticacao Stateless e Autorizacao RBAC
- **Como** usuário da plataforma (Cliente, Organizador, Operador ou Administrador),
- **Quero** autenticar-me através de credenciais seguras e receber um token JWT assinado,
- **Para que** eu possa acessar os recursos restritos ao meu perfil com agilidade e proteção.
- **Critérios de Aceitação (BDD):**
  ```gherkin
  Cenario: Autenticacao bem-sucedida de Cliente
    Dado que o cliente possui credenciais validas previamente cadastradas
    Quando requisitar autenticacao via endpoint POST /api/v1/auth/login
    Entao o sistema deve retornar status HTTP 200 OK
    E o corpo da resposta deve conter um Access Token JWT com a claim "roles": ["ROLE_CLIENT"]
    E o tempo de expiracao do token deve ser configurado para exatamente 15 minutos.

  Cenario: Acesso negado para recurso administrativo
    Dado que o usuario esta autenticado com o papel "ROLE_CLIENT"
    Quando tentar cadastrar um novo evento via POST /api/v1/events
    Entao o sistema deve barrar a chamada e retornar status HTTP 403 Forbidden.
  ```

#### [US-02] Rotacao Segura de Refresh Tokens
- **Como** cliente autenticado,
- **Quero** renovar meu token de acesso utilizando meu refresh token persistido no Redis,
- **Para que** eu continue logado com segurança sem a necessidade de reinserir minha senha a cada 15 minutos.
- **Critérios de Aceitação (BDD):**
  - O Refresh Token anterior é invalidado no Redis no momento da emissão do novo par de tokens.
  - Tentativas de reuso de tokens já revogados bloqueiam a sessão do usuário preventivamente.

---

### Epico 2: Inventario, Catalogo e Bloqueio Concorrente

#### [US-03] Visualizacao de Catalogo sob Alta Concorrencia
- **Como** comprador de ingressos,
- **Quero** visualizar a lista de eventos e o saldo de ingressos disponíveis por lote,
- **Para que** eu possa planejar minha compra com agilidade no segundo da abertura das vendas.
- **Critérios de Aceitação (BDD):**
  - Os dados do catálogo são recuperados da camada de cache Redis, garantindo latência $P_{95} \le 50\text{ ms}$.

#### [US-04] Alocacao Atomica de Assentos (Zero Overselling)
- **Como** comprador disputando uma vaga concorrida,
- **Quero** reservar um assento específico com confirmação atômica instantânea,
- **Para que** eu tenha garantia contratual de que aquele assento foi retido exclusivamente para mim.
- **Critérios de Aceitação (BDD):**
  ```gherkin
  Cenario: Disputa massiva concorrente pelo mesmo assento fisico
    Dado que o assento "A-12" do evento "Final de Campeonato" esta no estado DISPONIVEL
    Quando 1.000 requisicoes simultaneas tentarem reservar o assento "A-12"
    Entao exatamente 1 requisicao deve ser confirmada com status HTTP 201 Created
    E as outras 999 requisicoes concorrentes devem receber status HTTP 409 Conflict
    E o estado do assento no banco e no Redis deve transicionar para RESERVADO_TEMPORARIO.
  ```

#### [US-05] Expiracao Automatica de Reservas em 10 Minutos
- **Como** comprador navegando no catálogo,
- **Quero** que assentos cujas reservas expiraram por desistência voltem a ficar disponíveis,
- **Para que** eu tenha novas oportunidades de adquirir ingressos que haviam sido temporariamente bloqueados.
- **Critérios de Aceitação (BDD):**
  - O sistema monitora o tempo de vida da reserva ($10\text{ minutos}$).
  - Transcorrido o prazo sem pagamento, publica o evento `reservas.expiradas` no Kafka.
  - A trava do assento é liberada atomicamente e o status retorna para `DISPONIVEL`.

---

### Epico 3: Checkout Resiliente e Transacoes com Outbox

#### [US-06] Maquina de Estados Finitos do Pedido
- **Como** plataforma de bilhetagem,
- **Quero** que cada pedido transite estritamente conforme as regras formais da máquina de estados,
- **Para que** comportamentos inconsistentes (como pagar uma reserva já expirada) sejam rejeitados.
- **Critérios de Aceitação (BDD):**
  - Transições fora do grafo definido em [[06 - Maquinas de Estados e Regras de Negocio]] disparam exceção de domínio invariante.

#### [US-07] Publicacao Consistente via Transactional Outbox
- **Como** arquiteto de software da plataforma,
- **Quero** persistir a atualização do pedido e o evento Kafka dentro da mesma transação relacional,
- **Para que** falhas temporárias na rede ou no broker Kafka não causem discrepância entre banco e mensageria.
- **Critérios de Aceitação (BDD):**
  - A escrita do pedido e a inserção na tabela `outbox_events` compartilham a mesma transação ACID no PostgreSQL.
  - Queda momentânea do Kafka não cancela o pagamento; a publicação é concluída assincronamente pelo relay worker assim que a conexão restabelecer.

#### [US-08] Protecao contra Pagamento Duplicado via Idempotency-Key
- **Como** cliente finalizando a compra com oscilação na rede móvel,
- **Quero** submeter um identificador de idempotência no cabeçalho da chamada de checkout,
- **Para que** cliques sucessivos no botão de pagamento não resultem em cobranças financeiras duplicadas.
- **Critérios de Aceitação (BDD):**
  - Múltiplas requisições com o mesmo cabeçalho `Idempotency-Key` dentro de 24 horas recebem a mesma resposta HTTP original, sem disparar nova transação na adquirente simulada.

---

### Epico 4: Emissao Criptografica de Ingressos e Notificacoes

#### [US-09] Geracao de Ingresso em PDF com QR Code Assinado
- **Como** comprador com pagamento confirmado,
- **Quero** que meu ingresso seja emitido em formato PDF contendo carimbo de tempo e QR Code criptográfico,
- **Para que** eu possa apresentar o documento no estádio com garantia contra fraudes e cópias não autorizadas.
- **Critérios de Aceitação (BDD):**
  - O documento é compilado após o consumo assíncrono do tópico `pedidos.pagos`.
  - A assinatura do QR Code pode ser verificada offline através da chave pública da plataforma.

#### [US-10] Notificacao Reativa na Interface via WebSockets
- **Como** comprador aguardando na página de pagamento,
- **Quero** que a tela seja atualizada automaticamente no instante da confirmação do ingresso,
- **Para que** eu visualize meu ingresso na tela imediatamente sem precisar recarregar o navegador.
- **Critérios de Aceitação (BDD):**
  - O evento é despachado via WebSocket em tempo $\le 1.5\text{ segundo}$ após a aprovação financeira.

---

### Epico 5: Telemetria, DevOps e Reprodutibilidade

#### [US-11] Ambiente Reprodutivel em Comando Unico via Docker Compose
- **Como** desenvolvedor ou avaliador do projeto,
- **Quero** subir todos os microsserviços e dependências de infraestrutura através de um único comando,
- **Para que** eu possa testar a solução localmente sem configurações manuais complexas.
- **Critérios de Aceitação (BDD):**
  - O comando `docker compose up --build` instancia PostgreSQL, Kafka, Redis, Keycloak e microsserviços com sucesso.

#### [US-12] Integracao Continua e Validacao com Testcontainers
- **Como** equipe de engenharia,
- **Quero** que a pipeline de CI execute testes de unidade e testes de integração com contêineres reais em cada Pull Request,
- **Para que** regressões e quebras de contrato sejam detectadas antes do merge na branch `develop`.
- **Critérios de Aceitação (BDD):**
  - A esteira reprova Pull Requests cuja cobertura unitária na máquina de estados seja inferior a 70%.
