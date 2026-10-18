# inventory

Contrato de autenticação abaixo. Setor, usuário, perfil e permissão estão documentados em
`docs/wiki/API-Setor-Perfil-e-Permissoes.md`.

# - Autenticação

O login com Google é o jeito de entrar de todos os usuários. Não existe cadastro próprio,
verificação de e-mail nem recuperação de senha. A única exceção é a **conta de sistema** (admin),
que entra por usuário e senha — veja "Conta de sistema" abaixo.

O backend recebe o **ID token** que o Google Identity Services entrega ao frontend, valida a
assinatura contra o JWKS do Google mais os claims `iss`, `aud`, `exp` e `email_verified`, e
devolve o JWT da própria aplicação (HS256, 24h). Sem redirect, sem sessão e sem client secret —
o fluxo de ID token usa apenas o client id, que é público.

Os endpoints de autenticação são `POST /auth/google`, `POST /auth/login` (só conta de sistema) e
`GET /auth/me`.

## Configuração

| Variável | Descrição |
|---|---|
| `SECURITY_TOKEN_SECRET` | Obrigatória. Chave de assinatura do JWT. Gere com `openssl rand -hex 32`. |
| `GOOGLE_OAUTH_CLIENT_ID` | Client id do tipo *Web application*. Vazio faz `/auth/google` responder 500. |
| `GOOGLE_ALLOWED_DOMAINS` | Domínios que podem criar conta. Vazio libera qualquer um. |

Copie `.env.example` da raiz para `.env` (ignorado pelo git) e preencha.

`GOOGLE_ALLOWED_DOMAINS` é uma lista separada por vírgula em que cada entrada casa com o domínio
**e seus subdomínios**: `ifpe.edu.br` já cobre `@ifpe.edu.br`, `@discente.ifpe.edu.br` e
`@docente.ifpe.edu.br`. A verificação é a porta de entrada, aplicada só na criação da conta.

O mesmo valor precisa chegar ao frontend como `NEXT_PUBLIC_GOOGLE_CLIENT_ID`. O Next inlina essa
variável no bundle **durante o build**, então ela vai como build arg no `docker-compose.yml`, e
trocar o client id exige `docker compose build inventarium-front` — só subir de novo não basta.

No Google Cloud Console, em *APIs & Services → Credentials → OAuth 2.0 Client ID* do tipo
**Web application**, liste as origens do frontend em **Authorized JavaScript origins**
(`http://localhost:3000` e a de produção). **Não** configure redirect URI: este é o fluxo de
ID token, não o de authorization code.

## Contrato

`POST /auth/google` — sem autenticação, sem header `Authorization`.

```json
{ "credential": "<ID token do Google>" }
```

Resposta 200:

```json
{ "token": "<JWT da aplicação, 24h>",
  "user": { "id": 2, "name": "Aluno", "email": "aluno@discente.ifpe.edu.br" } }
```

| Status | Corpo | Quando |
|---|---|---|
| 400 | `Missing Google credential` | corpo ausente ou `credential` vazio |
| 401 | `Invalid Google token` | assinatura, `iss`, `aud`, `exp`, `email_verified` ou domínio recusados |
| 500 | erro padrão do Spring | `GOOGLE_OAUTH_CLIENT_ID` não configurado |

A mensagem de 401 é única de propósito: distinguir os motivos revelaria quais e-mails existem e
qual verificação falhou. O frontend classifica pelo status, nunca pelo texto do corpo — e trata
o 500 como "serviço indisponível", não como conta recusada.

`GET /auth/me` devolve o usuário do token: `{ id, name, email }`.

## Criação de conta

Quem entra pela primeira vez tem a conta criada na hora, desde que o domínio do e-mail esteja
permitido. Não há cadastro, aprovação nem confirmação por e-mail: o Google já provou a posse da
caixa, e o `GoogleTokenProvider` recusa qualquer token com `email_verified: false`.

Nos acessos seguintes o usuário é localizado pelo e-mail, normalizado em minúsculas — o Google
devolve o e-mail canonicalizado, mas o índice único do Postgres é case-sensitive, e sem normalizar
o mesmo dono viraria duas contas.

A coluna `google_id` guarda o `sub` do Google e **não é consultada para autenticar** — quem
autentica é a assinatura do ID token. Ela só é nula na conta de sistema (veja abaixo); o banco
garante isso: `google_id` nulo se e somente se `is_system_user` verdadeiro.

## Conta de sistema

Uma conta (`admin` por padrão) que entra por usuário e senha, sem Google, com o perfil
`ADMIN_ORGANIZATION`. Serve para o primeiro acesso e para atribuir perfis aos usuários do Google.

- A senha vem do `.env` (`SYSTEM_ADMIN_PASSWORD`), **sem valor padrão**: o repositório é público.
  O identificador de login vem de `SYSTEM_ADMIN_USERNAME` e é gravado na coluna `email`.
