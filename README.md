# Inventarium

Inventarium e um monorepo do projeto academico de gerenciamento de inventario. Ele agrupa tres aplicacoes que antes viviam em repositorios separados: backend, frontend web e app mobile.

## Estrutura

```text
.
├── apps/
│   ├── backend/   # API Java/Spring Boot, banco PostgreSQL e scripts de apoio
│   ├── mobile/    # Aplicativo Expo/React Native
│   └── web/       # Aplicacao web Next.js
├── docs/          # Documentacao do repositorio e guias de contribuicao
├── infra/         # Espaco reservado para infraestrutura e deploy
├── .github/       # GitHub Actions e Dependabot
├── .husky/        # Hooks locais do Git
└── package.json   # Ferramentas compartilhadas do monorepo
```

## Aplicacoes

### Backend

Local: `apps/backend`

Stack principal:

- Java 21
- Spring Boot
- Gradle
- PostgreSQL
- Spring Security com JWT

Partes importantes:

- `src/main/java/clp/inventory/controller`: endpoints da API.
- `src/main/java/clp/inventory/service`: regras de negocio.
- `src/main/java/clp/inventory/repository`: acesso a dados.
- `src/main/java/clp/inventory/model`: entidades do dominio.
- `src/main/resources/application.properties`: configuracao da aplicacao.
- `docker-compose.yml`: banco PostgreSQL local.
- `postgres/scripts`: scripts SQL de inicializacao.
- `.env.example`: exemplo das variaveis de ambiente esperadas.

Para rodar o banco local:

```bash
cd apps/backend
docker compose up -d
```

Para rodar a API:

```bash
cd apps/backend
gradlew.bat bootRun
```

No Linux/macOS:

```bash
cd apps/backend
./gradlew bootRun
```

### Web

Local: `apps/web`

Stack principal:

- Next.js
- React
- TypeScript
- Tailwind CSS
- ESLint

Para instalar e rodar:

```bash
cd apps/web
npm install
npm run dev
```

A aplicacao fica disponivel em:

```text
http://localhost:3000
```

### Mobile

Local: `apps/mobile`

Stack principal:

- Expo
- React Native
- TypeScript
- NativeWind
- Axios
- Expo Secure Store

Para instalar e rodar:

```bash
cd apps/mobile
npm install
npm run start
```

Comandos uteis:

```bash
npm run android
npm run ios
npm run web
```

O app mobile aponta para a API em `http://10.0.2.2:8080`, endereco comum para acessar o localhost da maquina host a partir do emulador Android.

## Configuracao local

Instale as dependencias das aplicacoes que for usar e tambem as dependencias da raiz:

```bash
npm install
```

O `npm install` na raiz instala as ferramentas compartilhadas do monorepo, como Husky, Commitlint, lint-staged e Secretlint.

Para o backend, copie o arquivo de exemplo e defina os valores locais:

```bash
cd apps/backend
cp .env.example .env
```

Arquivos `.env` reais nao devem ser commitados.

## Seguranca

Este repositorio evita expor secrets diretamente no codigo. Configuracoes sensiveis devem vir de variaveis de ambiente.

Exemplos de variaveis usadas pelo backend:

```text
SPRING_DATASOURCE_URL
SPRING_DATASOURCE_USERNAME
SPRING_DATASOURCE_PASSWORD
SPRING_MAIL_USERNAME
SPRING_MAIL_PASSWORD
SECURITY_TOKEN_SECRET
```

Antes de cada commit, o Secretlint roda nos arquivos staged para reduzir o risco de commitar tokens, senhas, chaves privadas ou credenciais.

Tambem e possivel rodar manualmente:

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

O Dependabot esta configurado em `.github/dependabot.yml`.

Ele verifica atualizacoes para:

- `apps/web`: dependencias npm.
- `apps/mobile`: dependencias npm.
- `apps/backend`: dependencias Gradle.
- `/`: GitHub Actions.

Quando encontra uma atualizacao, o GitHub abre um Pull Request automatico. A pipeline entao roda sobre esse PR para ajudar a validar a mudanca antes do merge.

## CI

Os workflows ficam em `.github/workflows`.

Atualmente existe o workflow `Quality`, que roda em pull requests e pushes para `main`.

Ele valida:

- secrets com Secretlint;
- mensagens de commit em pull requests com Commitlint.

## Documentacao

Mais detalhes de contribuicao estao em:

```text
docs/contributing.md
```

## Apresentação

Apresentação disponível no [Canva](https://canva.link/1df0uvva66e2vrf)
