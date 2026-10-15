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
updated: 2026-10-14
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

A matriz abaixo distribui as histórias de usuário de acordo com o plano de evolução dos pacotes avaliativos:

| Atividades do Usuário $\to$ | 1. Identificar e Autenticar | 2. Explorar e Reservar Assento | 3. Efetuar Checkout Resiliente | 4. Emitir e Despachar Ingresso | 5. Portaria e Operações |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **Fatia 1: MVP Core (Pacotes 1 e 2)** | **US-01:** Login JWT com papéis RBAC | **US-04:** Catálogo em cache Redis<br/>**US-05:** Alocação atômica de assento | **US-08:** Máquina de estados do pedido<br/>**US-09:** Publicação com Transactional Outbox | **US-12:** Consumo Kafka de eventos de pagamento | **US-16:** Orquestração completa via Docker Compose |
| **Fatia 2: Checkpoint (Pacote 3)** | **US-02:** Rotação de Refresh Token no Redis | **US-06:** Expiração estrita de reserva em 10 min | **US-10:** Suporte ao cabeçalho `Idempotency-Key` | **US-13:** Geração de PDF e assinatura do QR Code<br/>**US-14:** Push reativo via WebSocket | **US-17:** Pipeline de CI/CD com Testcontainers |
| **Fatia 3: Produto Completo (Pacote 4)** | **US-03:** Auditoria de acessos e Rate Limiting | **US-07:** Mapa de assentos interativo com WebSocket | **US-11:** Tratamento de recusas e falhas de gateway | **US-15:** Validação offline na portaria | **US-18:** Dashboard analítico de telemetria em tempo real |

---

## 3. Detalhamento das Historias de Usuario e Criterios BDD

