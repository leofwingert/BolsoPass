# BolsoPass — Versão 4.0 da Implementação

> **Escopo:** app da **bilheteria / portaria** — leitor de QR no celular para validar a entrada,
> inclusive sem internet, e **venda no balcão** (CPF → lugares vagos → cartão ou PIX).
> **Data:** 30/10/2026 (venda no balcão: 01/11/2026) · **Branch:** `feat/Mocks`
> **Versões anteriores:** [1.0](../Versão%201.0%20-%20Implementação/README.md) (cliente) ·
> [2.0](../Versão%202.0%20-%20Implementação/README.md) (admin e login único) ·
> [3.0](../Versão%203.0%20-%20Implementação/README.md) (admin refeito pelos mockups).

---

## 1. Resumo

O operador de portaria entra pelo mesmo login web, escolhe o evento e o portão, e lê o QR dinâmico
do ingresso com a câmera do celular:

```
Login (matrícula + PIN) → Portão (evento + portão + base offline) ─┬─ Leitor QR → Resultado
                                                                   ├─ Vender: CPF → lugares → cartão/PIX → ingresso
                                                                   ├─ Histórico do turno
                                                                   └─ Modo contingência (sem internet)
```

A tela é feita para celular (navegação embaixo), mas também funciona no computador.

| Parte | Pasta |
|---|---|
| Telas | [`apps/web-spa/src/features/operator`](../apps/web-spa/src/features/operator) |
| Validação do QR (regra pura + testes) | [`apps/web-spa/src/domain/ticketQr.ts`](../apps/web-spa/src/domain/ticketQr.ts) |
| API da portaria | [`backend/.../management/ManagementController`](../backend/src/main/java/com/bolsopass/management/ManagementController.java) |
| Venda no balcão (tela) | [`OperatorSalePage.tsx`](../apps/web-spa/src/features/operator/OperatorSalePage.tsx) |
| Venda no balcão (API) | [`backend/.../boxoffice/`](../backend/src/main/java/com/bolsopass/boxoffice/) |
| Leitura do QR pela câmera | biblioteca `jsqr` 1.4.0 (carregada só quando a câmera abre) |

> A primeira versão da API e da tela de preparo foi feita por outra IA; na 2.0 corrigimos os bugs
> dela, e nesta versão as telas foram construídas a partir dos mockups.

---

## 2. Como rodar

Igual às versões anteriores. Abra `http://localhost:3000/auth/login` e clique em **Bilheteria**
(matrícula `OP-88219`, PIN `123456`) para a portaria, ou em **Venda** (matrícula `VD-20001`, PIN `654321`)
para o terminal de venda, que só vende.

**Câmera:** o navegador só libera câmera (e a validação criptográfica) em **HTTPS** ou em
**localhost**. No computador, use a visão de celular do navegador (F12). Abrindo pelo IP da rede
num celular, use "Digitar código" até o HTTPS de desenvolvimento ser configurado.

**Teste rápido:** entre como cliente em outra janela, abra um ingresso do Festival Synthwave e
leia o QR da tela com a câmera (ou copie o código para "Digitar código").

---

## 3. Telas

| Rota | Tela | O que faz |
|---|---|---|
| `/auth/login` | Login | Mesmo login web de todos os perfis; matrícula leva para a portaria |
| `/operator/setup` | Portão | Dados do operador; escolha do evento (portões abertos/fechados, ingressos, % que já entrou) e do portão; baixa a base offline ao confirmar |
| `/operator/scan` | Leitor QR | Câmera com moldura, lanterna, troca de câmera, "Digitar código"; entradas no evento e leituras por minuto; última validação |
| (na mesma rota) | Resultado | Verde liberado, laranja já utilizado, vermelho inválido, roxo outro evento; titular, CPF parcial, setor e código; bipe + vibração; volta sozinho em 3 s quando liberado |
| `/operator/sell` | Vender (balcão, estilo maquininha) | Botão **Trocar evento** no topo (lista de eventos à venda; bloqueado só durante o pagamento) e **Voltar** da etapa de lugares. 1) CPF do cliente → mostra **nome e telefone** (e-mail parcial) para confirmar; sem cadastro, faz o cadastro rápido. 2) **Lugares vagos** do evento, por setor, até 6 por venda. 3) **CPF vinculado a cada ingresso** (o 1º já vem com o cliente) e cobrança no visor: **crédito** (1x a 6x, "aproximar cartão" simulado, opção de simular recusa), **PIX** (QR na tela e "PIX recebido" simulado) ou **dinheiro** (valor recebido, atalhos de notas e troco). 4) **Comprovante** em formato de cupom, com titular, CPF parcial e código de cada ingresso; botão de imprimir |
| `/operator/history` | Histórico | Totais do turno (lidos, ritmo, aprovados, recusados), busca, filtros, detalhes e o que ainda está na fila de envio |
| `/operator/offline` | Modo contingência | Situação da conexão, próxima sincronização, base no aparelho, fila de envio, "Tentar reconectar agora" e regras para operar sem internet |

