---
title: "Mockups e Mapa de Navegação do Frontend (SPA)"
aliases:
  - Mapa de Navegação
  - Mockups
  - UI UX Frontend
tags:
  - frontend
  - spa
  - mockups
  - ux
  - navegacao
  - pacotes/pacote-1
created: 2026-10-05
updated: 2026-10-14
status: pronto
---

# Mockups e Mapa de Navegacao do Frontend (SPA)

> [!abstract] Arquitetura de Interface e Interacao Reativa
> O frontend do [[00 - Visao Geral e Indice (MOC)|BolsoPass]] é estruturado como uma [Single Page Application (SPA)](https://en.wikipedia.org/wiki/Single-page_application) moderna e orientada a componentes. A aplicação integra chamadas RESTful autenticadas via JWT para comandos de escrita e estabelece canais de comunicação bidirecionais contínuos via [WebSocket Protocol (RFC 6455)](https://datatracker.ietf.org/doc/html/rfc6455) para atualizações de disponibilidade em tempo real e notificações de confirmação de ingresso.
>
> **Protótipo Interativo em Alta Fidelidade (HTML/CSS):**
> Foi desenvolvido um protótipo navegável completo disponível localmente no arquivo [`mockups-high-res/index.html`](mockups-high-res/index.html), simulando a seleção de assentos, cronômetro de 10 minutos, scanner de portaria e métricas do organizador.

---

## 1. Mapa de Navegacao Global da Aplicacao

O mapa de rotas foi concebido para segregar estritamente a jornada de cada papel de usuário definido em `RF-02` de [[03 - Requisitos Funcionais (5 Modulos)]], com guardas de rota (*route guards*) baseadas nas claims do token JWT:

```mermaid
graph TD
    Home[Rota / - Vitrine Geral de Eventos] --> Login[Rota /auth/login - Autenticacao e Cadastro]
    Home --> EventDetail[Rota /events/:id - Detalhes e Lotes do Evento]
    
    subgraph Jornada do Comprador (ROLE_CLIENT)
        EventDetail --> SeatMap[Rota /events/:id/seats - Mapa de Assentos em Tempo Real]
        SeatMap --> Checkout[Rota /checkout/:reservationId - Checkout com Cronometro de 10 min]
        Checkout --> TicketSuccess[Rota /tickets/:ticketId - Ingresso Confirmado e Download PDF]
        Login --> MyTickets[Rota /my-tickets - Meus Ingressos Comprados]
    end

    subgraph Painel do Organizador (ROLE_ORGANIZER)
        Login --> OrgDashboard[Rota /organizer/dashboard - Metricas de Vendas em Tempo Real]
        OrgDashboard --> CreateEvent[Rota /organizer/events/new - Cadastro de Eventos e Lotes]
        OrgDashboard --> SeatEditor[Rota /organizer/events/:id/map - Configurador de Assentos]
    end

    subgraph Operacao de Portaria (ROLE_OPERATOR)
        Login --> GateScanner[Rota /operator/scanner - Validador de QR Code e Check-in]
    end

    subgraph Administracao da Plataforma (ROLE_ADMIN)
        Login --> AdminPanel[Rota /admin/telemetry - Telemetria e Monitoramento do Sistema]
    end
```

### Tabela de Rotas, Perfis e Políticas de Acesso

| Rota no SPA | Perfil Autorizado | Protocolo de Comunicação | Guarda de Rota (RBAC) e Tratamento de Erro |
| :--- | :--- | :--- | :--- |
| `/` | Público (Todos) | HTTP REST / Cache Redis | Acesso irrestrito com catálogo aquecido. |
| `/events/:id` | Público (Todos) | HTTP REST / Cache Redis | Exibe lotes, setores e botão para seleção. |
| `/events/:id/seats` | `ROLE_CLIENT` | HTTP + WSS (WebSockets) | Redireciona para `/auth/login` se não autenticado. |
| `/checkout/:reservationId` | `ROLE_CLIENT` | HTTP REST com `Idempotency-Key` | Bloqueia se o TTL de 10 minutos tiver expirado. |
| `/tickets/:ticketId` | `ROLE_CLIENT` | HTTP REST + WSS | Valida se o usuário autenticado é o titular do pedido. |
| `/my-tickets` | `ROLE_CLIENT` | HTTP REST | Lista histórico de ingressos emitidos do comprador. |
| `/organizer/dashboard` | `ROLE_ORGANIZER`, `ROLE_ADMIN` | HTTP REST + WSS (Stream de Métricas) | Retorna HTTP 403 Forbidden para clientes comuns. |
| `/operator/scanner` | `ROLE_OPERATOR`, `ROLE_ADMIN` | Leitura Ótica + Cache Offline de Chaves | Funcional mesmo sem conectividade com a internet. |

---

## 2. Design System e Diretrizes de UI/UX

Para oferecer uma experiência visual sofisticada e moderna sob altíssima concorrência, a interface adota um design system próprio:

- **Paleta de Cores Estruturada:**
  - *Fundo Primário:* `#090D16` (Deep Midnight Blue) com degradê radial para `#1E1B4B`.
  - *Superfícies e Cartões:* `#111827` com transparência de 75% e desfoque *glassmorphism* (`backdrop-filter: blur(12px)`).
  - *Destaque Transacional:* Roxo Índigo (`#6366F1`) para ações primárias e botões de avanço.
  - *Confirmação e Sucesso:* Verde Esmeralda (`#10B981`) para assentos disponíveis e validação de ingressos.
  - *Atenção e Urgência:* Âmbar (`#F59E0B`) para assentos sob bloqueio temporário e avisos de prazo.
  - *Crítico e Bloqueado:* Carmim (`#EF4444`) para cronômetro regressivo, assentos vendidos e esgotamento de lotes.
- **Tipografia:**
  - Família principal: *Plus Jakarta Sans* para clareza em micro-interfaces e números legíveis em telas móveis.
  - Família monoespaçada: *JetBrains Mono* para códigos de assento, hashes criptográficos e cronômetro.
- **Acessibilidade e Usabilidade (WCAG 2.1 AA):**
  - Contraste de cores entre texto e fundo com razão $\ge 4.5:1$.
  - Navegação completa por teclado no mapa de assentos (teclas direcionais e Enter).
  - Indicadores sonoros e visuais de expiração de reserva nos 2 minutos finais.

---

## 3. Wireframes Estruturais das Telas Criticas

### Tela 1: Vitrine de Eventos e Lotes (`/` e `/events/:id`)
Apresenta o evento principal, o banner de abertura de vendas com status de tráfego em rajada (*flash crowd*) e a barra de progresso de esgotamento de cada lote:

```text
+---------------------------------------------------------------------------------------+
| [BP] BOLSOPASS             Eventos   Meus Ingressos              [ Gabriel S | CLIENT ]|
+---------------------------------------------------------------------------------------+
|                                                                                       |
|  [!] ABERTURA DE VENDAS ATIVA: 18.420 COMPRADORES CONECTADOS SIMULTANEAMENTE          |
|                                                                                       |
|  FESTIVAL INTERNACIONAL ROCK PUCRS 2026                                               |
|  Local: Estadio Central PUCRS | Data: 14 a 16 de Novembro de 2026                     |
|                                                                                       |
|  +------------------------+  +------------------------+  +------------------------+  |
|  | PISTA PREMIUM          |  | CADEIRA INFERIOR       |  | CAMAROTE VIP           |  |
|  | Lote 1 (85% vendido)   |  | Lote 2 (62% vendido)   |  | Lote Unico (ESGOTADO)  |  |
|  | R$ 480,00              |  | R$ 350,00              |  | R$ 950,00              |  |
|  | [============......]   |  | [=========...........] |  | [====================] |  |
|  | 145 ingressos restam   |  | 1.280 ingressos restam |  | Esgotado em 32s        |  |
|  |                        |  |                        |  |                        |  |
|  | [ ESCOLHER NO MAPA ]   |  | [ ESCOLHER NO MAPA ]   |  | [ INDISPONIVEL ]       |  |
|  +------------------------+  +------------------------+  +------------------------+  |
+---------------------------------------------------------------------------------------+
```

---

### Tela 2: Mapa Interativo de Assentos (`/events/:id/seats`)
> [!tip] Dinamica de Tempo Real
> Os assentos exibem seus estados de forma reativa: ao receber mensagens do tópico WebSocket do evento, cadeiras reservadas por outros compradores tornam-se indisponíveis instantaneamente no mapa sem necessidade de recarregar o navegador.

```text
+---------------------------------------------------------------------------------------+
| [BP] BOLSOPASS      <- Voltar aos Detalhes do Evento             [ Gabriel S | CLIENT ]|
+---------------------------------------------------------------------------------------+
| CANAL WEBSOCKET ATIVO: Assento A-07 reservado por outro usuario agora mesmo.         |
|                                                                                       |
| [============================= PALCO PRINCIPAL / TELAO 8K ==========================] |
|                                                                                       |
|   FILA A:  [O A1] [O A2] [X A3] [X A4] [O A5] [O A6] [* A7] [* A8]                     |
|   FILA B:  [O B1] [! B2] [X B3] [O B4] [O B5] [O B6] [O B7] [O B8]                     |
|   FILA C:  [O C1] [O C2] [O C3] [O C4] [X C5] [X C6] [O C7] [O C8]                     |
|   FILA D:  [O D1] [O D2] [O D3] [O D4] [O D5] [O D6] [O D7] [O D8]                     |
|                                                                                       |
|   LEGENDA: [O] Disponivel   [*] Selecionado   [!] Bloqueio (10m)   [X] Vendido        |
|                                                                                       |
|   +---------------------------------------------------------------------------------+ |
|   | RESUMO: Assento Selecionado: B-04 (Cadeira Coberta) - R$ 350,00                 | |
|   | Garantia de Concorrencia: Zero Overselling via Redis Redlock atômico            | |
|   | [ BOTAO: BLOQUEAR ASSENTO E AVANCAR PARA O PAGAMENTO (10 MINUTOS) ]             | |
|   +---------------------------------------------------------------------------------+ |
+---------------------------------------------------------------------------------------+
```

---

### Tela 3: Checkout Transacional com Cronometro (`/checkout/:id`)
> [!warning] Mecanismo de Sincronizacao de Tempo
> O cronômetro regressivo é sincronizado com o timestamp retornado pelo servidor na criação da reserva para impedir divergências causadas pelo relógio local da máquina do cliente. Caso o contador zere, o assento é desalocado automaticamente e a tela é bloqueada.

```text
+---------------------------------------------------------------------------------------+
| [BP] BOLSOPASS                                                                        |
+---------------------------------------------------------------------------------------+
| [!] SUA RESERVA EXPIRA EM: [ 09:42 ] MINUTOS                                          |
| Complete o pagamento antes que o assento seja automaticamente devolvido ao catalogo.  |
|                                                                                       |
| DADOS DE PAGAMENTO (GATEWAY SIMULADO):         RESUMO DO PEDIDO:                      |
| Numero do Cartao: [ 4532 .... .... 8821   ]   - Festival Rock PUCRS 2026              |
| Titular:          [ GABRIEL S SILVA       ]   - Assento: Cadeira Coberta B-04         |
| Validade:         [ 12/29 ]   CVV: [ 921 ]    - Ingresso: R$ 350,00                   |
| Condicao:         [ 1x de R$ 385,00 sem juros] - Taxa de Servico: R$ 35,00             |
|                                               -------------------------------------   |
| [ Idempotency-Key: 7b9e8312-d9f2-4b2a-a9e1 ]   TOTAL: R$ 385,00                       |
|                                                                                       |
| [   CONFIRMAR PAGAMENTO COM TRANSACTIONAL OUTBOX (R$ 385,00)   ]                      |
| * Transacao ACID relacional gravada no PostgreSQL e despachada ao cluster Kafka.      |
+---------------------------------------------------------------------------------------+
```

---

### Tela 4: Confirmacao e Emissao do Ingresso Digital (`/tickets/:id`)
> [!info] Exibicao e Validacao
> Exibe o QR Code contendo a assinatura digital criptográfica gerada assincronamente pelo Módulo de Mensageria e Despacho, acompanhado do botão para download do arquivo PDF oficial.

```text
+---------------------------------------------------------------------------------------+
| [BP] BOLSOPASS                                             [Meus Ingressos | Gabriel]|
+---------------------------------------------------------------------------------------+
|                          PAGAMENTO CONFIRMADO COM SUCESSO!                            |
|             Seu ingresso foi emitido e assinado digitalmente pela plataforma.         |
|                                                                                       |
| +-----------------------------------------------------------------------------------+ |
| | [BP] BOLSOPASS DIGITAL TICKET                         ID DO INGRESSO: #TK-992014  |
| |                                                                                   | |
| |   [   QR CODE ASSINADO   ]   Evento: Festival Rock PUCRS 2026                     | |
| |   [   +---------------+  ]   Data: 14 a 16 de Novembro de 2026                    | |
| |   [   | [x] [ ] [x]   |  ]   Local: Estadio Central PUCRS                         | |
| |   [   | [ ] [x] [ ]   |  ]   Setor: Cadeira Coberta | Assento: B-04               | |
| |   [   | [x] [x] [x]   |  ]   Titular: Gabriel S Silva                             | |
| |   [   +---------------+  ]   Portao de Entrada: Portao B (Rampa Sul)              | |
| |                                                                                   | |
| |   Assinatura Criptografica: ECDSA-P256-SHA256 (Hash: 9e3a7b...f12c)               | |
| |   Carimbo de Tempo: 2026-10-14T21:40:12-03:00 (Auditavel)                         | |
| +-----------------------------------------------------------------------------------+ |
|                                                                                       |
| [ BAIXAR INGRESSO EM FORMATO PDF ]               [ VOLTAR PARA A LISTA DE EVENTOS ]   |
+---------------------------------------------------------------------------------------+
```

---

### Tela 5: Interface de Portaria e Scanner do Operador (`/operator/scanner`)
Permite ao operador de catraca realizar a leitura de ingressos com validação de assinatura digital em milissegundos, operando com total autonomia caso a internet do estádio oscile:

```text
+---------------------------------------------------------------------------------------+
| [BP OPERADOR] - CATRACA 04 (PORTAO SUL)            MODO: [ ONLINE / OFFLINE TOGGLE ]  |
+---------------------------------------------------------------------------------------+
|                                                                                       |
| +----------------------------------+  +---------------------------------------------+ |
| |      CAMERA LEITORA ATIVA        |  |          STATUS: ACESSO AUTORIZADO          | |
| |                                  |  |                                             | |
| |      [==== MIRA LASER ====]      |  | Titular: Gabriel S Silva                    | |
| |                                  |  | Setor: Cadeira Coberta | Cadeira: B-04      | |
| |      Aponte o QR Code do cliente |  | Assinatura Criptografica: ECDSA OK          | |
| |                                  |  | Prevencao de Dupla Entrada: OK (1o Acesso)  | |
| +----------------------------------+  +---------------------------------------------+ |
|                                                                                       |
| Total de Pessoas Validadas nesta Portaria: 3.412 / 8.000 ingressos alocados           |
+---------------------------------------------------------------------------------------+
```

---

### Tela 6: Painel Analitico de Telemetria do Organizador (`/organizer/dashboard`)
Disponibiliza à diretoria de operações métricas consolidadas em tempo real sobre a saúde da venda em massa:

```text
+---------------------------------------------------------------------------------------+
| [BP GESTOR] - PAINEL ANALITICO DE TELEMETRIA                     [ Beatriz | ORGANIZER]|
+---------------------------------------------------------------------------------------+
| RITMO DE VENDAS ATUAL  | FATURAMENTO TOTAL BRUTO | CONVERSAO CHECKOUT | SOBRE-VENDA   |
| 4.820 ingressos/min    | R$ 2.450.800,00         | 96.4% concluidos   | 0 incidentes  |
| (Pico de Rajada Ativo) | (100% via Outbox ACID)  | (3.6% expirados)   | (Zero Toler.) |
|---------------------------------------------------------------------------------------|
| VAZAO DE REQUISICOES NO CATALOGO (HTTP REQ/S):     OCUPACAO SETORIAL:                 |
| [|||||||||||||||||||||||||||||||||||||||||||||]    - Camarote VIP: 100% [ESGOTADO]    |
| 5.120 req/s (Latencia P95: 42ms no Redis)          - Pista Premium: 85%               |
|                                                    - Cadeira Coberta: 62%             |
|                                                    - Arquibancada Geral: 44%          |
+---------------------------------------------------------------------------------------+
```
