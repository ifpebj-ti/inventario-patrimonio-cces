# Esteira de CI/CD

Esta pagina mostra a esteira atual do Inventarium e a direcao desejada para evolucao. Ela complementa o guia de desenvolvimento seguro e a estrategia de branches.

## Estado Atual

Hoje a esteira cobre validacao de pull requests, build e scan de imagens, publicacao de imagens no GHCR, criacao de releases e publicacao da wiki.

```mermaid
flowchart TD
    issue["Issue no GitHub"]
    branch["Branch de trabalho<br/>feat/*, fix/*, docs/*, infra/*, ci/*"]
    prdev["PR para development"]
    quality["Quality<br/>Secretlint + Commitlint"]
    prtemplate["Validate PR Template<br/>issue vinculada + tipo de release"]
    syncissue["Sync PR with Issue<br/>copia descricao da issue"]
    primages["Pull request container images<br/>build + Trivy sem push"]
    mergeDev["Merge em development"]
    devImages["Development container images<br/>build + Trivy + push GHCR"]
    ghcrDev["GHCR<br/>latest-dev + sha"]
    prmain["PR development -> main"]
    sourceCheck["Validate PR Source"]
    mergeMain["Merge em main"]
    release["Release<br/>tag + release notes"]
    prodImages["Build production images<br/>GHCR vX.Y.Z + sha"]
    wiki["Publish Wiki<br/>sincroniza docs/wiki"]
    closeIssues["Close Promoted Issues"]

    issue --> branch --> prdev
    prdev --> quality
    prdev --> prtemplate
    prdev --> syncissue
    prdev --> primages
    quality --> mergeDev
    prtemplate --> mergeDev
    primages --> mergeDev
    mergeDev --> devImages --> ghcrDev
    mergeDev --> prmain
    prmain --> sourceCheck
    sourceCheck --> mergeMain
    mergeMain --> release
    mergeMain --> wiki
    release --> prodImages
    release --> closeIssues
```

## O Que Ja Existe

| Parte | Situacao atual |
| --- | --- |
| Validacao de secrets | `Quality` roda Secretlint em pull requests. |
| Validacao de commits | `Quality` roda Commitlint em PRs, exceto Dependabot e promocao `development` -> `main`. |
| Validacao de template | `Validate PR Template` confere issue vinculada e tipo de release esperado. |
| Sincronizacao PR/issue | `Sync PR with Issue` copia a descricao da issue para o PR quando aplicavel. |
| Controle de origem para `main` | `Validate PR Source` aceita apenas `development` ou `hotfix/*`. |
| Build e scan em PR | `Pull request container images` builda e escaneia imagens afetadas sem publicar. |
| Imagens de development | Push em `development` publica imagens `latest-dev` e `sha-<commit-sha>` no GHCR. |
| Release | Merge em `main` vindo de `development` ou `hotfix/*` cria tag e GitHub Release quando o PR nao esta como `sem release`. |
| Imagens produtivas | Release publica imagens versionadas no GHCR. |
| Wiki | Push em `main` com mudanca em `docs/wiki` sincroniza a GitHub Wiki. |
| Fechamento de issues | `Close Promoted Issues` fecha issues promovidas quando `development` entra em `main`. |

## Fluxo de Homologacao Atual

```mermaid
sequenceDiagram
    participant Dev as Desenvolvedor
    participant PR as PR para development
    participant CI as GitHub Actions
    participant DevBranch as development
    participant GHCR as GHCR

    Dev->>PR: Abre PR com issue e sem release
    PR->>CI: Roda quality, template e scans
    CI-->>PR: Checks aprovados
    PR->>DevBranch: Merge
    DevBranch->>CI: Push dispara imagens de development
    CI->>GHCR: Publica latest-dev e sha
```

## Fluxo de Release Atual

```mermaid
sequenceDiagram
    participant DevBranch as development
    participant PRMain as PR development -> main
    participant CI as GitHub Actions
    participant Main as main
    participant GHCR as GHCR
    participant Wiki as GitHub Wiki

    DevBranch->>PRMain: Abre promocao
    PRMain->>CI: Valida origem e tipo de release
    PRMain->>Main: Merge
    Main->>CI: Release e publicacao
    CI->>GHCR: Publica imagens vX.Y.Z e sha
    CI->>Wiki: Sincroniza docs/wiki quando houver mudanca
```

## Direcao Desejada

A esteira ainda nao faz deploy automatico de ambiente, rollback operacional completo nem validacao funcional ponta a ponta. O desenho abaixo mostra a direcao desejada sem dizer que tudo isso ja existe.

```mermaid
flowchart TD
    pr["PR"]
    quality["Quality + testes"]
    scans["Scans de dependencia, secrets e imagens"]
    preview["Ambiente de preview<br/>futuro"]
    devMerge["Merge em development"]
    deployDev["Deploy automatico em homologacao<br/>futuro"]
    smokeDev["Smoke tests em homologacao<br/>futuro"]
    promote["PR development -> main"]
    release["Release versionada"]
    deployProd["Deploy controlado em producao<br/>futuro"]
    monitor["Logs, metricas, alertas e rollback<br/>futuro"]

    pr --> quality --> scans --> preview --> devMerge
    devMerge --> deployDev --> smokeDev --> promote
    promote --> release --> deployProd --> monitor
```

## Pontos Ainda Manuais

| Ponto | Como esta hoje |
| --- | --- |
| Deploy em VM | As imagens sao publicadas no GHCR, mas a atualizacao da stack ainda depende de operacao externa ou manual. |
| Evidencia de ambiente | Ainda nao ha smoke test automatizado contra uma URL de homologacao. |
| Observabilidade | Logs, metricas, alertas e dashboards ainda nao estao fechados como artefato de infraestrutura. |
| Rollback operacional | As tags `sha-<commit-sha>` permitem rollback de imagem, mas o procedimento operacional ainda precisa ser consolidado. |
| Dependabot sem criticos | A evidencia vem da aba Security/Dependabot do GitHub, nao apenas dos arquivos versionados. |

## Historico

| Versao | Data | Descricao |
| --- | --- | --- |
| 1.0 | 2026-09-15 | Criacao da pagina da esteira de CI/CD com visao atual e direcao futura. |
