# Guia de Execucao, Configuracao e Operacao

Este guia descreve como preparar, executar e operar o Inventarium em ambiente local. Ele tambem registra as diferencas esperadas para um deploy em VM, onde as imagens devem ser baixadas de um registry como o GHCR.

## Visao Geral

O ambiente local com Docker Compose sobe tres servicos:

```text
postgres           Banco PostgreSQL
inventarium-back   API Java/Spring Boot
inventarium-front  Aplicacao web Next.js
```

O aplicativo mobile nao faz parte do Compose de runtime. Durante o desenvolvimento, ele continua sendo executado pelo Expo.

## Pre-requisitos

- Docker Desktop ou Docker Engine com Docker Compose v2.
- Git.
- Node.js e npm, caso deseje rodar frontend, mobile ou ferramentas da raiz fora do Docker.
- JDK compativel com Gradle, caso deseje rodar o backend fora do Docker.

Para conferir Docker e Compose:

```bash
docker --version
docker compose version
```

## Configuracao

Copie o arquivo de exemplo da raiz:

```bash
cp .env.example .env
```

No PowerShell:

```powershell
Copy-Item .env.example .env
```

Preencha o `.env` antes de subir a aplicacao.

## Variaveis de Ambiente

| Variavel | Obrigatoria | Uso | Observacao |
| --- | --- | --- | --- |
| `SECURITY_TOKEN_SECRET` | Sim | Backend | Segredo usado para assinar os JWTs da aplicacao. Gere um valor forte. |
| `POSTGRES_PORT` | Nao | Compose | Porta publicada no host. Padrao: `5433`. |
| `BACKEND_PORT` | Nao | Compose | Porta publicada da API. Padrao: `8080`. |
| `FRONTEND_PORT` | Nao | Compose | Porta publicada da web. Padrao: `3000`. |
| `POSTGRES_USER` | Nao | Postgres/backend | Usuario do banco. Padrao: `admin`. |
| `POSTGRES_PASSWORD` | Nao | Postgres/backend | Senha do banco local. Padrao: `admin`. |
| `POSTGRES_DB` | Nao | Postgres/backend | Nome do banco. Padrao: `inventory_management`. |
| `BACKEND_IMAGE` | Nao | Compose | Nome/tag da imagem do backend. Local: `inventarium-back:local`. |
| `FRONTEND_IMAGE` | Nao | Compose | Nome/tag da imagem do frontend. Local: `inventarium-front:local`. |
| `GOOGLE_OAUTH_CLIENT_ID` | Sim | Backend/frontend | Client ID publico do Google OAuth. Precisa ser o mesmo nos dois lados. |
| `NEXT_PUBLIC_API_URL` | Nao | Frontend | URL publica que o navegador usa para chamar a API. Local: `http://localhost:8080`. |
| `GOOGLE_ALLOWED_DOMAINS` | Nao | Backend | Dominios permitidos no login Google. Padrao: `ifpe.edu.br`. |
| `SPRING_MAIL_USERNAME` | Nao | Backend | Conta SMTP usada para envio de emails. |
| `SPRING_MAIL_PASSWORD` | Nao | Backend | Senha de app do Gmail ou credencial SMTP equivalente. |

Variaveis sensiveis nao devem ser commitadas. Em producao, mantenha `SECURITY_TOKEN_SECRET`, `POSTGRES_PASSWORD` e `SPRING_MAIL_PASSWORD` em mecanismo seguro de secrets ou no arquivo `.env` protegido da VM.

Exemplo para gerar `SECURITY_TOKEN_SECRET`:

```bash
openssl rand -hex 32
```

## Execucao Local Com Docker Compose

Na raiz do repositorio, execute:

```bash
docker compose up --build
```

Para rodar em segundo plano:

```bash
docker compose up --build -d
```

O Compose ira:

```text
1. Subir o Postgres.
2. Aguardar o healthcheck do Postgres.
3. Buildar e subir o backend.
4. Aguardar o healthcheck de liveness do backend.
5. Buildar e subir o frontend web.
```

Servicos publicados:

```text
Frontend web: http://localhost:3000
Backend API:  http://localhost:8080
PostgreSQL:   localhost:5433
```

Se `POSTGRES_PORT` estiver definido como `5432`, o banco sera publicado em `localhost:5432`. Evite isso se ja existir outro Postgres rodando na maquina.

## Validacao

Confira os containers:

```bash
docker compose ps
```

Estado esperado:

```text
inventarium-postgres   healthy
inventarium-back       healthy
inventarium-front      running
```

Teste o backend:

```bash
curl http://localhost:8080/actuator/health/liveness
```

Resposta esperada:

```json
{"status":"UP"}
```

Teste o banco pelo container:

```bash
docker compose exec postgres pg_isready -U admin -d inventory_management
```

Resposta esperada:

```text
accepting connections
```

Teste o frontend no navegador:

```text
http://localhost:3000
```

## Acesso Ao Banco Pelo DBeaver

