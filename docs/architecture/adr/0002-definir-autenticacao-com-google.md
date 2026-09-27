# ADR 0002 - Definir Autenticação com Google e JWT Proprio da Aplicação

## Status

Accepted

## Contexto

O Inventarium precisa autenticar usuários institucionais sem manter senha local, fluxo próprio de cadastro, verificação de e-mail ou recuperação de senha. O projeto também precisa restringir a criação de contas por dominio de e-mail e manter uma sessão simples para chamadas autenticadas da API.

Como a aplicação web ja executa no navegador, o fluxo escolhido precisa integrar com o frontend, validar a identidade no backend e evitar confiar em dados enviados diretamente pelo cliente sem prova criptográfica.

## Decisao

Adotar login com Google usando OAuth 2.0 / OpenID Connect por ID token no frontend web. O frontend recebe o ID token pelo Google Identity Services e o envia para `POST /auth/google`. O backend valida assinatura, `iss`, `aud`, expiração e `email_verified` usando as chaves JWKS do Google.

Apos validar o ID token, o backend localiza ou cria o usuário, respeitando `GOOGLE_ALLOWED_DOMAINS`, e emite um JWT próprio do Inventarium assinado por `SECURITY_TOKEN_SECRET`. Esse JWT da aplicação e usado nas rotas protegidas como `Authorization: Bearer`.

## Consequencias

- O sistema não precisa armazenar senhas nem manter fluxos proprios de verificação ou recuperação de conta.
- O backend continua sendo a fonte de confianca: ele não aceita e-mail ou `sub` soltos enviados pelo frontend.
- O Google Identity Services passa a ser uma dependencia externa crítica do login.
- Frontend e backend precisam usar o mesmo `GOOGLE_OAUTH_CLIENT_ID`, pois o backend valida o claim `aud`.
- Em produção, o frontend precisa ser servido por HTTPS e estar cadastrado nas origens autorizadas do OAuth Client ID.
- O JWT próprio da aplicação sustenta a sessão apos o login; se `SECURITY_TOKEN_SECRET` mudar, sessoes ativas deixam de validar.

## Alternativas Consideradas

- Usuário e senha locais: aumentaria responsabilidades de segurança, armazenamento de senha, recuperação e verificação de e-mail.
- Enviar e-mail e `sub` diretamente do frontend para o backend: foi descartado porque o backend não teria prova criptográfica da identidade.
- Authorization Code Flow com callback no backend: também e seguro, mas mais complexo para o escopo atual e para o fluxo de login web ja adotado.

## Riscos

- Configuração incorreta de `GOOGLE_OAUTH_CLIENT_ID` entre frontend e backend faz todo login falhar.
- CORS e origens autorizadas devem ser revisados antes de produção.
- Nao ha revogacao antecipada do JWT próprio da aplicação antes da expiração natural.

## Referencias

- `backend/docs/AUTHENTICATION.md`
- `backend/docs/GOOGLE_AUTH_SETUP.md`
- `docs/wiki/C4-Contexto.md`
- `docs/wiki/C4-Containers.md`
- `docs/wiki/C4-Componentes.md`

## Decisões Relacionadas

- [ADR 0005 - Adotar API REST stateless com Bearer JWT](./0005-adotar-api-rest-stateless.md)