- No boot, o backend cria a conta (ou atualiza o hash) a partir dessas variáveis. Trocar a senha é
  trocar a variável e reiniciar. Senha vazia **desliga** o login por senha (a conta e o perfil
  ficam, só o hash é apagado). Senha com menos de 12 caracteres é recusada e a conta não é
  criada nem alterada.
- A conta de sistema nunca entra pelo Google.
- A conta não aparece em `GET /users` nem em `GET /sectors/{id}/users`, e `PATCH /users/{id}/profile`
  e a alocação em setor respondem `404` para ela. O perfil `ADMIN_ORGANIZATION`, que ela usa, não
  pode ser renomeado, excluído nem perder a permissão `ADMIN` pela API (409), e `/permissions` é
  somente leitura. Assim ninguém, nem outro admin, consegue deixar o sistema sem acesso
  administrativo pela API. Qualquer outro usuário pode ter o perfil trocado por um admin.
- `/profiles`, `/permissions` e `PATCH /users/{id}/profile` exigem `ADMIN`. Uma permissão nova não se
  cria pela API: entra por migration do Liquibase.

`POST /auth/login` — sem autenticação.

```json
{ "username": "admin", "password": "<SYSTEM_ADMIN_PASSWORD>" }
```

Resposta 200: a mesma do `POST /auth/google` (`{ token, user }`).

| Status | Corpo | Quando |
|---|---|---|
| 400 | `Missing credentials` | corpo ausente ou `username`/`password` vazios |
| 401 | `Invalid credentials` | usuário inexistente, senha errada ou login por senha desligado |

| 429 | `Too many attempts` | limite de tentativas estourado (veja abaixo); vem com o cabeçalho `Retry-After` em segundos |

A mensagem de 401 é única de propósito, igual ao `/auth/google`.

### Limite de tentativas

O login por senha tem duas camadas contra força bruta:

- **Backend, por usuário** (contador **em memória**: a aplicação roda numa instância e reiniciar o
  backend zera os contadores): 5 falhas em 15 min bloqueiam aquele usuário por 15 min, de qualquer IP.
  Usuário inexistente conta igual, então a resposta não revela quais usuários existem.
- **Nginx, por IP, só no `/auth/login`**: freio de taxa (zona `auth`, 10 requisições por minuto com
  rajada de 10; passou disso, `429` até o ritmo cair). Não é bloqueio de 15 min: limita a velocidade de
  quem tenta. Ver `infra/nginx/app-vm/sites-tls/21-api.conf`. O `/auth/google` e o `/auth/me` ficam só na
  zona geral do Nginx (20 req/s), sem limite de tentativas.
- Durante o bloqueio por usuário a resposta é `429` **mesmo com a senha certa** (e sem gastar BCrypt).
  Pedidos bloqueados não prolongam o bloqueio, e requisições com campos vazios (400) não contam.
- Um login com sucesso zera o contador do usuário.
- Os dois `429` trazem `Retry-After` e CORS (o do Nginx vem de `snippets/login-rate-limited.conf`).
- O bloqueio é temporário de propósito: bloqueio permanente permitiria a qualquer um travar o
  `admin`. Contrapartida: quem insistir consegue manter o `admin` indisponível, sem afetar o login
  Google dos demais.

Os limites do backend são configuráveis: `AUTH_LOGIN_USER_MAX_FAILURES`, `AUTH_LOGIN_WINDOW` e
`AUTH_LOGIN_BLOCK_DURATION` (ex.: `15m`). Os do Nginx ficam em `infra/nginx` e mudam com
`infra/scripts/deploy-nginx.sh`.

**IP do cliente.** Quem enxerga o IP real é o Nginx, por isso o limite por IP é dele: o backend não lê
o IP no login. Em desenvolvimento o `docker-compose.yml` sobe um Nginx (porta `NGINX_PORT`, padrão
`8081`, config em `infra/nginx/dev`) que reproduz esse freio; o frontend do compose já aponta para ele.
O `8080` do backend continua publicado para curl e debug, mas esse caminho **não** passa pelo limite
por IP. `FORWARD_HEADERS_STRATEGY=native` (padrão de produção, com o Nginx sobrescrevendo o
`X-Forwarded-For`) faz o backend enxergar o IP, o esquema e o host reais, mas o limitador de login não
depende dele. O `/auth/google` não tem limite de tentativas: não há segredo para adivinhar (o que
entra é um ID token assinado pelo Google).


# - Limpeza de Dados com clear-all-data.sh

## Visão Geral

O script `clear-all-data.sh` foi desenvolvido para facilitar a limpeza completa dos dados do banco de dados da aplicação. Ele é especialmente útil durante o desenvolvimento, testes e quando é necessário reiniciar o sistema com um estado limpo.

## Como Utilizar

1. Certifique-se de que o script tem permissões de execução:
   ```bash
   chmod +x clear-all-data.sh
   ```

2. Execute o script:
   ```bash
   ./clear-all-data.sh
   ```
   
## ⚠️ **ATENÇÃO**: 
Este script remove TODOS os dados do banco. Use apenas em ambientes de desenvolvimento ou teste.
