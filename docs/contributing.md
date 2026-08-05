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
```

Tipos aceitos: `build`, `chore`, `ci`, `docs`, `feat`, `fix`, `perf`, `refactor`, `revert`, `style` e `test`.

## Hooks locais

Depois de instalar as dependencias da raiz com `npm install`, o Husky configura:

- `pre-commit`: roda `lint-staged` e Secretlint nos arquivos staged.
- `commit-msg`: valida a mensagem do commit com Commitlint.

## Segredos

Nao commite arquivos `.env`, chaves privadas, certificados, keystores ou tokens reais. Use variaveis de ambiente locais e mantenha apenas arquivos `.env.example` versionados.
