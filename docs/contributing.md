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
docs/nome-da-documentacao
infra/nome-da-infraestrutura
ci/nome-da-automacao
```

Mudancas devem ser abertas por pull request e revisadas antes do merge na `main`.

## Versionamento, Tags e Releases

O projeto usa tags no formato `vMAJOR.MINOR.PATCH`, seguindo Semantic Versioning enquanto estiver em evolucao.

Exemplos:

```text
v0.1.0
v0.2.0
v0.2.1
```

- `MAJOR`: mudancas incompativeis ou grandes que quebram compatibilidade.
- `MINOR`: novas funcionalidades ou entregas relevantes sem quebra esperada.
- `PATCH`: correcoes pequenas e ajustes compativeis.

As releases sao preparadas automaticamente pelo workflow `Release`, com base no tipo de release marcado no pull request.

Fluxo esperado:

1. Toda issue deve ser aberta pelo template `Tarefa`, registrando contexto, objetivo, escopo e criterios de aceite.
2. Todo pull request deve referenciar a issue usando `Closes #numero`, `Fixes #numero` ou `Resolves #numero`.
3. Ao abrir ou editar o pull request, o workflow `Sync PR with Issue` copia a descricao da issue vinculada para o corpo do PR.
4. O workflow `Validate PR Template` confere se existe uma issue vinculada e se exatamente um tipo de release foi marcado.
5. Ao fazer merge do pull request na `main`, o workflow `Release` usa o tipo marcado para atualizar `package.json`, `package-lock.json` e `CHANGELOG.md`, criar a tag `vX.Y.Z` e publicar a GitHub Release.

Tipos de release no pull request:

- `patch`: correcoes compativeis, incrementando `PATCH`.
- `minor`: novas funcionalidades compativeis, incrementando `MINOR`.
- `major`: mudancas incompativeis, incrementando `MAJOR`.
- `sem release`: nao cria tag nem GitHub Release.

O commit de versao e feito automaticamente pelo `github-actions[bot]` apos o merge do PR. Se a `main` tiver protecao que bloqueie esse push, ajuste a regra da branch ou adapte o workflow para abrir um pull request interno de release.

No fluxo atual, a release e do monorepo inteiro. Nao existem releases separadas para backend, frontend e mobile.

## Changelog

O arquivo `CHANGELOG.md` registra o historico de releases de forma legivel para pessoas.

Ele deve ser atualizado preferencialmente pelo workflow `Release`. Evite editar releases antigas para esconder decisoes ou mudancas ja publicadas. Caso seja necessario corrigir uma informacao, adicione uma nova entrada ou uma nota de correcao.

## Hooks locais

Depois de instalar as dependencias da raiz com `npm install`, o Husky configura:

- `pre-commit`: roda `lint-staged` e Secretlint nos arquivos staged.
- `commit-msg`: valida a mensagem do commit com Commitlint.

## Segredos

Nao commite arquivos `.env`, chaves privadas, certificados, keystores ou tokens reais. Use variaveis de ambiente locais e mantenha apenas arquivos `.env.example` versionados.
