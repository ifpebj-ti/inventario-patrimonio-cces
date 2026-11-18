# ADR 0010 - Gates bloqueantes de Semgrep e de cobertura de testes na CI

## Status

Accepted.

## Contexto

Dois controles de qualidade existiam na esteira de PR sem bloquear nada:

- O Semgrep rodava com `continue-on-error: true`, sem `--error`, e o job não era check obrigatório. Em 100 execuções ele nunca reprovou um PR. A última tinha 51 achados, todos em `.github` (2 de severidade ERROR, 44 actions sem pin de SHA e 5 outros avisos), nenhum em `backend/src` ou `frontend/src`.
- O `backend/build.gradle` já definia `jacocoTestCoverageVerification` (mínimo de 90% de linhas), mas a CI executava só `./gradlew test`. O gate nunca rodava na esteira. A cobertura medida era de 93,4% das linhas.

## Decisao

1. **Semgrep bloqueante.** O job `Semgrep SAST` roda com `--error` e sem `continue-on-error`. Qualquer achado do conjunto `p/default`, de qualquer severidade, reprova o job. O job roda em todo PR, sem filtro de caminho, porque um check obrigatório que é pulado nunca reporta status. A imagem do Semgrep é fixada por versão e digest. Os achados existentes foram corrigidos antes de ligar o bloqueio (actions fixadas por SHA, `secrets` explícitos no `Release`, `cooldown` no Dependabot, scripts ajustados). Falsos positivos comprovados só podem ser suprimidos com `nosemgrep` e uma justificativa escrita na linha acima.
2. **Cobertura bloqueante.** O job `Backend format and tests` executa `./gradlew test jacocoTestCoverageVerification`. O piso do gate é de **80% de linhas** (reduzido dos 90% que estavam no build). O relatório do JaCoCo é publicado como artefato `backend-coverage-report`.
3. **Obrigatoriedade no ruleset.** `Backend format and tests` já é check obrigatório do ruleset `Protect main`. `Security scans / Semgrep SAST` precisa ser adicionado ao ruleset como passo manual depois do primeiro PR verde.

## Alternativas consideradas

- Bloquear o Semgrep só em severidade ERROR primeiro: descartada, porque os achados restantes eram todos de higiene de CI e simples de corrigir de uma vez.
- Manter o piso de cobertura em 90%: seria mais rígido e já passa com a cobertura atual, mas a decisão do time foi 80%.
- Manter o job do Semgrep condicionado por caminho: descartada, porque o check obrigatório ficaria pendente em PRs que não alteram código analisado.

## Consequencias

- Um achado novo do Semgrep ou uma queda da cobertura abaixo de 80% passa a impedir o merge.
- O piso de 80% deixa uma folga de cerca de 13 pontos sobre a cobertura medida (93,4%) e fica abaixo da meta de cobertura da Unidade 2 (acima de 90%). O gate evita regressões grandes, mas não garante a meta; subir o piso de volta é uma mudança de uma linha no `build.gradle`.
- Atualizar a versão do Semgrep passa a ser uma ação explícita (nova versão e digest na imagem). Regras novas do registro podem gerar achados novos e reprovar PRs sem mudança de código.
- O job foi renomeado de `Semgrep SAST baseline` para `Semgrep SAST`; o nome do check obrigatório no ruleset deve seguir o novo nome.
