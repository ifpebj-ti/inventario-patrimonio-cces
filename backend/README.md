# inventory

# - Autenticação

O login com Google é o **único** jeito de entrar. Não existe cadastro próprio, senha,
verificação de e-mail nem recuperação de senha.

O backend recebe o **ID token** que o Google Identity Services entrega ao frontend, valida a
assinatura contra o JWKS do Google mais os claims `iss`, `aud`, `exp` e `email_verified`, e
devolve o JWT da própria aplicação (HS256, 24h). Sem redirect, sem sessão e sem client secret —
o fluxo de ID token usa apenas o client id, que é público.

Só existem dois endpoints de autenticação: `POST /auth/google` e `GET /auth/me`.

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

A coluna `google_id` guarda o `sub` do Google. Ela é `NOT NULL` porque todo usuário nasce por este
caminho, mas **não é consultada para autenticar** — quem autentica é a assinatura do ID token.


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
