# Plataforma BolsoPass

> **Projeto Integrador de Construção de Software – Semestre 2026/2**  
> **Pontifícia Universidade Católica do Rio Grande do Sul (PUCRS) – Escola Politécnica**  
> *Plataforma Distribuída de Bilhetagem, Reserva Temporária e Controle de Acesso para Megaeventos em Picos de Alta Concorrência (Flash Crowd).*

---

## 1. Visao Geral do Projeto

O **BolsoPass** é uma plataforma de bilhetagem e controle de acesso projetada para operar sob tráfego massivo em rajada (*flash crowd traffic*), eliminando os colapsos estruturais típicos de bilheterias convencionais baseadas em chamadas HTTP síncronas.

### Pilares Fundamentais de Engenharia

- **Tolerancia Zero a Sobre-venda (*Zero Overselling*):** Alocação atômica de assentos numerados através de travas distribuídas baseadas em Redis e transações relacionais estritas.
- **Expiracao Estrita de Reservas (TTL de 10 Minutos):** Janela temporal rigorosa ($10\text{ min} \pm 1\text{ s}$) para conclusão do checkout; desalocação atômica automática e devolução do assento ao catálogo via eventos no Apache Kafka em caso de desistência ou timeout.
- **Eliminacao do Dual-Write Dilemma:** Aplicação do *Transactional Outbox Pattern* para garantir consistência atômica entre o commit do pedido no PostgreSQL e o despacho de mensagens no cluster Kafka.
- **Idempotencia Transacional de Ponta a Ponta:** Deduplicação nativa via cabeçalho HTTP `Idempotency-Key` e tabela `processed_messages` em todos os consumidores assíncronos.
- **Emissao Segura com Inviolabilidade Criptografica:** Ingressos em PDF com carimbo de tempo (*timestamp*) auditável e QR Code assinado criptograficamente (ECDSA P-256), permitindo validação rápida na catraca mesmo em modo 100% offline.

---

## 2. Mapa dos Pacotes de Entrega

O desenvolvimento da plataforma está estruturado em quatro pacotes evolutivos:

| Pacote | Tema Central | Status | Diretorio no Repositorio |
| :--- | :--- | :---: | :--- |
| **Pacote 1** | **Processo de Desenvolvimento e Requisitos** | **Concluído** | [`Pacote 1 - Processo de Desenvolvimento e Requisitos/`](Pacote%201%20-%20Processo%20de%20Desenvolvimento%20e%20Requisitos/) |
| **Pacote 2** | Arquitetura C4, Modelo de Dados, Kafka & DevOps | Concluído | [`Pacote 2 - Arquitetura e Infraestrutura/`](Pacote%202%20-%20Arquitetura%20e%20Infraestrutura/) |
| **Pacote 3** | Checkpoint de Microsserviços e Testes $\ge 70\%$ | Planejado | `src/` & `tests/` |
| **Pacote 4** | Produto Completo com Docker Compose Unificado | Planejado | `docker-compose.yml` |

---

## 3. Dossiê de Entregáveis do Pacote 1

Todos os artefatos exigidos pelo enunciado oficial para o **Pacote 1** encontram-se documentados, validados e organizados:

| Item Exigido no Enunciado | Documento Principal | Descrição Sintética |
| :--- | :--- | :--- |
| **Processo de Desenvolvimento com IA** | [`01 - Processo de Desenvolvimento Suportado por IA.md`](Pacote%201%20-%20Processo%20de%20Desenvolvimento%20e%20Requisitos/01%20-%20Processo%20de%20Desenvolvimento%20Suportado%20por%20IA.md) | Ciclo em 5 etapas, metodologia HITL, TDD de domínio, Testcontainers e contratos de integração. |
| **Documento de Visão do Produto** | [`02 - Documento de Visao do Produto.md`](Pacote%201%20-%20Processo%20de%20Desenvolvimento%20e%20Requisitos/02%20-%20Documento%20de%20Visao%20do%20Produto.md) | Oportunidade de mercado, 4 personas detalhadas, fronteiras de escopo, KPIs e matriz de riscos. |
| **Story Mapping & Backlog do Produto** | [`05 - User Story Mapping e Backlog.md`](Pacote%201%20-%20Processo%20de%20Desenvolvimento%20e%20Requisitos/05%20-%20User%20Story%20Mapping%20e%20Backlog.md) | Backbone horizontal de 5 macroatividades, matriz de releases e 18 histórias com cenários BDD (*Given/When/Then*). |
| **Requisitos Não-Funcionais (RNFs)** | [`04 - Requisitos Nao-Funcionais (RNFs).md`](Pacote%201%20-%20Processo%20de%20Desenvolvimento%20e%20Requisitos/04%20-%20Requisitos%20Nao-Funcionais%20%28RNFs%29.md) | 8 RNFs normatizados pela ISO/IEC 25010 com métricas quantitativas e métodos de verificação automatizada. |
| **Mockups e Mapa de Navegação** | [`07 - Mockups e Mapa de Navegacao Frontend.md`](Pacote%201%20-%20Processo%20de%20Desenvolvimento%20e%20Requisitos/07%20-%20Mockups%20e%20Mapa%20de%20Navegacao%20Frontend.md) | Arquitetura de informação do SPA, guardas RBAC, wireframes das 6 telas críticas e protótipo de alta resolução. |