---

## 4. Regras de negócio

| Regra | Requisito | Onde |
|---|---|---|
| QR no formato `BP1.<dados>.<assinatura>` com **assinatura ECDSA P-256 conferida no aparelho** | RF-13 | `domain/ticketQr.ts`, `lib/ecdsa.ts` |
| QR muda a cada 15 s: **print de tela ou código antigo é recusado** (token expirado) | RF-13 | `validateScan` |
| Ingresso de outro evento é barrado com orientação para a bilheteria | RF-13 | `validateScan` |
| **Ingresso já utilizado** é barrado, mostrando horário e portão da entrada anterior (possível cópia) | RF-13 | `OperatorProvider` |
| Entrada liberada é **enviada ao servidor na hora**, para outro portão não aceitar o mesmo ingresso | RF-13 | `OperatorProvider.scan` |
| A cada 10 s o aparelho envia a fila e baixa a base de novo (aprende entradas feitas em outros portões) | RF-13 | `OperatorProvider.syncNow` |
| **Sem internet a validação continua**; entradas ficam guardadas no aparelho e são enviadas quando a rede volta | RF-13 | `OperatorProvider` |
| Envio idempotente: a mesma entrada enviada duas vezes conta uma vez; vale a primeira | RF-13 | `ManagementController.syncScans` |
| CPF aparece parcial na portaria (`***.392.810-**`) | LGPD | `maskCpf` |
| Trocar de evento com entradas ainda não enviadas é bloqueado | RF-13 | `OperatorSetupPage` |
| Estado do posto (evento, portão, base, leituras) salvo no aparelho: sobrevive a recarregar a página | RNF | `OperatorProvider` |
| Só `ROLE_OPERATOR` (ou admin) acessa a portaria | RF-02 | `RequireArea`, `SecurityConfig` |
| **Dois perfis na bilheteria**: portaria (`ROLE_OPERATOR`: lê QR e vende) e terminal de venda (`ROLE_SELLER`: só vende, escolhe só o evento). O **admin não acessa a bilheteria** | RF-02 | `roles.ts`, `ManagementController`, `BoxOfficeController` |
| **Dinheiro só no balcão**: a compra online com `CASH` é recusada (400) | RF-11 | `OrderController` |
| **CPF vinculado ao ingresso**: o ingresso aparece na carteira da conta com aquele CPF, mesmo que outra pessoa tenha pago (venda no balcão ou acompanhante), e essa pessoa pode abrir o QR | RF-13 | `TicketService.mine` |
| **Venda no balcão usa o mesmo fluxo do site**: trava atômica do assento, reserva de 10 min, limite de ingressos por CPF, `Idempotency-Key`, outbox e ingresso com QR na carteira do cliente | RF-06 a RF-13 | `BoxOfficeService` |
| Cliente identificado pelo **CPF válido** (com ou sem máscara); o balcão vê nome e telefone, e o e-mail só parcial | RF-02 / LGPD | `BoxOfficeService.find` |
| CPF sem cadastro: cadastro rápido com nome, sobrenome e telefone com DDD | RF-03 | `BoxOfficeService.register` |
| No balcão a pessoa está presente: **não passa pela sala de espera virtual** | Regra do balcão | `ReservationService.reserve(..., boxOffice)` |
| Cartão recusado: tentar de novo com nova chave, trocar para PIX ou desistir (lugares voltam à venda) | RF-11 | `OperatorSalePage` |
| Toda venda no balcão entra no registro de alterações do evento, com a matrícula do operador | RF-03 | `BoxOfficeService.pay` |

---

## 5. Endpoints usados (`/api/v1`)

