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
updated: 2026-10-06
status: pronto
---

# Mockups e Mapa de Navegacao do Frontend (SPA)

> [!abstract] Arquitetura de Interface e Interacao Reativa
> O frontend do [[00 - Visao Geral e Indice (MOC)|BolsoPass]] é estruturado como uma [Single Page Application (SPA)](https://en.wikipedia.org/wiki/Single-page_application) moderna e orientada a componentes. A aplicação integra chamadas RESTful autenticadas via JWT para comandos de escrita e estabelece canais de comunicação bidirecionais contínuos via [WebSocket Protocol (RFC 6455)](https://datatracker.ietf.org/doc/html/rfc6455) para atualizações de disponibilidade em tempo real e notificações de confirmação de ingresso.

---

## 1. Mapa de Navegacao Global da Aplicacao

O mapa de rotas foi concebido para segregar estritamente a jornada de cada papel de usuário definido em `RF-02` de [[03 - Requisitos Funcionais (5 Modulos)]]:

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

---

## 2. Wireframes Estruturais das Telas Criticas do Comprador

### Tela 1: Mapa Interativo de Assentos (`/events/:id/seats`)
> [!tip] Dinamica de Tempo Real
> Os assentos exibem seus estados de forma reativa: ao receber mensagens do tópico WebSocket do evento, cadeiras reservadas por outros compradores tornam-se indisponíveis instantaneamente no mapa sem necessidade de recarregar o navegador.

```text
+-----------------------------------------------------------------------+
|  [BOLSOPASS]      Eventos   Meus Ingressos               [Perfil/Sair] |
+-----------------------------------------------------------------------+
|  <- Voltar aos Eventos                                                |
|  MEGA SHOW INTERNACIONAL 2026 - ESTADIO PUCRS                         |
|  Data: 15/11/2026 21:00 | Setor: Pista Premium                        |
|                                                                       |
|  [=========================== PALCO PRINCIPAL ======================]  |
|                                                                       |
|   FILA A:  [O A1] [O A2] [X A3] [X A4] [O A5] [O A6] [* A7] [* A8]        |
|   FILA B:  [O B1] [X B2] [X B3] [O B4] [O B5] [O B6] [O B7] [O B8]        |
|   FILA C:  [O C1] [O C2] [O C3] [O C4] [X C5] [X C6] [O C7] [O C8]        |
|                                                                       |
|   LEGENDA:  [O] Disponivel   [X] Bloqueado/Vendido   [*] Selecionado  |
|                                                                       |
|  +-----------------------------------------------------------------+  |
|  | Assento Selecionado: A7 (Pista Premium) - R$ 350,00             |  |
|  | [ BOTAO: BLOQUEAR ASSENTO E AVANCAR PARA O PAGAMENTO ]          |  |
|  +-----------------------------------------------------------------+  |
+-----------------------------------------------------------------------+
```

---

### Tela 2: Checkout Transacional com Cronometro (`/checkout/:id`)
> [!warning] Mecanismo de Sincronizacao de Tempo
> O cronômetro regressivo é sincronizado com o timestamp retornado pelo servidor na criação da reserva para impedir divergências causadas pelo relógio local da máquina do cliente. Caso o contador zere, o assento é desalocado automaticamente e a tela é bloqueada.

```text
+-----------------------------------------------------------------------+
|  [BOLSOPASS]                                                          |
+-----------------------------------------------------------------------+
|  ATENCAO: SUA RESERVA EXPIRA EM [ 08:45 ] MINUTOS                     |
|  Complete o pagamento antes que o assento seja liberado no sistema.   |
|                                                                       |
|  RESUMO DO PEDIDO:                                                    |
|  - Evento: Mega Show Internacional 2026                               |
|  - Assento: Fila A - Numero A7 (Pista Premium)                        |
|  - Valor do Ingresso: R$ 350,00 | Taxa de Servico: R$ 35,00           |
|  - Valor Total: R$ 385,00                                             |
|                                                                       |
|  DADOS DE PAGAMENTO (SIMULADOR FINANCEIRO):                           |
|  Numero do Cartao: [ 4532 .... .... 8821                        ]     |
|  Nome no Cartao:   [ GABRIEL S SILVA                            ]     |
|  Validade:         [ 12/28 ]         CVV: [ 921 ]                     |
|  Parcelas:         [ 1x de R$ 385,00 sem juros                 v]     |
|                                                                       |
|  [   FINALIZAR COMPRA AGORA (R$ 385,00)   ]                           |
|  * Transacao protegida contra cobranca duplicada via Idempotency-Key  |
+-----------------------------------------------------------------------+
```

---

### Tela 3: Confirmacao e Emissao do Ingresso (`/tickets/:id`)
> [!info] Exibicao e Validacao
> Exibe o QR Code contendo a assinatura digital criptográfica gerada assincronamente pelo Módulo de Mensageria e Despacho, acompanhado do botão para download do arquivo PDF oficial.

```text
+-----------------------------------------------------------------------+
|  [BOLSOPASS]                                             [Meus Ingressos]|
+-----------------------------------------------------------------------+
|  PAGAMENTO CONFIRMADO COM SUCESSO                                     |
|  Seu ingresso foi emitido e assinado digitalmente pela plataforma.    |
|                                                                       |
|  +-----------------------------------------------------------------+  |
|  |                    INGRESSO BOLSOPASS DIGITAL                   |  |
|  |                                                                 |  |
|  |   [   QR CODE CRIPTOGRAFICO   ]    Evento: Mega Show 2026       |  |
|  |   [   +-------------------+   ]    Data: 15/11/2026 as 21:00   |  |
|  |   [   |   CODIGO ASSINADO |   ]    Local: Estadio PUCRS        |  |
|  |   [   |   DIGITALMENTE    |   ]    Setor: Pista Premium        |  |
|  |   [   +-------------------+   ]    Assento: A-7                |  |
|  |                                    Titular: Gabriel S Silva    |  |
|  |                                    Hash: 9e3a7b...f12c         |  |
|  |                                                                 |  |
|  |   Carimbo de Tempo: 2026-10-05T21:40:00-03:00 (Assinatura RSA)  |  |
|  +-----------------------------------------------------------------+  |
|                                                                       |
|  [ BAIXAR INGRESSO EM PDF ]             [ ENVIAR POR E-MAIL ]         |
+-----------------------------------------------------------------------+
```

---

## 3. Interface de Portaria e Validacao de Acesso (`/operator/scanner`)

```text
+-----------------------------------------------------------------------+
|  [BOLSOPASS OPERADOR] - PORTARIA PRINCIPAL                [Desconectar]|
+-----------------------------------------------------------------------+
|  MODO DE LEITURA DE INGRESSOS (ONLINE / OFFLINE)                      |
|                                                                       |
|  [ VISOR DA CAMERA DO LEITOR DE QR CODE ATIVO ]                       |
|                                                                       |
|  RESULTADO DA ULTIMA LEITURA:                                         |
|  +-----------------------------------------------------------------+  |
|  |  STATUS: ACESSO AUTORIZADO (INGRESSO VALIDO)                    |  |
|  |  Titular: Gabriel S Silva                                       |  |
|  |  Setor: Pista Premium | Assento: A-7                            |  |
|  |  Carimbo de Emissao: Valido (Assinatura Criptografica OK)       |  |
|  |  Horario de Entrada: 20:14:32                                   |  |
|  +-----------------------------------------------------------------+  |
|                                                                       |
|  Total de Ingressos Validados nesta Portaria: 1.420 / 5.000            |
+-----------------------------------------------------------------------+
```
