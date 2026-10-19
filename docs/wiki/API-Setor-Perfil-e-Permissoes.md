# API de Setor, Usuário, Cargo e Permissão

Este documento descreve, para quem for implementar o front-end, todas as rotas de `/sectors`,
`/users` (listagem, alocação com cargo e administrador), `/roles` e `/permissions` como elas existem
hoje em `main`. O **cargo vale por alocação** (usuário em setor): o mesmo usuário pode ter cargos
diferentes em setores diferentes, e **administrador** é uma coluna do usuário, fora dos cargos (versão
2.0, sub-issue de #154). Do `/auth`, cobre só o
`POST /auth/login` da conta de sistema (issue #192). **Não cobre** `/inventories` nem `/items` —
essas rotas não foram tocadas por este trabalho.

## Antes de implementar: comportamentos não óbvios

- **Toda rota abaixo exige `Authorization: Bearer <jwt>`.** Sem header, a resposta é `403
  Forbidden` — não `401` — porque o `SecurityConfig` ainda não tem um `AuthenticationEntryPoint`
  customizado (é um TODO em aberto no backend).
- **Um JWT com assinatura válida mas cujo `subject` não existe mais em `im_user`** (ex: banco recriado) faz as rotas que checam permissão (`/sectors`, `/roles`, `/permissions`,
  `/users/{id}/sectors/{sectorId}` e `PATCH /users/{id}/administrator`) responderem `401 Unauthorized`,
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

## Modelo de acesso

Dois conceitos separados:

- **Administrador** (`administrator: true` no usuário): vê e gerencia tudo, em todos os setores: setores,
  usuários (cargo por setor, administrador, ativar e desativar), cargos, inventários e itens. É um poder global,
  fora do catálogo de permissões.
- **Cargo por alocação**: quem não é administrador só age nos setores em que está alocado, e o que pode fazer em
  cada um vem do **cargo que tem naquele setor**. Um cargo é um conjunto de permissões escolhido pelo
  administrador. O código só confere permissões, nunca o nome do cargo.

O catálogo de permissões é fixo (definido em código e migration) e tem 2 permissões:

| Permissão | O que libera no setor em que o cargo vale |
| --- | --- |
| `MANAGE_SECTOR` | Cria, edita, finaliza e exclui inventários do setor, importa e limpa itens, edita os dados dos itens; aloca e desaloca usuários no próprio setor, **só com cargos sem `MANAGE_SECTOR`**; lê a lista de cargos e permissões. |
| `MANAGE_ITEM` | Vê os inventários do setor e opera os itens: valida (escaneia) e registra observações. Não cria, edita nem finaliza inventários e não altera os dados do item. |
| (nenhuma) | Cargo sem permissões: somente leitura nos inventários e no setor. |

Os cargos de origem (criados pela migration 7.0) são **Gestor de Setor** (`MANAGE_SECTOR`), **Operador de Campo**
(`MANAGE_ITEM`) e **Consulta** (sem permissões), mas são só dados: o administrador pode renomeá-los, mudar as
permissões e criar outros. **Cargo de gestão** é o que tem `MANAGE_SECTOR`.

**Exemplo:** o mesmo usuário é Gestor de Setor no setor A e Operador de Campo no setor B. No A ele gerencia os
inventários; no B só valida itens (nada de criar, editar nem importar). O servidor confere o cargo do setor do
recurso a cada chamada.

**Usuário só nasce no primeiro login pelo Google, sem alocação e sem ser administrador.** Não há cadastro nem
pré-cadastro de usuário na API (`POST /users` não existe): quem entra pela primeira vez recebe `administrator: false`,
`allocations: []` e nenhum setor, ou seja, sem acesso a nada, até que alguém o aloque em um setor com um cargo
(`PUT /users/{id}/sectors/{sectorId}`). Quem aloca é o **administrador**, ou o **gestor do setor**.
**Consequência para o gestor:** ele só enxerga os usuários que já estão nos setores que ele gerencia, então um usuário
recém-criado (sem setor) só aparece para o administrador, que faz a primeira alocação.

**A conta de sistema (`admin`) é sempre administradora e nunca perde isso, por ação de ninguém, nem de outro
administrador** (as rotas por id respondem `404` para ela). Ninguém altera o próprio papel de administrador (`409`), e
como quem age é sempre um administrador ativo e diferente do alvo, o sistema nunca fica sem administrador.

---

## `POST /auth/login` — conta de sistema (admin)

Existe uma única conta que entra por usuário e senha, sem Google: a conta de sistema, com o
papel de administrador. Todos os outros usuários continuam entrando só pelo
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
  usuário por id (`PATCH /users/{id}/administrator`, `PUT`/`DELETE /users/{id}/sectors/{sectorId}`)
  respondem `404` para ela, como se não existisse. Ela só entra pelo login e é lida por `/auth/me`.

---

## `/sectors`

| Método e rota | Permissão exigida | Sucesso |
| --- | --- | --- |
| `POST /sectors` | administrador | `201` |
| `GET /sectors` | qualquer usuário autenticado (o administrador vê todos; os demais, os setores em que estão alocados) | `200` |
| `GET /sectors/{id}` | administrador ou usuário alocado ao setor (qualquer cargo) | `200` |
| `PUT /sectors/{id}` | administrador | `200` |
| `PATCH /sectors/{id}/activate` | administrador | `200` |
| `PATCH /sectors/{id}/deactivate` | administrador | `200` |
| `DELETE /sectors/{id}` | administrador | `204` |
| `GET /sectors/{id}/users` | administrador ou gestor (`MANAGE_SECTOR`) do setor | `200` |
| `GET /sectors/{id}/assignable-users` | administrador ou gestor (`MANAGE_SECTOR`) do setor | `200` |
| `PUT /sectors/{id}/members` | administrador ou gestor (`MANAGE_SECTOR`) do setor | `200` |

### Excluir setor (`DELETE /sectors/{id}`)

Só o administrador exclui (gestor, operador e consulta recebem `403`). A exclusão só vale para setor **sem usuário
alocado, sem inventário e sem auditoria de item** associados; senão a API responde `409` e o setor segue
existindo, podendo apenas ser ativado ou desativado. Sem corpo na requisição e na resposta (`204`).

Para não depender de tentativa e erro, todo `SectorDto` traz `deletable`: `true` quando a exclusão seria
aceita agora. Mostre o botão "Excluir" só para o administrador e só quando `deletable` for `true`; a regra é
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
- `403`: usuário autenticado que não é administrador (escrita) ou sem acesso ao setor (leitura).
- `404`: setor não existe (`GET`/`PUT`/`activate`/`deactivate`/`DELETE`/`users` por id).
- `409`: `DELETE` de setor que tem usuário, inventário ou auditoria associados.

### `GET /sectors/{id}/users` — usuários alocados a este setor

Exige administrador ou gestor (`MANAGE_SECTOR`) daquele setor (operador e consulta recebem `403`). É a rota
para a tela de setor abrir a aba lateral com quem está alocado ali. Devolve um array no mesmo formato
do `GET /users` (ver seção `/users` abaixo). `404` se o setor não existe.

### `GET /sectors/{id}/assignable-users` — quem pode ser alocado a este setor

Exige administrador ou gestor (`MANAGE_SECTOR`) daquele setor (`403` para os demais, `404` se o setor não existe).
Devolve os usuários **ativos** que **ainda não estão no setor**, ordenados por nome, **só com `id`, `name` e `email`**
(sem cargos, setores nem papel de administrador). A conta de sistema não vem. É para a tela de setores montar a
lista de quem pode entrar: o gestor vê todos os usuários ativos do sistema (inclusive quem acabou de entrar e ainda
não tem setor), mas só nome e e-mail.

```json
[{ "id": 7, "name": "Maria Silva", "email": "maria.silva@ifpe.edu.br" }]
```

### `PUT /sectors/{id}/members` — alocar vários usuários com o mesmo cargo

Corpo: `{ "userIds": [7, 9], "roleId": 12 }`. Aloca todos os usuários no setor com o cargo informado, **tudo ou nada**:
se um só não puder ser alocado, nenhum é. Quem já está no setor tem o cargo trocado. Resposta (`200`): os membros do
setor, no formato de `GET /sectors/{id}/users`.

**Permissão:** administrador, ou gestor daquele setor. O gestor só atribui cargos **sem** `MANAGE_SECTOR` e não mexe
em outro gestor do setor nem em administrador (`403`).

Erros: `403` conforme acima; `404` setor, usuário inexistente ou conta de sistema; `400` sem `roleId`, cargo
inexistente, `userIds` vazio, com itens vazios ou repetidos, ou com mais de 200 usuários. Para trocar o cargo ou
remover **um** usuário use `PUT`/`DELETE /users/{id}/sectors/{sectorId}`.

---

## `/users` — listagem, alocação com cargo e administrador

Um usuário pode estar alocado a **vários setores ao mesmo tempo** (tabela `im_sector_allocation`), com um **cargo
em cada um**. Não existe "o setor do usuário" nem "o perfil do usuário".

### `GET /users?roleId=`

**Permissão: administrador ou gestor (`MANAGE_SECTOR`) de algum setor.** O administrador recebe todos os usuários;
o gestor recebe apenas os usuários alocados a algum dos setores que ele gerencia. Com `roleId`, só quem tem esse
cargo em algum setor (no escopo de quem pergunta). Devolve um array de usuário no formato:

```json
{
  "id": 7,
  "name": "Maria Silva",
  "email": "maria.silva@ifpe.edu.br",
  "sectorIds": [1, 4],
  "allocations": [
    { "sectorId": 1, "roleId": 12, "roleName": "Gestor de Setor", "permissions": ["MANAGE_SECTOR"] },
    { "sectorId": 4, "roleId": 14, "roleName": "Consulta", "permissions": [] }
  ],
  "administrator": false,
  "active": true
}
```

`sectorIds` e `allocations` vêm ordenados por setor; vazios se o usuário não estiver alocado em nenhum. `administrator`
é o papel global. `active` é `false` para conta desativada. `googleId`, `createdAt` e `updatedAt` nunca aparecem no
JSON de usuário.

**Não existe filtro por setor aqui** (`?sectorId=` saiu). Para ver quem está alocado a um setor específico, use
`GET /sectors/{id}/users`.

A conta de sistema (`admin`) **não vem na lista**. Não conte com ela para saber quem é administrador.

Erro: `403` para quem não é administrador nem gestor de algum setor.

### `PUT /users/{id}/sectors/{sectorId}` — alocar usuário a um setor, ou trocar o cargo dele nele

Corpo: `{ "roleId": 12 }`. O cargo é **obrigatório** (`400` sem `roleId` ou com um cargo inexistente): não existe
alocação sem cargo. Se o usuário já está alocado ao setor, o cargo dele nele é trocado (e a chamada com o mesmo
cargo não muda nada); senão a alocação é criada. Os cargos do usuário em outros setores não mudam.

**Permissão:** o administrador, em qualquer setor; ou o gestor (`MANAGE_SECTOR`) daquele `{sectorId}`. O gestor só
mexe com usuários comuns, só atribui cargos **sem** `MANAGE_SECTOR` e nunca altera quem já é gestor naquele setor
nem um administrador (`403`). Sem nenhuma dessas condições, `403`.
**Recomendação de UX:** para o gestor, só ofereça os setores que ele gerencia e os cargos sem gestão como opção.

Resposta: o usuário atualizado (`200`), mesmo formato do `GET /users`.

Erros: `404` usuário ou setor não existe (ou o usuário é a conta de sistema); `400` cargo ausente ou inexistente;
`403` conforme acima.

### `DELETE /users/{id}/sectors/{sectorId}` — desalocar usuário de um setor

Mesma permissão do `PUT` (administrador, ou gestor daquele setor, que não remove outro gestor). Idempotente:
desalocar quem não está alocado a esse setor não dá erro, só devolve o estado atual. Remove só a alocação para
`{sectorId}` — os outros setores do usuário, se houver, não mudam.

Resposta: o usuário atualizado (`200`). Erros: `404` usuário ou setor não existe (ou o usuário é a conta de
sistema); `403` conforme acima.

### `PATCH /users/{id}/administrator` — conceder ou retirar o papel de administrador

Corpo: `{ "administrator": true }` (`400` sem o campo). **Permissão: administrador.** Ninguém altera o próprio papel
(`409`); a conta de sistema e ids inexistentes respondem `404`. Quem vira administrador passa a ver e gerenciar tudo,
em todos os setores, sem depender de cargo.

Resposta: o usuário atualizado. Erros: `403` quem não é administrador; `404`; `409` na própria conta; `400`.

### `PATCH /users/{id}/deactivate` e `PATCH /users/{id}/activate`

**Permissão: administrador.** Desativar bloqueia o acesso imediatamente (sessões abertas passam a responder `401` e o
login pelo Google é recusado) e preserva o histórico; ativar devolve o acesso. Resposta: o usuário atualizado, com
`active`.

**Regras de `deactivate` e `activate`:** exigem **administrador** (`403` para qualquer outro, inclusive o gestor, mesmo
para usuários do próprio setor); ninguém age sobre a própria conta (`409`); a conta de sistema e ids inexistentes
respondem `404`. Quem tem a conta desativada recebe `401` em qualquer chamada com o token que já tinha.

**Não existe excluir usuário.** `POST /users` e `DELETE /users/{id}` foram removidos: usuário só se ativa e
desativa, e o histórico (inventários e auditoria) é sempre preservado.

---

## `/roles` — cargos

O administrador cria, edita e exclui cargos e escolhe as permissões de cada um. O gestor só **lê** a lista (para
escolher o cargo ao alocar alguém no setor); operador, consulta e quem não está alocado recebem `403`.

| Método e rota | Permissão exigida | Sucesso |
| --- | --- | --- |
| `GET /roles` | administrador ou gestor (`MANAGE_SECTOR`) de algum setor | `200` |
| `GET /roles/{id}` | administrador | `200` ou `404` |
| `POST /roles` | administrador | `201` |
| `PUT /roles/{id}` | administrador | `200` |
| `DELETE /roles/{id}` | administrador | `204` |

Corpo de `POST`/`PUT`: `{ "name": "Auditoria", "description": "…", "permissionIds": [2] }`. `name`: obrigatório,
máximo 100 caracteres, **único** entre os cargos (`400` se vazio, longo ou repetido); `description`: opcional, até
255; `permissionIds`: ids do catálogo de `/permissions` (`400` se algum não existir; lista vazia = cargo somente
leitura).

Resposta (`RoleDto`):

```json
{
  "id": 12,
  "name": "Gestor de Setor",
  "description": "Gerencia os inventários e os usuários do setor.",
  "permissionIds": [1],
  "management": true,
  "allocationCount": 3,
  "userCount": 2,
  "deletable": false,
  "createdAt": "2026-10-03T20:21:58.123",
  "updatedAt": "2026-10-18T10:00:00.000"
}
```

- `management`: o cargo tem `MANAGE_SECTOR`.
- `allocationCount` / `userCount`: em quantas alocações e para quantos usuários o cargo vale. Use para avisar o
  administrador do impacto antes de mudar as permissões de um cargo em uso: **a mudança vale na hora, para todas as
  alocações dele**, inclusive se tirar uma permissão.
- `deletable`: o cargo **não tem nenhuma alocação e nenhuma permissão**, o único caso em que pode ser excluído.

**Excluir cargo:** `409` se o cargo tiver alguma alocação (mensagem com a contagem) **ou** alguma permissão marcada
(retire as permissões antes, editando o cargo). `404` se não existe. Mostre "Excluir" só quando `deletable`.

Erros comuns: `403` quem não pode; `404` cargo inexistente (`GET`/`PUT`/`DELETE` por id); `400` validação do corpo;
`409` exclusão recusada.

---

## `/permissions` — somente leitura

A lista exige administrador ou gestor de algum setor (`403` para os demais); a consulta por id exige
administrador. Não existe criar, editar nem excluir permissão pela API: `POST /permissions`, `PUT /permissions/{id}`
e `DELETE /permissions/{id}` respondem `405`. As permissões são definidas em código; uma nova entra por task +
migration, não por tela.

| Método e rota | O que faz |
| --- | --- |
| `GET /permissions` | Lista todas |
| `GET /permissions/{id}` | Busca por id (`404` se não existe) |

Resposta (`PermissionDto`): `{ "id": 1, "name": "MANAGE_SECTOR", "description": "..." }`. A tela de cargos usa
esta lista para mostrar quais permissões podem ser marcadas em um cargo. Permissões hoje: `MANAGE_SECTOR` e
`MANAGE_ITEM` (a permissão `ADMIN` saiu: administrador virou coluna do usuário).

---

## Sessão (`GET /auth/me` e o `user` do login)

```json
{
  "id": 7,
  "name": "Maria Silva",
  "email": "maria.silva@ifpe.edu.br",
  "administrator": false,
  "sectorIds": [1, 4],
  "allocations": [
    { "sectorId": 1, "roleId": 12, "roleName": "Gestor de Setor", "permissions": ["MANAGE_SECTOR"] },
    { "sectorId": 4, "roleId": 14, "roleName": "Consulta", "permissions": [] }
  ]
}
```

O front decide o que mostrar **por setor**: gerenciar inventários de um setor = `administrator` ou `MANAGE_SECTOR`
na alocação daquele setor; operar itens = isso ou `MANAGE_ITEM`. Mostrar "Setores" = `administrator` ou gestor em
algum setor. O servidor confere de novo em cada rota.

---

## Resumo das mudanças de contrato (versão 2.0)

Para quem já tinha algo montado em cima da versão 1.x. **Mudança incompatível (major):**

- O perfil global do usuário sai: `profileId` some de `UserDto` e de `/auth/me`; entram `administrator` e
  `allocations` (cargo por setor). `permissions` (lista plana) some de `/auth/me`.
- `/profiles` virou `/roles` (CRUD, só o administrador altera). `?profileId=` virou `?roleId=`.
- `PATCH /users/{id}/profile` foi removido; o cargo é escolhido ao alocar: `POST /users/{id}/sectors/{sectorId}`
  virou `PUT` com `{ "roleId": … }` (obrigatório; também troca o cargo se já alocado).
- Entra `PATCH /users/{id}/administrator`. A permissão `ADMIN` e o perfil `ADMIN_ORGANIZATION` saíram do catálogo.
- A migration 7.0 converte os dados: cada alocação recebe o perfil que o usuário tinha como cargo (sem perfil vira
  Consulta); quem era `ADMIN_ORGANIZATION` vira administrador (e as alocações dele viram Gestor de Setor).

## O que ainda não existe (não contar com isso)

- Histórico de quem alterou cargos e permissões, e permissões mais finas que as duas atuais.
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
| 2.0 | 2026-10-18 | **Major.** Cargo por alocação no lugar do perfil global; `/profiles` vira `/roles` com CRUD só para o administrador (excluir só sem alocação e sem permissão); `PUT /users/{id}/sectors/{sectorId}` com `roleId` obrigatório; `PATCH /users/{id}/administrator`; administrador vira coluna do usuário e a permissão `ADMIN` sai; `/auth/me` traz `administrator` e `allocations` (sub-issue de #154). |
| 2.1 | 2026-10-19 | Entram `GET /sectors/{id}/assignable-users` (só `id`, `name` e `email` de quem pode ser alocado) e `PUT /sectors/{id}/members` (alocação em lote com um cargo, tudo ou nada); nada existente muda de contrato (sub-issue de #154). |
