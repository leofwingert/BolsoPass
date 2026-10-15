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
updated: 2026-10-14
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
- **Classificação ISO 25010:** Eficiência de Desempenho / Capacidade e Comportamento Temporal.
- **Fundamentação Técnica:** Em sistemas distribuídos sujeitos a abertura massiva de vendas, médias simples de latência mascaram a experiência degradada da cauda longa. As métricas são expressas em percentis ($P_{95}$ e $P_{99}$).
- **Métricas e Critérios de Aceitação:**
  - **Catálogo de Eventos (Camada de Alta Leitura):** Vazão sustentada de $\ge 5.000\text{ requisições por segundo}$ com latência $P_{95} \le 50\text{ ms}$. Atendida integralmente pela camada de cache Redis sem onerar o banco relacional.
  - **Endpoint de Reserva de Assentos (Camada Transacional Concorrente):** Suporte a rajadas de $\ge 2.000\text{ requisições por segundo}$ com latência $P_{95} \le 200\text{ ms}$.
  - **Proteção de Pool de Conexões:** Pool HikariCP configurado com tamanho finito e isolamento, prevenindo esgotamento de conexões (*connection pool exhaustion*).
- **Método de Verificação:** Script de teste de carga com k6/Gatling simulando rampa de 0 a 5.000 usuários virtuais em 10 segundos, medindo throughput e percentis de resposta.

---

## 3. Consistencia Transacional e Concorrencia

### [RNF-02] Tolerancia Zero a Sobre-venda (Zero Overselling)
- **Classificação ISO 25010:** Integridade / Exatidão dos Dados.
- **Fundamentação Técnica:** Em megaeventos com capacidade numerada finita, a venda concorrente do mesmo assento gera custos jurídicos e cancelamentos traumáticos. O sistema deve garantir exclusão mútua estrita.
- **Métricas e Critérios de Aceitação:**
  - Sob estresse de 10.000 requisições simultâneas disputando as últimas 50 vagas de um setor, exatamente 50 reservas devem ser criadas com status HTTP 201 e 9.950 devem receber HTTP 409 Conflict.
  - A taxa de assentos sobre-vendidos sob qualquer cenário de concorrência deve ser rigorosamente $0,00\%$.
- **Método de Verificação:** Teste de integração automatizado multithread com `ExecutorService` disparando chamadas concorrentes para a mesma vaga, validando a integridade do banco de dados pós-execução.

### [RNF-03] Precisao Temporal na Expiracao de Reservas (TTL de 10 Minutos)
- **Classificação ISO 25010:** Confiabilidade / Precisão Temporal.
- **Fundamentação Técnica:** A reserva temporária confere 10 minutos para pagamento. Retenção a mais bloqueia assentos indevidamente; retenção a menos cancela compras ativas de boa-fé.
- **Métricas e Critérios de Aceitação:**
  - O tempo de retenção deve ser de exatamente $10\text{ minutos}$ ($600\text{ segundos}$), com tolerância máxima de dispersão de $\pm 1\text{ segundo}$.
  - O evento `ReservaExpiradaEvent` deve ser publicado no tópico `reservas.expiradas` do Kafka no instante da expiração, liberando o assento no catálogo sem intervenção manual.
- **Método de Verificação:** Teste unitário e de integração temporal com manipulação de relógio (*Clock abstraction / Awaitility*) comprovando que a desalocação ocorre em $t = 600\text{ s}$.

---

## 4. Resiliencia, Desacoplamento Temporal e Mensageria