Use o driver PostgreSQL e os dados do `.env`.

Com os valores padrao:

```text
Host: 127.0.0.1
Port: 5433
Database: inventory_management
Username: admin
Password: admin
```

String JDBC equivalente:

```text
jdbc:postgresql://127.0.0.1:5433/inventory_management
```

Dentro da rede Docker, o backend usa `postgres:5432`. Fora do Docker, ferramentas como DBeaver usam a porta publicada no host, por exemplo `127.0.0.1:5433`.

## Logs

Todos os servicos:

```bash
docker compose logs -f
```

Somente backend:

```bash
docker compose logs -f inventarium-back
```

Somente frontend:

```bash
docker compose logs -f inventarium-front
```

Somente Postgres:

```bash
docker compose logs -f postgres
```

## Reinicio e Parada

Recriar apenas o backend apos mudar variaveis de runtime:

```bash
docker compose up -d --force-recreate inventarium-back
```

Rebuildar o frontend apos mudar `GOOGLE_OAUTH_CLIENT_ID` ou `NEXT_PUBLIC_API_URL`:

```bash
docker compose build inventarium-front
docker compose up -d inventarium-front
```

Parar os containers sem apagar dados:

```bash
docker compose down
```

Parar os containers e apagar o volume do banco local:

```bash
docker compose down -v
```

Use `down -v` com cuidado, pois ele remove os dados persistidos no volume `postgres-data`.

## Execucao Local Sem Docker

Para rodar o backend fora do Docker, mantenha um Postgres acessivel e configure as variaveis de ambiente esperadas.

No Windows:

```powershell
cd backend
.\gradlew.bat bootRun
```

No Linux/macOS:

```bash
cd backend
./gradlew bootRun
```

Para empacotar a API sem executar testes:

```bash
cd backend
./gradlew bootJar -x test
```

No Windows:

```powershell
cd backend
.\gradlew.bat bootJar -x test
```

Para rodar o frontend fora do Docker:

```bash
cd frontend
npm install
npm run dev
```

Crie `frontend/.env.local` quando for rodar o frontend localmente sem Compose:

```env
NEXT_PUBLIC_GOOGLE_CLIENT_ID=seu-client-id.apps.googleusercontent.com
NEXT_PUBLIC_API_URL=http://localhost:8080
```

Para rodar o mobile:

```bash
cd mobile
npm install
npm run start
```

## Troubleshooting

### Variavel obrigatoria ausente

Erro:

```text
required variable SECURITY_TOKEN_SECRET is missing a value
```

Verifique se o `.env` existe na raiz e se a variavel tem valor:

```env
SECURITY_TOKEN_SECRET=valor-gerado-com-openssl
```

### DBeaver nao conecta no Postgres

Confira a porta publicada:

```bash
docker compose port postgres 5432
```

Se houver um Postgres local usando `5432`, mantenha o Compose em `5433`:

```env
POSTGRES_PORT=5433
```

### Backend fica unhealthy por SMTP

O healthcheck do container usa `/actuator/health/liveness`, que nao depende do Gmail. Se os logs mostrarem erro `535-5.7.8 Username and Password not accepted`, o problema esta nas credenciais SMTP, nao necessariamente na saude da API.

Gere uma nova senha de app no Google e atualize:

```env
SPRING_MAIL_PASSWORD=nova-senha-de-app
```

Depois recrie o backend:

```bash
docker compose up -d --force-recreate inventarium-back
```

### Frontend sem botao de login Google

Confira se `GOOGLE_OAUTH_CLIENT_ID` esta definido no `.env`. Como o Next.js embute `NEXT_PUBLIC_*` no build, apos mudar o client id e necessario rebuildar o frontend:

```bash
docker compose build inventarium-front
docker compose up -d inventarium-front
```

### Backend nao conecta no banco

Dentro do Compose, o backend deve usar:

```text
jdbc:postgresql://postgres:5432/inventory_management
```

Nao use `localhost` para conexao entre containers. `localhost` dentro do backend aponta para o proprio container do backend.

## Ambiente Local e Producao

No ambiente local, o Compose pode buildar as imagens:

```bash
docker compose up --build
```

Em uma VM de producao, o esperado e que as imagens ja tenham sido publicadas em um registry, como o GHCR. Nesse caso, a VM deve apenas baixar e executar:

```bash
docker compose pull
docker compose up -d
```

Exemplo de `.env` em VM usando GHCR:

```env
BACKEND_IMAGE=ghcr.io/ifpebj-ti/inventarium-back:v0.1.0
FRONTEND_IMAGE=ghcr.io/ifpebj-ti/inventarium-front:v0.1.0
NEXT_PUBLIC_API_URL=https://api.exemplo.edu.br
```

Em producao:

- Use HTTPS.
- Nao exponha o Postgres publicamente.
- Restrinja portas de entrada na VM.
- Guarde secrets fora do repositorio.
- Use imagens versionadas e rastreaveis por tag.
