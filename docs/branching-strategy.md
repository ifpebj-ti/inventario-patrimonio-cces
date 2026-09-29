# Branching Strategy

Este projeto adota desenvolvimento baseado em trunk: `main` e a única branch de integração e a branch estavel de entrega.

## Branches

- `main`: código integrado, revisado e pronto para produção.
- `feat/*`, `fix/*`, `docs/*`, `infra/*`, `ci/*`: branches curtas para trabalho normal, abertas a partir de `main` e mergeadas de volta nela.
- `hotfix/*`: correcao urgente; também parte de `main` e retorna por pull request para `main`.
- Dependabot: abre pull requests diretamente para `main`.

Não existe branch de integração, `qa` ou ambiente de homologação neste momento. Todo trabalho deve partir da `main` atualizada e ser sincronizado com ela antes do merge. Alterações, inclusive de documentação e CI, não devem ser enviadas diretamente para `main`: use uma branch curta e um pull request.

## Pull requests e issues

Todo PR humano para `main` deve:

1. referenciar exatamente uma issue com `Closes #número`, `Fixes #número` ou `Resolves #número`;
2. preencher o template e marcar exatamente um tipo de release;
3. passar pelos checks e pela revisão exigida nas protecoes da `main`.

Ao fazer merge na branch padrão, o GitHub fecha automaticamente a issue vinculada. Por isso, não ha workflow separado para promover ou fechar issues.

PRs do Dependabot são dispensados da issue e do template. O merge deles gera uma release `patch` automaticamente; atualizacoes potencialmente incompativeis devem ser avaliadas e, se necessário, tratadas em um PR humano com o tipo de release adequado.

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

Antes do merge, a pessoa autora atualiza manualmente o `CHANGELOG.md` na branch da PR, de acordo com o tipo de release e a seção `## O que foi feito`. Depois do merge, o workflow cria a tag `vMAJOR.MINOR.PATCH`, reconstrói as duas imagens a partir dessa tag, executa os scans de segurança novamente e publica a GitHub Release e as imagens produtivas no GHCR. O versionamento é único para todo o monorepo; frontend e backend recebem a mesma versão em imagens separadas.

As imagens construídas durante a PR são descartáveis e servem somente para validação. Elas não são promovidas para produção, pois a imagem publicada deve corresponder exatamente ao commit que recebeu a tag de release em `main`.

## Checks

- `Quality`: valida secrets, lint do frontend, formatação Java com Spotless, testes existentes do backend e mensagens de commit em PRs humanos. Enquanto a dívida de lint é tratada, ESLint e Spotless são informativos; os testes, Secretlint e Commitlint permanecem bloqueantes.
- `Validate PR Template`: valida issue vinculada e tipo de release.
- `Security scans`: executa Semgrep e Trivy em PRs para `main`. O Trivy verifica secrets, dependências e imagens Docker alteradas; o Semgrep realiza análise estática de segurança.
- `Release`: publica tag, GitHub Release e imagens produtivas quando aplicável.

Os relatórios de Semgrep e Trivy são gerados em SARIF, enviados para Security → Code scanning e disponibilizados como artifacts temporários da execução. O Trivy mantém achados de todas as severidades no relatório, mas bloqueia apenas segredos, falhas operacionais e vulnerabilidades `HIGH` ou `CRITICAL`. Na fase inicial, o Semgrep opera como baseline para triagem dos achados; depois da triagem, seu check deve tornar-se bloqueante.

Para evitar gasto desnecessário de runners, lint, testes, Semgrep e Trivy são condicionados aos caminhos alterados. PRs somente de documentação ainda validam template, commits e segredos, mas não constroem imagens nem executam testes que não se aplicam. Jobs condicionais ficam como *skipped* e satisfazem o respectivo check do ruleset.

Proteja `main` exigindo os checks aplicáveis, revisão e conversa resolvida antes do merge. Não configure mais regras ou checks obrigatórios para a antiga branch de integração.

## Imagens Docker

As imagens são publicadas no GitHub Container Registry como pacotes separados:

- `ghcr.io/<owner>/inventarium-front:vX.Y.Z`
- `ghcr.io/<owner>/inventarium-back:vX.Y.Z`

O workflow reutilizável `Build container images` executa scans de secrets, dependências e imagens, faz o build e só publica no GHCR se todos os scans passarem. Em pull requests, `Security scans` chama esse workflow com publicação desabilitada: uma imagem `linux/amd64` é construída apenas no runner para validação. Na release, depois do gate, o Buildx reconstrói e publica um manifesto único com `linux/amd64` e `linux/arm64`; o Docker seleciona automaticamente a variante compatível com a arquitetura do host. Para deploy e rollback, use sempre tags imutáveis de release.

Configure `NEXT_PUBLIC_API_URL` e `NEXT_PUBLIC_GOOGLE_CLIENT_ID` ou `GOOGLE_OAUTH_CLIENT_ID` como variáveis de repositório. O workflow aceita os equivalentes em GitHub Secrets quando necessário.

## Dependabot

O `.github/dependabot.yml` aponta npm, Gradle e GitHub Actions para `main`. Revise cada PR antes do merge, principalmente updates major e atualizacoes de segurança.
