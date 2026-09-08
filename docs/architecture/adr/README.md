# Architecture Decision Records

Este diretorio registra Architecture Decision Records (ADRs) do Inventarium.

ADRs documentam decisoes arquiteturais relevantes para que a equipe consiga entender o contexto, a decisao tomada, as alternativas consideradas e as consequencias da escolha ao longo da evolucao do projeto.

## Quando criar um ADR

Crie um ADR quando a decisao tiver impacto relevante em arquitetura, infraestrutura, seguranca, organizacao do repositorio, persistencia, comunicacao entre containers ou evolucao tecnica do sistema.

Nao e necessario criar ADR para decisoes triviais, locais a um arquivo, facilmente reversiveis ou sem impacto arquitetural.

## Convencao de Nomenclatura

Os ADRs devem usar numeracao sequencial com quatro digitos e titulo curto em kebab-case:

```text
0001-adotar-monorepo.md
0002-definir-autenticacao-com-google.md
0003-adotar-postgresql-com-liquibase.md
```

O numero nunca deve ser reutilizado, mesmo que um ADR seja substituido ou depreciado.

## Status Permitidos

- `Proposed`: decisao em avaliacao.
- `Accepted`: decisao adotada.
- `Superseded`: decisao substituida por outro ADR.
- `Deprecated`: decisao que deixou de ser recomendada, mas nao foi substituida diretamente.

Um ADR nao deve ser reescrito para esconder uma decisao antiga. Caso a decisao mude, crie um novo ADR explicando o novo contexto e marque o ADR anterior como `Superseded`, apontando para o novo registro.

## ADRs Registrados

| ADR | Titulo | Status |
| --- | --- | --- |
| [0001](./0001-adotar-monorepo.md) | Adotar monorepo para backend, frontend e mobile | Accepted |
| [0002](./0002-definir-autenticacao-com-google.md) | Definir autenticacao com Google e JWT proprio da aplicacao | Accepted |
| [0003](./0003-adotar-postgresql-com-liquibase.md) | Adotar PostgreSQL com Liquibase para persistencia relacional | Accepted |
| [0004](./0004-versionar-documentacao-na-wiki.md) | Versionar documentacao da wiki no repositorio | Accepted |
| [0005](./0005-adotar-api-rest-stateless.md) | Adotar API REST stateless com Bearer JWT | Accepted |

## Template

Use [`template.md`](./template.md) como base para novos registros.
