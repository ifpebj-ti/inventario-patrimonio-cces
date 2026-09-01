# inventory

# - Login com Google

O backend aceita o **ID token** que o Google Identity Services entrega ao frontend e devolve o
mesmo JWT da aplicação que o login por senha devolve. Não há redirect, sessão nem client secret.

## Configuração

| Variável | Descrição |
|---|---|
| `SECURITY_TOKEN_SECRET` | Obrigatória. Chave de assinatura do JWT. Gere com `openssl rand -hex 32`. |
| `GOOGLE_OAUTH_CLIENT_ID` | Client id do tipo *Web application*. Vazio faz `/auth/google` responder 500. |
| `GOOGLE_ALLOWED_DOMAINS` | Domínios que podem **criar** conta nova. Vazio libera qualquer um. |

Copie `.env.example` da raiz para `.env` (ignorado pelo git) e preencha.

`GOOGLE_ALLOWED_DOMAINS` é uma lista separada por vírgula em que cada entrada casa com o domínio
e seus subdomínios: `ifpe.edu.br` já cobre `@ifpe.edu.br`, `@discente.ifpe.edu.br` e
`@docente.ifpe.edu.br`. A restrição vale só para cadastro novo — quem já tem conta consegue
vincular e entrar pelo Google mesmo com e-mail de outro domínio.

No Google Cloud Console, em *APIs & Services → Credentials → OAuth 2.0 Client ID* do tipo
**Web application**, liste as origens do frontend em **Authorized JavaScript origins**
(`http://localhost:3000` e a de produção). **Não** configure redirect URI: este é o fluxo de
ID token, não o de authorization code.

## Contrato

`POST /auth/google` — sem autenticação, sem header `Authorization`.

```json
{ "credential": "<ID token do Google>" }
```

Resposta 200, idêntica em forma à do `POST /auth/login`:

```json
{ "token": "<JWT da aplicação, 24h>",
  "user": { "id": 2, "name": "Aluno", "email": "aluno@discente.ifpe.edu.br", "verified": true } }
```

| Status | Corpo | Quando |
|---|---|---|
| 400 | `Missing Google credential` | corpo ausente ou `credential` vazio |
| 401 | `Invalid Google token` | assinatura, `iss`, `aud`, `exp`, `email_verified` ou domínio recusados |
| 500 | erro padrão do Spring | `GOOGLE_OAUTH_CLIENT_ID` não configurado |

A mensagem de 401 é única de propósito: distinguir os motivos revelaria quais e-mails existem
e qual verificação falhou.

## Vinculação de contas

Se o e-mail do Google já tem cadastro, a conta existente é reaproveitada e passa a ter
`google_id` e `verified = true`. Quando essa conta **ainda não estava verificada**, a senha é
apagada na vinculação: ela foi escolhida por alguém que nunca provou controlar a caixa, e mantê-la
entregaria a conta a quem tivesse pré-cadastrado o e-mail da vítima. Essa pessoa pode definir uma
senha nova pelo fluxo de recuperação, que exige acesso ao e-mail.

Contas criadas via Google ficam sem senha e não conseguem autenticar por `POST /auth/login`.

## O que o frontend precisa fazer

- `NEXT_PUBLIC_GOOGLE_CLIENT_ID` com o mesmo valor de `GOOGLE_OAUTH_CLIENT_ID`.
- Carregar `https://accounts.google.com/gsi/client` e chamar
  `google.accounts.id.initialize({ client_id, callback })`; o callback recebe `{ credential }`.
- `POST /auth/google` com esse `credential` e, na resposta, reaproveitar o mesmo tratamento do
  login por senha: gravar o cookie `inventarium.token` e guardar o `user`. Sem recaptcha e sem o
  desvio de "e-mail não verificado".
- O tipo `User` do frontend não muda.


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
