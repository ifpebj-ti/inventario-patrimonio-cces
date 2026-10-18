# Guia de Execução, Configuração e Operação

Este guia descreve como preparar, executar e operar o Inventarium em ambiente local. Ele também registra as diferencas esperadas para um deploy em VM, onde as imagens devem ser baixadas de um registry como o GHCR.

## Visão Geral

O ambiente local com Docker Compose sobe tres serviços:

```text
postgres           Banco PostgreSQL
inventarium-back   API Java/Spring Boot
inventarium-front  Aplicação web Next.js
```

## Pre-requisitos

- Docker Desktop ou Docker Engine com Docker Compose v2.
- Git.
- Node.js e npm, caso deseje rodar frontend ou ferramentas da raiz fora do Docker.
- JDK compativel com Gradle, caso deseje rodar o backend fora do Docker.

Para conferir Docker e Compose:

```bash
docker --version
docker compose version
```

## Configuração

Copie o arquivo de exemplo da raiz:

```bash
cp .env.example .env
```

No PowerShell:

```powershell
Copy-Item .env.example .env
```

Preencha o `.env` antes de subir a aplicação.

## Variáveis de Ambiente

| Variavel | Obrigatoria | Uso | Observação |
| --- | --- | --- | --- |
| `SECURITY_TOKEN_SECRET` | Sim | Backend | Segredo usado para assinar os JWTs da aplicação. Gere um valor forte. |
| `POSTGRES_PORT` | Nao | Compose | Porta publicada no host. Padrao: `5433`. |
| `BACKEND_PORT` | Nao | Compose | Porta publicada da API. Padrao: `8080`. |
| `FRONTEND_PORT` | Nao | Compose | Porta publicada da web. Padrao: `3000`. |
| `POSTGRES_USER` | Nao | Postgres/backend | Usuário do banco. Padrao: `admin`. |
| `POSTGRES_PASSWORD` | Nao | Postgres/backend | Senha do banco local. Padrao: `admin`. |
| `POSTGRES_DB` | Nao | Postgres/backend | Nome do banco. Padrao: `inventory_management`. |
| `BACKEND_IMAGE` | Nao | Compose | Nome/tag da imagem do backend. Local: `inventarium-back:local`. |
| `FRONTEND_IMAGE` | Nao | Compose | Nome/tag da imagem do frontend. Local: `inventarium-front:local`. |
| `GOOGLE_OAUTH_CLIENT_ID` | Sim | Backend/frontend | Client ID publico do Google OAuth. Precisa ser o mesmo nos dois lados. |
| `NEXT_PUBLIC_API_URL` | Nao | Frontend | URL pública que o navegador usa para chamar a API. Local: `http://localhost:8080`. |
| `GOOGLE_ALLOWED_DOMAINS` | Nao | Backend | Dominios permitidos no login Google. Padrao: `ifpe.edu.br`. |
| `SPRING_MAIL_USERNAME` | Nao | Backend | Conta SMTP usada para envio de emails. |
| `SPRING_MAIL_PASSWORD` | Nao | Backend | Senha de app do Gmail ou credencial SMTP equivalente. Em produção é obrigatória (o compose de produção aborta sem ela). |
| `SYSTEM_ADMIN_USERNAME` | Nao | Backend | Usuário da conta de sistema que entra por senha (`POST /auth/login`). Padrao: `admin`. |
| `SYSTEM_ADMIN_PASSWORD` | Nao | Backend | Senha da conta de sistema. Vazia desliga o login por senha. Precisa ter **no mínimo 12 caracteres**: com menos, a conta não é criada e o backend só registra um erro no log. Trocar o valor e reiniciar troca a senha da conta. |
| `FORWARD_HEADERS_STRATEGY` | Nao | Backend | `none` (padrão local) ou `native`. Use `native` somente atrás do Nginx, que deve sobrescrever o `X-Forwarded-For`; é o padrão do compose de produção. O limite de tentativas de login não depende disso: o limite por IP é do Nginx e o do backend é por usuário. |
| `AUTH_LOGIN_USER_MAX_FAILURES`, `AUTH_LOGIN_WINDOW`, `AUTH_LOGIN_BLOCK_DURATION` | Nao | Backend | Limites do login por senha, por usuário. Padrao: 5 falhas, janela e bloqueio de `15m`. O compose de produção os repassa. O limite por IP é do Nginx (`infra/nginx`). |
| `IMAGE_TAG` | Nao | Compose de produção | Tag das imagens do GHCR. Padrao: `latest` (última release). Para rollback, use `vX.Y.Z`. |
| `CORS_ALLOWED_ORIGINS` | Nao | Compose de produção | Origens (separadas por vírgula) autorizadas a chamar a API pelo navegador. Padrao no compose de produção: `https://inventariumifpe.me`; em desenvolvimento: `http://localhost:3000`. `*` é recusado na inicialização do backend. |

