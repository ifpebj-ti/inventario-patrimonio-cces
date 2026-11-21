# Changelog

Todas as mudanças relevantes do Inventarium são registradas neste arquivo.

O histórico é atualizado manualmente na própria pull request antes do merge em `main`.

## [Unreleased]

### Breaking Changes

- Backend (FIN-08): o JWT de sessão passa de 24 h para 8 h e todo token traz `jti` e `ver` (versão de sessão da conta); token sem eles é recusado com `401`, então quem estava logado precisa entrar de novo uma vez após o deploy. Token encerrado responde `401` com `{ "error": "Sessão encerrada" }`. Desativar a conta passa a encerrar todas as sessões dela (reativar não devolve os tokens antigos), e trocar ou desligar `SYSTEM_ADMIN_PASSWORD` encerra as sessões da conta de sistema. Migration `15.0.xml` (`im_user.token_version` e `im_revoked_token`).
- Backend (FIN-07): `POST /inventory/{id}/finalize` passa a exigir que o inventário tenha itens e nenhum `PENDENTE` (todo item validado ou marcado como não localizado), para o administrador também. Quando não pode, responde `422` com `{ "message": "...", "pendingItems": N }` (`pendingItems` `0` se não há itens); o `409` continua só para "já finalizado". Antes dava para finalizar com todos os itens pendentes ou sem itens. Isso fecha o caso realista da corrida validação × finalização (o operador não tem mais o que validar quando a finalização passa).
- Backend: o item ganha `validationStatus` (`PENDENTE`, `VALIDADO`, `NAO_LOCALIZADO`) no `ItemDto`; `isValid` continua e é `true` só para `VALIDADO`. A migration `14.0.xml` troca a coluna `im_item.is_valid` por `validation_status` (quem estava validado vira `VALIDADO`, os demais `PENDENTE`). `PATCH /item/{id}/validate` com `isValid` falso também desfaz a marcação de não localizado (antes só desvalidava), e o `409` de item não validado passa a ser de item `PENDENTE`.
- Backend (FIN-06): o valor monetário passa a ter uma leitura única (`Money.parseToCentavos`, em reais para centavos) usada pelo cadastro avulso, pela edição e pela planilha, e valor ausente ou malformado passa a ser recusado. `POST /inventory/add-single-item` recebe `price` como texto em reais (por exemplo `"4500,00"`), igual aos outros dois caminhos; antes era número em centavos, e o preço ausente virava 0. `PUT /item/{id}` responde `400` para preço ausente ou malformado, em vez de manter o preço antigo e responder `200`. Na planilha, a linha com valor em branco ou malformado é recusada na validação e na importação (antes virava 0), então a coluna "Valor" passa a ser obrigatória em todas as linhas.
- Backend (FIN-05): `POST /inventory/add-items` passa a ser tudo ou nada e deixa de responder `206`. A planilha inteira é validada antes de gravar (agora também código repetido na planilha e nome de sala com mais de 100 caracteres) e gravada numa transação só; com qualquer linha inválida responde `400` sem gravar nada, com `failedItems` apenas das linhas com erro, `successes` `0` e `totalProcessed` igual ao número dessas linhas. Antes as linhas válidas entravam e as demais voltavam em `failedItems`, e o inventário ficava meio importado. O front deixa de aceitar `206`.
- Backend: `PUT /item/{id}` passa a responder `409` quando o inventário está finalizado ou o item está validado e o corpo muda código, descrição, responsável, valor ou sala (vale também para administrador); antes só o estado de validação era barrado depois do fechamento. Um `PUT` que não muda esses dados continua `200`, e as observações seguem livres. Para corrigir um item validado é preciso desvalidá-lo antes (gestor ou administrador).
- Backend: desvalidar um item (`PATCH /item/{id}/validate` com `{ "isValid": false }`, ou `PUT` com `isValid` falso) passa a exigir administrador ou gestor do setor e inventário aberto; antes o operador desvalidava pelo `PUT`. O `PATCH` deixa de devolver `200` sem aplicar nada.
- Backend: `GET /item/export-sheet` e `POST /item/send-email-sheet` passam a exigir administrador ou gestor do setor do inventário (`MANAGE_SECTOR`). Consulta e Operador, que antes exportavam, recebem `403`; o front esconde os botões para eles. O envio só aceita destinatário de domínio permitido, e o `200` agora significa que o servidor de e-mail aceitou a mensagem (antes voltava antes do envio).

### Added

- Backend (FIN-08): `POST /auth/logout` encerra no servidor a sessão do token enviado (`204`), sem afetar os tokens de outros dispositivos da mesma conta. O `jti` fica numa denylist até o token expirar, e o `SecurityFilter` a consulta junto da checagem de conta desativada; os registros já expirados são apagados a cada logout. O prazo do token é `security.token.ttl` (padrão `PT8H`).
- Frontend: "Sair" avisa o servidor (`POST /auth/logout`, melhor esforço) antes de apagar o cookie, e o cookie passa a durar 8 h, como o token.
- Backend (FIN-07): `PATCH /item/{id}/not-found` marca um item pendente como não localizado, com motivo obrigatório (até 255 caracteres). Exige poder operar o inventário e inventário aberto; o motivo grava `MARKED_NOT_FOUND` na auditoria do item e vira uma observação. O item conta como conferido para finalizar, fica travado como o validado (só observações, não valida direto) e só o gestor o devolve a pendente.
- Frontend: a página do item ganha "Marcar como não localizado" (com o motivo num diálogo) e o badge "Não localizado"; a tabela e o card de estatísticas mostram os três estados (verificados, não localizados e pendentes); a leitura de um item não localizado avisa que o gestor precisa desfazer a marcação; e o erro `422` da finalização mostra o motivo e a quantidade de pendentes em vez de "já foi finalizado".
- Backend: `POST /inventory/{id}/reopen` (administrador ou gestor do setor) volta um inventário finalizado para `EM_ANDAMENTO` e limpa `completedAt`; é o caminho de correção depois do fechamento. `409` se o inventário não está finalizado.

### Fixed

