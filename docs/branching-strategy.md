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
- `Validate PR Template`: valida issue vinculada e tipo de release conforme o fluxo.
- `Validate PR Source`: impede PR direto para `main` fora de `development` ou `hotfix/*`.
- `Close Promoted Issues`: fecha issues quando `development` e promovida para `main`.
- `Release`: publica tag e GitHub Release somente em `main`, quando aplicavel.

Depois de criar o workflow `Validate PR Source`, configure manualmente a regra de protecao da `main` para exigir o status check `Validate source branch`.

## Dependabot

O arquivo `.github/dependabot.yml` aponta npm, Gradle e GitHub Actions para `development`. Isso mantem atualizacoes de dependencia no mesmo fluxo das demais tarefas.

Alertas de seguranca podem depender das configuracoes de security updates do GitHub. Se um PR de seguranca aparecer contra `main`, revise o caso e escolha entre ajustar o alvo para `development` ou tratar como `hotfix/*`.
