# Architecture Decision Records

Os Architecture Decision Records (ADRs) do Inventarium ficam versionados no repositório principal em:

```text
docs/architecture/adr/
```

Eles registram decisões arquiteturais relevantes, incluindo contexto, decisao, consequencias, alternativas consideradas, riscos, referencias e status.

## ADRs Atuais

| ADR | Decisao | Status |
| --- | --- | --- |
| `0001-adotar-monorepo.md` | Adotar monorepo para backend, frontend e mobile. | Accepted |
| `0002-definir-autenticação-com-google.md` | Definir autenticação com Google e JWT próprio da aplicação. | Accepted |
| `0003-adotar-postgresql-com-liquibase.md` | Adotar PostgreSQL com Liquibase para persistência relacional. | Accepted |
| `0004-versionar-documentação-na-wiki.md` | Versionar documentação da wiki no repositório. | Accepted |
| `0005-adotar-api-rest-stateless.md` | Adotar API REST stateless com Bearer JWT. | Accepted |

## Convencao

Novos ADRs devem usar numeracao sequencial com quatro digitos e titulo curto em kebab-case:

```text
0006-nome-da-decisao.md
```

Status permitidos:

- Proposed
- Accepted
- Superseded
- Deprecated

Um ADR não deve ser alterado para esconder uma decisao antiga. Quando uma decisao for substituida, deve ser criado um novo ADR e o anterior deve ser marcado como `Superseded`.

## Fonte

A fonte oficial dos ADRs e o diretorio [`docs/architecture/adr`](https://github.com/ifpebj-ti/inventário-patrimônio-cces/tree/main/docs/architecture/adr).
