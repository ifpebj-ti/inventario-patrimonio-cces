# Branching Strategy

Este projeto adota desenvolvimento baseado em trunk: `main` e a unica branch de integracao e a branch estavel de entrega.

## Branches

- `main`: codigo integrado, revisado e pronto para producao.
- `feat/*`, `fix/*`, `docs/*`, `infra/*`, `ci/*`: branches curtas para trabalho normal, abertas a partir de `main` e mergeadas de volta nela.
- `hotfix/*`: correcao urgente; tambem parte de `main` e retorna por pull request para `main`.
- Dependabot: abre pull requests diretamente para `main`.

Nao existe branch de integracao, `qa` ou ambiente de homologacao neste momento. Todo trabalho deve ser atualizado com `main` antes do merge.

## Pull requests e issues

Todo PR humano para `main` deve:

1. referenciar exatamente uma issue com `Closes #numero`, `Fixes #numero` ou `Resolves #numero`;
2. preencher o template e marcar exatamente um tipo de release;
3. passar pelos checks e pela revisao exigida nas protecoes da `main`.

Ao fazer merge na branch padrao, o GitHub fecha automaticamente a issue vinculada. Por isso, nao ha workflow separado para promover ou fechar issues.

PRs do Dependabot sao dispensados da issue e do template. O merge deles gera uma release `patch` automaticamente; atualizacoes potencialmente incompativeis devem ser avaliadas e, se necessario, tratadas em um PR humano com o tipo de release adequado.

| Origem | Destino | Issue vinculada | Tipo de release |
| --- | --- | --- | --- |
| Branch de trabalho | `main` | Obrigatoria, exatamente uma | `patch`, `minor`, `major` ou `sem release` |
| `hotfix/*` | `main` | Obrigatoria, exatamente uma | `patch` |
| Dependabot | `main` | Dispensada | `patch` automatico |

## Releases

O workflow `Release` roda depois de todo PR mergeado em `main`.

- `patch`: correcao compativel.
- `minor`: funcionalidade ou entrega compativel.
- `major`: mudanca incompativel.
- `sem release`: merge sem tag e sem GitHub Release.

O workflow cria a tag `vMAJOR.MINOR.PATCH`, a GitHub Release e as imagens produtivas no GHCR quando houver release. As notas usam a secao `## O que foi feito` do PR. O versionamento e unico para todo o monorepo; frontend e backend recebem a mesma versao em imagens separadas.

## Checks

- `Quality`: valida secrets e mensagens de commit em PRs humanos.
- `Validate PR Template`: valida issue vinculada e tipo de release.
- `Sync PR with Issue`: copia a descricao da issue para PRs humanos.
- `Pull request container images`: builda e escaneia imagens alteradas em PRs para `main`, sem publicar no GHCR.
- `Release`: publica tag, GitHub Release e imagens produtivas quando aplicavel.

Proteja `main` exigindo os checks aplicaveis, revisao e conversa resolvida antes do merge. Nao configure mais regras ou checks obrigatorios para a antiga branch de integracao.

## Imagens Docker

As imagens sao publicadas no GitHub Container Registry como pacotes separados:

- `ghcr.io/<owner>/inventarium-front:vX.Y.Z`
- `ghcr.io/<owner>/inventarium-back:vX.Y.Z`

O workflow reutilizavel `Build container images` executa scan de secrets, dependencias e imagens, faz o build e so publica se todos os scans passarem. Para deploy e rollback, use sempre tags imutaveis de release.

Configure `NEXT_PUBLIC_API_URL` e `NEXT_PUBLIC_GOOGLE_CLIENT_ID` ou `GOOGLE_OAUTH_CLIENT_ID` como variaveis de repositorio. O workflow aceita os equivalentes em GitHub Secrets quando necessario.

## Dependabot

O `.github/dependabot.yml` aponta npm, Gradle e GitHub Actions para `main`. Revise cada PR antes do merge, principalmente updates major e atualizacoes de seguranca.
