---
title: "Requisitos Não-Funcionais (RNFs) e Métricas de Qualidade"
aliases:
  - Requisitos Não-Funcionais
  - RNFs
  - Métricas de Qualidade
tags:
  - requisitos
  - rnf
  - performance
  - seguranca
  - resiliencia
  - arquitetura
  - pacotes/pacote-1
created: 2026-10-05
updated: 2026-10-06
status: pronto
---

# Requisitos Nao-Funcionais (RNFs) e Metricas de Qualidade

> [!abstract] Resumo de Engenharia de Qualidade
> Os Requisitos Não-Funcionais (RNFs) do [[00 - Visao Geral e Indice (MOC)|BolsoPass]] foram formulados com métricas quantitativas e critérios rigorosos de aceitação, alinhados à norma [ISO/IEC 25010](https://iso25000.com/index.php/en/iso-25000-standards/iso-25010) para características de qualidade de software. Eles estabelecem as restrições que guiam as decisões de concorrência, consistência transacional e resiliência da plataforma.

---

## 1. Matriz Sintese de Requisitos Nao-Funcionais

| Identificador | Categoria (ISO 25010) | Descrição do Requisito | Métrica Alvo e Tolerância | Prioridade |
| :--- | :--- | :--- | :--- | :--- |
| **RNF-01** | Desempenho e Eficiência | Resiliência e vazão sob tráfego de rajada (*flash crowd*) | $\ge 2.000\text{ req/s}$ com $P_{95} \le 200\text{ ms}$ | Crítica |
| **RNF-02** | Integridade e Concorrência | Tolerância zero a sobre-venda (*Zero Overselling*) | $0\%$ de sobre-venda sob qualquer condição | Inegociável |
| **RNF-03** | Precisão Temporal | Expiração estrita de reservas temporárias de assentos | Exatamente $10\text{ min} \pm 1\text{ s}$ | Crítica |
| **RNF-04** | Resiliência e Consistência | Eliminação do *Dual-Write Dilemma* via Outbox Pattern | $0\%$ de perda de eventos entre banco e Kafka | Crítica |
| **RNF-05** | Confiabilidade | Idempotência estrita em chamadas HTTP e no consumo | $100\%$ de deduplicação comprovada em testes | Alta |
| **RNF-06** | Segurança | Autenticação stateless JWT e integridade dos ingressos | Criptografia assimétrica em 100% dos ingressos | Alta |
| **RNF-07** | Testabilidade | Pirâmide de testes e validação com contêineres reais | Cobertura unitária $\ge 70\%$ nos agregados | Alta |
| **RNF-08** | Manutenibilidade / DevOps | Ambiente conteinerizado e esteira automatizada de CI/CD | `docker compose up` único e CI verde em PR | Alta |

---

## 2. Desempenho e Eficiencia de Recursos

### [RNF-01] Resistencia a Trafego de Rajada (Flash Crowd Traffic)
- **Fundamentação Técnica:** Em sistemas distribuídos, a utilização de médias simples para avaliar o tempo de resposta é uma falácia que oculta o impacto das caudas longas de latência. Por essa razão, todas as métricas são expressas em percentis ($P_{95}$ e $P_{99}$).
- **Métricas e Critérios de Aceitação:**
  - **Catálogo de Eventos (Camada de Alta Leitura):** O endpoint de consulta ao catálogo deve suportar carga sustentada de $\ge 5.000\text{ requisições por segundo}$ com latência $P_{95} \le 50\text{ ms}$. Para alcançar essa marca, as requisições não devem tocar o banco relacional, sendo atendidas pela camada de cache Redis descrita no requisito `RF-05` de [[03 - Requisitos Funcionais (5 Modulos)]].
  - **Endpoint de Reserva de Assentos (Camada Transacional Concorrente):** O endpoint de alocação de assentos deve sustentar rajadas instantâneas de $\ge 2.000\text{ requisições por segundo}$ com latência $P_{95} \le 200\text{ ms}$.
  - **Proteção do Pool de Conexões:** O pool de conexões com o PostgreSQL (ex.: HikariCP) deve ser dimensionado com capacidade finita e isolamento de recursos, impedindo que requisições em espera esgotem os descritores de arquivo ou a memória do sistema operacional.

---

## 3. Consistencia Transacional e Concorrencia

### [RNF-02] Tolerancia Zero a Sobre-venda (Zero Overselling)
- **Fundamentação Técnica:** Em um festival com 50.000 lugares numerados, a venda duplicada de uma única cadeira constitui uma falha grave de integridade transacional. O modelo de concorrência não pode depender de consistência fraca ou leituras eventuais na camada de reserva.
- **Métricas e Critérios de Aceitação:**
  - Em testes de estresse reproduzindo 10.000 requisições simultâneas disputando as últimas 50 vagas de um setor, exatamente 50 reservas devem ser criadas com sucesso e 9.950 devem receber rejeição determinística (HTTP 409 Conflict).
  - O mecanismo de trava (seja via [Redis Redlock / SETNX](https://redis.io/docs/latest/develop/use/patterns/distributed-locks/) ou [PostgreSQL Explicit Locking com SELECT FOR UPDATE](https://www.postgresql.org/docs/current/explicit-locking.html)) deve ser verificado em testes de carga automatizados, comprovando 0% de sobre-venda.

### [RNF-03] Precisao Temporal na Expiracao de Reservas (TTL de 10 Minutos)
- **Fundamentação Técnica:** A reserva temporária existe para conferir ao comprador uma janela segura para efetuar o pagamento. Caso o pagamento não ocorra, a retenção indevida do assento causa prejuízo à produtora do evento.
- **Métricas e Critérios de Aceitação:**
  - O tempo de retenção deve ser de exatamente $10\text{ minutos}$, com margem de tolerância de dispersão de no máximo $\pm 1\text{ segundo}$.
  - A liberação deve ocorrer de forma automática, através da expiração atômica de chaves no Redis ou pelo disparo do evento `reservas.expiradas` no Kafka, retornando o assento ao estado `DISPONIVEL` sem qualquer intervenção humana.

---

## 4. Resiliencia, Desacoplamento Temporal e Mensageria

### [RNF-04] Eliminacao do Dual-Write Dilemma via Transactional Outbox
- **Fundamentação Técnica:** Em microsserviços, gravar o pagamento no banco e publicar a mensagem no Kafka em duas etapas não atômicas cria a clássica armadilha da escrita dupla: se o banco gravar e a rede cair antes do broker receber a mensagem, o cliente paga mas o ingresso nunca é gerado; se o broker receber e o banco falhar no commit, o ingresso é gerado sem confirmação financeira.
- **Métricas e Critérios de Aceitação:**
  - Aplicação mandatória do [Transactional Outbox Pattern](https://microservices.io/patterns/data/transactional-outbox.html).
  - O estado do pedido e o registro do evento de integração devem ser comitados dentro de uma **única transação ACID local relacional**.
  - O sistema deve tolerar a indisponibilidade completa do broker Apache Kafka durante o checkout: a transação no banco é concluída e o worker publica o evento de forma assíncrona assim que o Kafka retornar, garantindo entrega *At-Least-Once* sem perda de mensagens.

### [RNF-05] Idempotencia Rigorosa em Chamadas HTTP e no Consumo Kafka
- **Fundamentação Técnica:** De acordo com a [semântica de mensageria do Apache Kafka](https://kafka.apache.org/documentation/#design_semantics), a garantia padrão de entrega entre partições e consumidores é *At-Least-Once*. Mensagens repetidas são esperadas durante rebalanceamentos de rede.
- **Métricas e Critérios de Aceitação:**
  - **Camada HTTP:** O cabeçalho `Idempotency-Key` deve deduplicar requisições repetidas de checkout dentro de uma janela de 24 horas, retornando a mesma resposta original sem duplicar cobranças no simulador de gateway financeiro.
  - **Camada de Consumo de Mensagens:** Todo consumidor deve manter tabela de deduplicação (`processed_messages`) em banco relacional, tornando o reprocessamento de qualquer mensagem repetida uma operação inofensiva (*no-op idempotente*).

---

## 5. Seguranca, Autenticacao e Criptografia

### [RNF-06] Autenticacao Stateless e Inviolabilidade Criptografica do Ingresso
- **Fundamentação Técnica:** Sistemas em grande escala não podem depender de sessões centralizadas com estado compartilhado no gateway para autenticar requisições.
- **Métricas e Critérios de Aceitação:**
  - Autenticação integralmente *stateless* baseada em JWT com algoritmo de assinatura assimétrica RS256 ou EdDSA, emitido por provedor compatível como [Keycloak](https://www.keycloak.org/).
  - Validação de autorização baseada em papéis (RBAC) aplicada em 100% dos endpoints da API, conforme definido no requisito `RF-02` de [[03 - Requisitos Funcionais (5 Modulos)]].
  - Todo ingresso em PDF deve incorporar assinatura digital criptográfica no payload do seu QR Code, permitindo validação offline na catraca do evento sem necessidade de consulta à internet.

---

## 6. Testabilidade e Cobertura de Dominio

### [RNF-07] Piramide de Testes Automatizados e Reprodutibilidade com Testcontainers
- **Fundamentação Técnica:** Testes que utilizam bancos de dados em memória simplificados (como H2 ou mocks genéricos) frequentemente mascaram problemas sutis de concorrência, transações e dialetos SQL.
- **Métricas e Critérios de Aceitação:**
  - **Cobertura de Domínio:** A suíte de testes unitários deve assegurar cobertura de código $\ge 70\%$ nos agregados centrais e na Máquina de Estados Finitos do Pedido detalhada em [[06 - Maquinas de Estados e Regras de Negocio]].
  - **Testes de Integração com Contêineres Reais:** Utilização mandatória da biblioteca [Testcontainers](https://testcontainers.com/) para orquestrar instâncias reais de PostgreSQL, Apache Kafka e Redis durante a execução da esteira de testes automatizada.

---

## 7. Operabilidade, DevOps e Entrega Continua

### [RNF-08] Conteinerizacao Completa e Pipeline de CI/CD
- **Fundamentação Técnica:** A reprodutibilidade do ambiente de desenvolvimento elimina o clássico problema de "funciona na minha máquina", permitindo que qualquer membro da equipe ou avaliador execute a solução integralmente.
- **Métricas e Critérios de Aceitação:**
  - Toda a infraestrutura (microsserviços, bancos de dados, Kafka, Keycloak e UI de mensageria) deve ser executável via comando único `docker compose up --build`.
  - A esteira de integração contínua (GitHub Actions ou GitLab CI) deve validar a integridade de compilação, executar linters estáticos de código e rodar a suíte completa de testes a cada Pull Request submetido à branch `develop`.
