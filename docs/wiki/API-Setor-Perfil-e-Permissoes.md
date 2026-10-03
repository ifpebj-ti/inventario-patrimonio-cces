# API de Setor, Usuário, Perfil e Permissão

Este documento descreve, para quem for implementar o front-end, todas as rotas de `/sectors`,
`/users` (listagem e alocação), `/profiles` e `/permissions` como elas existem hoje em `main`,
após as issues #179 (remoção de `Organization`, `Sector` independente) e #181 (autorização real em
Setor e alocação usuário↔setor), ambas sub-issues de #154. **Não cobre** `/auth`, `/inventories` nem
`/items` — essas rotas não foram tocadas por este trabalho.

## Antes de implementar: comportamentos não óbvios

- **Toda rota abaixo exige `Authorization: Bearer <jwt>`.** Sem header, a resposta é `403
  Forbidden` — não `401` — porque o `SecurityConfig` ainda não tem um `AuthenticationEntryPoint`
  customizado (é um TODO em aberto no backend).
- **Um JWT com assinatura válida mas cujo `subject` não existe mais em `im_user`** (ex: usuário
  excluído) faz as rotas que checam permissão (`/sectors` e `PATCH /users/{id}/sector`) responderem
  `401 Unauthorized`, não `403`. É o único caso em que essas rotas devolvem 401 em vez de 403.
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
| `ADMIN` | `ADMIN_ORGANIZATION` | Escrita e leitura em `/sectors`; alocar qualquer usuário em qualquer setor. |
| `MANAGE_SECTOR` | `GESTOR_SETOR` | Leitura em `/sectors`; alocar/desvincular usuário **só dentro do próprio setor**. |
| `MANAGE_ITEM` | `OPERADOR_CAMPO` | Seedada, mas **nenhum endpoint checa essa permissão ainda** (Item/Inventory ficam para uma etapa futura). |
| (nenhuma) | `CONSULTA` | Não dá acesso a nada que exija permissão. |

Um usuário sem perfil atribuído (`profileId: null`) não tem nenhuma permissão. Perfis e
permissões continuam sendo entidades independentes e configuráveis via `/profiles` e
`/permissions` — a tabela acima é só o estado do seed, não uma regra fixa no código.

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
- `404`: setor não existe (`GET`/`PUT`/`activate`/`deactivate` por id).

---

## `/users` — listagem e alocação

### `GET /users?sectorId=&profileId=`

Sem checagem de permissão hoje — qualquer usuário autenticado pode listar. Devolve um array de
usuário no formato:

```json
{
  "id": 7,
  "name": "Maria Silva",
  "email": "maria.silva@ifpe.edu.br",
  "sectorId": 1,
  "profileId": 2,
  "createdAt": "2026-10-03T20:21:58.123",
  "updatedAt": "2026-10-03T20:21:58.123"
}
```

`sectorId`/`profileId` vêm `null` quando o usuário não está alocado. `googleId` nunca aparece no
JSON.

**Os filtros não se combinam.** Se `sectorId` e `profileId` forem enviados juntos, só `sectorId` é
aplicado e `profileId` é ignorado em silêncio (sem erro). Envie só um filtro por vez.

### `PATCH /users/{id}/sector` — alocar ou desvincular usuário de um setor

Corpo:

```json
{ "sectorId": 3 }
```

Para desvincular, envie `{ "sectorId": null }`.

**Permissão**: autorizado se quem está autenticado tem `ADMIN` (qualquer setor), **ou** tem
`MANAGE_SECTOR` e está alocado no setor relevante da operação:
- ao **atribuir** um setor (`sectorId` não nulo): o setor relevante é o **novo** setor —
  quem faz a chamada precisa estar alocado nesse setor de destino.
- ao **desvincular** (`sectorId: null`): o setor relevante é o setor **atual** do usuário-alvo —
  quem faz a chamada precisa estar alocado nesse setor antes da operação.

Sem nenhuma das duas condições, `403`. **Recomendação de UX**: para um usuário `GESTOR_SETOR`, não
ofereça na UI a opção de mover alguém para um setor que não é o dele — a chamada vai dar 403, então
é melhor nem mostrar a opção do que deixar o usuário tentar e tomar erro.

