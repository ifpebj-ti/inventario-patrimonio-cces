# ADR 0005 - Adotar API REST Stateless com Bearer JWT

## Status

Accepted

## Contexto

O Inventarium possui clientes web e mobile que consomem o backend para autenticacao, inventarios, itens, importacao/exportacao de planilhas e geracao de etiquetas. A API precisa ser simples de consumir pelos clientes, funcionar bem em containers e nao depender de sessao de servidor.

A autenticacao com Google e usada apenas no momento inicial de login. Depois disso, as rotas protegidas precisam validar uma credencial da propria aplicacao.

## Decisao

Adotar uma API REST stateless, usando JSON para os contratos principais, `multipart/form-data` para upload de planilhas, downloads para artefatos gerados e Bearer JWT para rotas protegidas.

O backend emite um JWT proprio da aplicacao apos validar o ID token do Google. Esse JWT e enviado pelos clientes no header `Authorization: Bearer`.

## Consequencias

- O backend nao precisa manter sessao de servidor.
- Web e mobile usam o mesmo padrao de autenticacao nas chamadas protegidas.
- A API fica adequada para execucao em containers e futura evolucao de infraestrutura.
- O JWT precisa ter segredo forte e configurado por ambiente.
- A revogacao antecipada de sessao nao existe no modelo atual; a sessao dura ate a expiracao do token.

## Alternativas Consideradas

- Sessao server-side com cookie: reduziria exposicao do token ao JavaScript se bem configurada, mas aumentaria acoplamento do backend com estado de sessao.
- Enviar o ID token do Google em todas as chamadas: aumentaria dependencia operacional do Google em cada requisicao e misturaria token de login externo com sessao interna da aplicacao.

## Riscos

- Armazenamento de token no cliente precisa ser protegido contra XSS e configuracoes inadequadas de cookie.
- Em producao, CORS e HTTPS precisam ser revisados junto com a infraestrutura.

## Referencias

- `backend/docs/AUTHENTICATION.md`
- `docs/wiki/C4-Containers.md`
- `docs/wiki/C4-Componentes.md`

## Decisoes Relacionadas

- [ADR 0002 - Definir autenticacao com Google e JWT proprio da aplicacao](./0002-definir-autenticacao-com-google.md)
