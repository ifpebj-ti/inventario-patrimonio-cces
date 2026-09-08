# ADR 0001 - Adotar Monorepo para Backend, Frontend e Mobile

## Status

Accepted

## Contexto

O Inventarium agrupa tres aplicacoes principais: backend Java/Spring Boot, frontend web Next.js e aplicativo mobile Expo/React Native. Essas partes evoluem em conjunto e compartilham decisoes de produto, contratos de API, documentacao, configuracoes de qualidade e convencoes de contribuicao.

Antes da organizacao atual, as aplicacoes viviam como repositorios separados. Isso dificultava a rastreabilidade entre mudancas coordenadas, aumentava o custo de documentar a arquitetura completa e criava mais pontos de manutencao para automacoes, hooks e guias do projeto.

## Decisao

Adotar um monorepo contendo `backend/`, `frontend/`, `mobile/`, `docs/`, configuracoes compartilhadas e automacoes do projeto.

## Consequencias

- Mudancas coordenadas entre API, web, mobile e documentacao podem ser revisadas em um unico pull request.
- A documentacao arquitetural consegue referenciar o sistema completo a partir de uma fonte versionada.
- Configuracoes compartilhadas, como Conventional Commits, hooks, Secretlint e Dependabot, ficam centralizadas.
- O repositorio fica maior e exige cuidado para que mudancas de uma aplicacao nao gerem ruido em outra.
- Pipelines e automacoes precisam filtrar corretamente quais partes do monorepo devem ser validadas em cada mudanca.

## Alternativas Consideradas

- Manter repositorios separados: reduziria o tamanho de cada repositorio, mas manteria o custo de coordenar contratos, documentacao e releases entre projetos.
- Criar um repositorio apenas para documentacao: facilitaria centralizar docs, mas separaria decisao arquitetural do codigo que a implementa.

## Riscos

- Alteracoes amplas podem misturar responsabilidades se a equipe nao mantiver escopos claros de PR.
- Dependencias e comandos precisam continuar documentados por aplicacao para evitar confusao entre stacks.

## Referencias

- `README.md`
- `docs/wiki/C4-Containers.md`
- `docs/wiki/C4-Componentes.md`

## Decisoes Relacionadas

- [ADR 0004 - Versionar documentacao da wiki no repositorio](./0004-versionar-documentacao-na-wiki.md)