| Método | Caminho | Uso |
|---|---|---|
| POST | `/auth/operator/login` | Login por matrícula + PIN |
| GET | `/operator/events` | Eventos atribuídos e portões |
| GET | `/operator/events/{id}/cache` | Base offline: chave pública, ingressos e entradas já feitas |
| POST | `/operator/events/{id}/scans` | Envio das entradas (inclusive as feitas sem internet) |
| GET | `/operator/box-office/customers?cpf=` | Busca o cliente pelo CPF (nome, telefone, e-mail parcial) |
| POST | `/operator/box-office/customers` | Cadastro rápido de cliente sem conta |
| GET | `/operator/box-office/events/{id}/seats` | Lugares vagos do evento |
| POST | `/operator/box-office/events/{id}/reservations` | Reserva os lugares em nome do cliente (10 min) |
| POST | `/operator/box-office/checkout` (header `Idempotency-Key`) | Cobra no cartão ou PIX |
| POST | `/operator/box-office/reservations/{id}/give-up` | Desistência após recusa |

---

## 6. O que foi mantido e o que ficou de fora dos mockups

| Mockup | Decisão |
|---|---|
| Login próprio da portaria e leitura do crachá por QR | **Mantido o login web único**, responsivo (decisão do usuário) |
| Escolha de evento e portão, leitor, resultado, histórico, contingência | Mantidos |
| Lanterna, alternar câmera, digitar código, bipe e vibração | Mantidos e funcionando |
| "Catraca eletromecânica destravada" | Trocado por "Pode entrar" / "Não liberar a entrada": não há catraca ligada ao sistema |
| Mesh 5G, latência do nó, cluster, bateria, "Turno Noite" | Fora: o sistema não mede esses dados |
| Verificação facial, Rádio PTT do supervisor | Fora: não há esse hardware |
| "Simular sem eventos" | Fora: era botão de teste do mockup |

---

## 7. Testes e verificação

| Verificação | Resultado |
|---|---|
| Frontend — `pnpm coverage` | 77 testes passando, **91,5% das linhas** (inclui 5 testes da venda no balcão: terminal de venda, PIX, dinheiro com troco, recusa, admin barrado) |
| Backend — `./mvnw test` | 48 testes passando, **92,9% das linhas** (inclui 6 testes da venda no balcão: dinheiro, CPF vinculado, admin barrado) |
| Venda no balcão no Chrome (celular) contra o servidor real | ✔ CPF → Sarah Jensen Oliveira, (11) 98844-3210 → 2 lugares → cartão aprovado → 2 ingressos emitidos (cliente + acompanhante) |
| | ✔ Limite por CPF: a 5ª compra da mesma cliente no setor é barrada com a mensagem do limite |
| Frontend — lint e checagem de tipos | Sem erros |
| Ponta a ponta no Chrome em tamanho de celular, com ingresso real da cliente Sarah | ✔ 1ª leitura **liberada** e registrada no servidor na hora |
| | ✔ Mesmo QR de novo: **já utilizado**, com horário e portão |
| | ✔ QR falso: **acesso negado** |
| | ✔ Sem internet: modo contingência abre e as telas continuam navegáveis |

**Bugs encontrados pelo teste e corrigidos:**

1. A entrada liberada só chegava ao servidor na sincronização seguinte (até 10 s); nesse intervalo
   outro portão aceitaria o mesmo ingresso. Agora o envio é imediato.
2. Sem internet, abrir pela primeira vez uma tela da portaria dava erro (código ainda não baixado).
   Agora todas as telas da portaria são baixadas quando o app abre.

---

## 8. Pendências

| Item | Situação |
|---|---|
| Abrir o app do zero sem internet (recarregar a página offline) | Precisa virar app instalável (PWA com service worker) |
| HTTPS no servidor de desenvolvimento, para testar a câmera num celular pela rede | Não configurado |
| Fechamento de caixa do terminal (total em dinheiro por turno) | Não iniciado |
| Maquininha real (integração com adquirente) | Pagamento simulado; cartão final 0000 simula recusa |
| Venda no balcão sem internet | Exige servidor (trava do assento); o leitor de QR continua offline |
| Categoria do ingresso (inteira/meia) na base offline | O servidor ainda envia sempre "Inteira" |
| Indicar o portão certo quando o setor do ingresso entra por outro portão | Não iniciado |
| Commit | Nada foi commitado ainda |