- Backend (FIN-03 e parte da FIN-14): item validado ou de inventário finalizado só aceita observações. `PUT /item/{id}` recusa com `409` a mudança de código, descrição, responsável, valor ou sala nesses casos; antes só a mudança de `isValid` era barrada, e um gestor podia, por exemplo, trocar o valor de um bem de um inventário concluído sem deixar rastro.
- Backend: a transição de validação passa a ter uma regra única, usada pelo `PATCH /item/{id}/validate` e pelo `PUT`: validar exige poder operar e inventário aberto; desvalidar exige gestão, inventário aberto e item validado. Desvalidar e alterar dados no mesmo `PUT` é `409`.
- Backend: auditoria de toda mudança. A desvalidação grava `VALIDATION_REVOKED` em `im_item_audit`; cada edição de dados grava `UPDATE_ITEM` em `im_inventory_audit` com o antes e depois dos campos que mudaram; a reabertura grava `REOPEN_INVENTORY`. Migration `11.0.xml` adiciona `id_item` (sem FK) a `im_inventory_audit`, para a trilha de edição não impedir excluir o item.
- Frontend: a página do item esconde "Editar item" quando o item está validado, e o salvar mostra mensagem por status (`403` e `409`) em vez do texto genérico.
- Backend (FIN-02): a planilha patrimonial, com responsável e valor de todos os bens, não sai mais para quem só pode ler nem para qualquer endereço. `send-email-sheet` valida o corpo (`@Email`, assunto sem quebra de linha, tamanhos), aceita só domínios de `EMAIL_SHEET_ALLOWED_DOMAINS` (padrão `ifpe.edu.br`, com subdomínios; `400` fora da lista), limita os envios por usuário (`EMAIL_SHEET_MAX_PER_WINDOW`, padrão 10 por `EMAIL_SHEET_WINDOW`, `1h`; `429` com `Retry-After`) e responde `502` se o envio falhar, em vez de engolir o erro.
- Backend: migration `10.0.xml` cria `im_inventory_audit`; cada exportação e envio, inclusive os recusados (domínio, limite, falha), grava ator, setor do autor, inventário, destinatário e resultado. A tabela não tem FK de setor nem de inventário, para o histórico não impedir excluí-los.
- Frontend: botões de exportar planilha e enviar por e-mail só aparecem para quem gerencia o inventário; o envio mostra mensagem por status (`400`, `403`, `429`, `502`) e confirma "Planilha enviada por e-mail" só depois do envio.
- Backend: `POST /inventory/add-single-item` deixa de receber a entidade `Item` e passa a ler o DTO `NewItemRequest` (`code`, `name`, `description`, `price`, `locale`, `responsible`). Antes, um `id` no corpo fazia o Hibernate atualizar um item existente, e um gestor podia mover para o próprio inventário um item de outro setor (ou de um inventário concluído) e reescrever seus dados. Agora o item é sempre novo; `id`, `qrCode`, `isValid`, `validatedAt` e `divergent` do corpo são ignorados, e `code` ausente ou em branco retorna 400.
- Backend: o 201 de `add-single-item` passa a devolver o `ItemDto` do item criado (com `id` e `qrCode`) em vez da entidade serializada.
- Backend (FIN-04): nome único de inventário, sala e setor deixa de depender só da conferência antes de gravar. Duas requisições simultâneas com o mesmo nome passavam na conferência: o inventário gravava duplicado (não havia índice) e sala e setor falhavam com erro genérico. A migration `12.0.xml` cria o índice único `(name, id_user)` em `im_inventory` e, se já houver nomes repetidos, para com a lista a corrigir. A violação da constraint de inventário, sala e setor passa a responder `400` com a mensagem de nome já existente.
- Backend: `PUT /inventory/{id}` passa a responder `400` ao renomear para um nome que o responsável do inventário já usa; antes a duplicata era gravada. A conferência de nome de setor passa a usar o nome sem espaços nas pontas, o mesmo que é gravado.
- Backend (FIN-05): importações simultâneas do mesmo inventário passavam todas na conferência "não tem itens" e gravavam juntas, com código repetido. `POST /inventory/add-items` agora trava o inventário (`SELECT ... FOR UPDATE`) e confere dentro da transação, então a segunda importação recebe `409`. A migration `13.0.xml` cria o índice único `(id_inventory, code)` em `im_item` (o mesmo código continua valendo em inventários diferentes) e, se já houver código repetido no mesmo inventário, para com a lista a corrigir.
- Backend: `POST /inventory/add-single-item` passa a responder `400` quando o código já existe no inventário; antes o segundo item com o mesmo código era gravado.
- Backend (FIN-06): o preço deixa de mudar de escala conforme o endpoint (×100) e de ser lido de formas diferentes. Os três caminhos usam a mesma função, que aceita `1500`, `1500,50`, `1500.50`, `1.500`, `1.500,00` e `R$ 1.500,00` (inclusive o espaço não separável que a listagem gera, para o front poder devolver o texto que recebeu) e recusa sinal, letras, mais de duas casas decimais, mais de nove dígitos inteiros e o formato americano (`1,500.00`). O ponto seguido de três dígitos é milhar: `1.500` passa a valer R$ 1.500 (antes virava R$ 1,50). Antes, valores fora do formato eram ignorados em silêncio no PUT e viravam 0 na planilha, o sinal de menos era removido e um valor muito grande estourava o `long`.
- Backend: o cadastro de sala pela importação da planilha e pelo item avulso (`findOrCreate`) usa `INSERT ... ON CONFLICT DO NOTHING` e relê a sala, então duas importações com a mesma sala no mesmo setor terminam com a mesma linha, sem falhar.

## [v8.3.0] - 2026-11-14

### Added

- Adiciona o `springdoc-openapi` (linha 3.x, compatível com o Spring Boot 4) e libera `/swagger-ui.html` e `/v3/api-docs` no `SecurityConfig`. Os endpoints em si continuam protegidos.
- `config/OpenApiConfig.java`: informações da API, esquema de autenticação `bearerAuth` (JWT) aplicado globalmente e ordem dos grupos.
- Os 9 controllers ficam agrupados por domínio (`@Tag`), com `@Operation` (resumo, descrição e permissão exigida) e `@ApiResponse` para sucesso e erros.
- Os DTOs ganham `@Schema` com descrição e exemplos. Os records de `dto/doc` existem só para documentar corpos que o código lê como `Map` ou entidade e não são usados em runtime.
- `SWAGGER_ENABLED=false` desliga as duas rotas (padrão: ligado).
- `backend/README.md`: seção "Documentação da API" com as URLs, como autenticar no botão **Authorize** e como anotar um endpoint novo.
- A branch também leva o `fix(backend)` que remove as chaves sem quebra de linha da resposta de `POST /inventory/add-items` (commit `b4e4c16`), que o Swagger passou a expor.

## [v8.2.0] - 2026-11-03

### Added

- **Versão do sistema:** o `version_tag` do workflow de build (a tag da release, ex.: `v8.1.0`) entra no build da imagem do frontend como `NEXT_PUBLIC_APP_VERSION` (Dockerfile, `docker-compose.yml` e os dois builds do workflow). O menu lateral mostra a versão no rodapé, no desktop (expandido e colapsado) e no celular, depois do login. Sem a variável (build local) aparece `dev`.
- **Login:** tela redesenhada. No computador, painel da marca (degradê azul e destaques das funções) ao lado de um cartão de entrada mais leve; no celular, tela cheia com o topo da marca e a folha branca arredondada com as opções de login. A lógica de autenticação (Google e usuário e senha) não mudou.
- Testes unitários do formatador de versão, documentação no guia de execução (e no espelho da wiki) e entradas no CHANGELOG.

## [v8.1.0] - 2026-11-02

### Added

- Backend: o Actuator passa para a porta de gerenciamento (8081), publicada só em `127.0.0.1`. Health e `/actuator/prometheus` existem só nela; na porta pública da API o Actuator deixa de existir.
- Backend: métricas Prometheus (`micrometer-registry-prometheus`, histograma de latência HTTP) e traces OpenTelemetry por OTLP para o Tempo (`spring-boot-starter-opentelemetry`), desligados por padrão (`TRACING_EXPORT_ENABLED`, `TRACING_OTLP_ENDPOINT` e `TRACING_SAMPLING_PROBABILITY`).
- Infra: o Nginx da VM da aplicação encaminha apenas `/actuator/health(/liveness|/readiness)` para a 8081 (com a 8080 como reserva na transição); `/actuator/prometheus` nunca fica público. O compose de produção publica a 8081 em loopback e repassa as variáveis `TRACING_*`.
- Observabilidade: `backend.alloy` coleta as métricas do backend, dashboard **Inventarium - Backend** e correção do link log → trace no datasource do Loki.
- Testes: `ActuatorExposureE2ETest`, `NoiseFilteringSpanPredicateTest` e testes de Alloy, Nginx e compose em `npm run test:github`.
- Documentação: ADR 0007, modelo de ameaças, C4, guias e runbook de deploy, mais CHANGELOG.

**Ordem de implantação (importante):** atualizar o Nginx da VM da aplicação (`infra/scripts/deploy-nginx.sh`) **antes** do deploy do backend novo. Um Nginx antigo com o backend novo deixa o health público sem resposta. Passo a passo no runbook `docs/wiki/Deploy-em-Producao-OCI.md`.

## [v8.0.0] - 2026-11-02

### Breaking Changes

