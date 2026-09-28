# Guia de Boas Praticas de Desenvolvimento Seguro

Este guia define boas praticas de desenvolvimento seguro para o Inventarium. Ele também resume o fluxo atual de versionamento, pull requests e releases do projeto.

Para detalhes completos de branches, checks e release, consulte também [Branching Strategy](https://github.com/ifpebj-ti/inventário-patrimônio-cces/blob/main/docs/branching-strategy.md) e [Contributing](https://github.com/ifpebj-ti/inventário-patrimônio-cces/blob/main/docs/contributing.md).

## Principios

- Proteger dados patrimoniais, dados de usuários e credenciais de runtime.
- Garantir que cada usuário acesse apenas os inventários e itens autorizados.
- Manter mudanças rastreaveis por issue, branch, pull request, commit, tag e release.
- Preferir configuração segura por padrão, com segredos fora do repositório.
- Revisar ameaças sempre que a superficie de ataque mudar.

## Autenticação e Autorização

O Inventarium usa login com Google. O frontend recebe o ID token do Google e o backend valida assinatura, emissor, audiencia, expiração e e-mail verificado antes de criar ou localizar o usuário local. Depois disso, o backend emite um JWT próprio da aplicação.

Boas praticas esperadas:

- validar tokens Google somente no backend;
- configurar `GOOGLE_OAUTH_CLIENT_ID` corretamente para o ambiente;
- restringir dominios autorizados com `GOOGLE_ALLOWED_DOMAINS`;
- nunca confiar apenas em dados enviados pelo cliente para identificar usuário ou permissão;
- validar ownership em toda leitura, escrita, exportação, geração de PDF e envio de e-mail;
- adicionar testes negativos para BOLA/IDOR sempre que houver endpoint por `id`;
- evitar mensagens de erro que permitam enumerar inventários, itens ou usuários.

## Segredos e Configuração

Segredos reais não devem ser versionados.

Nao commitar:

- `.env` com valores reais;
- chaves privadas;
- tokens pessoais;
- senhas de banco;
- senhas de app SMTP;
- certificados e keystores privados.

Manter versionados apenas arquivos de exemplo, como `.env.example`, sem segredo real.

Variáveis sensiveis de runtime, como `SECURITY_TOKEN_SECRET`, `POSTGRES_PASSWORD` e `SPRING_MAIL_PASSWORD`, devem ficar em mecanismo de secrets, variáveis protegidas da VM, Portainer ou configuração segura equivalente.

Variáveis `NEXT_PUBLIC_*` são embutidas no bundle do navegador quando usadas pelo Next.js. Portanto, nunca coloque segredo real em variáveis públicas do frontend.

## Backend e API

Boas praticas para o backend Spring Boot:

- validar entradas antes de persistir ou processar arquivos;
- aplicar autorização por usuário, inventário e item em todos os serviços;
- evitar regras críticas apenas no frontend;
- retornar erros consistentes e sem detalhes internos sensiveis;
- limitar tamanho, quantidade de linhas, colunas e tempo de processamento de planilhas;
- tratar risco de formula injection em exportacoes XLSX;
- manter `SPRING_JPA_HIBERNATE_DDL_AUTO=none` e usar Liquibase para evolucao do banco;
- criar migrations pequenas, revisaveis e reversiveis quando possível;
- evitar logs com tokens, senhas, e-mails desnecessarios ou dados patrimoniais sensiveis.

## Frontend

Boas praticas para o cliente web:

- não armazenar segredos no código cliente;
- tratar JWT como credencial sensivel;
- reduzir impacto de XSS com validação, escaping e politicas de segurança quando aplicável;
- manter chamadas autenticadas centralizadas nos serviços de API;
- não montar URLs sensiveis a partir de entrada não validada;
- exibir mensagens de erro claras para o usuário, mas sem detalhes internos da API;
- manter tipos TypeScript alinhados aos contratos esperados do backend.

## SAST no Ambiente de Desenvolvimento

Use ferramentas de análise estática de segurança (SAST) integradas à IDE desde o início do desenvolvimento. Elas ajudam a identificar padrões inseguros antes do pull request, como segredos expostos, injeções, uso inseguro de APIs e falhas de validação.

- habilite extensões ou plugins SAST compatíveis com Java/Spring e TypeScript/React;
- mantenha as regras atualizadas e corrija alertas confirmados antes de abrir o PR;
- trate alertas como evidência de revisão, mas não substitua testes, revisão humana, Secretlint, Trivy ou a validação de autorização no backend;
- não envie código, segredos ou dados de produção para serviços externos sem aprovação da equipe.

## Banco de Dados

Boas praticas para persistência:

- evoluir schema por Liquibase;
- preferir constraints no banco para invariantes críticas;
- revisar indices e unicidades quando regras de negocio passarem a depender de concorrência;
- usar usuário de banco com privilegios minimos necessários;
- não expor PostgreSQL publicamente em produção;
- proteger backups e volumes;
- documentar rotina de backup e restauração antes de uso produtivo.

## Dependências e Supply Chain

O projeto usa Dependabot para npm, Gradle e GitHub Actions com destino para `main`.

Boas praticas:

- revisar PRs do Dependabot antes do merge;
- observar changelogs quando houver major version;
- rodar testes e checks relevantes;
- corrigir vulnerabilidades críticas antes de fazer merge;
- evitar bibliotecas sem manutenção ou com risco conhecido;
- manter imagens Docker rastreaveis por tag de versão.

O workflow `Security scans` usa Semgrep para SAST e Trivy para scan de secrets, dependências e imagens. Os dois produzem relatórios SARIF enviados para Security → Code scanning; os arquivos também ficam como artifacts temporários da execução para consulta detalhada.

Vulnerabilidades `HIGH` ou `CRITICAL` detectadas pelo Trivy, ou falhas de execução do scan, devem bloquear a pipeline. O Semgrep está inicialmente em baseline para permitir a triagem dos achados existentes; depois dessa triagem, ele também deve bloquear pull requests com achados confirmados.

Não versione relatórios brutos de scan na `main`: eles são evidências de execução, podem ser volumosos e podem expor detalhes sensíveis. Use a aba Code Scanning e os artifacts da execução; uma branch de auditoria separada só deve ser criada se houver exigência formal de retenção versionada.

## Containers e Operação

Boas praticas para execução e operação:

- usar HTTPS em produção;
- não expor o banco diretamente para a internet;
- manter secrets de runtime fora da imagem Docker;
- publicar imagens no GHCR apenas depois de build e scan na release;
- usar tags versionadas para produção;
- usar tags versionadas anteriores para rollback;
- acompanhar logs sem registrar segredos.

O guia operacional completo está em [Guia de Execução, Configuração e Operação](./Guia-de-Execução-Configuração-e-Operação).

## Checks Esperados

Antes de abrir ou atualizar um pull request, execute os checks aplicáveis:

```bash
npm run secretlint
npm run lint:web
npm run test:github
```

Para verificar a formatação Java antes do pull request:

```bash
cd backend
./gradlew spotlessCheck
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

Nem todo PR altera todas as partes do monorepo. Rode pelo menos os checks relacionados aos arquivos alterados e registre as evidências no PR.

## Modelagem de Ameaças

A modelagem STRIDE fica em [Modelagem de Ameaças](./Modelagem-de-Ameaças).

Atualize a modelagem quando houver:

- novo endpoint autenticado;
- mudanca em autenticação, token ou sessão;
- novo fluxo de arquivo, PDF, planilha ou e-mail;
- alteracao em permissão, ownership ou papel de usuário;
- mudanca relevante de infraestrutura, banco, backup ou rede.

## Fluxo Atual de Versionamento

Resumo do fluxo:

| Origem | Destino | Uso | Release |
| --- | --- | --- | --- |
| `feat/*`, `fix/*`, `docs/*`, `infra/*`, `ci/*` | `main` | Trabalho normal | Marcar `patch`, `minor`, `major` ou `sem release` |
| Dependabot | `main` | Atualização automatizada | `patch` automatico |
| `hotfix/*` | `main` | Correcao urgente em produção | Marcar `patch` |

Fluxo de trabalho esperado:

1. Criar uma issue com contexto, objetivo, escopo e critérios de aceite.
2. Atualizar a `main` local com o estado remoto.
3. Criar uma branch curta a partir de `main`, por exemplo `docs/guia-segurança`.
4. Implementar a mudanca e commitar usando Conventional Commits.
5. Abrir PR para `main`, vinculando exatamente uma issue com `Closes #número`, `Fixes #número` ou `Resolves #número`.
6. Marcar exatamente um tipo de release no PR.
7. Aguardar checks, revisão e merge em `main`.

O merge em `main` fecha automaticamente a issue vinculada pelo GitHub.

## Como Chegar em uma Release

Uma release normal acontece quando um PR humano entra em `main`:

1. Abrir PR de uma branch de trabalho para `main`, com uma issue vinculada.
2. Marcar exatamente um tipo de release:
   - `patch`: correcao compativel;
   - `minor`: funcionalidade ou entrega compativel;
   - `major`: mudanca incompativel;
   - `sem release`: merge sem tag nem GitHub Release.
3. Descrever a entrega na seção `## O que foi feito`.
4. Fazer merge apos checks e revisão.

Quando o PR entra em `main`, o workflow `Release` prepara a tag `vMAJOR.MINOR.PATCH`, pública a GitHub Release e aciona a publicação das imagens produtivas no GHCR, quando o PR não está marcado como `sem release`.

O versionamento atual e do monorepo inteiro. Nao existem releases separadas para backend e frontend.

## Hotfix

Use `hotfix/*` somente para correcao urgente que precisa entrar direto em `main`.

Regras:

- abrir branch `hotfix/*`;
- vincular exatamente uma issue;
- abrir PR para `main`;
- marcar `patch`;

## Checklist de Revisão Segura

Antes do merge, revise:

- A mudanca acessa dados por `id`? Validou ownership?
- A mudanca processa arquivo? Ha limites e tratamento de erro?
- A mudanca exporta planilha? Ha proteção contra formula injection?
- A mudanca envia e-mail? O destinatario e autorizado?
- A mudanca adiciona variavel? Ela e pública ou segredo de runtime?
- A mudanca altera Docker ou CI? Os scans continuam rodando?
- A mudanca altera autenticação, permissão ou dados sensiveis? A modelagem de ameaças foi revisada?
- A documentação da wiki precisa ser atualizada?

## Histórico

| Versão | Data | Descrição |
| --- | --- | --- |
| 1.0 | 2026-09-15 | Criação do guia de desenvolvimento seguro com resumo do fluxo de versionamento e release. |