---

## 4. Prototipo Interativo em Alta Resolucao (Mockups)

A plataforma conta com uma aplicação interativa desenvolvida em HTML5, CSS3 moderno (design system com dark theme e glassmorphism) e JavaScript vanilla, simulando a experiência completa do usuário:

- **Localização do Protótipo:** [`Pacote 1 - Processo de Desenvolvimento e Requisitos/mockups-high-res/index.html`](Pacote%201%20-%20Processo%20de%20Desenvolvimento%20e%20Requisitos/mockups-high-res/index.html)
- **Telas Simuladas:**
  1. *Vitrine Geral de Eventos:* Apresentação do megaevento, contadores de flash crowd e status de esgotamento de lotes.
  2. *Mapa Interativo de Assentos:* Grade de cadeiras com status dinâmico via WebSockets (Disponível, Bloqueado 10m, Vendido e Selecionado).
  3. *Checkout Transacional com Cronômetro:* Contador regressivo ativo de 10:00 sincronizado com o servidor e cabeçalho `Idempotency-Key`.
  4. *Ingresso Digital Confirmado:* Bilhete criptografado com QR Code em vetor SVG de alta fidelidade e assinatura digital auditável.
  5. *Terminal de Scanner da Portaria:* Mira de leitura ótica, verificação em $\le 300\text{ ms}$, prevenção de dupla entrada e alternador de modo offline.
  6. *Painel Analítico de Telemetria do Organizador:* Indicadores em tempo real de vazão de requisições, esgotamento por minuto e ocupação setorial.

### Como Executar os Mockups
Basta abrir o arquivo `index.html` em qualquer navegador web moderno:
```bash
# macOS
open "Pacote 1 - Processo de Desenvolvimento e Requisitos/mockups-high-res/index.html"

# Linux
xdg-open "Pacote 1 - Processo de Desenvolvimento e Requisitos/mockups-high-res/index.html"

# Windows
start "" "Pacote 1 - Processo de Desenvolvimento e Requisitos/mockups-high-res/index.html"
```

---

## 5. Especificacoes Formais e Agent Skills

O processo de desenvolvimento com IA utiliza contratos formais e guardrails definidos no padrão Agent Skills:

- **Contratos de Interface:**
  - OpenAPI 3.0 (Reservas e Checkout): [`Pacote 1 - Processo de Desenvolvimento e Requisitos/especificacoes-ia/specs/openapi-checkout.yaml`](Pacote%201%20-%20Processo%20de%20Desenvolvimento%20e%20Requisitos/especificacoes-ia/specs/openapi-checkout.yaml)
  - AsyncAPI 2.6 (Tópicos e Eventos Kafka): [`Pacote 1 - Processo de Desenvolvimento e Requisitos/especificacoes-ia/specs/asyncapi-kafka.yaml`](Pacote%201%20-%20Processo%20de%20Desenvolvimento%20e%20Requisitos/especificacoes-ia/specs/asyncapi-kafka.yaml)
- **Agent Skills:**
  - Regras de Domínio e Invariantes: `bolsopass-domain`
  - Topologia e Contratos Kafka: `kafka-contracts`
  - Estratégia de Testes ($\ge 70\%$ e Testcontainers): `testing-strategy`
  - Eliminação do Dual-Write Dilemma: `transactional-outbox`

---

## 6. Navegacao no Obsidian

Para visualizar os diagramas de fluxo, links conceituais bidirecionais e mapas visuais, utilize a pasta do cofre no Obsidian:
- **Índice Geral (MOC):** [`00 - Visao Geral e Indice (MOC).md`](Pacote%201%20-%20Processo%20de%20Desenvolvimento%20e%20Requisitos/00%20-%20Visao%20Geral%20e%20Indice%20%28MOC%29.md)
- **Grafo Visual Interativo:** [`Mapa Visual dos Requisitos.canvas`](Pacote%201%20-%20Processo%20de%20Desenvolvimento%20e%20Requisitos/Mapa%20Visual%20dos%20Requisitos.canvas)
- **Matriz de Rastreabilidade Cruzada:** [`08 - Matriz de Rastreabilidade e Pacotes de Entrega.md`](Pacote%201%20-%20Processo%20de%20Desenvolvimento%20e%20Requisitos/08%20-%20Matriz%20de%20Rastreabilidade%20e%20Pacotes%20de%20Entrega.md)