- **Salas por setor** (`im_room`): nome único no setor sem diferenciar maiúsculas, acentos e espaços ("Sala 105" = "sala  105"). API `GET`/`POST /sectors/{id}/rooms` e `PUT`/`DELETE /rooms/{id}`: quem lê o setor lista; administrador e gestor do setor criam, renomeiam e excluem; sala com itens ou auditoria não é excluída (`409`). Em **Setores**, o botão **Salas** abre a barra lateral com isso.
- **Validar exige a sala em setor com salas.** `PATCH /item/{id}/validate` aceita `{ roomId }` e responde `400` sem ela ou com sala de outro setor; setor sem salas valida como antes. O `PUT /item/{id}` que marca o item como validado segue a mesma regra (lê `foundRoomId`) e agora também grava a auditoria.
- **Divergência.** O item guarda a sala cadastrada (`id_room`) e a encontrada na última validação (`id_found_room`); `im_item_audit` ganha `id_found_room`, `id_expected_room` e `divergent`. A sala encontrada é só registro e não altera a cadastrada. Validar de novo na mesma sala não duplica o registro; em outra sala, grava um novo.
- **Tela:** ao escanear em setor com salas, o item abre **sem ser validado**, com **"Sala em que o item está"** já na sala cadastrada; trocar mostra "Divergente: cadastrada em X, encontrada em Y" e **Validar item** envia a sala. A tabela ganha a coluna **Encontrada** e o filtro **Mostrar só divergentes**; a página do item escolhe a sala por seleção; a exportação XLSX ganha "Sala encontrada" e "Divergência" no fim.
- **Planilha:** a coluna Sala acha ou cria a sala do setor do inventário (também em `add-single-item`). A pré-validação (`validate-sheet` com `inventoryId`) lista as **salas novas que serão criadas** antes de Processar, e a importação informa `createdRooms`.
- **Contrato da API:** `ItemDto` e `ItemAuditDto` ganham campos de sala (`roomId`, `roomName`, `foundRoomId`, `foundRoomName`, `divergent`) e `POST /inventory/validate-sheet` ganha o parâmetro opcional `inventoryId`.
- **Migration `9.0.xml`** no padrão dos changelogs (tags do Liquibase, `<sql>` só na conversão de dados): cria as salas por setor a partir de `im_item.locale` (grafias que só diferem em caixa, acento ou espaços viram a mesma sala), liga os itens e **remove a coluna `locale`**. O `locale` do `ItemDto` continua como o nome da sala. Itens de inventário sem setor (legado) perdem o texto da sala, pois não têm onde virar sala. **Faça backup do banco antes de subir esta versão.**
- Docs: API, planilha, modelagem de dados, diagrama de classes, doc do frontend e `CHANGELOG.md`.

## [v7.6.0] - 2026-11-01

### Added

- **Três contas de teste, uma por permissão**, no estilo da conta de sistema (login por usuário e senha, sem Google, pelo mesmo formulário "Entrar com usuário e senha"):

| Login | Permissão | O que faz no "Setor de Testes" |
| --- | --- | --- |
| `teste.consulta` | `VIEW_SECTOR` (Consultar) | Só lê inventários, itens, observações e exportação |
| `teste.operador` | `MANAGE_ITEM` + `VIEW_SECTOR` | Valida itens e registra observações; não edita dados nem gerencia inventários |
| `teste.gestor` | `MANAGE_SECTOR` + `VIEW_SECTOR` | Gerencia os inventários do setor, edita itens e aloca usuários nele |

- **`TestAccountsInitializer`** (novo `ApplicationRunner`, espelha o `SystemAdminInitializer`): a cada subida cria só o que falta. Para cada conta ligada: o usuário (conta de sistema, **não administradora**), o cargo de teste ("Teste · Consulta", "Teste · Operador", "Teste · Gestor"), a alocação no **"Setor de Testes"** (gravada direto no repositório, porque a API responde 404 para conta de sistema) e, se o setor não tem inventário, um **"Inventário de teste"** com 8 itens fictícios. Idempotente: não duplica contas, setor, cargos, alocações nem inventário, e respeita edições do administrador nos cargos de teste.
- **Uma senha por conta**, sem valor padrão (repositório público): `TEST_VIEWER_PASSWORD`, `TEST_OPERATOR_PASSWORD` e `TEST_MANAGER_PASSWORD`. Mínimo de 12 caracteres; **vazia desliga o login daquela conta e, se ela não existia, não a cria**; curta é recusada com erro no log; senha nova troca o hash. As três são independentes.
- As contas ficam **fora** de `GET /users` e de `GET /sectors/{id}/users` e não são alocáveis, desativáveis nem promovíveis pela API (como a conta admin). O login usa o mesmo limite de tentativas do admin (por usuário no backend, por IP no Nginx).
- **Configuração:** variáveis repassadas em `docker-compose.yml` e `docker-compose.production.yml` (vazias por padrão = contas desligadas), `.env.example`, e no bootstrap do Portainer (lista de variáveis permitidas e checagem de 12+ caracteres, como a do admin).
- Docs: seção "Contas de teste" no `backend/README.md`, tabelas de variáveis dos dois guias de execução, `Deploy-em-Producao-OCI.md` e `CHANGELOG.md`.

## [v7.5.0] - 2026-11-01

### Added

- **Nova permissão Consultar (`VIEW_SECTOR`), a base da leitura.** Quem a tem lê tudo dos inventários **do setor em que está alocado, e só dele**: lista e detalhe dos inventários, itens, observações, exportação da planilha e histórico de validação. **Não altera nada** (validar, escanear, editar, importar e excluir seguem com `403`).
- **Estar alocado deixou de bastar para ler.** A leitura exige o cargo ter `VIEW_SECTOR`, `MANAGE_ITEM` ou `MANAGE_SECTOR`. Um cargo **sem nenhuma permissão** fica alocado mas sem acesso aos dados do setor. `GET /inventory/user-inventories` e `GET /sectors` só trazem setores em que o cargo dá leitura (consultas JPQL agora juntam a alocação ao cargo e às permissões).
- **As permissões de gerenciar incluem a Consulta** (quem altera precisa enxergar): ao salvar um cargo com `MANAGE_SECTOR` ou `MANAGE_ITEM`, o `RoleService` inclui `VIEW_SECTOR` mesmo que o corpo não a traga. `MANAGE_ITEM` e `MANAGE_SECTOR` não mudam de escopo.
- **Migration `8.0.xml`**: cria `VIEW_SECTOR` e a associa a **todos os cargos existentes**, então quem lia (por estar alocado) continua lendo e o cargo "Consulta" passa a ter só ela. A migration só acrescenta dados.
- **Frontend:** na tela de cargos, as permissões aparecem em ordem (Consultar, Gerenciar Item, Gerenciar Setor); marcar uma de gerenciar marca e trava Consultar; cargo sem permissão aparece como "Sem acesso aos dados". Ao alocar usuários, o cargo escolhido por padrão é o de menos permissões **entre os que dão acesso** (o de só Consultar), não um cargo sem acesso.
- Docs: API (`docs/wiki/API-Setor-Perfil-e-Permissoes.md`, versão 2.2), `frontend/docs/administration.md` e `CHANGELOG.md`.

## [v7.4.1] - 2026-11-01

### Fixed

- Atualiza a dependência transitiva `follow-redirects` de **1.16.1 para 1.16.2** no `frontend/package-lock.json` (vem do `axios`). A 1.16.1 tem uma vulnerabilidade **HIGH** (CVE-2026-116689) que o Trivy passou a bloquear em todo PR; a 1.16.2 é a versão corrigida.
- O diff é só o bloco do `follow-redirects` no lockfile (3 linhas: versão, `resolved` e `integrity`). `package.json` não muda, e o `axios` aceita a faixa.

## [v7.4.0] - 2026-10-26

### Added

- Coletor **Grafana Alloy** (`infra/alloy/`) em cada VM: envia consumo do servidor (CPU, memória, disco, rede), consumo por container e logs dos containers para a VM de observabilidade (Prometheus e Loki), com o rótulo `vm`. Sem porta externa; socket do Docker e host somente leitura.
- Scripts `deploy-alloy.sh` (máquina do operador) e `bootstrap-alloy.sh` (VM): instalam ou atualizam o Alloy na VM da aplicação ou na de observabilidade (via ProxyJump).
- Dashboard provisionado **Inventarium - Infraestrutura** no Grafana, com seletor de VM.
- Correção: o `bootstrap-observability.sh` agora reinicia o Grafana após o deploy, porque ele só lê o provisionamento ao iniciar (o dashboard não aparecia sem isso).
- ADR 0007, runbook (4.11), modelo de ameaças (I-15) e CHANGELOG atualizados.
- Observação sobre a issue: no lugar de unit `systemd`, a stack usa `restart: unless-stopped` do Docker; no lugar de node_exporter e cAdvisor separados, usa os exporters embutidos no Alloy. Traces e métricas do backend ficam para a issue #215.

