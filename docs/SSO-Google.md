# Autenticação SSO com Google

## Objetivo

Implementar autenticação de usuários utilizando Single Sign-On (SSO) com contas Google,
tornando-o o único meio de acesso ao Inventarium, sem necessidade de gerenciamento de senha
própria pela aplicação.

## Contexto

O sistema permite que usuários autorizados realizem login utilizando uma conta Google.

A autenticação utiliza o fluxo OpenID Connect de **ID token**: o Google Identity Services (GIS)
entrega ao frontend um token assinado comprovando a identidade do usuário, e a aplicação obtém
apenas as informações necessárias para identificar e autorizar o acesso (nome, e-mail e um
identificador estável da conta).

Autenticação e autorização são responsabilidades distintas: o Google comprova a identidade do
usuário; o Inventarium define quais usuários podem ter conta, através de uma lista de domínios
de e-mail permitidos. Um sistema de permissões por usuário (papéis/perfis) não faz parte desta
issue.

## Escopo

Esta issue contempla:

* configuração da aplicação no Google Cloud (projeto, tela de consentimento OAuth, Client ID);
* integração com OpenID Connect via Google Identity Services;
* integração do botão de login do Google no frontend, com obtenção do ID token no navegador;
* endpoint de backend que recebe e valida o ID token;
* obtenção dos dados básicos do usuário autenticado (nome, e-mail);
* criação ou localização do usuário interno correspondente;
* geração do token de sessão utilizado pelo Inventarium;
* tratamento de usuários não autorizados (domínio de e-mail fora da lista permitida);
* logout;
* configuração segura das credenciais do provedor;
* remoção do login por usuário e senha local.

## Fora do escopo

* autenticação com outros provedores;
* gerenciamento completo de permissões e perfis (papéis de usuário), a ser tratado em outra
  issue.

## Fluxo implementado

1. O usuário acessa o Inventarium.
2. Clica no botão "Fazer Login com o Google".
3. O Google Identity Services autentica o usuário dentro da própria página.
4. O Google devolve um ID token (JWT assinado) diretamente ao frontend.
5. O frontend envia esse token para o backend, em `POST /auth/google`.
6. O backend valida a assinatura do token contra o JWKS público do Google e confere os claims
   `iss`, `aud`, `exp`/`nbf` e `email_verified`.
7. O usuário interno é localizado pelo e-mail ou criado, caso o domínio do e-mail esteja na
   lista de domínios permitidos.
8. O backend emite seu próprio token de sessão (JWT HS256, válido por 24h).
9. O usuário é redirecionado para a área autenticada do sistema.

## Requisitos de segurança

* As credenciais não são armazenadas no código-fonte: Client ID e a chave de assinatura do JWT
  da aplicação são fornecidos por variáveis de ambiente.
* O backend valida issuer, audience, expiração e o claim `email_verified` de todo ID token
  recebido.
* Apenas os dados necessários do usuário são armazenados: nome, e-mail, identificador da conta
  Google e timestamps de criação/atualização.
* Usuários autenticados pelo Google não recebem acesso automaticamente: a criação de conta nova
  é restrita aos domínios de e-mail configurados como permitidos.
* Nenhum log expõe o token bruto do Google ou o token de sessão da aplicação.

## Critérios de aceite

* [x] Aplicação registrada e configurada corretamente no Google Cloud
* [x] Login com Google disponível na aplicação
* [x] Botão de login do Google entrega o ID token corretamente ao frontend
* [x] Endpoint de backend valida o ID token corretamente
* [x] Token retornado pelo Google é validado
* [x] Nome e e-mail do usuário autenticado podem ser recuperados
* [x] Usuário interno é identificado ou criado conforme a regra definida
* [x] Usuário não autorizado recebe resposta adequada
* [x] Sessão ou token da aplicação é criado após autenticação bem-sucedida
* [x] Logout encerra a sessão do lado do cliente
* [x] Credenciais do Google não estão versionadas no repositório
* [ ] Fluxo de autenticação possui testes automatizados relevantes
* [x] Documentação de configuração do SSO foi adicionada
* [ ] Implementação revisada por outro integrante da equipe

## Evidências

* Link para o Pull Request
* Evidência do fluxo de login funcionando
* Link para documentação de configuração:
  [`backend/docs/AUTHENTICATION.md`](../backend/docs/AUTHENTICATION.md) e
  [`backend/docs/GOOGLE_AUTH_SETUP.md`](../backend/docs/GOOGLE_AUTH_SETUP.md)
* Evidência dos testes automatizados

## Trabalho futuro

* Testes automatizados para o fluxo de autenticação.
* Revogação de sessão do lado do servidor (hoje o logout só encerra a sessão no navegador; o
  token da aplicação permanece válido até expirar).
* Uso de `nonce` na obtenção do ID token, como camada adicional contra reuso do token.
* Sistema de permissões por usuário (papéis/perfis).
