# ADR 0001 - Adotar Monorepo para Backend, Frontend e Mobile

## Status

Accepted

## Contexto

O Inventarium agrupa tres aplicações principais: backend Java/Spring Boot, frontend web Next.js e aplicativo mobile Expo/React Native. Essas partes evoluem em conjunto e compartilham decisões de produto, contratos de API, documentação, configuracoes de qualidade e convencoes de contribuição.

Antes da organização atual, as aplicações viviam como repositórios separados. Isso dificultava a rastreabilidade entre mudanças coordenadas, aumentava o custo de documentar a arquitetura completa e criava mais pontos de manutenção para automações, hooks e guias do projeto.

## Decisao

Adotar um monorepo contendo `backend/`, `frontend/`, `mobile/`, `docs/`, configuracoes compartilhadas e automações do projeto.

## Consequencias

- Mudanças coordenadas entre API, web, mobile e documentação podem ser revisadas em um único pull request.
- A documentação arquitetural consegue referenciar o sistema completo a partir de uma fonte versionada.
- Configuracoes compartilhadas, como Conventional Commits, hooks, Secretlint e Dependabot, ficam centralizadas.
- O repositório fica maior e exige cuidado para que mudanças de uma aplicação não gerem ruido em outra.
- Pipelines e automações precisam filtrar corretamente quais partes do monorepo devem ser validadas em cada mudanca.

## Alternativas Consideradas

- Manter repositórios separados: reduziria o tamanho de cada repositório, mas manteria o custo de coordenar contratos, documentação e releases entre projetos.
- Criar um repositório apenas para documentação: facilitaria centralizar docs, mas separaria decisao arquitetural do código que a implementa.

## Riscos

- Alteracoes amplas podem misturar responsabilidades se a equipe não mantiver escopos claros de PR.
- Dependências e comandos precisam continuar documentados por aplicação para evitar confusao entre stacks.

## Referencias

- `README.md`
- `docs/wiki/C4-Containers.md`
- `docs/wiki/C4-Componentes.md`

## Decisões Relacionadas

- [ADR 0004 - Versionar documentação da wiki no repositório](./0004-versionar-documentação-na-wiki.md)
