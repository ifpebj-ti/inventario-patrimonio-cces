# Guia de Boas Praticas de Desenvolvimento Seguro

Este guia define boas praticas de desenvolvimento seguro para o Inventarium. Ele tambem resume o fluxo atual de versionamento, pull requests e releases do projeto.

Para detalhes completos de branches, checks e release, consulte tambem [Branching Strategy](https://github.com/ifpebj-ti/inventario-patrimonio-cces/blob/main/docs/branching-strategy.md) e [Contributing](https://github.com/ifpebj-ti/inventario-patrimonio-cces/blob/main/docs/contributing.md).

## Principios

- Proteger dados patrimoniais, dados de usuarios e credenciais de runtime.
- Garantir que cada usuario acesse apenas os inventarios e itens autorizados.
- Manter mudancas rastreaveis por issue, branch, pull request, commit, tag e release.
- Preferir configuracao segura por padrao, com segredos fora do repositorio.
- Revisar ameacas sempre que a superficie de ataque mudar.

## Autenticacao e Autorizacao

O Inventarium usa login com Google. O frontend recebe o ID token do Google e o backend valida assinatura, emissor, audiencia, expiracao e e-mail verificado antes de criar ou localizar o usuario local. Depois disso, o backend emite um JWT proprio da aplicacao.

Boas praticas esperadas:

- validar tokens Google somente no backend;
- configurar `GOOGLE_OAUTH_CLIENT_ID` corretamente para o ambiente;
- restringir dominios autorizados com `GOOGLE_ALLOWED_DOMAINS`;
- nunca confiar apenas em dados enviados pelo cliente para identificar usuario ou permissao;
- validar ownership em toda leitura, escrita, exportacao, geracao de PDF e envio de e-mail;
- adicionar testes negativos para BOLA/IDOR sempre que houver endpoint por `id`;
- evitar mensagens de erro que permitam enumerar inventarios, itens ou usuarios.

## Segredos e Configuracao

Segredos reais nao devem ser versionados.

Nao commitar:

- `.env` com valores reais;
- chaves privadas;
- tokens pessoais;
- senhas de banco;
- senhas de app SMTP;
- certificados e keystores privados.

Manter versionados apenas arquivos de exemplo, como `.env.example`, sem segredo real.

Variaveis sensiveis de runtime, como `SECURITY_TOKEN_SECRET`, `POSTGRES_PASSWORD` e `SPRING_MAIL_PASSWORD`, devem ficar em mecanismo de secrets, variaveis protegidas da VM, Portainer ou configuracao segura equivalente.

Variaveis `NEXT_PUBLIC_*` sao embutidas no bundle do navegador quando usadas pelo Next.js. Portanto, nunca coloque segredo real em variaveis publicas do frontend.

## Backend e API

Boas praticas para o backend Spring Boot:

- validar entradas antes de persistir ou processar arquivos;
- aplicar autorizacao por usuario, inventario e item em todos os servicos;
- evitar regras criticas apenas no frontend;
- retornar erros consistentes e sem detalhes internos sensiveis;
- limitar tamanho, quantidade de linhas, colunas e tempo de processamento de planilhas;
- tratar risco de formula injection em exportacoes XLSX;
- manter `SPRING_JPA_HIBERNATE_DDL_AUTO=none` e usar Liquibase para evolucao do banco;
- criar migrations pequenas, revisaveis e reversiveis quando possivel;
- evitar logs com tokens, senhas, e-mails desnecessarios ou dados patrimoniais sensiveis.

## Frontend e Mobile

Boas praticas para clientes web e mobile:

- nao armazenar segredos no codigo cliente;
- tratar JWT como credencial sensivel;
- reduzir impacto de XSS com validacao, escaping e politicas de seguranca quando aplicavel;
- manter chamadas autenticadas centralizadas nos servicos de API;
- nao montar URLs sensiveis a partir de entrada nao validada;
- exibir mensagens de erro claras para o usuario, mas sem detalhes internos da API;
- manter tipos TypeScript alinhados aos contratos esperados do backend.

## Banco de Dados

Boas praticas para persistencia:

- evoluir schema por Liquibase;
- preferir constraints no banco para invariantes criticas;
- revisar indices e unicidades quando regras de negocio passarem a depender de concorrencia;
- usar usuario de banco com privilegios minimos necessarios;
- nao expor PostgreSQL publicamente em producao;
- proteger backups e volumes;
- documentar rotina de backup e restauracao antes de uso produtivo.

## Dependencias e Supply Chain

O projeto usa Dependabot para npm, Gradle e GitHub Actions com destino para `development`.

Boas praticas:

- revisar PRs do Dependabot antes do merge;
- observar changelogs quando houver major version;
- rodar testes e checks relevantes;
- corrigir vulnerabilidades criticas antes de promover release;
- evitar bibliotecas sem manutencao ou com risco conhecido;
- manter imagens Docker rastreaveis por tag de versao.

Os workflows de imagens usam Trivy para scan de secrets, dependencias e imagens. Vulnerabilidades `HIGH` ou `CRITICAL` em dependencias ou imagens devem falhar a pipeline conforme configuracao atual.

## Containers e Operacao

Boas praticas para execucao e operacao:

- usar HTTPS em producao;
- nao expor o banco diretamente para a internet;
- manter secrets de runtime fora da imagem Docker;
- publicar imagens no GHCR apenas depois de build e scan na release;
- usar tags versionadas para producao;
- usar tags versionadas anteriores para rollback;
- acompanhar logs sem registrar segredos.

O guia operacional completo esta em [Guia de Execucao, Configuracao e Operacao](./Guia-de-Execucao-Configuracao-e-Operacao).

## Checks Esperados

Antes de abrir ou atualizar um pull request, execute os checks aplicaveis:

```bash
npm run secretlint
npm run lint:web
npm run lint:mobile
npm run test:github
```

Para backend, no Windows:

```powershell
cd backend
.\gradlew.bat test
```

Para backend em Linux/macOS:

```bash
cd backend
./gradlew test
```

Nem todo PR altera todas as partes do monorepo. Rode pelo menos os checks relacionados aos arquivos alterados e registre as evidencias no PR.

## Modelagem de Ameacas

A modelagem STRIDE fica em [Modelagem de Ameacas](./Modelagem-de-Ameacas).

Atualize a modelagem quando houver:

- novo endpoint autenticado;
- mudanca em autenticacao, token ou sessao;
- novo fluxo de arquivo, PDF, planilha ou e-mail;
- alteracao em permissao, ownership ou papel de usuario;
- mudanca relevante de infraestrutura, banco, backup ou rede.

## Fluxo Atual de Versionamento

Resumo do fluxo:

| Origem | Destino | Uso | Release |
| --- | --- | --- | --- |
| `feat/*`, `fix/*`, `docs/*`, `infra/*`, `ci/*` | `development` | Trabalho normal | Marcar `sem release` |
| Dependabot | `development` | Atualizacao automatizada | Dispensado |
| `development` | `main` | Promocao de entrega | Marcar `patch`, `minor`, `major` ou `sem release` |
| `hotfix/*` | `main` | Correcao urgente em producao | Marcar `patch` |
| `main` | `development` | Sync apos hotfix/release | Marcar `sem release` |

Fluxo de trabalho esperado:

1. Criar uma issue com contexto, objetivo, escopo e criterios de aceite.
2. Atualizar a `development` local com o estado remoto.
3. Criar uma branch curta a partir de `development`, por exemplo `docs/guia-seguranca`.
4. Implementar a mudanca e commitar usando Conventional Commits.
5. Abrir PR para `development`, vinculando exatamente uma issue com `Closes #numero`, `Fixes #numero` ou `Resolves #numero`.
6. Marcar `sem release` no PR de trabalho.
7. Aguardar checks, revisao e merge em `development`.

O merge em `development` nao fecha a issue. A issue e fechada quando a `development` e promovida para `main` e o workflow `Close Promoted Issues` encontra os PRs promovidos.

## Como Chegar em uma Release

Uma release normal acontece por promocao de `development` para `main`:

1. Abrir PR `development` -> `main`.
2. Nao vincular uma unica issue nesse PR, porque ele promove um pacote de mudancas.
3. Marcar exatamente um tipo de release:
   - `patch`: correcao compativel;
   - `minor`: funcionalidade ou entrega compativel;
   - `major`: mudanca incompativel;
   - `sem release`: promocao sem tag nem GitHub Release.
4. Descrever a entrega na secao `## O que foi feito`.
5. Fazer merge apos checks e revisao.

Quando o PR entra em `main`, o workflow `Release` prepara a tag `vMAJOR.MINOR.PATCH`, publica a GitHub Release e aciona a publicacao das imagens produtivas no GHCR, quando o PR nao esta marcado como `sem release`.

O versionamento atual e do monorepo inteiro. Nao existem releases separadas para backend, frontend e mobile.

## Hotfix

Use `hotfix/*` somente para correcao urgente que precisa entrar direto em `main`.

Regras:

- abrir branch `hotfix/*`;
- vincular exatamente uma issue;
- abrir PR para `main`;
- marcar `patch`;
- apos merge, sincronizar `main` de volta para `development` com PR `main` -> `development` marcado como `sem release`.

## Checklist de Revisao Segura

Antes do merge, revise:

- A mudanca acessa dados por `id`? Validou ownership?
- A mudanca processa arquivo? Ha limites e tratamento de erro?
- A mudanca exporta planilha? Ha protecao contra formula injection?
- A mudanca envia e-mail? O destinatario e autorizado?
- A mudanca adiciona variavel? Ela e publica ou segredo de runtime?
- A mudanca altera Docker ou CI? Os scans continuam rodando?
- A mudanca altera autenticacao, permissao ou dados sensiveis? A modelagem de ameacas foi revisada?
- A documentacao da wiki precisa ser atualizada?

## Historico

| Versao | Data | Descricao |
| --- | --- | --- |
| 1.0 | 2026-09-15 | Criacao do guia de desenvolvimento seguro com resumo do fluxo de versionamento e release. |