## [v7.3.0] - 2026-10-25

### Added

- A release passa a publicar cada imagem com duas tags, `vX.Y.Z` e `latest` (a mesma imagem, e o `latest` só depois dos scans do Trivy).
- Novo job `deploy-production` no `release.yml`: chama o webhook da stack no Portainer e confere `/actuator/health` da API e a página inicial por até 5 minutos. Sem o secret `PORTAINER_STACK_WEBHOOK_URL` o job termina sem falhar. A URL é mascarada nos logs.
- Documentação do runbook, da esteira, do modelo de ameaças (I-2 e I-8) e CHANGELOG.
- **VM de observabilidade (ADR 0007):** stack em `infra/observability/` (Prometheus, Loki, Tempo, Grafana e SonarQube, com versão fixa e limite de memória), `deploy-observability.sh` e `bootstrap-observability.sh`. Terraform: portas no NSG só para a subnet da aplicação, VM padrão 2 OCPUs / 8 GB e `ignore_changes`. Ainda faltam o coletor Alloy na VM da aplicação e as métricas do backend (próximos PRs).
- **Vulnerabilidades do Trivy no frontend:** `next` 16.4.1, `follow-redirects` 1.16.1 e `zod` 4.6.7 (migração das validações + testes). Sobra só `cookie` (LOW, via nookies).
- Fora do escopo: workflow do SonarQube.

## [v7.2.0] - 2026-10-19

### Added

- Os modais **Novo cargo**, **Editar cargo**, **Novo setor**, **Editar setor** e **Adicionar usuários** passam a seguir o padrão do modal "Gerenciar acesso" do usuário:
- cabeçalho com o título e o × para fechar (no lugar do botão "Fechar");
- conteúdo em **seções** com título e descrição, separadas por divisória ("Dados do cargo" e "Permissões"; "Dados do setor"; "Usuários" e "Cargo no setor");
- **rodapé fixo** com o estado à esquerda e Cancelar/Salvar à direita. Ao editar, o estado mostra "Nenhuma alteração pendente" ou "N alterações pendentes", e **Salvar só habilita quando algo mudou**; ao criar, mostra o que falta ("Informe o nome do setor") ou o que já está marcado ("2 permissões selecionadas", "3 selecionados"). Em Adicionar usuários, a contagem e o botão "Alocar N usuários" foram para o rodapé.
- Um só componente (`AdminModal`, com `AdminModalSection` e `AdminModalFooter`, em `shared.tsx`) passa a servir a todos, inclusive ao modal de acesso do usuário.
- Só frontend: nenhum endpoint, contrato, permissão ou migration muda.
- Docs: `frontend/docs/administration.md` e `CHANGELOG.md`.

## [v7.1.0] - 2026-10-19

