# ADR 0002 - Definir Autenticacao com Google e JWT Proprio da Aplicacao

## Status

Accepted

## Contexto

O Inventarium precisa autenticar usuarios institucionais sem manter senha local, fluxo proprio de cadastro, verificacao de e-mail ou recuperacao de senha. O projeto tambem precisa restringir a criacao de contas por dominio de e-mail e manter uma sessao simples para chamadas autenticadas da API.

Como a aplicacao web ja executa no navegador, o fluxo escolhido precisa integrar com o frontend, validar a identidade no backend e evitar confiar em dados enviados diretamente pelo cliente sem prova criptografica.

## Decisao

Adotar login com Google usando OAuth 2.0 / OpenID Connect por ID token no frontend web. O frontend recebe o ID token pelo Google Identity Services e o envia para `POST /auth/google`. O backend valida assinatura, `iss`, `aud`, expiracao e `email_verified` usando as chaves JWKS do Google.

Apos validar o ID token, o backend localiza ou cria o usuario, respeitando `GOOGLE_ALLOWED_DOMAINS`, e emite um JWT proprio do Inventarium assinado por `SECURITY_TOKEN_SECRET`. Esse JWT da aplicacao e usado nas rotas protegidas como `Authorization: Bearer`.

## Consequencias

- O sistema nao precisa armazenar senhas nem manter fluxos proprios de verificacao ou recuperacao de conta.
- O backend continua sendo a fonte de confianca: ele nao aceita e-mail ou `sub` soltos enviados pelo frontend.
- O Google Identity Services passa a ser uma dependencia externa critica do login.
- Frontend e backend precisam usar o mesmo `GOOGLE_OAUTH_CLIENT_ID`, pois o backend valida o claim `aud`.
- Em producao, o frontend precisa ser servido por HTTPS e estar cadastrado nas origens autorizadas do OAuth Client ID.
- O JWT proprio da aplicacao sustenta a sessao apos o login; se `SECURITY_TOKEN_SECRET` mudar, sessoes ativas deixam de validar.

## Alternativas Consideradas

- Usuario e senha locais: aumentaria responsabilidades de seguranca, armazenamento de senha, recuperacao e verificacao de e-mail.
- Enviar e-mail e `sub` diretamente do frontend para o backend: foi descartado porque o backend nao teria prova criptografica da identidade.
- Authorization Code Flow com callback no backend: tambem e seguro, mas mais complexo para o escopo atual e para o fluxo de login web ja adotado.

## Riscos

- Configuracao incorreta de `GOOGLE_OAUTH_CLIENT_ID` entre frontend e backend faz todo login falhar.
- CORS e origens autorizadas devem ser revisados antes de producao.
- Nao ha revogacao antecipada do JWT proprio da aplicacao antes da expiracao natural.

## Referencias

- `backend/docs/AUTHENTICATION.md`
- `backend/docs/GOOGLE_AUTH_SETUP.md`
- `docs/wiki/C4-Contexto.md`
- `docs/wiki/C4-Containers.md`
- `docs/wiki/C4-Componentes.md`

## Decisoes Relacionadas

- [ADR 0005 - Adotar API REST stateless com Bearer JWT](./0005-adotar-api-rest-stateless.md)
