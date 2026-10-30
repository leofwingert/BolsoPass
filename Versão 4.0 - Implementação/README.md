# BolsoPass — Versão 4.0 da Implementação

> **Escopo:** app da **bilheteria / portaria** — leitor de QR no celular para validar a entrada,
> inclusive sem internet.
> **Data:** 30/10/2026 · **Branch:** `feat/Mocks`
> **Versões anteriores:** [1.0](../Versão%201.0%20-%20Implementação/README.md) (cliente) ·
> [2.0](../Versão%202.0%20-%20Implementação/README.md) (admin e login único) ·
> [3.0](../Versão%203.0%20-%20Implementação/README.md) (admin refeito pelos mockups).

---

## 1. Resumo

O operador de portaria entra pelo mesmo login web, escolhe o evento e o portão, e lê o QR dinâmico
do ingresso com a câmera do celular:

```
Login (matrícula + PIN) → Portão (evento + portão + base offline) → Leitor QR → Resultado
                                                                       ├─ Histórico do turno
                                                                       └─ Modo contingência (sem internet)
```

A tela é feita para celular (navegação embaixo), mas também funciona no computador.

| Parte | Pasta |
|---|---|
| Telas | [`apps/web-spa/src/features/operator`](../apps/web-spa/src/features/operator) |
| Validação do QR (regra pura + testes) | [`apps/web-spa/src/domain/ticketQr.ts`](../apps/web-spa/src/domain/ticketQr.ts) |
| API da portaria | [`backend/.../management/ManagementController`](../backend/src/main/java/com/bolsopass/management/ManagementController.java) |
| Leitura do QR pela câmera | biblioteca `jsqr` 1.4.0 (carregada só quando a câmera abre) |

> A primeira versão da API e da tela de preparo foi feita por outra IA; na 2.0 corrigimos os bugs
> dela, e nesta versão as telas foram construídas a partir dos mockups.

---

## 2. Como rodar

Igual às versões anteriores. Abra `http://localhost:3000/auth/login` e clique em **Bilheteria**
(matrícula `OP-88219`, PIN `123456`).

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

---

## 5. Endpoints usados (`/api/v1`)

| Método | Caminho | Uso |
|---|---|---|
| POST | `/auth/operator/login` | Login por matrícula + PIN |
| GET | `/operator/events` | Eventos atribuídos e portões |
| GET | `/operator/events/{id}/cache` | Base offline: chave pública, ingressos e entradas já feitas |
| POST | `/operator/events/{id}/scans` | Envio das entradas (inclusive as feitas sem internet) |

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
| Frontend — `pnpm test:unit` | 46 testes passando (inclui os da validação do QR) |
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
| Venda no balcão da bilheteria | Não iniciada |
| Categoria do ingresso (inteira/meia) na base offline | O servidor ainda envia sempre "Inteira" |
| Indicar o portão certo quando o setor do ingresso entra por outro portão | Não iniciado |
| Commit | Nada foi commitado ainda |