Todas as histórias de usuário são formalizadas utilizando o padrão [Gherkin/BDD (Cucumber)](https://cucumber.io/docs/gherkin/reference/), viabilizando a conversão direta dos cenários de teste em especificações executáveis automatizadas.

---

### Epico 1: Identidade, Autenticacao e Governanca (IAM)

#### [US-01] Autenticacao Stateless e Autorizacao RBAC
- **Como** usuário da plataforma (Cliente, Organizador, Operador ou Administrador),
- **Quero** autenticar-me através de credenciais seguras e receber um token JWT assinado,
- **Para que** eu possa acessar os recursos restritos ao meu perfil com agilidade e proteção.
- **Módulo Relacionado:** Módulo de Identidade (IAM) | [[04 - Requisitos Nao-Funcionais (RNFs)#RNF-06\|RNF-06]]
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

#### [US-02] Rotacao Segura de Refresh Tokens em Chave-Valor
- **Como** cliente autenticado na plataforma,
- **Quero** renovar meu token de acesso utilizando meu refresh token persistido no Redis,
- **Para que** eu continue logado com segurança sem a necessidade de reinserir minha senha a cada 15 minutos.
- **Módulo Relacionado:** Módulo de Identidade (IAM) | [[04 - Requisitos Nao-Funcionais (RNFs)#RNF-06\|RNF-06]]
- **Critérios de Aceitação (BDD):**
  ```gherkin
  Cenario: Renovacao valida de sessao com Refresh Token
    Dado que o cliente possui um Refresh Token valido persistido no Redis
    Quando requisitar renovacao via endpoint POST /api/v1/auth/refresh
    Entao o sistema deve retornar HTTP 200 com novo par de Access e Refresh Tokens
    E o Refresh Token anterior deve ser imediatamente revogado no Redis.

  Cenario: Deteccao de reuso malicioso de Refresh Token
    Dado que um Refresh Token ja foi utilizado e revogado anteriormente
    Quando um cliente tentar utilizá-lo novamente
    Entao o sistema deve retornar HTTP 401 Unauthorized
    E invalidar imediatamente toda a familia de tokens daquele usuario por seguranca.
  ```

#### [US-03] Trilha de Auditoria e Rate Limiting de Autenticacao
- **Como** administrador de segurança da plataforma,
- **Quero** registrar logs estruturados de acessos e limitar tentativas repetidas de login,
- **Para que** ataques de forca bruta sejam mitigados e incidentes de segurança possam ser auditados.
- **Módulo Relacionado:** Módulo de Identidade (IAM) | [[04 - Requisitos Nao-Funcionais (RNFs)#RNF-06\|RNF-06]]
- **Critérios de Aceitação (BDD):**
  ```gherkin
  Cenario: Bloqueio por excesso de tentativas incorretas (Rate Limiting)
    Dado que um endereco IP errou a senha 5 vezes consecutivas em menos de 1 minuto
    Quando tentar nova requisicao de login para a mesma conta
    Entao o gateway deve retornar HTTP 429 Too Many Requests
    E registrar o evento de seguranca na trilha de auditoria.
  ```

---

### Epico 2: Inventario, Catalogo e Concorrencia

#### [US-04] Visualizacao de Catalogo sob Alta Concorrencia (Cache Redis)
- **Como** comprador de ingressos disputando uma abertura de vendas,
- **Quero** visualizar a lista de eventos e o saldo de ingressos disponíveis por lote com resposta instantânea,
- **Para que** eu possa selecionar meu evento sem enfrentar lentidão causada por sobrecarga de leitura.
- **Módulo Relacionado:** Módulo de Inventário | [[04 - Requisitos Nao-Funcionais (RNFs)#RNF-01\|RNF-01]]
- **Critérios de Aceitação (BDD):**
  ```gherkin
  Cenario: Consulta de catalogo com cache aquecido
    Dado que ha 5.000 requisicoes simultaneas por segundo na rota GET /api/v1/events
    Quando os clientes consultarem a lista de eventos
    Entao 100% das respostas devem ser servidas a partir do cache Redis
    E a latencia P95 das consultas deve ser menor ou igual a 50 milissegundos.
  ```

#### [US-05] Alocacao Atomica de Assentos (Zero Overselling)
- **Como** comprador disputando uma vaga concorrida em lote promocional,
- **Quero** reservar um assento específico com confirmação atômica instantânea,
- **Para que** eu tenha garantia contratual de que aquele assento foi retido exclusivamente para mim.
- **Módulo Relacionado:** Módulo de Inventário | [[04 - Requisitos Nao-Funcionais (RNFs)#RNF-02\|RNF-02]]
- **Critérios de Aceitação (BDD):**
  ```gherkin
  Cenario: Disputa massiva concorrente pelo mesmo assento fisico
    Dado que o assento "A-12" do evento "Final de Campeonato" esta no estado DISPONIVEL
    Quando 1.000 requisicoes simultaneas tentarem reservar o assento "A-12"
    Entao exatamente 1 requisicao deve ser confirmada com status HTTP 201 Created
    E as outras 999 requisicoes concorrentes devem receber status HTTP 409 Conflict
    E o estado do assento no banco e no Redis deve transicionar para RESERVADO_TEMPORARIO.
  ```

#### [US-06] Expiracao Estrita de Reservas em 10 Minutos (TTL e Kafka)
- **Como** comprador navegando no catálogo,
- **Quero** que assentos cujas reservas expiraram por desistência voltem a ficar disponíveis para compra,
- **Para que** o público tenha novas oportunidades de adquirir ingressos que haviam sido temporariamente retidos.
- **Módulo Relacionado:** Módulo de Inventário | [[04 - Requisitos Nao-Funcionais (RNFs)#RNF-03\|RNF-03]]
- **Critérios de Aceitação (BDD):**
  ```gherkin
  Cenario: Expiracao temporal automatica de reserva sem pagamento
    Dado que uma reserva foi criada as 20:00:00 com TTL de 10 minutos
    Quando o relogio atingir exatamente 20:10:00 sem que haja pagamento aprovado
    Entao o sistema deve transicionar a reserva para o estado EXPIRADO
    E publicar o evento ReservaExpiradaEvent no topico reservas.expiradas
    E o assento correspondente deve retornar atomicamente para o estado DISPONIVEL.
  ```

#### [US-07] Mapa Interativo de Assentos Reativo via WebSockets
- **Como** comprador visualizando a planta do estádio,
- **Quero** ver assentos bloqueados e liberados mudando de cor em tempo real na tela sem recarregar o navegador,
- **Para que** eu saiba exatamente quais cadeiras ainda posso selecionar.
- **Módulo Relacionado:** Módulo de Inventário e Frontend | [[07 - Mockups e Mapa de Navegacao Frontend]]
- **Critérios de Aceitação (BDD):**
  ```gherkin
  Cenario: Atualizacao reativa de assento bloqueado por outro usuario
    Dado que o cliente "A" esta com a tela do mapa de assentos aberta
    Quando o cliente "B" efetuar a reserva com sucesso do assento "C-15"
    Entao a interface do cliente "A" deve atualizar o assento "C-15" para a cor vermelha (Bloqueado)
    E o tempo de propagacao do evento via WebSocket deve ser inferior a 1 segundo.
  ```

---

### Epico 3: Checkout Resiliente e Transacoes com Outbox

#### [US-08] Maquina de Estados Finitos (FSM) do Pedido
- **Como** arquiteto e desenvolvedor da plataforma,
- **Quero** que cada pedido transite estritamente conforme as regras formais da máquina de estados finitos,
- **Para que** comportamentos inconsistentes (como pagar uma reserva já expirada) sejam rejeitados determinísticamente.
- **Módulo Relacionado:** Módulo de Checkout | [[06 - Maquinas de Estados e Regras de Negocio#1. Maquina de Estados Finitos (FSM) do Pedido\|FSM]]
- **Critérios de Aceitação (BDD):**
  ```gherkin
  Cenario: Tentativa de pagamento de pedido em estado EXPIRADO
    Dado que um pedido encontra-se no estado EXPIRADO por decurso do prazo de 10 minutos
    Quando o gateway tentar confirmar o pagamento desse pedido
    Entao o agregador do pedido deve rejeitar a transicao e lancar excecao de dominio
    E o status do pedido deve permanecer inalterado como EXPIRADO.
  ```

#### [US-09] Publicacao Confiável via Transactional Outbox (Eliminacao do Dual-Write)
- **Como** engenheiro de confiabilidade da plataforma,
- **Quero** persistir a atualização do pedido e o registro do evento na mesma transação relacional ACID,
- **Para que** indisponibilidades temporárias do Kafka não gerem discrepância entre o banco de dados e a mensageria.
- **Módulo Relacionado:** Módulo de Checkout | [[04 - Requisitos Nao-Funcionais (RNFs)#RNF-04\|RNF-04]]
- **Critérios de Aceitação (BDD):**
  ```gherkin
  Cenario: Resiliencia transacional com indisponibilidade do broker Kafka
    Dado que o cluster Apache Kafka esta temporariamente inoperante
    Quando o cliente concluir o pagamento com sucesso na adquirente simulada
    Entao o pedido deve ser gravado como PAGO e o evento inserido na tabela outbox_events em transacao ACID unica
    E quando o Kafka restabelecer conectividade, o relay worker deve publicar o evento pedidos.pagos com sucesso.
  ```

#### [US-10] Protecao contra Pagamento Duplicado via Idempotency-Key
- **Como** cliente finalizando a compra sob oscilação de rede móvel 4G/5G,
- **Quero** enviar um cabeçalho `Idempotency-Key` na requisição de pagamento,
- **Para que** cliques múltiplos acidentais no botão de checkout não gerem duplicidade de débito no meu cartão.
- **Módulo Relacionado:** Módulo de Checkout | [[04 - Requisitos Nao-Funcionais (RNFs)#RNF-05\|RNF-05]]
- **Critérios de Aceitação (BDD):**
  ```gherkin
  Cenario: Requisicoes repetidas com a mesma chave de idempotencia
    Dado que uma requisicao POST /api/v1/checkout foi concluida com o cabecalho "Idempotency-Key: abc-123"
    Quando o cliente submeter exatamente a mesma chamada com "Idempotency-Key: abc-123" dentro de 24 horas
    Entao o sistema deve responder com o mesmo status HTTP 200 e payload da requisicao original
    E nenhuma nova chamada de debito deve ser executada no simulador financeiro.
  ```

#### [US-11] Tratamento Resiliente de Recusas e Falhas Financeiras
- **Como** comprador cujo cartão foi recusado por limite insuficiente,
- **Quero** ser notificado imediatamente e ter a opção de tentar outro cartão dentro dos 10 minutos restantes,
- **Para que** eu não perca minha reserva de assento por um problema temporário com a operadora do cartão.
- **Módulo Relacionado:** Módulo de Checkout | [[06 - Maquinas de Estados e Regras de Negocio]]
- **Critérios de Aceitação (BDD):**
  ```gherkin
  Cenario: Nova tentativa com outro cartao dentro da janela de TTL
    Dado que um pedido no estado AGUARDANDO_PAGAMENTO teve pagamento recusado (status RECUSADO)
    E o tempo restante da reserva de 10 minutos e de 4 minutos
    Quando o cliente submeter novos dados de cartao valido
    Entao o pedido deve transicionar novamente para AGUARDANDO_PAGAMENTO e ser processado.
  ```

---

### Epico 4: Emissao Criptografica de Ingressos e Notificacoes (Despacho)

#### [US-12] Consumo Idempotente de Eventos de Pagamento no Kafka
- **Como** worker de mensageria da plataforma,
- **Quero** checar a tabela de mensagens já processadas antes de compilar um ingresso,
- **Para que** retransmissões automáticas do Kafka (*At-Least-Once*) não emitam ingressos duplicados.
- **Módulo Relacionado:** Módulo de Despacho | [[04 - Requisitos Nao-Funcionais (RNFs)#RNF-05\|RNF-05]]
- **Critérios de Aceitação (BDD):**
  ```gherkin
  Cenario: Mensagem duplicada recebida do topico pedidos.pagos
    Dado que a mensagem com ID "msg-999" ja foi consumida e registrada na tabela processed_messages
    Quando o broker Kafka reenviar a mensagem "msg-999" para o mesmo consumidor
    Entao o worker deve identificar a chave primaria duplicada
    E descartar a mensagem como no-op sem disparar nova geracao de arquivo PDF.
  ```

#### [US-13] Geracao de Ingresso em PDF com QR Code Assinado Criptograficamente
- **Como** comprador com pagamento aprovado,
- **Quero** baixar meu ingresso oficial em formato PDF com QR Code contendo assinatura digital assimétrica,
- **Para que** eu possa ingressar no estádio com segurança e comprovação documental contra fraudes.
- **Módulo Relacionado:** Módulo de Despacho | [[04 - Requisitos Nao-Funcionais (RNFs)#RNF-06\|RNF-06]]
- **Critérios de Aceitação (BDD):**
  ```gherkin
  Cenario: Compilacao de ingresso com assinatura digital
    Dado que um evento PedidoPagoEvent foi consumido com sucesso
    Quando o servico de emissao processar o lote
    Entao deve gerar um PDF contendo carimbo de data/hora (timestamp)
    E incorporar um QR Code cujo payload inclui assinatura ECDSA P-256 verificavel pela chave publica da plataforma.
  ```

#### [US-14] Notificacao Reativa no Frontend via WebSockets
- **Como** comprador aguardando confirmação na página de checkout,
- **Quero** que a tela exiba a confirmação e o ingresso no segundo em que o PDF for compilado,
- **Para que** eu não precise atualizar manualmente o navegador nem aguardar a chegada de e-mails.
- **Módulo Relacionado:** Módulo de Despacho e Frontend | [[07 - Mockups e Mapa de Navegacao Frontend]]
- **Critérios de Aceitação (BDD):**
  ```gherkin
  Cenario: Notificacao instantanea de ingresso emitido
    Dado que o comprador esta com a conexao WebSocket ativa no canal /ws/orders/{id}
    Quando a emissao do ingresso for finalizada pelo servico de despacho
    Entao o frontend deve receber a mensagem de conclusao em tempo menor que 1,5 segundo
    E transicionar a interface automaticamente para a tela de visualizacao do ingresso (/tickets/:id).
  ```

#### [US-15] Validacao Offline de Ingressos na Portaria
- **Como** operador de catraca na entrada do estádio,
- **Quero** validar a autenticidade do QR Code utilizando a chave pública da plataforma mesmo sem internet,
- **Para que** a entrada do público continue rápida e segura mesmo em caso de colapso de sinal móvel no local.
- **Módulo Relacionado:** Módulo de Despacho e Operações | [[03 - Requisitos Funcionais (5 Modulos)#RF-13\|RF-13]]
- **Critérios de Aceitação (BDD):**
  ```gherkin
  Cenario: Bipagem de QR Code valido em modo offline
    Dado que o dispositivo leitor da portaria esta desconectado da internet mas possui a chave publica
    Quando o operador escanear um QR Code valido assinado pela plataforma
    Entao o validador deve verificar a assinatura criptografica em tempo menor que 300 ms
    E exibir na tela: "ACESSO AUTORIZADO - INGRESSO VALIDO".
  ```

---

### Epico 5: Telemetria, DevOps e Reprodutibilidade

#### [US-16] Orquestracao Completa do Ecossistema via Docker Compose
- **Como** engenheiro de software ou avaliador da PUCRS,
- **Quero** inicializar todos os microsserviços, bancos e cluster Kafka através de um comando único,
- **Para que** eu possa subir o ambiente local de homologação sem atritos ou dependências manuais.
- **Módulo Relacionado:** Módulo de Operações e DevOps | [[04 - Requisitos Nao-Funcionais (RNFs)#RNF-08\|RNF-08]]
- **Critérios de Aceitação (BDD):**
  ```gherkin
  Cenario: Inicializacao integrada do ecossistema
    Dado que o repositorio foi clonado em um ambiente Linux com Docker instalado
    Quando o desenvolvedor executar "docker compose up --build -d"
    Entao todos os conteineres (PostgreSQL, Redis, Kafka, Keycloak e servicos backend/frontend) devem atingir status HEALTHY
    E as APIs devem estar disponiveis para consumo na porta padronizada do gateway.
  ```

#### [US-17] Pipeline de Integracao Continua (CI/CD) com Testcontainers
- **Como** desenvolvedor submetendo alterações de código,
- **Quero** que a esteira de CI execute testes unitários e testes de integração com contêineres reais em cada PR,
- **Para que** quebras de contrato de banco, concorrência ou Kafka sejam detectadas antes da branch `develop`.
- **Módulo Relacionado:** Módulo de Operações e DevOps | [[04 - Requisitos Nao-Funcionais (RNFs)#RNF-07\|RNF-07]]
- **Critérios de Aceitação (BDD):**
  ```gherkin
  Cenario: Validacao de qualidade em Pull Request
    Dado que um Pull Request foi aberto contra a branch develop
    Quando a pipeline do GitHub Actions executar
    Entao deve validar cobertura unitaria de dominio >= 70% nas maquinas de estado
    E executar a suite de testes de integracao com Testcontainers (PostgreSQL, Kafka e Redis)
    E bloquear o merge caso qualquer teste falhe.
  ```

#### [US-18] Painel Analitico de Telemetria e Vendas em Tempo Real
- **Como** diretora de operações ou organizadora do evento,
- **Quero** acompanhar o ritmo de vendas por minuto e a taxa de abandono de reservas em um painel em tempo real,
- **Para que** eu possa tomar decisões operacionais baseadas em dados durante a abertura dos lotes.
- **Módulo Relacionado:** Módulo de Operações e DevOps | [[03 - Requisitos Funcionais (5 Modulos)#RF-16\|RF-16]]
- **Critérios de Aceitação (BDD):**
  ```gherkin
  Cenario: Atualizacao continua de metricas de vendas
    Dado que a abertura de vendas esta em andamento com milhares de pedidos
    Quando a organizadora abrir a rota /organizer/dashboard
    Entao o painel deve exibir graficos de throughput de pedidos por minuto, conversao de checkout e total arrecadado
    E os dados devem ser atualizados a cada 2 segundos via stream de telemetria.
  ```