Resposta: o `User` atualizado, mesmo formato do `GET /users`.

Erros: `404` usuário não existe; `400` `sectorId` enviado não existe; `403` conforme acima.

### `PATCH /users/{id}/profile` — alocar ou desvincular perfil

Corpo: `{ "profileId": 5 }` ou `{ "profileId": null }` para desvincular.

**Sem nenhuma checagem de permissão ainda** — qualquer usuário autenticado pode trocar o perfil de
qualquer outro usuário. Isso é uma lacuna conhecida (TODO no backend), não uma decisão de produto.
Até isso ser resolvido, considere esconder essa ação na UI para quem não for `ADMIN`, já que o
backend não vai impedir — a validação, por enquanto, é só de UX, não de segurança.

Resposta: o `User` atualizado. Erros: `404` usuário não existe; `400` `profileId` enviado não
existe.

---

## `/profiles`

CRUD completo, **sem checagem de permissão ainda** (TODO no backend) — qualquer autenticado pode
gerenciar perfis.

| Método e rota | O que faz |
| --- | --- |
| `POST /profiles` | Cria perfil |
| `GET /profiles` | Lista todos |
| `GET /profiles/{id}` | Busca por id |
| `PUT /profiles/{id}` | Edita nome/descrição |
| `DELETE /profiles/{id}` | Remove (só se nenhum usuário estiver com esse perfil) |
| `POST /profiles/{id}/permissions/{permissionId}` | Associa uma permissão ao perfil |
| `DELETE /profiles/{id}/permissions/{permissionId}` | Remove a associação |

Corpo de `POST`/`PUT`: `{ "name": "GESTOR_SETOR", "description": "..." }` — `name` obrigatório,
único, máximo 100 caracteres; `description` opcional, máximo 255 caracteres.

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
muda nada.

Erros: `400` nome vazio/duplicado/tamanho; `404` perfil ou permissão não existe; `409` ao tentar
apagar um perfil que está atribuído a algum usuário.

Perfis seedados hoje: `ADMIN_ORGANIZATION`, `GESTOR_SETOR`, `OPERADOR_CAMPO`, `CONSULTA` (ver
tabela de permissões acima).

---

## `/permissions`

CRUD completo, **sem checagem de permissão ainda** (TODO no backend).

| Método e rota | O que faz |
| --- | --- |
| `POST /permissions` | Cria permissão |
| `GET /permissions` | Lista todas |
| `GET /permissions/{id}` | Busca por id |
| `PUT /permissions/{id}` | Edita nome/descrição |
| `DELETE /permissions/{id}` | Remove (só se nenhum perfil tiver essa permissão) |

Corpo de `POST`/`PUT`: `{ "name": "ADMIN", "description": "..." }` — `name` obrigatório, único,
máximo 100 caracteres; `description` opcional, máximo 255 caracteres.

Resposta (`PermissionDto`): `{ "id": 1, "name": "ADMIN", "description": "..." }`.

Erros: `400` nome vazio/duplicado/tamanho; `404` não existe; `409` ao tentar apagar uma permissão
que está associada a algum perfil.

Permissões seedadas hoje: `ADMIN`, `MANAGE_SECTOR`, `MANAGE_ITEM` (só 3 — nomes mudaram de uma
versão anterior do seed; se algo no front ou em anotações antigas citar `SECTOR_MANAGE`,
`USER_ASSIGN_SECTOR` ou `USER_ASSIGN_PROFILE`, está desatualizado).

---

## O que ainda não existe (não contar com isso)

- Checagem de permissão em `/profiles`, `/permissions` e em `PATCH /users/{id}/profile` — hoje é
  tudo aberto a qualquer autenticado.
- Qualquer vínculo entre `Inventory`/`Item` e `Sector`, ou autorização por setor nessas rotas —
  `MANAGE_ITEM` existe no seed mas nenhum endpoint a verifica ainda.
- Histórico/fluxo de validação entre setores.

## Histórico

| Versão | Data | Descrição |
| --- | --- | --- |
| 1.0 | 2026-10-03 | Criação do documento, cobrindo o estado da API após as issues #179 e #181. |
