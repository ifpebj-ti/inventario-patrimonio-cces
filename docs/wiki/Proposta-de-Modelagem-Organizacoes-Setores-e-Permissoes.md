# Proposta de Modelagem: Setores e Permissoes

Este documento registra uma proposta de evolucao do modelo de dados do Inventarium. Ele nao descreve o schema implementado hoje; a modelagem atual continua documentada em [Documento de Modelagem de Dados](./Documento-de-Modelagem-de-Dados).

> Revisao 2.0: a versao original desta proposta (1.0) incluia uma entidade `im_organization`, pensada para um cenario multi-institucional. Essa camada foi removida — ver [Motivacao](#motivacao) e o historico ao final do documento.

O objetivo desta proposta e preparar o sistema para funcionar de forma setorizada, permitindo que o Inventarium tenha varios setores, usuarios vinculados a setores, inventarios pertencentes a setores, perfis de acesso com permissoes e historico de validacao/tombamento.

## Motivacao

No modelo atual, os inventarios ficam vinculados diretamente ao usuario que os criou. Isso limita alguns fluxos reais de patrimonio:

- um setor pode ter mais de um usuario responsavel pelo levantamento patrimonial;
- um usuario deve atuar dentro do seu setor principal;
- um inventario precisa representar um setor ou unidade operacional, e nao apenas uma conta individual;
- o backend precisa impedir que uma pessoa valide um patrimonio de um setor ao qual ela nao tem acesso;
- a aplicacao precisa registrar quem realizou a validacao, quando isso aconteceu e em qual contexto;
- as permissoes devem deixar de ser implicitas e passar a ser governadas por perfis.

Cada implantacao do Inventarium atende a uma unica instituicao. A versao original desta proposta previa uma entidade `im_organization` para suportar varias instituicoes no mesmo banco de dados, mas essa necessidade nao se confirmou: nao ha, hoje, nenhum cenario de multi-tenancy entre instituicoes diferentes. Manter `im_organization` so acrescentava uma camada hierarquica (setor pertence a organizacao) sem nenhum caso de uso real por tras dela. A revisao 2.0 remove essa entidade: `im_sector` passa a existir de forma independente, sem vinculo com organizacao alguma.

## Entidades Propostas

### `im_sector`

Representa um setor, departamento, campus, coordenacao ou unidade interna da instituicao.

| Campo | Tipo sugerido | Obrigatorio | Observacao |
| --- | --- | --- | --- |
| `id` | `BIGINT` | Sim | Chave primaria. |
| `name` | `VARCHAR(255)` | Sim | Nome do setor. |
| `code` | `VARCHAR(100)` | Nao | Codigo interno, se existir. |
| `active` | `BOOLEAN` | Sim | Setor ativo (`true`) ou desativado (`false`), default `true`. Desativar e a forma preferida de remover um setor de uso sem apagar seu historico (inventarios, alocacoes, auditoria de itens ja associados a ele). |
| `created_at` | `TIMESTAMP` | Sim | Data de criacao. |
| `updated_at` | `TIMESTAMP` | Sim | Data de atualizacao. |

Setores existem de forma independente, sem vinculo com nenhuma entidade superior.

### `im_sector_allocation`

Representa a alocacao de um usuario a um setor. Um usuario pode estar alocado a mais de um setor ao mesmo tempo (por exemplo, alguem que da suporte a dois departamentos) — por isso o vinculo entre `im_user` e `im_sector` deixa de ser um FK unico em `im_user` e passa a ser esta tabela associativa.

| Campo | Tipo sugerido | Obrigatorio | Observacao |
| --- | --- | --- | --- |
| `id` | `BIGINT` | Sim | Chave primaria. |
| `id_user` | `BIGINT` | Sim | FK para `im_user.id`. |
| `id_sector` | `BIGINT` | Sim | FK para `im_sector.id`. |

Regra principal: um usuario pode ter nenhuma, uma ou varias alocacoes; o par (`id_user`, `id_sector`) deve ser unico — nao faz sentido alocar o mesmo usuario duas vezes ao mesmo setor.

### `im_user`

Continua representando uma pessoa autenticada via Google, mas passa a ter vinculo operacional com setor(es) e perfil.

Campos atuais devem ser preservados:

- `id`
- `name`
- `email`
- `google_id`
- `created_at`
- `updated_at`

Novo campo proposto:

| Campo | Tipo sugerido | Obrigatorio | Observacao |
| --- | --- | --- | --- |
| `id_profile` | `BIGINT` | Sim no modelo alvo | FK para `im_profile.id`. Define o conjunto de permissoes do usuario. |

O vinculo com setor **nao** fica em `im_user` — um usuario pode estar em varios setores ao mesmo tempo, entao essa relacao vive inteiramente em `im_sector_allocation` (zero ou mais registros por usuario).

Observacao de migracao: `id_profile` pode nascer opcional para nao quebrar usuarios existentes criados pelo login Google. Depois de popular os dados, o backend pode tornar o vinculo obrigatorio.

### `im_inventory`

Continua representando um inventario patrimonial, mas o dono operacional passa a ser o setor.

Campos atuais devem ser preservados:

- `id`
- `name`
- `description`
- `created_at`
- `updated_at`
- `id_user`

Novos campos propostos:

| Campo | Tipo sugerido | Obrigatorio | Observacao |
| --- | --- | --- | --- |
| `id_sector` | `BIGINT` | Sim no modelo alvo | FK para `im_sector.id`. Define a qual setor o inventario pertence. |
| `active` | `BOOLEAN` | Sim | Inventario ativo (`true`) ou desativado (`false`), default `true`. Mesma logica de `im_sector.active`: desativar em vez de apagar, preservando o historico de itens e auditoria. |

O campo `id_user` pode continuar existindo como usuario criador/responsavel pela criacao do inventario, mas nao deve ser a unica base de ownership. Para autorizacao e listagem, o setor passa a ser a fronteira principal.

### `im_item`

Continua representando um bem patrimonial dentro de um inventario.

Campos atuais devem ser preservados:

- `id`
- `code`
- `name`
- `description`
- `price`
- `locale`
- `responsible`
- `qr_code`
- `is_valid`
- `validated_at`
- `id_inventory`

No modelo proposto, o setor esperado do item pode ser inferido por `item -> inventory -> sector`. Caso o sistema precise registrar transferencia ou localizacao setorial independente do inventario, pode ser avaliado um campo futuro `id_current_sector` em `im_item`, mas ele nao e necessario para a primeira versao da proposta.

### `im_profile`

Representa um perfil funcional de acesso.

| Campo | Tipo sugerido | Obrigatorio | Observacao |
| --- | --- | --- | --- |
| `id` | `BIGINT` | Sim | Chave primaria. |
| `name` | `VARCHAR(100)` | Sim | Nome unico do perfil, como `ADMIN`, `GESTOR_SETOR`, `OPERADOR_CAMPO`. |
| `description` | `VARCHAR(255)` | Nao | Descricao do papel. |
| `created_at` | `TIMESTAMP` | Sim | Data de criacao. |
| `updated_at` | `TIMESTAMP` | Sim | Data de atualizacao. |

### `im_permission`

Representa uma permissao do sistema. Ver [Permissoes Propostas](#permissoes-propostas-revisao-40) para a lista completa — inicialmente so tres: `ADMIN`, `MANAGE_SECTOR` e `MANAGE_ITEM`.

| Campo | Tipo sugerido | Obrigatorio | Observacao |
| --- | --- | --- | --- |
| `id` | `BIGINT` | Sim | Chave primaria. |
| `name` | `VARCHAR(100)` | Sim | Nome unico da permissao, como `ADMIN`, `MANAGE_SECTOR`, `MANAGE_ITEM`. |
| `description` | `VARCHAR(255)` | Nao | Descricao da permissao. |

### `im_profile_permission`

Tabela associativa entre perfis e permissoes.

| Campo | Tipo sugerido | Obrigatorio | Observacao |
| --- | --- | --- | --- |
| `id_profile` | `BIGINT` | Sim | FK para `im_profile.id`. |
| `id_permission` | `BIGINT` | Sim | FK para `im_permission.id`. |

Chave primaria sugerida: `id_profile, id_permission`.

Regra principal: um perfil pode ter varias permissoes, e uma permissao pode pertencer a varios perfis.

### `im_validation_history`

Representa o historico de tombamentos e validacoes. Essa tabela deve responder perguntas como: quem tombou ou validou qual patrimonio, em qual horario, estando em qual setor, e qual era o setor dono do item. Nao existe mais validacao parcial (ver revisao 4.0): validar um item de um setor ao qual o usuario nao tem acesso e negado antes de gerar qualquer evento, entao todo registro aqui representa uma validacao efetivamente autorizada.

| Campo | Tipo sugerido | Obrigatorio | Observacao |
| --- | --- | --- | --- |
| `id` | `BIGINT` | Sim | Chave primaria. |
| `id_item` | `BIGINT` | Sim | FK para `im_item.id`. Patrimonio afetado. |
| `id_inventory` | `BIGINT` | Nao | FK para `im_inventory.id`. Inventario no momento do evento. |
| `id_actor_user` | `BIGINT` | Sim | FK para `im_user.id`. Usuario que realizou a acao. |
| `id_actor_sector` | `BIGINT` | Nao | FK para `im_sector.id`. Setor do usuario no momento da acao (nulo se o usuario agiu como `ADMIN` sem alocacao relevante). |
| `id_target_sector` | `BIGINT` | Nao | FK para `im_sector.id`. Setor dono/esperado do item, para fins de rastreabilidade. |
| `event_type` | `VARCHAR(50)` | Sim | Tipo do evento. Hoje so `VALIDATION`; `TRANSFER_REQUEST` continua reservado para uma eventual transferencia de item entre setores (fora de escopo). |
| `status` | `VARCHAR(50)` | Sim | Estado do evento. Hoje so `VALIDATED`. |
| `details` | `VARCHAR(1000)` | Nao | Observacao textual do evento. |
| `occurred_at` | `TIMESTAMP` | Sim | Data e hora do evento. |

## Diagrama Entidade-Relacionamento Proposto

```mermaid
erDiagram
    IM_SECTOR ||--o{ IM_SECTOR_ALLOCATION : recebe
    IM_USER ||--o{ IM_SECTOR_ALLOCATION : aloca
    IM_SECTOR ||--o{ IM_INVENTORY : possui
    IM_USER ||--o{ IM_INVENTORY : cria
    IM_INVENTORY ||--o{ IM_ITEM : contem
    IM_ITEM ||--o{ IM_OBSERVATION : possui
    IM_PROFILE ||--o{ IM_USER : define
    IM_PROFILE ||--o{ IM_PROFILE_PERMISSION : agrega
    IM_PERMISSION ||--o{ IM_PROFILE_PERMISSION : compoe
    IM_ITEM ||--o{ IM_VALIDATION_HISTORY : registra
    IM_INVENTORY ||--o{ IM_VALIDATION_HISTORY : contextualiza
    IM_USER ||--o{ IM_VALIDATION_HISTORY : executa
    IM_SECTOR ||--o{ IM_VALIDATION_HISTORY : setor_origem
    IM_SECTOR ||--o{ IM_VALIDATION_HISTORY : setor_destino

    IM_SECTOR {
        bigint id PK
        varchar_255 name
        varchar_100 code
        timestamp created_at
        timestamp updated_at
    }

    IM_SECTOR_ALLOCATION {
        bigint id PK
        bigint id_user FK
        bigint id_sector FK
    }

    IM_USER {
        bigint id PK
        varchar_100 name
        varchar_255 email UK
        varchar_255 google_id UK
        bigint id_profile FK
        timestamp created_at
        timestamp updated_at
    }

    IM_INVENTORY {
        bigint id PK
        varchar_255 name
        varchar_255 description
        timestamp created_at
        timestamp updated_at
        bigint id_user FK
        bigint id_sector FK
    }

    IM_ITEM {
        bigint id PK
        varchar_255 code
        varchar_255 name
        varchar_1000 description
        bigint price
        varchar_255 locale
        varchar_255 responsible
        varchar_255 qr_code
        boolean is_valid
        timestamp validated_at
        bigint id_inventory FK
    }

    IM_OBSERVATION {
        bigint id PK
        varchar_1000 content
        bigint id_item FK
    }

    IM_PROFILE {
        bigint id PK
        varchar_100 name UK
        varchar_255 description
        timestamp created_at
        timestamp updated_at
    }

    IM_PERMISSION {
        bigint id PK
        varchar_100 name UK
        varchar_255 description
    }

    IM_PROFILE_PERMISSION {
        bigint id_profile PK_FK
        bigint id_permission PK_FK
    }

    IM_VALIDATION_HISTORY {
        bigint id PK
        bigint id_item FK
        bigint id_inventory FK
        bigint id_actor_user FK
        bigint id_actor_sector FK
        bigint id_target_sector FK
        varchar_50 event_type
        varchar_50 status
        varchar_1000 details
        timestamp occurred_at
    }
```

## Diagrama de Classes Proposto

```mermaid
classDiagram
    class Sector {
        +Long id
        +String name
        +String code
        +LocalDateTime createdAt
        +LocalDateTime updatedAt
    }

    class User {
        +Long id
        +String name
        +String email
        -String googleId
        +LocalDateTime createdAt
        +LocalDateTime updatedAt
    }

    class Inventory {
        +Long id
        +String name
        +String description
        +LocalDateTime createdAt
        +LocalDateTime updatedAt
    }

    class Item {
        +Long id
        +String code
        +String name
        +String description
        +Long price
        +String locale
        +String responsible
        +String qrCode
        +Boolean isValid
        +LocalDateTime validatedAt
    }

    class Observation {
        +Long id
        +String content
    }

    class SectorAllocation {
        +Long id
    }

    class Profile {
        +Long id
        +String name
        +String description
        +LocalDateTime createdAt
        +LocalDateTime updatedAt
    }

    class Permission {
        +Long id
        +String name
        +String description
    }

    class ValidationHistory {
        +Long id
        +String eventType
        +String status
        +String details
        +LocalDateTime occurredAt
    }

    Sector "1" --> "0..*" SectorAllocation : recebe
    User "1" --> "0..*" SectorAllocation : aloca
    Sector "1" --> "0..*" Inventory : possui
    User "1" --> "0..*" Inventory : cria
    Inventory "1" --> "0..*" Item : contem
    Item "1" --> "0..*" Observation : possui
    Profile "1" --> "0..*" User : atribuido
    Profile "0..*" --> "0..*" Permission : permissoes
    Item "1" --> "0..*" ValidationHistory : historico
    Inventory "0..1" --> "0..*" ValidationHistory : contexto
    User "1" --> "0..*" ValidationHistory : ator
    Sector "0..1" --> "0..*" ValidationHistory : origem
    Sector "0..1" --> "0..*" ValidationHistory : destino
```

## Regras de Negocio Propostas

| Regra | Descricao |
| --- | --- |
| Setor e uma entidade de primeiro nivel | Um setor nao depende de nenhuma entidade superior (nao ha mais `im_organization`). |
| Usuario pode ser alocado a varios setores | Um setor pode ter varios usuarios alocados, e um usuario pode estar alocado a varios setores ao mesmo tempo, via `im_sector_allocation`. Nao ha mais um unico "setor do usuario". |
| Inventario pertence a um setor | Um setor pode ter varios inventarios. Um inventario deve pertencer a um unico setor no modelo alvo. |
| Criador do inventario continua rastreavel | `im_inventory.id_user` deve indicar quem criou o inventario, mesmo que o ownership operacional seja do setor. |
| Autorizacao passa por setor e perfil | O backend deve validar se o usuario tem permissao (`ADMIN`, `MANAGE_SECTOR` ou `MANAGE_ITEM`) e, salvo `ADMIN`, se o recurso pertence a um setor em que o usuario esta alocado. |
| Validar item de setor sem acesso e negado | Nao existe validacao parcial: se o usuario nao for `ADMIN` e nao estiver alocado no setor do item, a validacao e recusada (403) e nenhum evento e registrado. |
| Historico nao substitui estado atual | `im_validation_history` registra eventos. O estado atual do item continua em `im_item.is_valid` e `im_item.validated_at`. |

## Fluxo de Validacao

1. O usuario autentica via Google; o backend identifica suas alocacoes de setor (`user -> im_sector_allocation`) e seu perfil/permissoes.
2. O usuario escaneia um patrimonio por QR Code ou codigo patrimonial.
3. O backend localiza o item e identifica o setor esperado por `item -> inventory -> sector`.
4. O backend verifica se o usuario tem `ADMIN`, ou se tem `MANAGE_SECTOR`/`MANAGE_ITEM` **e** esta alocado nesse setor.
5. Se autorizado, o item e validado e um evento `VALIDATION`/`VALIDATED` e registrado em `im_validation_history`.
6. Se nao autorizado, a requisicao e recusada (403) — nao ha validacao parcial nem fluxo de pendencia.

## Permissoes Propostas (revisao 4.0)

A lista de permissoes foi simplificada: em vez de uma permissao por acao (modelo das revisoes anteriores), o sistema passa a ter tres permissoes amplas, cada uma cobrindo um conjunto de acoes relacionadas. Inicialmente so existem estas tres permissoes.

| Permissao | Uso esperado |
| --- | --- |
| `ADMIN` | Acesso irrestrito, sem limite de setor: criar, editar, ativar e desativar qualquer setor; adicionar e remover usuarios de qualquer setor; adicionar e remover o perfil de um usuario; criar, editar e desativar qualquer inventario; adicionar e remover item de qualquer inventario; validar item de qualquer inventario. |
| `MANAGE_SECTOR` | Igual a `ADMIN`, mas restrito ao(s) setor(es) em que o usuario com essa permissao esta alocado: adicionar e remover usuarios do setor; criar, editar e desativar inventario do setor; adicionar e remover item do inventario do setor; validar item do inventario do setor. |
| `MANAGE_ITEM` | Restrito ao(s) setor(es) em que o usuario com essa permissao esta alocado: adicionar e remover item do inventario do setor; validar item do inventario do setor. Nao inclui gerenciar usuarios nem criar/editar/desativar inventario. |

As permissoes anteriores (`SECTOR_MANAGE`, `USER_ASSIGN_SECTOR`, `USER_ASSIGN_PROFILE`, `INVENTORY_CREATE`, `INVENTORY_UPDATE`, `INVENTORY_DELETE`, `ITEM_IMPORT`, `ITEM_UPDATE`, `ITEM_VALIDATE`, `ITEM_VALIDATE_EXTERNAL_SECTOR`, `VALIDATION_RESOLVE_PENDING`, `REPORT_EXPORT`) saem de cena. Nao ha mais uma permissao equivalente a `ITEM_VALIDATE_EXTERNAL_SECTOR` porque a validacao parcial entre setores deixou de existir: validar item de um setor ao qual o usuario nao tem acesso agora e simplesmente negado (ver [Fluxo de Validacao](#fluxo-de-validacao)).

## Perfis Iniciais Sugeridos

| Perfil | Descricao | Permissoes esperadas |
| --- | --- | --- |
| `ADMIN_ORGANIZATION` | Administra o Inventarium como um todo (nome do perfil mantido por compatibilidade com o que ja esta seedado em `im_profile` — ver Pontos em Aberto). | `ADMIN` |
| `GESTOR_SETOR` | Gerencia usuarios, inventarios e itens do(s) proprio(s) setor(es). | `MANAGE_SECTOR` |
| `OPERADOR_CAMPO` | Realiza levantamento em campo, cuidando dos itens do(s) proprio(s) setor(es). | `MANAGE_ITEM` |
| `CONSULTA` | Apenas consulta informacoes. | Nenhuma — leitura nao e controlada por permissao. |

## Impactos Esperados no Backend

A maior parte do escopo original ja foi implementada: entidades JPA `Organization`, `Sector`, `Profile`, `Permission` e `ItemAudit` (equivalente ao `ValidationHistory` desta proposta, com a tabela renomeada para `im_item_audit`), FKs de setor/perfil em `im_user`, FK de setor em `im_inventory`, CRUDs de setor/perfil/permissao, endpoint de validacao de item com registro de auditoria, e testes E2E cobrindo os fluxos de sucesso e erro. Revisao completa de autorizacao por setor/perfil em todas as rotas e filtros de listagem por setor em todos os recursos continuam fora de escopo. O fluxo de pendencia entre setores (bloqueio, notificacao, confirmacao), cogitado nas revisoes 1.0–3.0, foi descartado na revisao 4.0 — nao faz mais parte da proposta.

### Impactos da remocao de `im_organization` (revisao 2.0)

Como `Organization` ja estava implementada, remove-la exige desfazer parte do que foi construido:

- remover a entidade JPA `Organization`, `OrganizationRepository`, `OrganizationService`, `OrganizationController` e `OrganizationDto`;
- remover os endpoints `/organizations`;
- migration Liquibase removendo a FK `im_sector.id_organization` e a coluna, e removendo (ou preservando como tabela orfa, a depender da decisao de produto) `im_organization`;
- ajustar `Sector`, `SectorDto`, `SectorService`, `SectorController` para nao exigirem mais `organizationId` na criacao/atualizacao de setor;
- remover a permissao `ORGANIZATION_MANAGE` do seed de permissoes (e de qualquer perfil associado a ela);
- remover/ajustar os testes que criavam uma organizacao como pre-requisito para criar um setor (`SectorE2ETest`, `InventorySectorE2ETest`, `ItemValidationE2ETest`, etc.) e remover `OrganizationE2ETest`;
- avaliar se o perfil `ADMIN_ORGANIZATION` deve ser renomeado (ver Pontos em Aberto).

### Impactos da alocacao de usuario a multiplos setores (revisao 3.0)

`User.sector` (campo unico, endpoint `PATCH /users/{id}/sector`) ja esta implementado e precisa ser substituido pela nova entidade. Fica para uma proxima etapa de codigo:

- nova entidade JPA `SectorAllocation`, `SectorAllocationRepository`, `SectorAllocationService`;
- endpoints para alocar/desalocar usuario de um setor (ex.: `POST`/`DELETE /users/{id}/sectors/{sectorId}`, no mesmo estilo de `POST`/`DELETE /profiles/{id}/permissions/{permissionId}`), substituindo `PATCH /users/{id}/sector`;
- migration Liquibase criando `im_sector_allocation` e removendo `id_sector` (coluna e FK) de `im_user`;
- `GET /users?sectorId=` passa a precisar de uma junção com `im_sector_allocation` em vez de `findBySector_Id` direto em `User`;
- `ItemService.validateItem` passa a comparar o setor do item com o **conjunto** de setores alocados ao usuario (em vez de `actor.getSector()`), decidindo `VALIDATION` vs `PARTIAL_VALIDATION` conforme haja ou nao alguma alocacao que bata com o setor do item;
- ajuste nos testes que hoje assumem um unico setor por usuario (`UserSectorProfileE2ETest`, `ItemValidationE2ETest`, `SectorE2ETest`, `InventorySectorE2ETest`).

### Impactos da simplificacao das permissoes e remocao da validacao parcial (revisao 4.0)

O seed de permissoes ja implementado (13 linhas em `im_permission`, incluindo as 12 que sobraram apos a revisao 2.0, mais as associacoes perfil-permissao em `im_profile_permission`) precisa ser refeito, e `ItemService.validateItem` precisa ser simplificado:

- nova migration Liquibase substituindo o seed de permissoes por apenas tres linhas (`ADMIN`, `MANAGE_SECTOR`, `MANAGE_ITEM`) e reassociando os quatro perfis conforme a tabela de Perfis Iniciais Sugeridos (1 permissao por perfil, exceto `CONSULTA`, que fica sem nenhuma);
- migration Liquibase adicionando `active` (`BOOLEAN NOT NULL DEFAULT true`) em `im_sector` e `im_inventory`;
- a checagem de autorizacao (ainda nao implementada em nenhum endpoint — todos os TODOs espalhados pelo codigo) passa a checar se o usuario tem `ADMIN`, ou se tem `MANAGE_SECTOR`/`MANAGE_ITEM` **e** esta alocado (via `im_sector_allocation`) no setor do recurso acessado — sem mais isso, recusar com 403;
- `ItemService.validateItem` perde a logica de comparar setores para decidir `VALIDATION` vs `PARTIAL_VALIDATION`: passa a ser so a checagem de autorizacao acima (autorizado → valida e grava `VALIDATION`/`VALIDATED`; nao autorizado → 403, sem gravar nada);
- `ItemValidationE2ETest` perde os casos `validateItem_differentSector_recordsPartialValidation` e `validateItem_actorWithoutSector_recordsSimpleValidation` (nao fazem mais sentido), substituidos por casos de 403 quando o usuario nao tem permissao/alocacao adequada; `SeedDataE2ETest` precisa ser reescrito para as 3 permissoes novas;
- `SectorController`/`SectorService` e `InventoryController`/`InventoryService` ganham suporte a `active` (provavelmente um endpoint de ativar/desativar, ao lado do `DELETE` ja existente — ver Pontos em Aberto).

## Pontos em Aberto

| Ponto | Decisao pendente |
| --- | --- |
| Alocacao guarda historico ou so o estado atual? | Nesta revisao, uma alocacao e so um registro de "esta alocado agora" (sem `started_at`/`ended_at`) — desalocar e apagar a linha, sem rastro. Se o produto precisar de historico de lotacao (quem passou por qual setor, quando), a tabela precisaria de colunas adicionais. |
| Existe um setor "principal" entre as alocacoes de um usuario? | Hoje nenhuma alocacao e marcada como principal/padrao. Casos que precisem de um unico valor (ex.: relatorios, tela inicial) teriam que escolher um criterio (mais antiga? primeira cadastrada?) ou a UI simplesmente lista todas. |
| O que o audit registra quando o usuario nao tem acesso ao setor do item? | Resolvido: nada. A validacao e recusada (403) antes de qualquer evento ser gravado — nao ha mais "validacao parcial" para registrar. |
| Usuario pode trocar de setor? | Resolvido por esta revisao: "trocar de setor" deixa de fazer sentido como operacao unica — agora e alocar a um setor novo e, se for o caso, desalocar do antigo, via dois registros independentes em `im_sector_allocation`. |
| Setor e campus sao a mesma entidade? | Se o IFPE precisar separar campus de setor, pode ser necessario criar uma hierarquia adicional. |
| Inventario pode abranger mais de um setor? | A proposta assume um setor por inventario. Inventarios multi-setor exigiriam tabela associativa. |
| O perfil `ADMIN_ORGANIZATION` deve ser renomeado? | Sem `Organization`, o nome fica deslocado do que o perfil representa (administrador do sistema). Renomear exige migration de dados (perfis ja seedados) e ajuste em qualquer lugar que referencie o nome por string. |
| `im_organization` deve ser apagada ou so desvinculada? | Remover a tabela e definitivo; so remover a FK e os dados de `im_organization` continuam no banco, sem uso. Depende de haver algum dado real ja cadastrado em producao. |
| `DELETE` de setor/inventario continua existindo junto com `active`? | `active` cobre o caso de uso principal (tirar de circulacao sem perder historico). Definir se o `DELETE` fisico (ja implementado, com guard de 409 quando ha dependentes) continua disponivel para quem tem `ADMIN`, ou se passa a ser so uma operacao de desativacao. |

## Historico

| Versao | Data | Descricao |
| --- | --- | --- |
| 1.0 | 2026-09-16 | Criacao da proposta de modelagem com organizacoes, setores, perfis, permissoes e historico de validacao. |
| 2.0 | 2026-10-02 | Remocao de `im_organization`: `im_sector` passa a existir sem vinculo com organizacao. Atualizados o diagrama ER, o diagrama de classes, as regras de negocio, a lista de permissoes e os impactos esperados no backend. |
| 3.0 | 2026-10-02 | Usuario pode estar alocado a mais de um setor ao mesmo tempo: nova entidade `im_sector_allocation` substitui o `id_sector` unico de `im_user`. Atualizados o diagrama ER, o diagrama de classes, as regras de negocio, o fluxo de validacao entre setores e os impactos esperados no backend. |
| 4.0 | 2026-10-03 | Simplificacao do modelo de permissoes: as 12 permissoes granulares da revisao anterior dao lugar a apenas tres (`ADMIN`, `MANAGE_SECTOR`, `MANAGE_ITEM`). Remove a validacao parcial entre setores por completo (valida se autorizado, senao e negado) e adiciona o campo `active` em `im_sector`/`im_inventory` para ativar/desativar em vez de apagar. Atualizados os perfis iniciais sugeridos, o fluxo de validacao, as regras de negocio, os impactos esperados no backend e os pontos em aberto. |
