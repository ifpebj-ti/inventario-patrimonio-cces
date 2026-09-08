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

As releases sao preparadas automaticamente pelo workflow `Release Please`.

Fluxo esperado:

1. Pull requests normais entram na `main` usando Conventional Commits.
2. O workflow `Release Please` analisa os commits da `main`.
3. Quando houver mudancas publicaveis, ele abre ou atualiza um pull request de release.
4. O pull request de release atualiza `CHANGELOG.md` e `.release-please-manifest.json`.
5. Ao fazer merge desse pull request, o workflow cria a tag `vX.Y.Z` e publica a GitHub Release.

No fluxo atual, a release e do monorepo inteiro. Nao existem releases separadas para backend, frontend e mobile.

## Changelog

O arquivo `CHANGELOG.md` registra o historico de releases de forma legivel para pessoas.

Ele deve ser atualizado preferencialmente pelo Release Please. Evite editar releases antigas para esconder decisoes ou mudancas ja publicadas. Caso seja necessario corrigir uma informacao, adicione uma nova entrada ou uma nota de correcao.

## Hooks locais

Depois de instalar as dependencias da raiz com `npm install`, o Husky configura:

- `pre-commit`: roda `lint-staged` e Secretlint nos arquivos staged.
- `commit-msg`: valida a mensagem do commit com Commitlint.

## Segredos

Nao commite arquivos `.env`, chaves privadas, certificados, keystores ou tokens reais. Use variaveis de ambiente locais e mantenha apenas arquivos `.env.example` versionados.