### [RNF-04] Eliminacao do Dual-Write Dilemma via Transactional Outbox
- **Classificação ISO 25010:** Confiabilidade / Tolerância a Falhas e Recuperabilidade.
- **Fundamentação Técnica:** A persistência em banco relacional e a publicação no Kafka em etapas separadas não garantem atomicidade, podendo gerar pagamentos sem ingresso ou ingressos sem pagamento.
- **Métricas e Critérios de Aceitação:**
  - Utilização mandatória do [Transactional Outbox Pattern](https://microservices.io/patterns/data/transactional-outbox.html), persistindo o pedido e o evento na tabela `outbox_events` sob a **mesma transação ACID relacional**.
  - Tolerância à queda completa do cluster Apache Kafka no instante do pagamento: nenhuma mensagem é perdida ($0\%$ de perda de eventos). A publicação é postergada até o retorno do broker.
- **Método de Verificação:** Teste de integração com Testcontainers simulando pausa forçada do contêiner Kafka durante a execução do checkout, comprovando envio assíncrono posterior pelo relay worker.

### [RNF-05] Idempotencia Rigorosa em Chamadas HTTP e no Consumo Kafka
- **Classificação ISO 25010:** Confiabilidade / Consistência Operacional.
- **Fundamentação Técnica:** Devido a instabilidades de rede e à semântica *At-Least-Once* do Kafka, chamadas de rede e mensagens podem ser repetidas.
- **Métricas e Critérios de Aceitação:**
  - **Camada HTTP:** O cabeçalho `Idempotency-Key` com padrão UUID deve deduplicar requisições em janela de 24 horas, retornando a mesma resposta original e evitando cobrança financeira repetida.
  - **Camada Kafka:** Todos os workers de mensageria devem registrar os IDs das mensagens processadas na tabela `processed_messages`, garantindo execução como *no-op idempotente* para eventos repetidos.
- **Método de Verificação:** Teste de integração enviando 3 requisições consecutivas com a mesma `Idempotency-Key`, assegurando que o gateway simulador receba apenas uma instrução de débito.

---

## 5. Seguranca, Autenticacao e Criptografia

### [RNF-06] Autenticacao Stateless e Inviolabilidade Criptografica do Ingresso
- **Classificação ISO 25010:** Segurança / Confidencialidade e Integridade.
- **Fundamentação Técnica:** A autenticação não pode criar afinidade de sessão no gateway. A emissão de ingressos deve impedir falsificação e permitir verificação física em portarias desconectadas.
- **Métricas e Critérios de Aceitação:**
  - Autenticação stateless baseada em JWT com algoritmo assimétrico (RS256 ou ECDSA P-256) emitido por provedor compatível ([Keycloak](https://www.keycloak.org/)).
  - Validação estrita de papéis RBAC (`ROLE_CLIENT`, `ROLE_ORGANIZER`, `ROLE_OPERATOR`, `ROLE_ADMIN`) em 100% dos endpoints.
  - O QR Code do ingresso deve conter assinatura criptográfica assimétrica digitalizada, viabilizando conferência offline com tempo de validação $\le 300\text{ ms}$.
- **Método de Verificação:** Testes de segurança asserindo que chamadas sem token retornam HTTP 401, chamadas com perfil incompatível retornam HTTP 403 e validação da chave pública no scanner de QR Code.

---

## 6. Testabilidade e Cobertura de Dominio

### [RNF-07] Piramide de Testes Automatizados e Reprodutibilidade com Testcontainers
- **Classificação ISO 25010:** Manutenibilidade / Testabilidade.
- **Fundamentação Técnica:** Concorrência, isolamento transacional e filas não podem ser testados de forma confiável com bancos em memória simplificados (H2/mocks).
- **Métricas e Critérios de Aceitação:**
  - **Cobertura de Domínio:** A suíte de testes unitários deve assegurar cobertura de código $\ge 70\%$ nos agregados centrais e na Máquina de Estados Finitos do Pedido detalhada em [[06 - Maquinas de Estados e Regras de Negocio]].
  - **Testes de Integração com Contêineres Reais:** Utilização mandatória de [Testcontainers](https://testcontainers.com/) instanciando contêineres reais de PostgreSQL, Kafka e Redis durante a esteira de validação.
- **Método de Verificação:** Relatório automatizado de cobertura (JaCoCo) gerado na esteira e asserção de que a taxa de cobertura de domínio atinge o limiar mínimo de 70%.

---

## 7. Operabilidade, DevOps e Entrega Continua

### [RNF-08] Conteinerizacao Completa e Pipeline de CI/CD
- **Classificação ISO 25010:** Portabilidade e Manutenibilidade.
- **Fundamentação Técnica:** O ambiente de homologação e avaliação deve ser 100% reproduzível sem requerer instalações manuais de dependências de sistema operacional.
- **Métricas e Critérios de Aceitação:**
  - Toda a infraestrutura da plataforma (microsserviços, bancos de dados, Kafka, Redis, Keycloak e frontend) deve ser executável por meio de comando único `docker compose up --build`.
  - Esteira automatizada de CI/CD (GitHub Actions / GitLab CI) executando linters estáticos, build e testes automatizados a cada Pull Request submetido à branch `develop`.
- **Método de Verificação:** Execução do comando de compose a partir de repositório limpo, aferindo que todos os contêineres atinjam estado operacional saudável (*healthy*) em menos de 180 segundos.
