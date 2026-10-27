# 05 — Processo de GCS (Gerenciamento da Configuração de Software)

> Define a ferramenta, o fluxo de branches, as regras de contribuição e a estratégia de versionamento do BolsoPass.

---

## 1. Ferramenta

| Item | Decisão |
|---|---|
| **SCM** | Git |
| **Hospedagem** | GitHub (repositório privado da organização) |
| **Modelo de repositório** | **Monorepo** com Maven multi-module + pnpm workspace |
| **Linha principal** | `main` (produção) |
| **Linha de integração** | `develop` (próxima release) |

### Por que monorepo

- Compartilhamento fácil de contratos (OpenAPI/AsyncAPI) e libs internas.
- Um único pipeline cobre a build cruzada entre serviços.
- Refactorings transversais (ex: trocar campo em evento) ficam atômicos.

---

## 2. Modelo de branches (GitFlow adaptado)

```
main      ────●──────●──────●────▶   (releases taggeadas)
                \    /\    /
release/*        \  /  \  /          (congelamento + hotfix pre-release)
                  \/    \/
develop   ──●──●──●──●──●──●──●──▶   (integração contínua)
            │  │  │     │  │
feature/*   ●──┘  │     │  │        (features)
bugfix/*          ●─────┘  │        (bugs achados em develop)
hotfix/*                   ●──▶ main (correção emergencial em prod)
```

### Nomenclatura

| Prefixo | Uso | Exemplo |
|---|---|---|
| `feature/` | Nova funcionalidade | `feature/orders-idempotency-key` |
| `bugfix/` | Correção de bug em develop | `bugfix/inventory-lock-ttl` |
| `hotfix/` | Correção emergencial em produção | `hotfix/qr-signature-encoding` |
| `release/` | Congelamento pré-release | `release/0.3.0` |
| `chore/` | Tarefas internas (build, docs, deps) | `chore/upgrade-spring-boot-3.3` |
| `docs/` | Alterações só de documentação | `docs/pacote-2-c4` |

### Regras

- Branches sempre criadas a partir de `develop` (exceto `hotfix/*` que sai de `main`).
- Branches devem viver **≤ 5 dias**. Mais que isso, rebase diário contra `develop`.
- Nome em `kebab-case`, prefixo + escopo + verbo (opcional) + alvo.

---

## 3. Fluxo de contribuição

1. **Issue aberta** no GitHub descrevendo o trabalho (template de issue).
2. Dev cria branch `feature/<nome>` a partir de `develop`.
3. Commits locais seguindo **Conventional Commits**.
4. Dev abre **Pull Request** cedo (draft permitido) → CI roda.
5. PR precisa de:
   - **≥ 1 review aprovado** (2 reviews para mudanças em `*-domain-*` ou contratos).
   - **Todos os checks verdes** (ver [04 - Pipeline](04%20-%20Pipeline%20de%20Desenvolvimento.md)).
   - **Branch atualizada** com `develop`.
   - Issue linkada (`Closes #123`).
6. **Merge via Squash and Merge** → mensagem final no formato Conventional Commit.
7. Branch deletada automaticamente após merge.

---

## 4. Conventional Commits

Formato: `<type>(<scope>): <subject>`

| Type | Uso |
|---|---|
| `feat` | Nova funcionalidade |
| `fix` | Correção de bug |
| `docs` | Documentação |
| `style` | Formatação (sem impacto de código) |
| `refactor` | Refactor sem mudar comportamento |
| `perf` | Melhoria de performance |
| `test` | Adição/correção de testes |
| `build` | Build system / deps |
| `ci` | Pipeline CI/CD |
| `chore` | Tarefas de manutenção |

**Exemplos:**

```
feat(orders): adiciona suporte a Idempotency-Key
fix(inventory): corrige TTL do lock Redis em cancelamento
docs(pacote2): mapeia tópicos Kafka no AsyncAPI
test(ticketing): cobre QR com assinatura inválida
refactor(iam): extrai RbacAuthorizationFilter
```

Validado automaticamente via **commitlint** no CI (ver [04 - Pipeline §2.1](04%20-%20Pipeline%20de%20Desenvolvimento.md#21-lint--format-bloqueante)).

---

## 5. Versionamento

- **SemVer 2.0.0** — `MAJOR.MINOR.PATCH`.
- Tags assinadas: `git tag -s v1.2.3 -m "release 1.2.3"`.
- Release notes geradas automaticamente com **release-please** ou **semantic-release**.
- Pacote 1 congelado em `v0.1.0`, Pacote 2 em `v0.2.0`.

| Incremento | Quando usar |
|---|---|
| **MAJOR** | Breaking change em contrato público (OpenAPI/AsyncAPI/payload de evento) |
| **MINOR** | Nova feature retrocompatível |
| **PATCH** | Bug fix retrocompatível |

---

## 6. Code review — critérios

Reviewer verifica (checklist no template de PR):

- [ ] A mudança resolve o que a issue pede, nada além.
- [ ] Nome das variáveis/métodos descreve a intenção.
- [ ] Testes cobrem o caminho feliz **e** pelo menos um caminho de falha.
- [ ] Nenhum segredo (chave, token, senha) hard-coded.
- [ ] Logs em pontos de decisão críticos (sem PII em claro).
- [ ] Documentação atualizada se mudou contrato ou arquitetura.
- [ ] Migrations Flyway versionadas sequencialmente, sem edição retroativa.

---

## 7. Políticas transversais

| Política | Regra |
|---|---|
| **Segredos** | Nunca em código. `.env.example` versionado, `.env` no `.gitignore`. GitHub Secrets em CI. |
| **Dependências** | PRs de atualização via Dependabot. Review obrigatório. |
| **Licenças** | Scan automático; proibidas GPL/AGPL em runtime (OK em dev). |
| **PII / LGPD** | Logs de CPF/email/telefone sempre mascarados. |
| **Grandes binários** | Git LFS para arquivos > 1MB (fonts, PDFs de referência). |

---

## 8. Ambientes

| Ambiente | Branch | Deploy | URL |
|---|---|---|---|
| **Local** | qualquer | `docker compose up` manual | `localhost` |
| **Staging** | `develop` | automático em cada merge | `staging.bolsopass.app` (simulado) |
| **Produção** | `main` | manual após tag `v*.*.*` + aprovação | `bolsopass.app` (simulado) |

---

## 9. Rotina de releases

1. Criar branch `release/0.3.0` a partir de `develop`.
2. Rodar bateria completa (incluindo E2E Playwright noturno).
3. Corrigir bugs encontrados nesta branch (merge back em `develop`).
4. Quando verde: merge em `main` + tag `v0.3.0` + merge em `develop`.
5. CI dispara deploy em produção.
6. Release notes publicadas no GitHub Releases.

---

## 10. Papéis no repositório

| Papel | Permissão | Responsabilidade |
|---|---|---|
| **Maintainer** | Admin | Branch protection, releases, segredos |
| **Developer** | Write | Branches `feature/*`, PRs, reviews |
| **Reviewer** | Write (grupo CODEOWNERS) | Reviews obrigatórios em módulos sob sua posse |

Arquivo `CODEOWNERS` na raiz distribui a propriedade por diretório (ex: `/orders-service/ @dev-backend-1 @dev-backend-2`).
