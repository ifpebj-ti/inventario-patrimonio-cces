# Architecture Decision Records

Os Architecture Decision Records (ADRs) do Inventarium ficam versionados no repositorio principal em:

```text
docs/architecture/adr/
```

Eles registram decisoes arquiteturais relevantes, incluindo contexto, decisao, consequencias, alternativas consideradas, riscos, referencias e status.

## ADRs Atuais

| ADR | Decisao | Status |
| --- | --- | --- |
| `0001-adotar-monorepo.md` | Adotar monorepo para backend, frontend e mobile. | Accepted |
| `0002-definir-autenticacao-com-google.md` | Definir autenticacao com Google e JWT proprio da aplicacao. | Accepted |
| `0003-adotar-postgresql-com-liquibase.md` | Adotar PostgreSQL com Liquibase para persistencia relacional. | Accepted |
| `0004-versionar-documentacao-na-wiki.md` | Versionar documentacao da wiki no repositorio. | Accepted |
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

Um ADR nao deve ser alterado para esconder uma decisao antiga. Quando uma decisao for substituida, deve ser criado um novo ADR e o anterior deve ser marcado como `Superseded`.

## Fonte

A fonte oficial dos ADRs e o diretorio [`docs/architecture/adr`](https://github.com/ifpebj-ti/inventario-patrimonio-cces/tree/main/docs/architecture/adr).
