# ADR 0004 - Versionar Documentação da Wiki no Repositório

## Status

Accepted

## Contexto

O projeto precisa manter documentação de visão, requisitos, arquitetura, modelagem de dados, segurança, testes e guias operacionais. Essa documentação deve ser revisada pela equipe e publicada na Wiki do GitHub, mas também precisa ficar historica e rastreavel junto ao código.

Editar a wiki manualmente facilita publicação rápida, mas dificulta revisão por pull request e pode desalinhar documentos da versão real do repositório.

## Decisao

Manter os arquivos fonte da wiki em `docs/wiki` dentro do repositório principal e publicar a wiki a partir desses arquivos. Mudanças relevantes de documentação devem passar por pull request antes do merge em `main`.

## Consequencias

- A documentação passa pelo mesmo fluxo de revisão do código.
- Alteracoes em arquitetura e documentação podem ser versionadas juntas.
- A Wiki do GitHub continua sendo o ponto de leitura, enquanto o repositório permanece como fonte oficial.
- A equipe precisa evitar edicoes manuais diretas na wiki que não voltem para o repositório.

## Alternativas Consideradas

- Editar apenas a Wiki do GitHub: reduziria atrito de escrita, mas perderia rastreabilidade no fluxo normal de PR.
- Manter documentos apenas no repositório, sem wiki: simplificaria a fonte, mas reduziria a navegabilidade para avaliadores e interessados externos.

## Riscos

- Se a publicação automatica falhar, a wiki publicada pode ficar atrasada em relação ao repositório.
- Links internos precisam seguir a convencao da wiki para não quebrar apos publicação.

## Referencias

- `docs/wiki/Home.md`
- `docs/wiki/_Sidebar.md`
- `docs/wiki/C4-Contexto.md`
- `docs/wiki/C4-Containers.md`
- `docs/wiki/C4-Componentes.md`
- `docs/wiki/Documento-de-Modelagem-de-Dados.md`

## Decisões Relacionadas

- [ADR 0001 - Adotar monorepo para backend, frontend e mobile](./0001-adotar-monorepo.md)
