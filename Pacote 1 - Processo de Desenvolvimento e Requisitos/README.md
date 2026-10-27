# Pacote 1: Processo de Desenvolvimento e Requisitos

> **Pontifícia Universidade Católica do Rio Grande do Sul (PUCRS)**  
> **Escola Politécnica – Construção de Software (2026/2)**  
> **Trabalho Integrador:** Plataforma BolsoPass – Megaeventos em Tráfego de Rajada (*Flash Crowd*)

---

## 1. Guia de Avaliacao dos Entregaveis

Este diretório contém a consolidação técnica integral do **Pacote 1**, atendendo a cada uma das exigências formuladas no enunciado oficial:

```text
.
├── README.md                                        <- Guia rápido de avaliação e navegação
├── 00 - Visao Geral e Indice (MOC).md               <- Mapa de conteúdo geral da documentação
├── 01 - Processo de Desenvolvimento Suportado por IA.md <- Ciclo, ferramentas, agentes e specs
├── 02 - Documento de Visao do Produto.md            <- Problema, proposta de valor, personas e escopo
├── 03 - Requisitos Funcionais (5 Modulos).md        <- Especificação detalhada das 5 frentes
├── 04 - Requisitos Nao-Funcionais (RNFs).md         <- 8 RNFs quantitativos e verificáveis (ISO 25010)
├── 05 - User Story Mapping e Backlog.md             <- 18 Histórias de Usuário completas com BDD
├── 06 - Maquinas de Estados e Regras de Negocio.md  <- FSM do Pedido, Outbox e Idempotency-Key
├── 07 - Mockups e Mapa de Navegacao Frontend.md     <- Rotas SPA, RBAC e wireframes estruturais
├── 08 - Matriz de Rastreabilidade e Pacotes de Entrega.md <- Matriz cruzada de conformidade
├── Mapa Visual dos Requisitos.canvas                <- Grafo visual interativo no Obsidian Canvas
├── mockups-high-res/                                <- Protótipo web interativo em alta fidelidade
│   └── index.html
└── especificacoes-ia/                               <- Contratos e Agent Skills de suporte à IA
    ├── specs/ (OpenAPI 3.0 e AsyncAPI 2.6)
    └── skills/ (Domínio, Kafka, Testes e Outbox)
```

---

## 2. Rastreabilidade com os Requisitos do Enunciado

| Requisito do Enunciado | Arquivo Correspondente | Resumo dos Itens Atendidos |
| :--- | :--- | :--- |
| **Descrição do processo de desenvolvimento suportado por IA** | [`01 - Processo de Desenvolvimento Suportado por IA.md`](01%20-%20Processo%20de%20Desenvolvimento%20Suportado%20por%20IA.md) | Ciclo iterativo em 5 etapas, metodologia Human-in-the-Loop, TDD com cobertura $\ge 70\%$ nos agregados, integração com Testcontainers, contratos OpenAPI 3.0 e AsyncAPI 2.6 sob `especificacoes-ia/specs/`, e 4 Agent Skills em `especificacoes-ia/skills/`. |
| **Documento de Visão do Produto** | [`02 - Documento de Visao do Produto.md`](02%20-%20Documento%20de%20Visao%20do%20Produto.md) | Problema de esgotamento de conexões e sobre-venda, declaração formal de posicionamento, 4 personas detalhadas (Gabriel, Beatriz, Marcos e Ana), delimitação estrita de fronteiras, especificação do simulador de gateway financeiro e matriz de riscos. |
| **Backlog com Épicos e Histórias no formato Story Mapping** | [`05 - User Story Mapping e Backlog.md`](05%20-%20User%20Story%20Mapping%20e%20Backlog.md) | Mapeamento no modelo Jeff Patton com backbone horizontal de 5 atividades e matriz vertical de 3 fatias de release. Contém 18 histórias de usuário completas (US-01 a US-18) cobrindo todos os 5 módulos com critérios de aceitação executáveis no padrão BDD/Gherkin (*Given/When/Then*). |
| **Requisitos Não-Funcionais** | [`04 - Requisitos Nao-Funcionais (RNFs).md`](04%20-%20Requisitos%20Nao-Funcionais%20%28RNFs%29.md) | 8 RNFs normatizados pela ISO/IEC 25010 com métricas quantitativas mensuráveis e tolerâncias: vazão em rajada ($\ge 5.000\text{ req/s}$ no catálogo e $\ge 2.000\text{ req/s}$ na reserva), tolerância zero a sobre-venda ($0\%$), expiração estrita de reservas em 10 min ($\pm 1\text{ s}$), eliminação do Dual-Write via Transactional Outbox, idempotência de ponta a ponta e cobertura de testes $\ge 70\%$. |
| **Mockups e Mapa de Navegação do Front-End** | [`07 - Mockups e Mapa de Navegacao Frontend.md`](07%20-%20Mockups%20e%20Mapa%20de%20Navegacao%20Frontend.md) | Arquitetura de informação do SPA, mapa de rotas com guardas de autorização RBAC, wireframes estruturais e design system. Acompanha protótipo navegável em alta fidelidade no arquivo [`mockups-high-res/index.html`](mockups-high-res/index.html). |

---

## 3. As Cinco Frentes Funcionais Autônomas

O escopo funcional do sistema distribui-se em 5 domínios autônomos documentados em [`03 - Requisitos Funcionais (5 Modulos).md`](03%20-%20Requisitos%20Funcionais%20%285%20Modulos%29.md):

1. **Identidade e Governança (IAM):** Autenticação stateless via JWT, rotação de refresh tokens no Redis, controle granular de papéis (RBAC: Cliente, Organizador, Operador e Admin) e auditoria.
2. **Inventário e Concorrência:** Catálogo otimizado para alta taxa de leitura (*cache-aside* Redis), mapas de assentos numerados, alocação atômica (*Zero Overselling*) e desalocação automática por timeout de 10 minutos.
3. **Transações e Checkout:** Máquina de estados finitos do pedido, suporte a cabeçalho `Idempotency-Key` e aplicação estrita do *Transactional Outbox Pattern* sob transação ACID relacional no PostgreSQL para publicação segura no Kafka.
4. **Mensageria e Despacho:** Consumo assíncrono idempotente via tabela `processed_messages`, compilação de ingressos em PDF com carimbo de tempo (*timestamp*) e QR Code com assinatura digital ECDSA P-256 (validação offline na portaria), além de push reativo via WebSockets.
5. **Telemetria e DevOps:** Ingestão contínua de eventos no Kafka, painel analítico com métricas em tempo real, orquestração via Docker Compose e esteira de CI/CD automatizada.

---

## 4. Visualização do Protótipo em Alta Resolução

Para experimentar a interface do usuário em tempo de execução:

```bash
# Abrir os mockups interativos no navegador
xdg-open "Pacote 1 - Processo de Desenvolvimento e Requisitos/mockups-high-res/index.html"
```

A aplicação simula:
- Navegação entre as 6 telas críticas (Vitrine, Mapa de Assentos, Checkout, Ingresso Digital, Portaria e Dashboard de Telemetria).
- Seleção reativa de assentos no mapa.
- Cronômetro regressivo ativo de 10:00 minutos com alerta visual e sonoro de expiração.
- Emissão de bilhete digital com assinatura criptográfica assimétrica.
- Modo de operação da portaria com alternância entre online e offline.
