# ADR 0004 - Versionar Documentacao da Wiki no Repositorio

## Status

Accepted

## Contexto

O projeto precisa manter documentacao de visao, requisitos, arquitetura, modelagem de dados, seguranca, testes e guias operacionais. Essa documentacao deve ser revisada pela equipe e publicada na Wiki do GitHub, mas tambem precisa ficar historica e rastreavel junto ao codigo.

Editar a wiki manualmente facilita publicacao rapida, mas dificulta revisao por pull request e pode desalinhar documentos da versao real do repositorio.

## Decisao

Manter os arquivos fonte da wiki em `docs/wiki` dentro do repositorio principal e publicar a wiki a partir desses arquivos. Mudancas relevantes de documentacao devem passar por pull request antes do merge em `main`.

## Consequencias

- A documentacao passa pelo mesmo fluxo de revisao do codigo.
- Alteracoes em arquitetura e documentacao podem ser versionadas juntas.
- A Wiki do GitHub continua sendo o ponto de leitura, enquanto o repositorio permanece como fonte oficial.
- A equipe precisa evitar edicoes manuais diretas na wiki que nao voltem para o repositorio.

## Alternativas Consideradas

- Editar apenas a Wiki do GitHub: reduziria atrito de escrita, mas perderia rastreabilidade no fluxo normal de PR.
- Manter documentos apenas no repositorio, sem wiki: simplificaria a fonte, mas reduziria a navegabilidade para avaliadores e interessados externos.

## Riscos

- Se a publicacao automatica falhar, a wiki publicada pode ficar atrasada em relacao ao repositorio.
- Links internos precisam seguir a convencao da wiki para nao quebrar apos publicacao.

## Referencias

- `docs/wiki/Home.md`
- `docs/wiki/_Sidebar.md`
- `docs/wiki/C4-Contexto.md`
- `docs/wiki/C4-Containers.md`
- `docs/wiki/C4-Componentes.md`
- `docs/wiki/Documento-de-Modelagem-de-Dados.md`

## Decisoes Relacionadas

- [ADR 0001 - Adotar monorepo para backend, frontend e mobile](./0001-adotar-monorepo.md)
