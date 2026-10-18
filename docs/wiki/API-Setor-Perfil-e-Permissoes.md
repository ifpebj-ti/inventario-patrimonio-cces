# API de Setor, Usuário, Perfil e Permissão

Este documento descreve, para quem for implementar o front-end, todas as rotas de `/sectors`,
`/users` (listagem, alocação e perfil), `/profiles` e `/permissions` como elas existem hoje em `main`,
após as issues #179 (remoção de `Organization`, `Sector` independente) e #181 (autorização real em
Setor e alocação usuário↔setor), ambas sub-issues de #154. Do `/auth`, cobre só o
`POST /auth/login` da conta de sistema (issue #192). **Não cobre** `/inventories` nem `/items` —
essas rotas não foram tocadas por este trabalho.

## Antes de implementar: comportamentos não óbvios

- **Toda rota abaixo exige `Authorization: Bearer <jwt>`.** Sem header, a resposta é `403
  Forbidden` — não `401` — porque o `SecurityConfig` ainda não tem um `AuthenticationEntryPoint`
  customizado (é um TODO em aberto no backend).
- **Um JWT com assinatura válida mas cujo `subject` não existe mais em `im_user`** (ex: banco recriado) faz as rotas que checam permissão (`/sectors`, `/profiles`, `/permissions`,
  `/users/{id}/sectors/{sectorId}` e `PATCH /users/{id}/profile`) responderem `401 Unauthorized`,
  não `403`. É o único caso em que essas rotas devolvem 401 em vez de 403.
- **O corpo de erro nunca tem um campo `message`.** O backend não tem `@ControllerAdvice`/handler
  próprio, então quem responde é o tratamento padrão do Spring Boot, que por padrão omite a
  mensagem detalhada. O corpo de qualquer erro (400/401/403/404/409) tem sempre este formato:
  ```json
  {
    "timestamp": "2026-10-03T20:21:58.929+00:00",
    "status": 403,
    "error": "Forbidden",
    "path": "/sectors"
  }
  ```
  **Não dá para montar uma mensagem de erro para o usuário a partir do corpo da resposta** — use
  somente o status HTTP para decidir o que mostrar na tela.
- **CORS: só as origens configuradas em `CORS_ALLOWED_ORIGINS` chamam a API pelo navegador.** O
  padrão é `http://localhost:3000`; em produção, `https://inventariumifpe.me`. Uma origem não
  listada recebe `403` já no preflight. São aceitos apenas os headers `Authorization` e
  `Content-Type`; um header extra no cliente derruba o preflight. `Retry-After` e
  `Content-Disposition` são legíveis pelo navegador. Não há cookies nem credenciais de navegador.
- Todos os campos de resposta estão em `camelCase` e batem exatamente com os nomes abaixo (são os
  nomes dos componentes dos records Java que viram o JSON).

## Modelo de permissões

Hoje existem só 3 permissões, seedadas automaticamente, e cada perfil seedado tem no máximo uma:

| Permissão | Perfil que tem | O que libera hoje |
| --- | --- | --- |
| `ADMIN` | `ADMIN_ORGANIZATION` | Vê e gerencia tudo: setores, usuários (perfil, alocação, ativar e desativar), catálogos, inventários e itens de qualquer setor. |
| `MANAGE_SECTOR` | `GESTOR_SETOR` | Vê os setores e inventários a que está vinculado; cria, edita, finaliza e exclui inventários desses setores, importa e limpa itens; atribui perfil a usuários dos seus setores **só entre perfis sem permissão de gestão** (Operador e Consulta) e aloca usuários nos próprios setores; **não ativa nem desativa usuários**; lê os catálogos de perfis e permissões. |
| `MANAGE_ITEM` | `OPERADOR_CAMPO` | Vê os inventários do seu setor e opera os itens: valida (escaneia) e registra observações. Não cria, edita nem finaliza inventários e não altera os dados do item. |
| (nenhuma) | `CONSULTA` | Somente leitura nos inventários e setores a que está vinculado. |

**Usuário só nasce no primeiro login pelo Google, sem perfil.** Não há cadastro nem pré-cadastro de usuário na
API (`POST /users` não existe): quem entra pela primeira vez recebe `profileId: null`, nenhuma permissão e nenhum
setor, até que um **administrador** atribua o perfil (`PATCH /users/{id}/profile`) e aloque os setores. A troca de
perfil exige `profileId` (`400` sem ele): não existe "voltar a sem perfil". Contas antigas podem ter `CONSULTA`.
**Consequência para o gestor:** ele só enxerga os usuários que já estão nos seus setores, então um usuário recém-criado
(sem setor) só aparece para o administrador, que faz a primeira alocação.
Perfis e permissões são definidos pelas migrations e seus catálogos são
somente para consulta. Alterações na política entram por código e migration. O admin pode atribuir um
perfil existente ao usuário em `/users`, mas não mudar as permissões daquele perfil pela API.

**A conta de sistema (`admin`) nunca perde o perfil `ADMIN_ORGANIZATION` nem a permissão `ADMIN`,
por ação de ninguém, nem de outro admin.** Qualquer outro usuário, mesmo com `ADMIN`, pode ter o
perfil trocado ou removido por outro admin.

---

## `POST /auth/login` — conta de sistema (admin)

Existe uma única conta que entra por usuário e senha, sem Google: a conta de sistema, com o
perfil `ADMIN_ORGANIZATION` (portanto `ADMIN`). Todos os outros usuários continuam entrando só pelo
Google (`POST /auth/google`). Sem `Authorization`.

```json
{ "username": "admin", "password": "<senha configurada no backend>" }
```

- Resposta `200`: a mesma do login Google, `{ "token": "<JWT 24h>", "user": { "id", "name", "email" } }`.
  O `email` dessa conta é o próprio identificador de login (ex: `admin`, sem `@`). O token é usado
  exatamente como o do Google (`Authorization: Bearer ...`).
- `400`: `username` ou `password` ausentes/vazios.
- `401`: usuário inexistente, senha errada **ou** login por senha desligado no backend (sem senha
  configurada). A resposta é a mesma nos três casos, de propósito.
- `429`: muitas tentativas. Corpo `Too many attempts` e cabeçalho `Retry-After` (segundos até poder
  tentar de novo). Vem de **duas camadas**, ambas com `Retry-After` e CORS: o **backend** bloqueia o
  usuário por 15 min depois de 5 falhas em 15 min (vale **mesmo com a senha certa**, de qualquer IP); o
  **Nginx** freia o IP que faz muitas requisições seguidas ao `/auth/login` (10 por minuto, rajada de
  10; passou disso, `429` com `Retry-After` curto até o ritmo cair, sem bloqueio longo).
  `/auth/google` e `/auth/me` **não** têm limite de tentativas. Mostre uma mensagem própria (o
  frontend usa "Conta bloqueada, tente novamente mais tarde.", sem informar o
  tempo) em vez de tratar como erro genérico. O `Retry-After` é legível pelo navegador (CORS expõe o
  cabeçalho), caso a tela queira usá-lo.
- A senha e o identificador vêm de `SYSTEM_ADMIN_PASSWORD`/`SYSTEM_ADMIN_USERNAME` no `.env` do
  backend; o front não tem como saber se o login por senha está ligado, só tentar.
- Essa conta **não aparece** em `GET /users` nem em `GET /sectors/{id}/users`, e as rotas que mexem em
  usuário por id (`PATCH /users/{id}/profile`, `POST`/`DELETE /users/{id}/sectors/{sectorId}`)
  respondem `404` para ela, como se não existisse. Ela só entra pelo login e é lida por `/auth/me`.

---

## `/sectors`

| Método e rota | Permissão exigida | Sucesso |
| --- | --- | --- |
| `POST /sectors` | `ADMIN` | `201` |
| `GET /sectors` | qualquer usuário autenticado (`ADMIN` vê todos; os demais, os setores a que estão vinculados) | `200` |
| `GET /sectors/{id}` | `ADMIN` ou usuário vinculado ao setor | `200` |
| `PUT /sectors/{id}` | `ADMIN` | `200` |
| `PATCH /sectors/{id}/activate` | `ADMIN` | `200` |
| `PATCH /sectors/{id}/deactivate` | `ADMIN` | `200` |
| `DELETE /sectors/{id}` | `ADMIN` | `204` |
| `GET /sectors/{id}/users` | `ADMIN` ou `MANAGE_SECTOR` vinculado ao setor | `200` |

### Excluir setor (`DELETE /sectors/{id}`)

Só `ADMIN` exclui (gestor, operador e consulta recebem `403`). A exclusão só vale para setor **sem usuário
alocado, sem inventário e sem auditoria de item** associados; senão a API responde `409` e o setor segue
existindo, podendo apenas ser ativado ou desativado. Sem corpo na requisição e na resposta (`204`).

Para não depender de tentativa e erro, todo `SectorDto` traz `deletable`: `true` quando a exclusão seria
aceita agora. Mostre o botão "Excluir" só para `ADMIN` e só quando `deletable` for `true`; a regra é
conferida de novo no `DELETE` (outro usuário pode ter alocado alguém no meio-tempo).

### Corpo de `POST`/`PUT`

```json
{
  "name": "Patrimônio",
  "code": "PAT"
}
```

- `name`: obrigatório, não pode ser vazio/em branco, máximo 255 caracteres, **precisa ser único**
  entre todos os setores (não só entre "irmãos" — não existe mais hierarquia de setor).
- `code`: opcional, máximo 100 caracteres, sem checagem de unicidade.
- `active` não é aceito no corpo — muda só pelos endpoints de activate/deactivate.

### Resposta (`SectorDto`)

```json
{
  "id": 1,
  "name": "Patrimônio",
  "code": "PAT",
  "active": true,
  "createdAt": "2026-10-03T20:21:58.123",
  "updatedAt": "2026-10-03T20:21:58.123",
  "deletable": true
}
```

`deletable` é só de leitura (ignorado no corpo de `POST`/`PUT`).

`GET /sectors` devolve **todos os setores, ativos e inativos, junto**. Se uma tela (ex: dropdown
de alocação de usuário) só deve oferecer setores ativos, o filtro por `active` precisa ser feito
no front — a API não tem um parâmetro para isso hoje.

### Erros

- `400`: `name` vazio/em branco, `name`/`code` passando do tamanho máximo, `name` duplicado.
- `403`: usuário autenticado sem `ADMIN` (escrita) ou sem `ADMIN`/`MANAGE_SECTOR` (leitura).
- `404`: setor não existe (`GET`/`PUT`/`activate`/`deactivate`/`DELETE`/`users` por id).
- `409`: `DELETE` de setor que tem usuário, inventário ou auditoria associados.

### `GET /sectors/{id}/users` — usuários alocados a este setor

Exige `ADMIN` ou `MANAGE_SECTOR` vinculado a esse setor (operador e consulta recebem `403`). É a rota
para a tela de setor abrir a aba lateral com quem está alocado ali. Devolve um array no mesmo formato
do `GET /users` (ver seção `/users` abaixo). `404` se o setor não existe.

---

## `/users` — listagem e alocação a setor

Um usuário pode estar alocado a **vários setores ao mesmo tempo** (tabela `im_sector_allocation`)
— não existe mais um único "setor do usuário".

### `GET /users?profileId=`

**Permissão: `ADMIN` ou `MANAGE_SECTOR`.** O admin recebe todos os usuários; o gestor recebe apenas os usuários
alocados a algum dos seus setores. Devolve um array de usuário no formato:

```json
{
  "id": 7,
  "name": "Maria Silva",
  "email": "maria.silva@ifpe.edu.br",
  "sectorIds": [1, 4],
  "profileId": 2,
  "active": true
}
```

`sectorIds` é sempre uma lista (vazia se o usuário não estiver alocado a nenhum setor).
`profileId` vem `null` para quem entrou pelo Google e ainda não recebeu perfil; `active` é `false` para conta
desativada. O campo `pendingGoogleLogin` foi removido junto com o pré-cadastro. `googleId`, `createdAt` e `updatedAt`
nunca aparecem no JSON de usuário (isso já era assim antes, não é uma mudança desta revisão).

**Não existe mais filtro por setor aqui** (`?sectorId=` saiu). Para ver quem está alocado a um
setor específico, use `GET /sectors/{id}/users`.

A conta de sistema (`admin`) **não vem na lista**, nem com `?profileId=` do perfil
`ADMIN_ORGANIZATION`. Não conte com ela para saber quem é administrador.

Erro: `403` para quem não é `ADMIN` nem `MANAGE_SECTOR`.

### `POST /users/{id}/sectors/{sectorId}` — alocar usuário a um setor

Sem corpo. **Permissão**: autorizado se quem está autenticado tem `ADMIN` (qualquer setor), **ou**
tem `MANAGE_SECTOR` e já está alocado nesse mesmo `{sectorId}` — ou seja, um `GESTOR_SETOR` só
aloca gente nos setores em que ele próprio está. Sem nenhuma das duas condições, `403`. O gestor só vincula usuários sem permissão de gestão
(Operador e Consulta); para os demais, `403`.
**Recomendação de UX**: para um usuário `GESTOR_SETOR`, só ofereça na UI os setores em que ele
está alocado como opção de destino — a chamada para qualquer outro setor vai dar 403.

Idempotente: alocar quem já está alocado a esse setor não dá erro, só devolve o estado atual sem
duplicar nada.

Resposta: o `User` atualizado (`200`), mesmo formato do `GET /users`.

Erros: `404` usuário ou setor não existe (ou o usuário é a conta de sistema); `403` conforme acima.

### `DELETE /users/{id}/sectors/{sectorId}` — desalocar usuário de um setor

Mesma permissão do `POST` (`ADMIN`, ou `MANAGE_SECTOR` estando alocado nesse `{sectorId}`).
Idempotente: desalocar quem não está alocado a esse setor não dá erro, só devolve o estado atual.
Remove só a alocação para `{sectorId}` — os outros setores do usuário, se houver, não mudam.

Resposta: o `User` atualizado (`200`). Erros: `404` usuário ou setor não existe (ou o usuário é a
conta de sistema); `403` conforme acima.

### `PATCH /users/{id}/profile` — atribuir perfil

Corpo: `{ "profileId": 5 }`. O `profileId` é obrigatório: `null` responde `400`, porque não existe usuário
sem perfil.

**Permissão: `ADMIN` ou `MANAGE_SECTOR`.** O admin atribui qualquer perfil a qualquer usuário, inclusive a
outro admin e a si mesmo. O gestor só altera usuários **sem permissão de gestão que compartilham um setor
com ele** e só atribui perfis **sem permissão de gestão** (Operador e Consulta); fora disso, `403`. Operador
e consulta recebem `403`. **A conta de sistema é a exceção**: o perfil dela não pode ser alterado por aqui
(responde `404`, como se ela não existisse — ela também não aparece em `GET /users`).

Resposta: o `User` atualizado. Erros: `403` sem permissão; `404` usuário não existe (ou é a conta de
sistema); `400` `profileId` ausente ou inexistente.

### `PATCH /users/{id}/deactivate` e `PATCH /users/{id}/activate`

**Permissão: `ADMIN`.** Desativar bloqueia o acesso imediatamente (sessões abertas passam a responder `401` e o login pelo Google é
recusado) e preserva o histórico; ativar devolve o acesso. Resposta: o `User` atualizado, com `active`.

**Regras de `deactivate` e `activate`:** exigem **`ADMIN`** (`403` para qualquer outro perfil, inclusive o gestor,
mesmo para usuários do próprio setor); ninguém age sobre a própria conta (`409`); a conta de sistema e ids
inexistentes respondem `404`. Quem tem a conta desativada recebe `401` em qualquer chamada com o token que já tinha.

**Não existe excluir usuário.** `POST /users` e `DELETE /users/{id}` foram removidos: usuário só se ativa e
desativa, e o histórico (inventários e auditoria) é sempre preservado.

---

## `/profiles` — somente leitura

A consulta exige `ADMIN` ou `MANAGE_SECTOR` (o gestor usa o catálogo ao cadastrar usuários); operador e
consulta recebem `403`.

| Método e rota | Comportamento |
| --- | --- |
| `GET /profiles` | Lista os perfis, descrições e permissionIds (`200`) |
| `GET /profiles/{id}` | Consulta um perfil (`200` ou `404`) |
| `POST /profiles` | Cadastro indisponível (`405` para ADMIN) |
| `PUT/PATCH/DELETE /profiles/{id}` | Alterações indisponíveis (`405` para ADMIN) |
| `POST/DELETE /profiles/{id}/permissions/{permissionId}` | Associação e remoção indisponíveis (`405` para ADMIN) |

Os quatro perfis nativos são `ADMIN_ORGANIZATION`, `GESTOR_SETOR`, `OPERADOR_CAMPO`
e `CONSULTA`. O catálogo e seus vínculos de permissões são definidos pelas migrations.
Dados existentes são preservados; não há exclusão ou redefinição automática de perfis legados.

`ProfileDto` retorna `id`, `name`, `description`, `permissionIds`, `createdAt` e `updatedAt`.
A interface exibe uma tabela com busca e um link para gerenciar usuários. A atribuição de
perfil ao usuário continua em `PATCH /users/{id}/profile` (ver as regras acima).

Este bloqueio protege o catálogo. As regras de acesso a inventários e itens são aplicadas em cada
rota: leitura por setor para todo perfil; criar, editar, finalizar, excluir, importar e limpar itens só para
`ADMIN` ou gestor do setor; validar itens e registrar observações também para o operador. Inventário
finalizado não aceita validação, importação, remoção nem limpeza de itens, e cada inventário aceita uma única
importação (para importar outra planilha, limpe os itens antes).

---
## `/permissions` — somente leitura

**Exige `ADMIN` ou `MANAGE_SECTOR`** (`403` para os demais). Não existe criar, editar nem excluir permissão pela API:
`POST /permissions`, `PUT /permissions/{id}` e `DELETE /permissions/{id}` **foram removidos e
respondem `405`**. As permissões são definidas em código; uma nova entra por task + migration, não
por tela.

| Método e rota | O que faz |
| --- | --- |
| `GET /permissions` | Lista todas |
| `GET /permissions/{id}` | Busca por id (`404` se não existe) |

Resposta (`PermissionDto`): `{ "id": 1, "name": "ADMIN", "description": "..." }`. A tela de perfis
usa esta lista para mostrar quais permissões podem ser associadas a um perfil.

Permissões seedadas hoje: `ADMIN`, `MANAGE_SECTOR`, `MANAGE_ITEM` (só 3 — nomes mudaram de uma
versão anterior do seed; se algo no front ou em anotações antigas citar `SECTOR_MANAGE`,
`USER_ASSIGN_SECTOR` ou `USER_ASSIGN_PROFILE`, está desatualizado).

---

## Resumo das mudanças de contrato (versão 1.6, issue #205)

Para quem já tinha algo montado em cima da versão anterior:

- `/profiles`, `/permissions` e `PATCH /users/{id}/profile` **passaram a exigir `ADMIN`**. Antes
  qualquer usuário autenticado podia chamá-las; agora os demais recebem `403`.
- `POST`, `PUT` e `DELETE /permissions` **foram removidos** (`405`). `GET` continua.
- Novos `409` em `/profiles`: renomear, excluir ou tirar a permissão `ADMIN` do perfil
  `ADMIN_ORGANIZATION`.
- Criar ou renomear um perfil para um nome que só difere por espaços nas pontas agora responde `400`
  (antes podia responder `500`).

## O que ainda não existe (não contar com isso)

- Endpoint para listar ou contar os usuários de um perfil (use `GET /users?profileId=`, que não
  traz a conta de sistema).
- Histórico/fluxo de validação entre setores.

## Histórico

| Versão | Data | Descrição |
| --- | --- | --- |
| 1.0 | 2026-10-03 | Criação do documento, cobrindo o estado da API após as issues #179 e #181. |
| 1.1 | 2026-10-03 | `GET /users` passa a exigir `ADMIN` (issue #185). |
| 1.2 | 2026-10-03 | Usuário pode estar alocado a vários setores ao mesmo tempo: `PATCH /users/{id}/sector` sai, entram `POST`/`DELETE /users/{id}/sectors/{sectorId}` e `GET /sectors/{id}/users`; `GET /users` perde o filtro `?sectorId=`; resposta de usuário troca `sectorId` por `sectorIds` (issue #187). |
| 1.3 | 2026-10-10 | Documenta `POST /auth/login` da conta de sistema (issue #192). |
| 1.4 | 2026-10-11 | `POST /auth/login` passa a responder `429` com `Retry-After` após falhas repetidas (issue #201). |
| 1.5 | 2026-10-11 | A conta de sistema sai de `GET /users` e `GET /sectors/{id}/users`, e as rotas de perfil e alocação respondem `404` para ela (issue #202). |
| 1.6 | 2026-10-11 | `/profiles`, `/permissions` e `PATCH /users/{id}/profile` passam a exigir `ADMIN`; `/permissions` vira somente leitura (`POST`/`PUT`/`DELETE` removidos); o perfil `ADMIN_ORGANIZATION` fica protegido (409); nome de perfil com `trim` (issue #205). |
| 1.7 | 2026-10-18 | O `429` de `POST /auth/login` passa a vir de duas camadas (Nginx por IP e backend por usuário); `/auth/google` e `/auth/me` ficam sem limite de tentativas (issue #228). |
| 1.8 | 2026-10-18 | `POST /users` e `DELETE /users/{id}` removidos (usuário só nasce no primeiro login Google, **sem perfil**, e nunca é excluído); `PATCH /users/{id}/activate` e `/deactivate` passam a exigir `ADMIN`; o campo `pendingGoogleLogin` sai de `UserDto` (issue #232). |
| 1.9 | 2026-10-18 | Entra `DELETE /sectors/{id}` (só `ADMIN`, `409` se o setor tiver usuário, inventário ou auditoria) e o campo `deletable` em `SectorDto` (issue #234). |
