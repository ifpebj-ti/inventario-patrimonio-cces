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
- **Um JWT com assinatura válida mas cujo `subject` não existe mais em `im_user`** (ex: usuário
  excluído) faz as rotas que checam permissão (`/sectors`, `/profiles`, `/permissions`,
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
- Todos os campos de resposta estão em `camelCase` e batem exatamente com os nomes abaixo (são os
  nomes dos componentes dos records Java que viram o JSON).

## Modelo de permissões

Hoje existem só 3 permissões, seedadas automaticamente, e cada perfil seedado tem no máximo uma:

| Permissão | Perfil que tem | O que libera hoje |
| --- | --- | --- |
| `ADMIN` | `ADMIN_ORGANIZATION` | Escrita e leitura em `/sectors`; alocar/desalocar qualquer usuário em qualquer setor; `GET /users`; tudo em `/profiles` e `/permissions` (leitura); `PATCH /users/{id}/profile`. |
| `MANAGE_SECTOR` | `GESTOR_SETOR` | Leitura em `/sectors`; alocar/desalocar usuário **só nos setores em que o próprio ator está alocado**. |
| `MANAGE_ITEM` | `OPERADOR_CAMPO` | Seedada, mas **nenhum endpoint checa essa permissão ainda** (Item/Inventory ficam para uma etapa futura). |
| (nenhuma) | `CONSULTA` | Não dá acesso a nada que exija permissão. |

Um usuário sem perfil atribuído (`profileId: null`) não tem nenhuma permissão. **Perfis** são
configuráveis via `/profiles` (um perfil pode ter qualquer combinação de permissões), mas
**permissões não**: elas são definidas em código e `/permissions` é só leitura. Uma permissão nova
só entra por task + migration. A tabela acima é o estado do seed dos perfis, não uma regra fixa no
código — exceto `ADMIN_ORGANIZATION`, que é protegido (ver `/profiles`).

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
  tentar de novo). O bloqueio vale por usuário (5 falhas em 15 min) e por IP (10 falhas em 15 min),
  dura 15 min e vale **mesmo com a senha certa**. Mostre uma mensagem própria ("Muitas tentativas,
  tente de novo em N minutos") em vez de tratar como erro genérico. O `Retry-After` é legível pelo
  navegador (CORS expõe o cabeçalho).
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
| `GET /sectors` | `ADMIN` ou `MANAGE_SECTOR` | `200` |
| `GET /sectors/{id}` | `ADMIN` ou `MANAGE_SECTOR` | `200` |
| `PUT /sectors/{id}` | `ADMIN` | `200` |
| `PATCH /sectors/{id}/activate` | `ADMIN` | `200` |
| `PATCH /sectors/{id}/deactivate` | `ADMIN` | `200` |
| `GET /sectors/{id}/users` | `ADMIN` ou `MANAGE_SECTOR` | `200` |

**Não existe `DELETE /sectors/{id}`.** Setor nunca é removido — só ativado/desativado via os dois
`PATCH` acima. Não implemente um botão de "excluir setor" na UI.

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
  "updatedAt": "2026-10-03T20:21:58.123"
}
```

`GET /sectors` devolve **todos os setores, ativos e inativos, junto**. Se uma tela (ex: dropdown
de alocação de usuário) só deve oferecer setores ativos, o filtro por `active` precisa ser feito
no front — a API não tem um parâmetro para isso hoje.

### Erros

- `400`: `name` vazio/em branco, `name`/`code` passando do tamanho máximo, `name` duplicado.
- `403`: usuário autenticado sem `ADMIN` (escrita) ou sem `ADMIN`/`MANAGE_SECTOR` (leitura).
- `404`: setor não existe (`GET`/`PUT`/`activate`/`deactivate`/`users` por id).

### `GET /sectors/{id}/users` — usuários alocados a este setor

Mesma permissão de leitura de `/sectors` (`ADMIN` ou `MANAGE_SECTOR`, sem precisar estar alocado
nesse setor específico — é a mesma regra de `GET /sectors/{id}`). É a rota para a tela de setor
abrir a aba lateral com quem está alocado ali. Devolve um array no mesmo formato do `GET /users`
(ver seção `/users` abaixo). `404` se o setor não existe.

---

## `/users` — listagem e alocação a setor

Um usuário pode estar alocado a **vários setores ao mesmo tempo** (tabela `im_sector_allocation`)
— não existe mais um único "setor do usuário".

### `GET /users?profileId=`

**Permissão: `ADMIN`.** Devolve um array de usuário no formato:

```json
{
  "id": 7,
  "name": "Maria Silva",
  "email": "maria.silva@ifpe.edu.br",
  "sectorIds": [1, 4],
  "profileId": 2
}
```

`sectorIds` é sempre uma lista (vazia se o usuário não estiver alocado a nenhum setor).
`profileId` vem `null` quando o usuário não tem perfil. `googleId`, `createdAt` e `updatedAt`
nunca aparecem no JSON de usuário (isso já era assim antes, não é uma mudança desta revisão).

**Não existe mais filtro por setor aqui** (`?sectorId=` saiu). Para ver quem está alocado a um
setor específico, use `GET /sectors/{id}/users`.

A conta de sistema (`admin`) **não vem na lista**, nem com `?profileId=` do perfil
`ADMIN_ORGANIZATION`. Não conte com ela para saber quem é administrador.

Erro: `403` se quem está autenticado não tem `ADMIN`.

### `POST /users/{id}/sectors/{sectorId}` — alocar usuário a um setor

Sem corpo. **Permissão**: autorizado se quem está autenticado tem `ADMIN` (qualquer setor), **ou**
tem `MANAGE_SECTOR` e já está alocado nesse mesmo `{sectorId}` — ou seja, um `GESTOR_SETOR` só
aloca gente nos setores em que ele próprio está. Sem nenhuma das duas condições, `403`.
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

### `PATCH /users/{id}/profile` — alocar ou desvincular perfil

Corpo: `{ "profileId": 5 }` ou `{ "profileId": null }` para desvincular.

**Permissão: `ADMIN`.** Quem não tem `ADMIN` recebe `403`, inclusive para tentar mudar o próprio
perfil (não dá para "se promover"). Esconda essa ação na UI para quem não for `ADMIN`.

Um admin pode trocar ou remover o perfil de qualquer outro usuário, inclusive de outro admin e de si
mesmo. **A conta de sistema é a exceção**: o perfil dela não pode ser alterado por aqui (responde `404`,
como se ela não existisse — ela também não aparece em `GET /users`).

Resposta: o `User` atualizado. Erros: `403` sem `ADMIN`; `404` usuário não existe (ou é a conta de
sistema); `400` `profileId` enviado não existe.

---

## `/profiles`

**Todas as rotas exigem `ADMIN`**, inclusive as de leitura (`403` para qualquer outro perfil). Não
mostre a tela de perfis para quem não for `ADMIN`.

| Método e rota | O que faz | Sucesso |
| --- | --- | --- |
| `POST /profiles` | Cria perfil | `201` |
| `GET /profiles` | Lista todos | `200` |
| `GET /profiles/{id}` | Busca por id | `200` |
| `PUT /profiles/{id}` | Edita nome/descrição | `200` |
| `DELETE /profiles/{id}` | Remove (só se nenhum usuário estiver com esse perfil) | `204` |
| `POST /profiles/{id}/permissions/{permissionId}` | Associa uma permissão ao perfil | `200` com o perfil |
| `DELETE /profiles/{id}/permissions/{permissionId}` | Remove a associação | `200` com o perfil |

Corpo de `POST`/`PUT`: `{ "name": "GESTOR_SETOR", "description": "..." }` — `name` obrigatório,
único, máximo 100 caracteres (espaços nas pontas são removidos antes de validar e gravar, então
`"X "` e `"X"` são o mesmo nome); `description` opcional, máximo 255 caracteres. A unicidade do nome
diferencia maiúsculas de minúsculas.

Resposta (`ProfileDto`):

```json
{
  "id": 2,
  "name": "GESTOR_SETOR",
  "description": "Gerencia inventarios e usuarios do proprio setor.",
  "permissionIds": [2],
  "createdAt": "2026-10-03T20:21:58.123",
  "updatedAt": "2026-10-03T20:21:58.123"
}
```

`POST`/`DELETE .../permissions/{permissionId}` são idempotentes: adicionar uma permissão já
associada, ou remover uma que não está associada, devolve `200` normalmente (sem erro), só não
muda nada. Excluir um perfil **não** apaga as permissões dele, só o vínculo.

### Erros

| Status | Quando |
| --- | --- |
| `400` | nome vazio, ausente, duplicado ou com mais de 100 caracteres; descrição com mais de 255 |
| `403` | quem chama não tem `ADMIN` |
| `404` | perfil ou permissão não existe |
| `409` | o perfil de administrador foi alvo de uma operação proibida, **ou** o perfil a excluir ainda tem usuários (veja abaixo) |

O `409` tem **dois significados** e o corpo do erro não diz qual (ver "O corpo de erro nunca tem um
campo `message`"). Diferencie pelo perfil:

- **Perfil `ADMIN_ORGANIZATION` (protegido).** É o perfil da conta de sistema, e o backend depende
  dele para subir. Não pode ser **renomeado** (a descrição pode), **excluído** nem **perder a
  permissão `ADMIN`** (`DELETE .../permissions/{id da ADMIN}`). Adicionar outras permissões a ele e
  remover essas extras é permitido. Na UI, não ofereça renomear, excluir nem remover `ADMIN` desse
  perfil.
- **Qualquer outro perfil, ao excluir:** `409` quer dizer que ainda há usuário com esse perfil
  (inclusive a conta de sistema). Para a tela já poder avisar antes, use
  `GET /users?profileId=<id>` (só `ADMIN`; a conta de sistema não aparece nessa lista, então um `409`
  pode ocorrer mesmo com a lista vazia — trate o `409` de qualquer forma).

Perfis seedados hoje: `ADMIN_ORGANIZATION`, `GESTOR_SETOR`, `OPERADOR_CAMPO`, `CONSULTA` (ver
tabela de permissões acima). Só o `ADMIN_ORGANIZATION` é protegido; os outros três podem ser
editados e excluídos (se sem usuário).

---

## `/permissions` — somente leitura

**Exige `ADMIN`** (`403` para os demais). Não existe criar, editar nem excluir permissão pela API:
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
- Qualquer vínculo entre `Inventory`/`Item` e `Sector`, ou autorização por setor nessas rotas —
  `MANAGE_ITEM` existe no seed mas nenhum endpoint a verifica ainda.
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