> Alocação de usuários pela tela de Setores (#239).

### Added

- Adicionada a alocação de usuários pela tela de **Setores**: **Usuários associados** abre uma barra lateral à direita com os usuários do setor agrupados por cargo; nela o administrador (em qualquer setor) e o gestor (nos setores que gerencia) trocam o cargo, removem usuários e abrem **Adicionar usuários**, um modal com os usuários ativos do sistema que ainda não estão no setor, para marcar um ou vários, escolher um cargo e alocar todos de uma vez. Novos endpoints: `GET /sectors/{id}/assignable-users` (só `id`, `name` e `email`) e `PUT /sectors/{id}/members` (`{ userIds, roleId }`, tudo ou nada; o gestor só atribui cargos sem `MANAGE_SECTOR` e não mexe em outro gestor nem em administrador).

### Changed

- O modal "Gerenciar acesso" de Usuários deixa de alocar: mostra primeiro a lista de setores e cargos em leitura (com atalho para Setores), depois o papel de **Administrador** e, por último, Desativar/Reativar; **Salvar alterações** fecha o modal. Os cartões da lista de Usuários deixam de mostrar papel e setores. O botão "Usuários associados" de Setores só aparece para o administrador e para o gestor daquele setor.

## [v7.0.0] - 2026-10-19

### Breaking Changes

- **Cargo por alocação no lugar do perfil global.** O perfil sai do usuário (`im_user.id_profile`) e o cargo passa a valer **por setor**, na alocação (`im_sector_allocation.id_role`, obrigatório). O mesmo usuário pode ser gestor no setor A e só operar itens (ou só consultar) no B. A autorização lê o cargo da alocação do setor do recurso: `canManageSector`, `canManageInventory`, `canOperateInventory` e a checagem de alocar usuário passam a usar o cargo daquele setor. O código só confere permissões, nunca o nome do cargo.
- **Administrador vira coluna** (`im_user.is_administrator`), fora dos cargos. A permissão `ADMIN` e o perfil `ADMIN_ORGANIZATION` saem do catálogo; a conta de sistema é sempre administradora. `PATCH /users/{id}/administrator` (só administrador, nunca na própria conta, então o sistema não fica sem administrador).
- **CRUD de cargos (`/roles`).** Só o administrador cria, edita e exclui; o gestor lê a lista para escolher o cargo ao alocar. O catálogo de permissões segue fixo (`MANAGE_SECTOR` e `MANAGE_ITEM`). `RoleDto` traz `management`, `allocationCount`, `userCount` e `deletable`. **Excluir cargo só sem nenhuma alocação e sem nenhuma permissão** (senão `409`). Mudar as permissões de um cargo em uso vale na hora para todas as alocações, e a tela pede confirmação mostrando o impacto.
- **Alocar com cargo:** `PUT /users/{id}/sectors/{sectorId}` com `roleId` obrigatório cria a alocação ou troca o cargo. O gestor só atribui cargos sem `MANAGE_SECTOR` e não altera outro gestor nem um administrador.
- **`/auth/me`** passa a trazer `administrator` e `allocations` (setor, cargo e permissões do cargo).
- **Migration `7.0.xml`** converte os dados existentes: cada alocação recebe como cargo o perfil que o usuário tinha (sem perfil vira Consulta); quem era `ADMIN_ORGANIZATION` vira administrador (e as alocações dele viram Gestor de Setor); `id_profile` é removido; os cargos de origem ganham os nomes "Gestor de Setor", "Operador de Campo" e "Consulta". Uma transação só: se algo falhar, nada fica pela metade. **Faça backup do banco antes de subir esta versão**: a migration não tem volta automática.
- **Mudanças de contrato da API:** `/profiles` virou `/roles`; `?profileId=` virou `?roleId=`; `PATCH /users/{id}/profile` foi removido; `POST /users/{id}/sectors/{sectorId}` virou `PUT` com `{ "roleId": … }` (obrigatório; também troca o cargo se o usuário já estiver no setor); `/auth/me` e o `user` do login deixam de trazer `profileId` e `permissions` e passam a trazer `administrator` e `allocations` (setor, cargo e permissões do cargo); `UserDto` troca `profileId` por `administrator` e `allocations`; a permissão `ADMIN` e o perfil `ADMIN_ORGANIZATION` saem do catálogo.
- **Frontend:** a tela "Perfis e permissões" vira "Cargos e permissões" (`/roles`); o modal "Gerenciar acesso" ganha a caixa Administrador e um seletor de cargo em cada setor (alterações pendentes salvas em lote); as telas de inventário e de item decidem por setor o que o usuário pode fazer, e a lista de inventários mostra Editar/Excluir só nos setores que ele gerencia.
- Docs: API (`docs/wiki/API-Setor-Perfil-e-Permissoes.md`, versão 2.0), `frontend/docs/administration.md`, `backend/README.md`, `backend/docs/AUTHENTICATION.md` e `CHANGELOG.md` (Breaking Changes, Added e Changed).

### Changed

- Quem entra pela primeira vez com Google continua nascendo sem acesso, agora **sem alocação e sem ser administrador**; o gestor do setor (cargo com `MANAGE_SECTOR`) só aloca cargos sem gestão e não altera outro gestor nem um administrador. `GET /users` e a lista de membros do setor valem para o gestor só nos setores que ele gerencia.

## [v6.2.0] - 2026-10-19

### Added

- **Excluir setor, só para o administrador.** Entra `DELETE /sectors/{id}` (`204`). Só `ADMIN` exclui: gestor, operador e consulta recebem `403`. O setor só é excluído se não tiver **usuário alocado, inventário nem auditoria de item** associados; senão a API responde `409` e o setor continua existindo, podendo apenas ser ativado ou desativado. Setor inexistente responde `404`. A checagem das auditorias existe porque `im_item_audit` tem FK para o setor.
- **Campo `deletable` no `SectorDto`**, somente de leitura, para a tela saber quando oferecer a exclusão. A regra é conferida de novo no `DELETE`.
- **Botão "Excluir" na tela de Setores**, ao lado de Editar e Ativar/Desativar, com confirmação no padrão dos modais administrativos. Só aparece para o admin e só em setor com `deletable: true`. Se a API recusar (`409`/`404`), a tela mostra o aviso em toast e recarrega a lista; `403` revalida a sessão, como nas outras ações.
- **Acesso à área de Setores (sem mudança de comportamento, agora coberto por teste):** admin vê todos os setores e faz tudo; gestor de setor acessa a área, vê só os setores em que está alocado (lista vazia se não estiver em nenhum) e não cria, edita, ativa nem exclui; operador, consulta e sem perfil não veem o menu e a rota mostra "Acesso restrito".
- Docs: API (`docs/wiki/API-Setor-Perfil-e-Permissoes.md`, versão 1.9, que antes dizia que não existia exclusão de setor), `frontend/docs/administration.md` e `CHANGELOG.md` em `[Unreleased]`.

## [v6.1.0] - 2026-10-18

### Added

- **Usuário só nasce no primeiro login pelo Google, e nasce sem perfil.** Saem `POST /users` (cadastro por admin ou gestor), o `AdminUserService` e a tela/modal "Novo usuário". O primeiro login passa a criar o usuário com `profileId: null` (antes `CONSULTA`): sem permissões e sem setor, até que um administrador atribua o perfil (`PATCH /users/{id}/profile`) e os setores. Contas já existentes não mudam.
- **Usuário não é excluído, só desativado.** Sai `DELETE /users/{id}` e o botão "Excluir usuário" (o delete ainda ignorava as linhas de `im_item_audit`, que têm FK NOT NULL para o usuário).
- **Só `ADMIN` ativa ou desativa usuários.** `PATCH /users/{id}/activate` e `/deactivate` passam a responder `403` ao gestor (antes ele desativava quem atuava só nos setores dele); a regra de escopo do gestor (`requireRemoveUser`) sai, por não ter mais uso. A desativação continua derrubando a sessão na hora (`SecurityFilter`).
- **Botão rápido na lista de Usuários**, ao lado de "Gerenciar acesso", só para administrador e nunca na própria conta: desativar abre a confirmação já existente; reativar executa direto. O resultado aparece em um aviso (sucesso e erro), e o modal "Gerenciar acesso" não abre por causa do clique. O botão "Desativar/Reativar" de dentro do modal continua, e o gestor vê "Somente o administrador ativa ou desativa usuários.".
- **Pré-cadastro removido por completo**, já que só o cadastro o criava: campo `pendingGoogleLogin` do `UserDto`, ramo do login que vinculava o pré-cadastro, selo "Aguardando primeiro acesso" e a coluna `pending_google_login`. Migration nova `6.0.xml` (sem editar changeset antigo): apaga alocações e usuários pendentes que porventura existam (um pré-cadastro nunca autenticou, então só pode ter alocações; se tivesse outra dependência, o `DELETE` falha e o changeset inteiro é desfeito), recria `ck_user_system_google` na forma estrita (`google_id` nulo se e somente se conta de sistema) e remove a coluna.
- Docs: doc de API (versão 1.8, para o front), `frontend/docs/administration.md`, `backend/README.md` e `backend/docs/AUTHENTICATION.md` (que ainda dizia que não havia como desativar usuário nem revogar sessão); `CHANGELOG.md` em `[Unreleased]`.

### Changed

- A tela de Usuários (item do menu e rota) passa a ser **só do administrador**; o gestor deixa de vê-la.
- A confirmação das telas administrativas (desativar/reativar usuário e setor, alterar perfil, alocar/desalocar) ganhou cabeçalho próprio para o título, nome em destaque, lista das consequências e botão com o verbo da ação (vermelho quando tira acesso ou desfaz algo); o foco inicial fica em "Cancelar" e saiu o botão "Fechar" redundante.
- Os modais administrativos (acesso do usuário, setor e usuários do setor) seguem o mesmo padrão da confirmação: cabeçalho fixo com o título e uma divisória, conteúdo com espaçamento próprio que rola quando é maior que a tela.
- O modal "Acesso de <usuário>" foi refeito: cabeçalho com avatar, estado e e-mail; perfil em cartões com descrição e a marca "Perfil atual"; setores como etiquetas e um campo "Adicionar setor" em forma de seleção com busca (lista completa ao clicar no campo vazio, filtro por nome ou sigla e inclusão ao clicar); perfil e setores viram alterações pendentes salvas de uma vez em "Salvar alterações" (rodapé com a contagem e "Cancelar"; só mexer no próprio perfil pede confirmação); e "Desativar conta" por último, numa caixa de destaque.

## [v6.0.1] - 2026-10-18

### Fixed

- **Nginx de produção:** o freio por IP (`zona auth`, 10 req/min com rajada de 10) passa a valer só em `location = /auth/login`. Antes valia para todo `/auth/`, então `/auth/google` e `/auth/me` também levavam 429; o `/auth/me` roda a cada carregamento de página e o campus inteiro sai por um IP só. Os dois agora ficam na zona geral (`api`, 20 req/s). O `/auth/google` recebe um ID token assinado pelo Google (sem segredo para adivinhar).
- **429 do nginx com CORS e `Retry-After`** (`snippets/login-rate-limited.conf`, via `error_page 429 = @login_rate_limited`). Sem isso o navegador esconde a resposta e o front mostraria "serviço indisponível". Os cabeçalhos de segurança são incluídos de novo no `location` nomeado, porque um `add_header` ali descarta os herdados do `server`.
- **Backend só por usuário:** `LoginAttemptLimiter` perde o mapa de IP e `AuthController` deixa de ler o IP. O bloqueio por usuário não muda (5 falhas em 15 min bloqueiam por 15 min, inclusive com a senha certa). Sai `AUTH_LOGIN_IP_MAX_FAILURES` (properties, compose de produção, `.env.example`, whitelist do `bootstrap-portainer-stack.sh` e testes). `FORWARD_HEADERS_STRATEGY` continua (padrão `native` em produção), mas o limitador não depende mais dele.
- **Frontend:** a mensagem de bloqueio do login passa a ser "Conta bloqueada, tente novamente mais tarde.", sem informar o tempo; saiu a leitura do `Retry-After` no front.
- Docs: `backend/README.md`, doc de API (versão 1.7, para quem faz o frontend), guias de execução, runbook de deploy (4.3) e modelagem de ameaças (I-9).

## [v6.0.0] - 2026-10-12

> Importação de planilha em CSV (#221).

### Breaking Changes

- Os endpoints de validação e importação de itens passam a aceitar exclusivamente CSV; uploads XLS/XLSX deixam de ser aceitos. A exportação continua em XLSX.

### Added

- Adicionados modelo CSV para download, verificação de extensão/cabeçalho no frontend, parsing CSV no backend, mensagens do fluxo de validação/importação e limpeza dos itens com confirmação na interface.
- Adicionada verificação local de códigos vazios/longos, duplicatas no CSV, preço inválido/negativo, aspas e linhas físicas antes da pré-validação remota.
- Adicionados testes Playwright do fluxo CSV no navegador e execução no workflow Quality, com respostas de API simuladas e cache Next isolado.
- Documentado o fluxo de planilha em duas etapas, a detecção de duplicatas durante a importação sequencial e as pendências de validação prévia, persistência em lote e paralelismo.

### Changed

- A importação no frontend exige aprovação HTTP 200 explícita para o arquivo atual; troca/remoção invalida o resultado, respostas antigas são ignoradas e cliques repetidos não reenviam o arquivo. Erros HTTP, rede, leitura e contratos inesperados bloqueiam Processar sem inferência pelo texto.
- Relatórios de importação total/parcial ou totalmente recusada preservam contadores atuais/legados e detalhes das falhas. A limpeza confirmada bloqueia ações simultâneas e só atualiza itens/seleção após sucesso.

## [v5.1.0] - 2026-10-12

### Added

- Nginx versionado em `infra/nginx/app-vm/`: redirecionamento 80→443, TLS 1.2/1.3, HSTS, nosniff, X-Frame-Options, Referrer-Policy, Permissions-Policy, CSP do front (em `Report-Only` na primeira semana), rate limit e proxy para front, API, Portainer, Sonar e Grafana.
- Scripts da VM em `infra/scripts/app-vm/`: bootstrap idempotente, instalação do Nginx com `nginx -t` e rollback automático, emissão e renovação de certificados Let's Encrypt (certbot em container), criação da stack GitOps no Portainer, backup e restore drill do Postgres; e `infra/scripts/deploy-nginx.sh` para atualizar a VM sem recriá-la.
- Terraform: IP público reservado, bucket de backup com retenção e IAM por instance principal, IPs privados fixos, cloud-init comprimido com os arquivos versionados e `ignore_changes` no `user_data` da VM que guarda o banco. A porta 9443 deixa de ser exposta (o Portainer fica em loopback, atrás do Nginx com allowlist de IPs). Exige Terraform 1.12 ou superior.
- Workflow `Infra CI`: testes dos scripts, `nginx -t`, `shellcheck`, scripts no Oracle Linux 8, `terraform fmt` e `validate`. As actions estão fixadas por SHA de commit (achados do Semgrep sobre tags mutáveis).
- Workflow de segurança: removido o `if:` do job `container-images` em `security-scans.yml`. Com ele, PRs que só alteram `infra/` ou `docs/` nunca reportavam o check obrigatório "Trivy container and dependency scans / Container images" e ficavam `BLOCKED`. O workflow reutilizável já pula o que não mudou, então o scan **não fica menos rigoroso**; apenas passa a sempre reportar o check.
- ADR 0006, runbook `Deploy em Produção na OCI`, ameaças de infraestrutura na modelagem de ameaças (nova revisão 2026-10-11; a revisão 2026-09-14 volta a ser a original) e CHANGELOG.

## [v5.0.2] - 2026-10-11

### Fixed

- Novo `CorsConfig`: CORS global lendo `cors.allowed-origins` (`CORS_ALLOWED_ORIGINS`, padrão `http://localhost:3000`).
- Removido o `@CrossOrigin(origins = "*")` dos 7 controllers.
- Métodos `GET/POST/PUT/PATCH/DELETE/OPTIONS`; headers aceitos apenas `Authorization` e `Content-Type`; `Retry-After` e `Content-Disposition` expostos; sem credenciais.
- O backend recusa `*` na inicialização; lista vazia não libera nenhuma origem.
- `docker-compose.yml` repassa `CORS_ALLOWED_ORIGINS`; documentação da API, da autenticação e do guia de operação atualizadas; ameaça 15 da modelagem de ameaças marcada como mitigada.

## [v5.0.1] - 2026-10-11

### Fixed

- `docker-compose.production.yml`: nome de projeto (`inventarium`) e volume (`inventarium_postgres-data`) fixos, back e front publicados só em `127.0.0.1`, imagens do GHCR selecionadas por `IMAGE_TAG` (padrão `latest`), healthcheck do front.
- O back passa a receber `SYSTEM_ADMIN_*`, `FORWARD_HEADERS_STRATEGY` (padrão `native`), `AUTH_LOGIN_*` e `CORS_ALLOWED_ORIGINS` (esta última só é repassada; o backend passa a lê-la na issue #208).
- `.env.example` e `backend/.env.example` com as variáveis de produção; `SECURITY_TOKEN_SECRET` e `SPRING_MAIL_PASSWORD` sem valor de exemplo.
- Removido de `backend/docs/GOOGLE_AUTH_SETUP.md` um `SECURITY_TOKEN_SECRET` de exemplo. **Se algum ambiente o usou, rotacione o segredo.**
- Guia de execução e espelho da wiki atualizados; link quebrado do README corrigido; CHANGELOG.
- Testes novos validando o compose de produção com `docker compose config`.

## [v5.0.0] - 2026-10-11

### Breaking Changes

O CRUD de perfil (criar, vincular e remover permissão, excluir só sem usuário) já existia desde a #138 e a #155. Esta PR fecha o que faltava de segurança e robustez, só no backend:
- **Autorização `ADMIN`** em todas as rotas de `/profiles` e `/permissions` (inclusive leitura) e em `PATCH /users/{id}/profile`. Antes qualquer usuário autenticado podia mudar o próprio perfil para administrador.
- **`/permissions` somente leitura**: `POST`, `PUT` e `DELETE` saem (respondem `405`). Permissões são definidas em código; uma nova entra por migration. Isso também impede renomear ou excluir a permissão `ADMIN`.
- **Perfil `ADMIN_ORGANIZATION` protegido** (`409`): não pode ser renomeado (a descrição pode), excluído nem perder a permissão `ADMIN`. Antes, renomear ou excluir esse perfil impedia o backend de subir (o `SystemAdminInitializer` o procura pelo nome) e tirar `ADMIN` deixava todos sem acesso.
- **Conta de sistema**: continua sem poder perder o perfil nem a permissão `ADMIN` por ação de ninguém (o `404` do PATCH vem da #203, agora com teste vindo de um admin). Os demais usuários, mesmo admins, podem ter o perfil trocado ou removido por outro admin, então não há regra de "último admin".
- **Bugs de robustez**: o nome é aparado com `trim()` antes da checagem de duplicado (criar `"X "` com `"X"` existente respondia `500`), e a corrida de dois pedidos iguais também vira `400`.
- **Exclusão com checagem explícita** (`UserRepository.existsByProfile_Id`): perfil com qualquer usuário, inclusive a conta de sistema, responde `409`. A captura da FK fica como rede de segurança.
- `docs/wiki/API-Setor-Perfil-e-Permissoes.md` (versão 1.6) atualizado para quem fará o frontend: permissão exigida por rota, os dois sentidos do `409` e como diferenciá-los, `/permissions` somente leitura, recomendações de UX e um resumo das mudanças de contrato. `backend/README.md` ajustado.

## [v4.4.0] - 2026-10-11

### Added

- A conta de sistema (`admin`) deixa de aparecer em `GET /users` (com e sem `?profileId=`) e em `GET /sectors/{id}/users`. O filtro fica em `UserRepository` (`findAllBySystemUserFalse` e `findByProfile_IdAndSystemUserFalse`) e em `usersAllocatedToSector`.
- `PATCH /users/{id}/profile` e `POST`/`DELETE /users/{id}/sectors/{sectorId}` respondem `404` para a conta de sistema, como se ela não existisse. Antes, qualquer usuário autenticado conseguia tirar o perfil `ADMIN_ORGANIZATION` dela pelo PATCH (que não tem checagem de permissão), e o `SystemAdminInitializer` só repara isso no boot quando há senha configurada.
- `findById` e `findByEmail` continuam enxergando a conta: login, `/auth/me`, autorização por permissão e o inicializador dependem deles.
- Removido `UserService.listAllUsers()`, que não tinha chamadores.
- `docs/wiki/API-Setor-Perfil-e-Permissoes.md` (nova versão 1.4) e `backend/README.md` atualizados.

## [v4.3.0] - 2026-10-11

### Added

- Limite de tentativas no `POST /auth/login`, com bloqueio temporário e contadores em memória (sem migration nem dependência nova):
- **por usuário**: 5 falhas em 15 min bloqueiam aquele usuário por 15 min, de qualquer IP;
- **por IP**: 10 falhas em 15 min bloqueiam aquele IP por 15 min, para qualquer usuário.
- Durante o bloqueio a resposta é `429 Too Many Requests` com `Retry-After` (segundos) e corpo `Too many attempts`. O bloqueio vale mesmo com a senha certa e é checado antes do BCrypt, então também poupa CPU.
- Usuário inexistente conta igual (a resposta continua sem revelar quais usuários existem). Requisições com campos vazios (400) não contam. Pedidos bloqueados não prolongam o bloqueio. Login com sucesso zera o contador do usuário, mas não o do IP.
- O bloqueio é temporário de propósito: um bloqueio permanente permitiria a qualquer um travar o `admin`. Contrapartida documentada: quem insistir consegue manter o `admin` indisponível, sem afetar o login Google dos demais.
- `LoginAttemptLimiter` tem teto de 10.000 chaves por mapa, descartando só entradas expiradas ou as mais antigas sem bloqueio ativo, e registra um `WARN` quando bloqueia (com o identificador sanitizado).
- Os limites são configuráveis (`AUTH_LOGIN_USER_MAX_FAILURES`, `AUTH_LOGIN_IP_MAX_FAILURES`, `AUTH_LOGIN_WINDOW`, `AUTH_LOGIN_BLOCK_DURATION`).
- IP do cliente: `getRemoteAddr()`. Atrás de proxy, `FORWARD_HEADERS_STRATEGY=native` (padrão `none`) com o nginx **sobrescrevendo** o `X-Forwarded-For`. Sem proxy confiável o cabeçalho é falsificável, por isso o padrão fica desligado.
- O `Retry-After` é exposto por CORS, para o navegador conseguir ler.
- Frontend: novo tipo `rateLimited` em `AuthError` e mensagem própria no formulário de senha ("Muitas tentativas. Tente novamente em N minutos."), em vez de "Falha inesperada".
- `README`, `docs/wiki/API-Setor-Perfil-e-Permissoes.md` (versão 1.4) e `backend/docs/AUTHENTICATION.md` atualizados; `.env.example` e `docker-compose.yml` com `FORWARD_HEADERS_STRATEGY`.

## [v4.2.0] - 2026-10-10

### Added

- Link discreto **"Entrar com usuário e senha"** abaixo do botão do Google na tela de login. Ele abre o formulário no mesmo card, com "Voltar" para fechar. O Google continua sendo o caminho principal e o card não muda para quem só usa o Google.
- O link fica fora do ternário do Google: sem `NEXT_PUBLIC_GOOGLE_CLIENT_ID` o aviso aparece e o login por senha continua disponível.
- Formulário (`react-hook-form` + `zod`) com usuário e senha. Campos vazios mostram a validação sem chamar o backend. Credencial errada mostra o erro na tela e em toast, e limpa a senha. Backend fora do ar mostra a mensagem de indisponibilidade.
- `passwordSignInRequest` chama `POST /auth/login`. O mapeamento de erro por status HTTP foi extraído para `toAuthError` e é usado também pelo login Google, sem mudar o comportamento dele.
- `AuthProvider` ganha `signInWithPassword`. A criação da sessão (cookie, usuário e redirect para `/dashboard`) foi extraída para uma função usada pelos dois logins.

## [v4.1.0] - 2026-10-10

> Conta de sistema com login por usuário e senha (#193).

### Added

- Conta de sistema (`admin` por padrão) que entra por usuário e senha, sem Google, com o perfil `ADMIN_ORGANIZATION`. Todos os outros usuários continuam entrando só pelo Google.
- Migration `20261010-1` (changeset novo, sem editar os antigos): `im_user.is_system_user`, `im_user.password_hash` e `google_id` nullable. O banco garante, por CHECK constraint, que `google_id` é nulo se e somente se `is_system_user` é verdadeiro, e que só conta de sistema tem senha.
- `POST /auth/login` devolve o mesmo `{ token, user }` do login Google. Falha sempre com `401` e a mesma mensagem (usuário inexistente, senha errada ou login por senha desligado); quando não há conta utilizável a senha é conferida contra um hash descartável, para não vazar por tempo.
- A conta é criada/atualizada no boot (`SystemAdminInitializer`) a partir de `SYSTEM_ADMIN_USERNAME` e `SYSTEM_ADMIN_PASSWORD`. **Sem senha default** (o repositório é público): senha vazia desliga o login por senha, senha com menos de 12 caracteres é recusada, e trocar a variável e reiniciar troca a senha.
- O identificador de login fica na coluna `email` (valor `admin`, sem `@`, então nenhuma conta Google casa com ele). A conta de sistema é recusada no fluxo Google.
- `passwordHash`, `googleId` e `systemUser` ficam fora de qualquer resposta JSON (`@JsonIgnore`), então o contrato de `/auth/me` não muda.
- `.env.example`, `docker-compose.yml`, `backend/README.md` e `docs/wiki/API-Setor-Perfil-e-Permissoes.md` atualizados.

## [v4.0.2] - 2026-10-10

### Fixed

- Sobe só as dependências do frontend que o Trivy obrigatório estava barrando, todas com versão corrigida disponível:

| Pacote | De | Para | Vulnerabilidade |
|---|---|---|---|
| `next` | 16.3.5 | 16.3.8 | `GHSA-vcvr-r3jv-pc5j` (CRITICAL, corrigida na 16.3.6) e `CVE-2026-94483` (HIGH, corrigida na 16.3.8) |
| `eslint-config-next` | 16.3.5 | 16.3.8 | acompanha o `next` |
| `sharp` (transitiva) | 0.35.4 | 0.35.5 | `GHSA-wq5f-xc86-pv6w` (HIGH) |
| `source-map-js` (transitiva) | 1.2.1 | 1.2.2 | `CVE-2026-93749` (HIGH) |

Nenhuma mudança de código. `package.json` muda 2 linhas (`next` e `eslint-config-next`, que já eram fixos em versão exata); o resto é `package-lock.json`: as 40 entradas que mudaram são só da família dessas bibliotecas (`@next/*` e os binários nativos por plataforma do `sharp`/`libvips`), conferido contra o lock da `main`.

## [v4.0.1] - 2026-10-10

> Migração do backend para o Spring Boot 4.1.1 (#195) e documentação da API de Setor, Perfil e Permissão (#184).

### Added

- Novo documento `docs/wiki/API-Setor-Perfil-e-Permissoes.md` cobrindo `/sectors`, `/users` (listagem e alocação), `/profiles` e `/permissions`.
- Para cada rota: permissão exigida, formato de corpo/resposta e os códigos de erro possíveis.
- Registra comportamentos não óbvios confirmados empiricamente (subindo a aplicação via Testcontainers): `403` sem token, `401` quando o `subject` do JWT não corresponde a um usuário existente, ausência de campo `message` no corpo de erro (padrão do Spring Boot), e o filtro `sectorId`/`profileId` de `/users` não ser combinável.

### Changed

- Spring Boot 3.5.16 → **4.1.1** (Spring Framework 7.0.9, Security 7.1.1, Tomcat 11, Hibernate 7.4.5, Liquibase 5.0.3). Corrige as duas CVEs CRITICAL do `spring-webmvc 6.2.19` (GHSA-pc63-qcmh-9cmg e GHSA-j9f9-w8pj-32f8), que só têm correção no Framework 7.0.9 e estavam travando o Trivy obrigatório de qualquer PR que mexe em backend/frontend.
- Gradle wrapper 8.13 → 8.14.6 (o Boot 4.1 exige 8.14+).
- `build.gradle`: starters renomeados (`webmvc`, `liquibase`), Testcontainers 2 (`testcontainers-junit-jupiter`/`testcontainers-postgresql`), remoção do pin `tomcat.version` (era do Boot 3) e pin do Jackson 2 movido para `jackson-2-bom.version` (segue em 2.21.7, com as correções das CVEs anteriores; o Boot 4 gerencia 2.21.5).
- `GoogleTokenProvider`: usa `JdkClientHttpRequestFactory` do Spring Framework no lugar do `RestTemplateBuilder`, que mudou de módulo. Mesmos 5 s de timeout; é o mesmo cliente que o Boot já escolhia por padrão.
- `ItemController`: `new ResponseEntity<>(null, status)` ficou ambíguo no Spring 7; trocado por `ResponseEntity.status(...).build()` (mesma resposta).
- Testes: `TestRestTemplate` agora vem de `spring-boot-resttestclient` (`@AutoConfigureTestRestTemplate`).
- `application.properties`: o Jackson 3 passou a (1) recusar campo primitivo ausente no corpo, o que dava **400** em todo `POST` de criação cujo DTO tem `long id`/`boolean active` que o cliente não envia, e (2) ordenar as chaves do JSON alfabeticamente. Os dois padrões do Boot 3 foram mantidos por configuração.

## [v4.0.0] - 2026-10-03

> Tag duplicada: aponta para o mesmo commit da `v3.0.0` (`55c4e97`), sem mudanças de código.

## [v3.0.0] - 2026-10-03

> Usuário em vários setores (#188).

### Breaking Changes

- Migration Liquibase: cria `im_sector_allocation` (id/id_user/id_sector, par único), copia os dados existentes de `im_user.id_sector` e derruba a coluna antiga.
- `SectorAllocation` (entidade), `SectorAllocationRepository`, `SectorAllocationService` — permite um usuário estar alocado a vários setores ao mesmo tempo.
- `PATCH /users/{id}/sector` sai; entram `POST`/`DELETE /users/{id}/sectors/{sectorId}` (idempotentes, mesmo estilo de `/profiles/{id}/permissions/{permissionId}`): `ADMIN` em qualquer setor, ou `MANAGE_SECTOR` + estar alocado no setor informado.
- `GET /sectors/{id}/users` novo — lista usuários alocados a um setor (mesma permissão de leitura de `/sectors`: `ADMIN` ou `MANAGE_SECTOR`). É a rota que a tela de setor usa para a aba lateral de usuários.
- `GET /users` perde o filtro `?sectorId=` (só `?profileId=` continua). `UserDto` novo: resposta de usuário troca `sectorId` (escalar) por `sectorIds` (lista).

## [v2.0.0] - 2026-10-03

> Permissões reais em Setor e na alocação usuário↔setor (#182).

### Breaking Changes

- Simplifica o seed de permissões para o modelo da revisão 4.0 da proposta: `ADMIN`, `MANAGE_SECTOR`, `MANAGE_ITEM` (trocando as 12 permissões antigas, ainda não lançadas em produção).
- `ADMIN_ORGANIZATION` → `ADMIN`; `GESTOR_SETOR` → `MANAGE_SECTOR`; `OPERADOR_CAMPO` → `MANAGE_ITEM`; `CONSULTA` continua sem nenhuma.
- Cria `AuthorizationService` para checar permissão e alocação de setor do usuário autenticado.
- `/sectors` escrita (create/update/activate/deactivate) exige `ADMIN`.
- `/sectors` leitura (list/getById) exige `ADMIN` ou `MANAGE_SECTOR`.
- `PATCH /users/{id}/sector` exige `ADMIN`, ou `MANAGE_SECTOR` + estar alocado no setor relevante da operação (o novo setor ao atribuir; o setor atual do usuário-alvo ao desvincular).
- `Profile`/`Permission` (CRUD) e `PATCH /users/{id}/profile` continuam só com `TODO` — ficam para uma etapa futura.
- `MANAGE_ITEM` fica seedada mas ainda sem nenhum endpoint checando ela (Item/Inventory são etapa futura).

## [v1.0.0] - 2026-10-03

> Remoção de `Organization` (#180), relatórios SARIF nos scans (#173) e documentação do changelog manual (#176).

### Breaking Changes

- Remove por completo a entidade `Organization` (model, repository, service, controller, DTO, endpoints `/organizations`, permissao `ORGANIZATION_MANAGE`).
- `Sector` passa a ser criado diretamente, sem `id_organization` e sem hierarquia de setor pai/filho; nome passa a ser unico de verdade.
- `DELETE /sectors/{id}` sai; entram `PATCH /sectors/{id}/activate` e `PATCH /sectors/{id}/deactivate`, com o novo campo `active`.
- Edita diretamente os changesets Liquibase de `ORGANIZATION`, `SECTOR` e do seed de permissoes (nada disso chegou a producao ainda, entao nao precisou de migration nova).
- Ajusta os testes E2E afetados (`SectorE2ETest` reescrito, `UserSectorProfileE2ETest`, `SeedDataE2ETest`, `ProfileE2ETest`).
- `Inventory` nao foi tocado nesta etapa — continua sem vinculo com setor, como ja estava em `main` (fica para uma etapa futura).

### Added

- Adicionado lint do frontend ao workflow `Quality`.
- Adicionados `spotlessCheck` e testes Gradle do backend ao workflow `Quality`.
- Renomeado o workflow de imagens de PR para `Security scans`.
- Consolidado o Semgrep no workflow de segurança junto ao Trivy.
- Configurada geração de SARIF do Semgrep e Trivy.
- Enviados os relatórios para Security → Code scanning e como artifacts temporários.
- Mantido o bloqueio do Trivy antes de qualquer push de imagem ao GHCR.
- Protegidas interpolações de contexto GitHub em scripts Bash dos workflows.
- Adicionado o `CHANGELOG.md` versionado para manutenção manual nas Pull Requests.
- Documentado o fluxo de atualização manual do changelog conforme o tipo de release.
- Registrada a limitação de automação com `GITHUB_TOKEN`, mantendo os checks obrigatórios íntegros.
- Documentada a publicação de imagens multi-arquitetura `linux/amd64` e `linux/arm64` no GHCR.
- Atualizados README, estratégia de branching, guia de contribuição, guia operacional e documentação da esteira de CI/CD.

## [v0.4.0] - 2026-09-28

### Added

- Adicionada a dependência `html5-qrcode` para leitura óptica via câmera de dispositivos desktop e mobile.
- Criados os componentes `ScannerTriggerButton` e `ScannerCard` para inicialização e acionamento do fluxo de leitura.
- Implementado o modal `BarcodeQrScannerModal` com captura de vídeo, seleção dinâmica de câmeras disponíveis, mira visual de enquadramento e feedback sonoro nativo via Web Audio API (`AudioContext`).
- Implementado o drawer lateral `ItemDetailDrawer` para visualização e edição rápida dos dados do patrimônio localizado.
- Implementado o fluxo contínuo de auditoria com o botão "Salvar e Escanear Próximo".
- Padronizados o grid, dimensões e responsividade dos cards no topo da tela de inventário.

## [v0.3.1] - 2026-09-21

### Fixed

- Removido o aplicativo mobile descontinuado e seu manifesto npm do dependency graph.
- Atualizada a configuração do Dependabot para monitorar apenas pacotes ativos.
- Atualizada a documentação operacional para refletir o escopo atual do projeto.

## [v0.3.0] - 2026-09-21

### Added

- Adicionado CRUD de organizações, setores e perfis no backend, com testes E2E.
- Evoluído o changelog Liquibase com as novas estruturas de domínio.
- Adicionada a base de infraestrutura Terraform para OCI.

## [v0.2.1] - 2026-09-19

### Added

- Documentação da wiki: modelagem de ameaças STRIDE, análise de concorrência, guia de desenvolvimento seguro, guia operacional, documento de visão, esteira de CI/CD e mapa da documentação.
- Proposta de modelo de dados por setor, ligada na wiki.

### Changed

- As imagens de contêiner passam a ser publicadas apenas em releases, e a documentação foi alinhada a isso.

## [v0.2.0] - 2026-09-14

### Added

- Adicionadas sidebar responsiva e breadcrumbs dinâmicos no frontend.
- Corrigidas dependências pelo Dependabot.

## [v0.1.1] - 2026-09-14

### Fixed

- Corrigidas vulnerabilidades e o runtime das imagens Docker de frontend e backend.
- Ajustado o scan de imagens do Trivy.

## [v0.1.0] - 2026-09-08

> Tag criada manualmente ao configurar a automação de releases, com o projeto como estava em 2026-09-08.
