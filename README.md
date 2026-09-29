# Inventarium

[![Licença](https://img.shields.io/github/license/ifpebj-ti/inventario-patrimonio-cces?style=flat-square)](LICENSE)
[![Último commit](https://img.shields.io/github/last-commit/ifpebj-ti/inventario-patrimonio-cces?style=flat-square)](https://github.com/ifpebj-ti/inventario-patrimonio-cces/commits/main)
[![Tamanho do repositório](https://img.shields.io/github/repo-size/ifpebj-ti/inventario-patrimonio-cces?style=flat-square)](https://github.com/ifpebj-ti/inventario-patrimonio-cces)
[![Contribuidores](https://img.shields.io/github/contributors/ifpebj-ti/inventario-patrimonio-cces?style=flat-square)](https://github.com/ifpebj-ti/inventario-patrimonio-cces/graphs/contributors)
[![Issues abertas](https://img.shields.io/github/issues/ifpebj-ti/inventario-patrimonio-cces?style=flat-square)](https://github.com/ifpebj-ti/inventario-patrimonio-cces/issues)
[![Pull requests abertos](https://img.shields.io/github/issues-pr/ifpebj-ti/inventario-patrimonio-cces?style=flat-square)](https://github.com/ifpebj-ti/inventario-patrimonio-cces/pulls)
[![Forks](https://img.shields.io/github/forks/ifpebj-ti/inventario-patrimonio-cces?style=flat-square)](https://github.com/ifpebj-ti/inventario-patrimonio-cces/network/members)
[![Stars](https://img.shields.io/github/stars/ifpebj-ti/inventario-patrimonio-cces?style=flat-square)](https://github.com/ifpebj-ti/inventario-patrimonio-cces/stargazers)
[![Quality](https://github.com/ifpebj-ti/inventario-patrimonio-cces/actions/workflows/quality.yml/badge.svg?branch=main)](https://github.com/ifpebj-ti/inventario-patrimonio-cces/actions/workflows/quality.yml)
[![Security scans](https://github.com/ifpebj-ti/inventario-patrimonio-cces/actions/workflows/security-scans.yml/badge.svg?branch=main)](https://github.com/ifpebj-ti/inventario-patrimonio-cces/actions/workflows/security-scans.yml)

Inventarium e um monorepo do projeto academico de gerenciamento de inventário. Ele agrupa a API backend, a aplicação web e a documentação do produto.

## Estrutura

```text
.
├── backend/       # API Java/Spring Boot, banco PostgreSQL e scripts de apoio
├── frontend/      # Aplicação web Next.js
├── docs/          # Documentação do repositório e guias de contribuição
├── infra/         # Espaco reservado para infraestrutura e deploy
├── .github/       # GitHub Actions e Dependabot
├── .husky/        # Hooks locais do Git
└── package.json   # Ferramentas compartilhadas do monorepo
```

## Aplicações

### Backend

Local: `backend`

Stack principal:

- Java 21
- Spring Boot
- Gradle
- PostgreSQL
- Spring Security com JWT
- Login com Google (OAuth 2.0 / OpenID Connect)

Partes importantes:

- `src/main/java/clp/inventory/controller`: endpoints da API.
- `docs/AUTHENTICATION.md`: fluxo de autenticação com Google.
- `docs/GOOGLE_AUTH_SETUP.md`: configuração das variáveis do login com Google.
- `src/main/java/clp/inventory/service`: regras de negocio.
- `src/main/java/clp/inventory/repository`: acesso a dados.
- `src/main/java/clp/inventory/model`: entidades do dominio.
- `src/main/resources/application.properties`: configuração da aplicação.
- `docker-compose.yml`: ambiente local com PostgreSQL, backend e frontend web.
- `.env.example`: exemplo das variáveis de ambiente esperadas.

Para empacotar a API:

```bash
cd backend
./gradlew bootJar -x test
```

No Windows:

```bash
cd backend
gradlew.bat bootJar -x test
```

Para rodar a API fora do Docker:

```bash
cd backend
./gradlew bootRun
```

### Web

Local: `frontend`

Stack principal:

- Next.js
- React
- TypeScript
- Tailwind CSS
- ESLint

Para instalar e rodar:

```bash
cd frontend
npm install
npm run dev
```

A aplicação fica disponível em:

```text
http://localhost:3000
```

## Configuração local

Instale as dependências das aplicações que for usar e também as dependências da raiz:

```bash
npm install
```

O `npm install` na raiz instala as ferramentas compartilhadas do monorepo, como Husky, Commitlint, lint-staged e Secretlint.

Para rodar com Docker Compose, copie o arquivo de exemplo da raiz e defina os valores locais:

```bash
cp .env.example .env
```

Depois, suba a aplicação web completa:

```bash
docker compose up --build
```

Serviços publicados:

```text
Frontend web: http://localhost:3000
Backend API:  http://localhost:8080
PostgreSQL:   localhost:5433
```

O `docker-compose.yml` builda as imagens locais `inventarium-front:local` e `inventarium-back:local`, alem de subir o PostgreSQL. Em ambiente de VM, as variáveis `FRONTEND_IMAGE` e `BACKEND_IMAGE` podem apontar para imagens publicadas no GHCR.

O guia completo de execução, configuração e operação está em:

```text
docs/execução-configuração-operação.md
```

Para rodar apenas o backend fora do Compose, use também o exemplo dentro de `backend` quando necessário.

Arquivos `.env` reais não devem ser commitados.

## Segurança

Este repositório evita expor secrets diretamente no código. Configuracoes sensiveis devem vir de variáveis de ambiente.

Exemplos de variáveis usadas pelo backend:

```text
SPRING_DATASOURCE_URL
SPRING_DATASOURCE_USERNAME
SPRING_DATASOURCE_PASSWORD
SECURITY_TOKEN_SECRET
GOOGLE_OAUTH_CLIENT_ID
GOOGLE_ALLOWED_DOMAINS
```

Antes de cada commit, o Secretlint roda nos arquivos staged para reduzir o risco de commitar tokens, senhas, chaves privadas ou credenciais.

Também e possível rodar manualmente:

```bash
npm run secretlint
```

## Padrao de commits

O projeto usa Conventional Commits.

Formato:

```text
tipo(escopo opcional): mensagem curta
```

Exemplos:

```text
feat(web): add product form
fix(backend): validate inventory quantity
chore(repo): configure dependabot
```

Tipos aceitos:

```text
build, chore, ci, docs, feat, fix, perf, refactor, revert, style, test
```

O hook `commit-msg` valida automaticamente a mensagem do commit.

## Hooks locais

Os hooks ficam em `.husky/`.

Fluxo do commit:

```text
git add .
git commit -m "tipo(escopo): mensagem"
        |
        v
pre-commit roda lint-staged e Secretlint
        |
        v
commit-msg valida Conventional Commits
        |
        v
commit criado
```

## Dependabot

O Dependabot está configurado em `.github/dependabot.yml`.

Ele verifica atualizacoes para:

- `frontend`: dependências npm.
- `backend`: dependências Gradle.
- `/`: GitHub Actions.

Quando encontra uma atualização, o GitHub abre um Pull Request automatico. A pipeline entao roda sobre esse PR para ajudar a validar a mudanca antes do merge.

## CI

Os workflows ficam em `.github/workflows`.

O workflow `Quality` roda em todos os pull requests; `Security scans` roda nos pull requests destinados à `main`.

O workflow `Quality` valida:

- secrets com Secretlint;
- lint do frontend com ESLint;
- formatação Java com Spotless;
- testes existentes do backend com Gradle e Testcontainers;
- mensagens de commit em pull requests humanos com Commitlint.

O workflow `Security scans` executa Semgrep e Trivy antes do merge. O Semgrep e o Trivy geram relatórios SARIF enviados para a aba Security → Code scanning e mantêm artifacts temporários na execução. Durante o pull request, as imagens são apenas construídas e escaneadas em `linux/amd64`; o push para o GHCR só ocorre no workflow `Release`, após o merge em `main`, o gate de segurança e a criação de uma tag de release. A publicação gera uma única tag multi-arquitetura para `linux/amd64` e `linux/arm64`.

Consulte os detalhes, os checks exigidos e os pontos ainda manuais em [Esteira de CI/CD](docs/wiki/Esteira-de-CI-CD.md).

## Documentação

Mais detalhes de contribuição estao em:

```text
docs/contributing.md
```

As decisões arquiteturais relevantes são registradas como ADRs em:

```text
docs/architecture/adr
```

Tags e GitHub Releases seguem o formato `vMAJOR.MINOR.PATCH` e são preparadas pelo workflow `Release`, com base no tipo marcado no pull request. O [CHANGELOG.md](CHANGELOG.md) é atualizado manualmente na própria PR antes do merge, para que sua entrada possa ser revisada junto da entrega. As imagens de produção publicadas no GHCR usam a mesma tag da release e atendem hosts `amd64` e `arm64`.

## Apresentação

Apresentação disponível no [Canva](https://canva.link/1df0uvva66e2vrf)
