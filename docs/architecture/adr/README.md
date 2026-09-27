# Architecture Decision Records

Este diretorio registra Architecture Decision Records (ADRs) do Inventarium.

ADRs documentam decisões arquiteturais relevantes para que a equipe consiga entender o contexto, a decisao tomada, as alternativas consideradas e as consequencias da escolha ao longo da evolucao do projeto.

## Quando criar um ADR

Crie um ADR quando a decisao tiver impacto relevante em arquitetura, infraestrutura, segurança, organização do repositório, persistência, comunicação entre containers ou evolucao técnica do sistema.

Nao e necessário criar ADR para decisões triviais, locais a um arquivo, facilmente reversiveis ou sem impacto arquitetural.

## Convencao de Nomenclatura

Os ADRs devem usar numeracao sequencial com quatro digitos e titulo curto em kebab-case:

```text
0001-adotar-monorepo.md
0002-definir-autenticação-com-google.md
0003-adotar-postgresql-com-liquibase.md
```

O número nunca deve ser reutilizado, mesmo que um ADR seja substituido ou depreciado.

## Status Permitidos

- `Proposed`: decisao em avaliacao.
- `Accepted`: decisao adotada.
- `Superseded`: decisao substituida por outro ADR.
- `Deprecated`: decisao que deixou de ser recomendada, mas não foi substituida diretamente.

Um ADR não deve ser reescrito para esconder uma decisao antiga. Caso a decisao mude, crie um novo ADR explicando o novo contexto e marque o ADR anterior como `Superseded`, apontando para o novo registro.

## ADRs Registrados

| ADR | Titulo | Status |
| --- | --- | --- |
| [0001](./0001-adotar-monorepo.md) | Adotar monorepo para backend, frontend e mobile | Accepted |
| [0002](./0002-definir-autenticação-com-google.md) | Definir autenticação com Google e JWT próprio da aplicação | Accepted |
| [0003](./0003-adotar-postgresql-com-liquibase.md) | Adotar PostgreSQL com Liquibase para persistência relacional | Accepted |
| [0004](./0004-versionar-documentação-na-wiki.md) | Versionar documentação da wiki no repositório | Accepted |
| [0005](./0005-adotar-api-rest-stateless.md) | Adotar API REST stateless com Bearer JWT | Accepted |

## Template

Use [`template.md`](./template.md) como base para novos registros.
