# Contributing

## Commits

Este monorepo usa Conventional Commits. O formato esperado e:

```text
tipo(escopo opcional): mensagem curta no imperativo
```

Exemplos:

```text
feat(web): add inventory list
fix(backend): validate empty sku
chore(repo): configure dependabot
infra(oracle): provision app vm
```

Tipos aceitos: `build`, `chore`, `ci`, `docs`, `feat`, `fix`, `infra`, `perf`, `refactor`, `revert`, `style` e `test`.

## Branches

Use nomes curtos com prefixo de contexto:

```text
feat/nome-da-funcionalidade
fix/nome-da-correcao
docs/nome-da-documentação
infra/nome-da-infraestrutura
ci/nome-da-automação
```

Mudanças devem ser abertas por pull request e revisadas antes do merge na `main`.

Fluxo resumido:

1. Criar uma branch de trabalho a partir de `main`, ou receber um PR do Dependabot.
2. Abrir PR diretamente para `main`.
3. Escolher o tipo de release no próprio PR humano.
4. Usar `hotfix/*` apenas para correcao urgente e marcar `patch`.

Veja o guia completo em [Branching Strategy](./branching-strategy.md).

## Versionamento, Tags e Releases

O projeto usa tags no formato `vMAJOR.MINOR.PATCH`, seguindo Semantic Versioning enquanto estiver em evolucao.

Exemplos:

```text
v0.1.0
v0.2.0
v0.2.1
```

- `MAJOR`: mudanças incompativeis ou grandes que quebram compatibilidade.
- `MINOR`: novas funcionalidades ou entregas relevantes sem quebra esperada.
- `PATCH`: correcoes pequenas e ajustes compativeis.

As releases são preparadas automaticamente pelo workflow `Release`, com base no tipo marcado no PR humano que entra em `main`. Merges do Dependabot recebem `patch` automaticamente.

Fluxo esperado:

1. Toda issue deve ser aberta pelo template `Tarefa`, registrando contexto, objetivo, escopo e critérios de aceite.
2. Todo PR humano para `main` deve referenciar exatamente uma issue usando `Closes #número`, `Fixes #número` ou `Resolves #número` e marcar exatamente um tipo de release.
3. Ao abrir ou editar o PR de trabalho, o workflow `Sync PR with Issue` copia a descrição da issue vinculada para o corpo do PR.
4. O workflow `Validate PR Template` confere as regras esperadas para cada origem e destino.
5. Ao fazer merge em `main`, o GitHub fecha automaticamente a issue vinculada ao PR.
6. O workflow `Release` usa o tipo marcado para criar a tag `vX.Y.Z`, publicar a GitHub Release e acionar a publicação das imagens de produção no GHCR.

Tipos de release no pull request:

- `patch`: correcoes compativeis, incrementando `PATCH`.
- `minor`: novas funcionalidades compativeis, incrementando `MINOR`.
- `major`: mudanças incompativeis, incrementando `MAJOR`.
- `sem release`: não cria tag nem GitHub Release. Use em mudanças de documentação, CI ou manutenção sem entrega publicavel.

O versionamento da entrega fica nas tags Git e nas GitHub Releases. O workflow não altera `package.json`, `package-lock.json` nem arquivos de changelog durante a release.

No fluxo atual, a release e do monorepo inteiro. Nao existem releases separadas para backend e frontend.

## Dependabot

O Dependabot abre PRs para `main` nos ecossistemas npm, Gradle e GitHub Actions. Esses PRs são dispensados da issue vinculada, do sync com issue e da marcacao manual de release; ao serem mergeados, recebem uma release `patch` automaticamente.

Limite conhecido: alertas de segurança podem se comportar de forma diferente do agendamento comum do Dependabot, dependendo das configuracoes do GitHub para security updates. Revise qualquer PR de segurança antes do merge.

## Hooks locais

Depois de instalar as dependências da raiz com `npm install`, o Husky configura:

- `pre-commit`: roda `lint-staged` e Secretlint nos arquivos staged.
- `commit-msg`: valida a mensagem do commit com Commitlint.

## Validações locais e CI

Antes de abrir ou atualizar um pull request, execute os checks aplicáveis à alteração:

```bash
npm run secretlint
npm run lint:web
npm run test:github
```

Para o backend, execute a formatação e os testes com Java 21. No Windows:

```powershell
cd backend
.\gradlew.bat spotlessCheck
.\gradlew.bat test
```

Em Linux ou macOS:

```bash
cd backend
./gradlew spotlessCheck
./gradlew test
```

No pull request para `main`, o workflow `Quality` repete essas validações. O workflow `Security scans` executa Semgrep e Trivy, publica relatórios SARIF em Security → Code scanning e bloqueia o merge quando o Trivy encontra vulnerabilidades configuradas como bloqueantes. O Semgrep permanece em baseline até a triagem inicial dos alertas.

## Segredos

Nao commite arquivos `.env`, chaves privadas, certificados, keystores ou tokens reais. Use variáveis de ambiente locais e mantenha apenas arquivos `.env.example` versionados.
