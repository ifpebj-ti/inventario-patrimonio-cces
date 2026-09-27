# ADR 0005 - Adotar API REST Stateless com Bearer JWT

## Status

Accepted

## Contexto

O Inventarium possui clientes web e mobile que consomem o backend para autenticação, inventários, itens, importação/exportação de planilhas e geração de etiquetas. A API precisa ser simples de consumir pelos clientes, funcionar bem em containers e não depender de sessão de servidor.

A autenticação com Google e usada apenas no momento inicial de login. Depois disso, as rotas protegidas precisam validar uma credencial da própria aplicação.

## Decisao

Adotar uma API REST stateless, usando JSON para os contratos principais, `multipart/form-data` para upload de planilhas, downloads para artefatos gerados e Bearer JWT para rotas protegidas.

O backend emite um JWT próprio da aplicação apos validar o ID token do Google. Esse JWT e enviado pelos clientes no header `Authorization: Bearer`.

## Consequencias

- O backend não precisa manter sessão de servidor.
- Web e mobile usam o mesmo padrão de autenticação nas chamadas protegidas.
- A API fica adequada para execução em containers e futura evolucao de infraestrutura.
- O JWT precisa ter segredo forte e configurado por ambiente.
- A revogacao antecipada de sessão não existe no modelo atual; a sessão dura até a expiração do token.

## Alternativas Consideradas

- Sessão server-side com cookie: reduziria exposicao do token ao JavaScript se bem configurada, mas aumentaria acoplamento do backend com estado de sessão.
- Enviar o ID token do Google em todas as chamadas: aumentaria dependencia operacional do Google em cada requisição e misturaria token de login externo com sessão interna da aplicação.

## Riscos

- Armazenamento de token no cliente precisa ser protegido contra XSS e configuracoes inadequadas de cookie.
- Em produção, CORS e HTTPS precisam ser revisados junto com a infraestrutura.

## Referencias

- `backend/docs/AUTHENTICATION.md`
- `docs/wiki/C4-Containers.md`
- `docs/wiki/C4-Componentes.md`

## Decisões Relacionadas

- [ADR 0002 - Definir autenticação com Google e JWT próprio da aplicação](./0002-definir-autenticação-com-google.md)
