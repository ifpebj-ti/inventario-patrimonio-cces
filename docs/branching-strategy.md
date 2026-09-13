# Branching Strategy

Este projeto usa `development` como branch de integracao e `main` como branch estavel de entrega.

## Branches

- `main`: codigo publicado ou pronto para producao. Recebe apenas PRs vindos de `development` ou `hotfix/*`.
- `development`: integracao das tarefas do ciclo atual. Recebe PRs de trabalho, Dependabot e sync vindo de `main`.
- `feat/*`, `fix/*`, `docs/*`, `infra/*`, `ci/*`: branches de trabalho abertas contra `development`.
- `hotfix/*`: correcao urgente aberta diretamente contra `main`.

Nao existe branch `qa` neste momento. Quando houver ambiente de homologacao, a branch `development` deve representar esse ambiente. Quando houver producao, a branch `main` deve representar o codigo liberado para producao.

## Destinos de PR

| Origem | Destino | Issue vinculada | Tipo de release | Observacao |
| --- | --- | --- | --- | --- |
| Branch de trabalho | `development` | Obrigatoria, exatamente uma | `sem release` | Merge nao fecha a issue |
| `development` | `main` | Nao usar uma issue unica | `patch`, `minor`, `major` ou `sem release` | Promove o pacote de mudancas |
| `hotfix/*` | `main` | Obrigatoria, exatamente uma | `patch` | Fecha a issue no merge |
| `main` | `development` | Nao usar | `sem release` | Sync manual apos hotfix/release |
| Dependabot | `development` | Dispensada | Dispensado | Automatizacao nao exige template completo |

## Ciclo das issues

Issues de tarefa continuam abertas quando o PR de trabalho e mergeado em `development`. Isso evita tratar uma tarefa como entregue antes da promocao para `main`.

Quando o PR `development` -> `main` e mergeado, o workflow `Close Promoted Issues` percorre os commits promovidos, encontra os PRs mergeados em `development`, extrai as issues vinculadas por `Closes #numero`, `Fixes #numero` ou `Resolves #numero`, e fecha essas issues como concluidas.

PRs do Dependabot sem issue sao ignorados. PR humano promovido sem issue gera erro no workflow depois de processar as issues validas.

## Releases

O workflow `Release` roda apenas em merges para `main` vindos de:

- `development`, para uma release normal do ciclo.
- `hotfix/*`, para uma correcao urgente.

O tipo de release vem somente da opcao marcada no PR que entra em `main`. O workflow nao infere release por commits.

Use:

- `patch`: correcao compativel.
- `minor`: funcionalidade ou entrega compativel.
- `major`: mudanca incompativel.
- `sem release`: merge sem tag e sem GitHub Release.

As notas da release usam a secao `## O que foi feito` do PR de promocao ou hotfix. A lista completa de issues promovidas nao e usada automaticamente nas notas para evitar releases grandes demais e pouco legiveis.

## Sync main -> development

O sync de `main` para `development` e manual de proposito. Apos um hotfix direto em `main`, abra um PR `main` -> `development`, marque `sem release`, e faca o merge para manter a integracao alinhada.

Esse sync nao fecha issue, nao copia descricao de issue e nao publica release.

## Checks

Checks minimos esperados:

- `Quality`: roda em PRs e em push para `main` e `development`.
- `Development container images`: publica imagens de homologacao no GHCR a cada push em `development`.
- `Validate PR Template`: valida issue vinculada e tipo de release conforme o fluxo.
- `Validate PR Source`: impede PR direto para `main` fora de `development` ou `hotfix/*`.
- `Close Promoted Issues`: fecha issues quando `development` e promovida para `main`.
- `Release`: publica tag e GitHub Release somente em `main`, quando aplicavel, e depois publica as imagens produtivas.

Depois de criar o workflow `Validate PR Source`, configure manualmente a regra de protecao da `main` para exigir o status check `Validate source branch`.

## Imagens Docker

As imagens do frontend e do backend sao publicadas no GitHub Container Registry como pacotes separados:

- `ghcr.io/<owner>/<repo>-frontend`
- `ghcr.io/<owner>/<repo>-backend`

O workflow reutilizavel `Build container images` executa, nesta ordem:

1. scan de secrets com Trivy no repositorio;
2. scan de dependencias com Trivy em `frontend` e `backend`;
3. build local das imagens;
4. scan das imagens Docker com Trivy;
5. push para o GHCR somente se todos os scans anteriores passarem.

Os scans de dependencia e de imagem falham o workflow quando encontram vulnerabilidades `HIGH` ou `CRITICAL`. O push das imagens fica em um job separado e depende dos scans das duas imagens; assim, uma falha em qualquer artefato impede a publicacao de todos os artefatos daquele ciclo.

Em `development`, as imagens recebem:

- `latest-dev`, para a VM de homologacao sempre puxar a imagem corrente sem troca manual de tag;
- `sha-<commit-sha>`, para rollback de homologacao.

Em producao, as imagens sao publicadas apenas apos a GitHub Release ser criada pelo workflow `Release`. Como a release ainda e unica para a aplicacao, frontend e backend usam a mesma tag de versao, mas em imagens separadas:

- `ghcr.io/<owner>/<repo>-frontend:vX.Y.Z`
- `ghcr.io/<owner>/<repo>-backend:vX.Y.Z`

As imagens produtivas tambem recebem `sha-<commit-sha>` para rastreabilidade.

Configure as variaveis de repositorio `NEXT_PUBLIC_API_URL_DEV`, `NEXT_PUBLIC_API_URL_PROD` e `NEXT_PUBLIC_GOOGLE_CLIENT_ID` ou `GOOGLE_OAUTH_CLIENT_ID` para preencher os build args do frontend. Se o Google Client ID estiver cadastrado como secret em vez de variavel, o workflow tambem aceita `NEXT_PUBLIC_GOOGLE_CLIENT_ID` ou `GOOGLE_OAUTH_CLIENT_ID` via GitHub Secrets. O build do frontend falha se esses valores nao estiverem configurados, para evitar publicar uma imagem apontando para uma URL incorreta.

## Dependabot

O arquivo `.github/dependabot.yml` aponta npm, Gradle e GitHub Actions para `development`. Isso mantem atualizacoes de dependencia no mesmo fluxo das demais tarefas.

Alertas de seguranca podem depender das configuracoes de security updates do GitHub. Se um PR de seguranca aparecer contra `main`, revise o caso e escolha entre ajustar o alvo para `development` ou tratar como `hotfix/*`.
