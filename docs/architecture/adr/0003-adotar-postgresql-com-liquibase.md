# ADR 0003 - Adotar PostgreSQL com Liquibase para Persistencia Relacional

## Status

Accepted

## Contexto

O Inventarium trabalha com dados estruturados de usuarios, inventarios, itens patrimoniais e observacoes. Essas entidades possuem relacionamentos claros, chaves estrangeiras, restricoes de obrigatoriedade e necessidade de rastreabilidade do schema ao longo da evolucao do projeto.

A equipe precisa de um banco relacional que funcione bem em desenvolvimento local com Docker e que permita versionar alteracoes de schema junto ao codigo.

## Decisao

Adotar PostgreSQL como banco relacional principal e Liquibase para versionar a criacao e evolucao do schema.

O schema atual e definido em changelogs XML, com tabelas `im_user`, `im_inventory`, `im_item` e `im_observation`, chaves primarias, chaves estrangeiras, constraints unicas e sequences por entidade.

## Consequencias

- O modelo de dados passa a ter integridade relacional explicita por chaves estrangeiras.
- Alteracoes de schema ficam versionadas no repositorio e podem ser aplicadas de forma reprodutivel.
- O ambiente local pode subir PostgreSQL via Docker Compose.
- A equipe precisa manter as entidades JPA e os changelogs Liquibase consistentes.
- Regras de negocio que ainda estao apenas na aplicacao podem precisar virar constraints no banco se houver maior concorrencia ou novos caminhos de escrita.

## Alternativas Consideradas

- Banco em memoria ou arquivo local: facilitaria testes simples, mas nao atenderia bem ao uso persistente e relacional do dominio.
- Banco NoSQL: reduziria rigidez de schema, mas traria menos valor para um dominio com relacionamentos e cardinalidades bem definidos.
- Geracao automatica de schema pelo Hibernate: aceleraria desenvolvimento inicial, mas reduziria controle e rastreabilidade das migracoes.

## Riscos

- Divergencia entre JPA e Liquibase pode gerar comportamento inesperado em runtime.
- Constraints ausentes no banco podem permitir inconsistencias se houver escrita fora dos servicos atuais.

## Referencias

- `docs/wiki/Documento-de-Modelagem-de-Dados.md`
- `backend/src/main/resources/db/changelog/db.changelog-master.xml`
- `backend/src/main/resources/db/changelog/changes/0.0.xml`
- `docs/wiki/C4-Containers.md`

## Decisoes Relacionadas

- [ADR 0001 - Adotar monorepo para backend, frontend e mobile](./0001-adotar-monorepo.md)