Variáveis sensiveis não devem ser commitadas. Em produção, mantenha `SECURITY_TOKEN_SECRET`, `POSTGRES_PASSWORD` e `SPRING_MAIL_PASSWORD` em mecanismo seguro de secrets ou no arquivo `.env` protegido da VM.

Exemplo para gerar `SECURITY_TOKEN_SECRET`:

```bash
openssl rand -hex 32
```

## Deploy em Produção na OCI

O passo a passo do go-live (domínio, DNS, Terraform, Nginx com TLS, Portainer, backup e restauração), a operação do dia a dia e a solução de problemas estão em [Deploy em Produção na OCI](./Deploy-em-Produção-OCI) (arquivo `docs/wiki/Deploy-em-Producao-OCI.md`).

## Imagens Publicadas No GHCR

A pipeline pública imagens no GitHub Container Registry, mas não injeta secrets de runtime dentro das imagens. Esse comportamento e intencional:

- frontend: `NEXT_PUBLIC_GOOGLE_CLIENT_ID` e `NEXT_PUBLIC_API_URL` são argumentos de build, porque o Next.js inclui variáveis `NEXT_PUBLIC_*` no bundle do navegador. No GitHub Actions, esses valores podem vir de repository variables; o Google Client ID também pode vir de GitHub Secrets, embora não seja um segredo real depois que chega ao browser. A pipeline falha se esses valores não estiverem configurados;
- backend: `SECURITY_TOKEN_SECRET`, credenciais do banco, credenciais de e-mail e configuracoes Spring são variáveis de runtime e devem ficar na stack, no Compose, no Portainer ou no mecanismo de secrets da VM;
- uma troca de imagem não deve exigir redigitar secrets quando a stack reutiliza as mesmas variáveis salvas.

Cada tag de release do GHCR é uma imagem multi-arquitetura, com variantes `linux/amd64` e `linux/arm64`. Ao executar `docker compose pull`, o Docker escolhe a variante compatível com a CPU da VM automaticamente; a referência da imagem permanece a mesma.

Em produção usa-se o `docker-compose.production.yml`, que não constrói nada: ele puxa `ghcr.io/ifpebj-ti/inventarium-back` e `inventarium-front` na tag `IMAGE_TAG` (padrão `latest`). Ele também:

- fixa o nome do projeto (`inventarium`) e do volume do banco (`inventarium_postgres-data`), para que mudar o diretório ou recriar a stack no Portainer não aponte para um volume novo e vazio;
- publica as portas do backend e do frontend apenas em `127.0.0.1`; quem atende a internet é o Nginx do host;
- não publica a porta do Postgres.

Configure a stack da VM ou o `.env` protegido (permissão `0600`) com:

```bash
# latest acompanha a última release; para rollback fixe uma tag publicada, como v0.2.0.
IMAGE_TAG=latest

SECURITY_TOKEN_SECRET=<valor-forte>
POSTGRES_USER=<usuário>
POSTGRES_PASSWORD=<senha>
POSTGRES_DB=inventory_management
SPRING_MAIL_USERNAME=<conta-smtp>
SPRING_MAIL_PASSWORD=<senha-ou-app-password>
GOOGLE_OAUTH_CLIENT_ID=<client-id-google>
GOOGLE_ALLOWED_DOMAINS=ifpe.edu.br
SYSTEM_ADMIN_USERNAME=admin
SYSTEM_ADMIN_PASSWORD=<12-ou-mais-caracteres>
FORWARD_HEADERS_STRATEGY=native
CORS_ALLOWED_ORIGINS=https://inventariumifpe.me
```

`NEXT_PUBLIC_API_URL` não faz parte da stack: ela é argumento de build do frontend e vem das variáveis do repositório no GitHub. Mudar a URL exige uma nova release.

Para rollback, troque apenas `IMAGE_TAG` para uma tag de versão anterior já publicada, como `v0.2.0`, e atualize a stack com re-pull das imagens. As demais variáveis podem permanecer iguais. O rollback de imagem não reverte migrações do Liquibase já aplicadas.

No Portainer, prefira manter essas variáveis como environment da stack, não como valores digitados manualmente a cada recriacao de container. Se a pipeline passar a fazer deploy automatico na VM depois, ela deve atualizar somente a referencia da imagem versionada ou forcar pull/recreate da stack, preservando as variáveis de runtime ja configuradas.

## Execução Local Com Docker Compose

Na raiz do repositório, execute:

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

Serviços publicados:

```text
Frontend web: http://localhost:3000
Backend API:  http://localhost:8080
PostgreSQL:   localhost:5433
```

Se `POSTGRES_PORT` estiver definido como `5432`, o banco sera publicado em `localhost:5432`. Evite isso se ja existir outro Postgres rodando na máquina.

## Validação

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

Com os valores padrão:

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

Todos os serviços:

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

Recriar apenas o backend apos mudar variáveis de runtime:

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

## Execução Local Sem Docker

Para rodar o backend fora do Docker, mantenha um Postgres acessivel e configure as variáveis de ambiente esperadas.

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

### DBeaver não conecta no Postgres

Confira a porta publicada:

```bash
docker compose port postgres 5432
```

Se houver um Postgres local usando `5432`, mantenha o Compose em `5433`:

```env
POSTGRES_PORT=5433
```

### Backend fica unhealthy por SMTP

O healthcheck do container usa `/actuator/health/liveness`, que não depende do Gmail. Se os logs mostrarem erro `535-5.7.8 Username and Password not accepted`, o problema está nas credenciais SMTP, não necessariamente na saude da API.

Gere uma nova senha de app no Google e atualize:

```env
SPRING_MAIL_PASSWORD=nova-senha-de-app
```

Depois recrie o backend:

```bash
docker compose up -d --force-recreate inventarium-back
```

### Frontend sem botao de login Google

Confira se `GOOGLE_OAUTH_CLIENT_ID` está definido no `.env`. Como o Next.js embute `NEXT_PUBLIC_*` no build, apos mudar o client id e necessário rebuildar o frontend:

```bash
docker compose build inventarium-front
docker compose up -d inventarium-front
```

### Backend não conecta no banco

Dentro do Compose, o backend deve usar:

```text
jdbc:postgresql://postgres:5432/inventory_management
```

Nao use `localhost` para conexao entre containers. `localhost` dentro do backend aponta para o próprio container do backend.

## Ambiente Local e Produção

No ambiente local, o Compose pode buildar as imagens:

```bash
docker compose up --build
```

Em uma VM de produção, o esperado e que as imagens ja tenham sido publicadas em um registry, como o GHCR. Nesse caso, a VM deve apenas baixar e executar:

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

Em produção:

- Use HTTPS.
- Nao exponha o Postgres publicamente.
- Restrinja portas de entrada na VM.
- Guarde secrets fora do repositório.
- Use imagens versionadas publicadas pela release.
