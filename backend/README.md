# inventory

Contrato de autenticação abaixo. Setor, usuário, perfil e permissão estão documentados em
`docs/wiki/API-Setor-Perfil-e-Permissoes.md`.

# - Autenticação

O login com Google é o jeito de entrar dos usuários comuns. Admins e gestores de setor podem
pré-cadastrar usuários por e-mail, com perfil e setores, em `POST /users` (o gestor, apenas Operador
de Campo e Consulta nos seus próprios setores). Quem entra pela primeira vez sem pré-cadastro nasce
como Consulta, e uma conta desativada não consegue entrar (nem manter a sessão aberta). Não há senha local para esses usuários,
verificação de e-mail própria nem recuperação de senha. A exceção é a **conta de sistema** (admin),
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

`GET /auth/me` devolve o DTO de sessão do token: `{ id, name, email, profileId, sectorIds,
permissions }`. Ele não serializa a entidade `User` nem expõe campos internos de autenticação.

## Criação de conta

Quem entra pela primeira vez sem pré-cadastro tem a conta criada na hora, desde que o domínio
do e-mail esteja permitido. O admin também pode pré-cadastrar o e-mail, definindo perfil e setores
antes do primeiro acesso. O Google prova a posse da caixa: o `GoogleTokenProvider` recusa qualquer
token com `email_verified: false`. O pré-cadastro por ADMIN é uma autorização explícita e pode
incluir domínios fora da lista de criação automática.

O usuário é localizado pelo e-mail normalizado em minúsculas. A migration `3.0.xml` acrescenta
um índice único sobre `lower(email)`, impedindo duplicidade por diferença entre maiúsculas e minúsculas.

A coluna `google_id` guarda o `sub` verificado do Google. No primeiro acesso de um pré-cadastro,
esse valor é vinculado à conta e `pending_google_login` passa a falso, preservando ID, nome,
perfil e setores. Nos acessos posteriores, o `sub` precisa coincidir com o registrado.
O banco permite `google_id` nulo apenas para contas de sistema ou pré-cadastros pendentes.

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
- `/profiles` e `/permissions` (somente leitura) exigem `ADMIN` ou `MANAGE_SECTOR`, e
  `PATCH /users/{id}/profile` também: o gestor só atribui perfis sem permissão de gestão a usuários dos
  seus setores. Uma permissão nova não se cria pela API: entra por migration do Liquibase.

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
o IP no login. Em desenvolvimento, sem Nginx na frente, só vale o bloqueio por usuário.
`FORWARD_HEADERS_STRATEGY=native` (padrão de produção, com o Nginx sobrescrevendo o
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
