# Changelog

Todas as mudanças relevantes do Inventarium são registradas neste arquivo.

O histórico é atualizado manualmente na própria pull request antes do merge em `main`.

## [Unreleased]

### Breaking Changes

- **Cargo por alocação no lugar do perfil global, e administrador como coluna do usuário.** O perfil (`im_user.id_profile`) sai do usuário e o cargo passa a valer **por setor**, na alocação (`im_sector_allocation.id_role`, obrigatório): o mesmo usuário pode ser gestor em um setor e só consulta em outro. Mudanças de contrato da API: `/profiles` virou `/roles`; `?profileId=` virou `?roleId=`; `PATCH /users/{id}/profile` foi removido; `POST /users/{id}/sectors/{sectorId}` virou `PUT` com `{ "roleId": … }` (obrigatório; também troca o cargo se o usuário já estiver no setor); `/auth/me` e o `user` do login deixam de trazer `profileId` e `permissions` e passam a trazer `administrator` e `allocations` (setor, cargo e permissões do cargo); `UserDto` troca `profileId` por `administrator` e `allocations`; a permissão `ADMIN` e o perfil `ADMIN_ORGANIZATION` saem do catálogo. A migration `7.0.xml` converte os dados existentes: cada alocação recebe como cargo o perfil que o usuário tinha (sem perfil vira Consulta), quem era `ADMIN_ORGANIZATION` vira administrador (e as alocações dele viram Gestor de Setor), os cargos de origem ganham os nomes "Gestor de Setor", "Operador de Campo" e "Consulta", e a coluna `id_profile` é removida. **Faça backup do banco antes de subir esta versão**: a migration não tem volta automática.
- Os endpoints de validação e importação de itens passam a aceitar exclusivamente CSV; uploads XLS/XLSX deixam de ser aceitos. A exportação continua em XLSX.

### Added

- Adicionado o coletor Grafana Alloy (`infra/alloy/`) e os scripts `infra/scripts/deploy-alloy.sh` e `bootstrap-alloy.sh`: cada VM envia consumo do servidor, consumo por container e logs dos containers para a VM de observabilidade (métricas ao Prometheus e logs ao Loki, com o rótulo `vm`). O Grafana ganha o dashboard provisionado **Inventarium - Infraestrutura**. Testes de compose, `alloy validate` e consistência do dashboard em `npm run test:github`; ADR 0007 e modelo de ameaças (I-15) atualizados (issue #211). Traces e métricas do backend continuam para o PR do backend.
- Adicionada a stack de observabilidade da VM privada em `infra/observability/` (Prometheus, Loki, Tempo, Grafana e SonarQube com limites de memória, versões fixas e retenção curta), o script `infra/scripts/deploy-observability.sh` (instala ou atualiza a VM existente por SSH via a VM da aplicação) e `bootstrap-observability.sh` (kernel do SonarQube, segredos 0600 gerados na VM). O Terraform passa a abrir no NSG, só para a subnet da aplicação, as portas 3000, 3100, 4317, 4318, 9000 e 9090, o padrão da VM sobe para 2 OCPUs / 8 GB e `ignore_changes` impede recriar a VM por mudança de imagem ou cloud-init. Novo ADR 0007 e testes em `npm run test:github` (issue #211). Os coletores da VM da aplicação e as métricas do backend vêm em PRs seguintes.
- Adicionado o job `deploy-production` ao workflow de release: depois de publicar a GitHub Release, ele chama o webhook da stack no Portainer (secret de repositório `PORTAINER_STACK_WEBHOOK_URL`) e confere `/actuator/health` e a página inicial. Sem o secret o job termina sem falhar. A lógica está em `.github/scripts/release/deploy-production.js`, com testes em `npm run test:github` (issue #212). As imagens de release passam a ser publicadas também com a tag `latest` (depois dos scans), que é a que a stack usa; as tags `vX.Y.Z` continuam para rollback.
- Adicionada a alocação de usuários pela tela de **Setores**: **Usuários associados** abre uma barra lateral à direita com os usuários do setor agrupados por cargo; nela o administrador (em qualquer setor) e o gestor (nos setores que gerencia) trocam o cargo, removem usuários e abrem **Adicionar usuários**, um modal com os usuários ativos do sistema que ainda não estão no setor, para marcar um ou vários, escolher um cargo e alocar todos de uma vez. Novos endpoints: `GET /sectors/{id}/assignable-users` (só `id`, `name` e `email`) e `PUT /sectors/{id}/members` (`{ userIds, roleId }`, tudo ou nada; o gestor só atribui cargos sem `MANAGE_SECTOR` e não mexe em outro gestor nem em administrador).
- Adicionado o CRUD de cargos (`/roles`): só o administrador cria, edita e exclui; o gestor lê a lista para escolher o cargo ao alocar. Cada cargo é um conjunto de permissões do catálogo (`MANAGE_SECTOR` e `MANAGE_ITEM`) escolhido pelo administrador. O `RoleDto` traz `management`, `allocationCount`, `userCount` e `deletable`; excluir cargo só é aceito sem nenhuma alocação **e** sem nenhuma permissão (senão `409`). Mudar as permissões de um cargo vale na hora para todas as alocações dele, e a tela pede confirmação mostrando o impacto quando o cargo está em uso.
- Adicionado `PATCH /users/{id}/administrator` (só o administrador, nunca na própria conta) e a caixa **Administrador** no modal "Gerenciar acesso".
- Adicionado, no modal "Gerenciar acesso", um seletor de cargo em cada setor do usuário (o setor entra com o cargo de menos permissões); trocar o cargo de um setor já alocado é uma alteração pendente como as demais.
- As telas de inventário e de item passam a decidir por setor o que o usuário pode fazer (gerenciar inventário, importar, editar dados do item, escanear e validar), com `canManageInventoryIn`/`canOperateItemsIn` e o hook `useInventorySector`; na lista de inventários, Editar e Excluir aparecem só nos setores que o usuário gerencia, e o formulário de inventário só oferece esses setores.
- Adicionada a exclusão de setor (`DELETE /sectors/{id}`), **só para o administrador** e só para setor sem usuário, inventário nem auditoria associados (senão `409`; esses setores só podem ser ativados ou desativados). `SectorDto` ganha o campo `deletable` e a tela de Setores ganha o botão **Excluir**, com confirmação, que só aparece nesses casos. Gestor de setor continua vendo só os próprios setores e sem criar, editar nem excluir (sub-issue #234 de #154).
- Adicionado, na lista de Usuários, o botão rápido **Desativar/Ativar** ao lado de "Gerenciar acesso" (só administrador): desativar pede confirmação e reativar executa direto, com aviso de sucesso ou erro.
- Adicionados testes Playwright do fluxo CSV no navegador e execução no workflow Quality, com respostas de API simuladas e cache Next isolado.
- Adicionada verificação local de códigos vazios/longos, duplicatas no CSV, preço inválido/negativo, aspas e linhas físicas antes da pré-validação remota.
- Adicionados modelo CSV para download, verificação de extensão/cabeçalho no frontend, parsing CSV no backend, mensagens do fluxo de validação/importação e limpeza dos itens com confirmação na interface.
- Documentado o fluxo de planilha em duas etapas, a detecção de duplicatas durante a importação sequencial e as pendências de validação prévia, persistência em lote e paralelismo.
- Adicionado o `docker-compose.production.yml` para a VM de produção, com imagens do GHCR selecionadas por `IMAGE_TAG`, nome de projeto e de volume fixos, portas do backend e do frontend apenas em loopback e healthcheck do frontend.
- Adicionados testes (`npm run test:github`) que validam o compose de produção com `docker compose config`.
- Adicionada a configuração do Nginx da VM da aplicação em `infra/nginx/app-vm/`: TLS 1.2/1.3, redirecionamento 80→443, HSTS, `X-Content-Type-Options`, `X-Frame-Options`, `Referrer-Policy`, `Permissions-Policy`, CSP (em `Report-Only` na primeira semana), rate limit e proxy para front, API, Portainer, Sonar e Grafana.
- Adicionados os scripts da VM em `infra/scripts/app-vm/` (bootstrap, emissão e renovação de certificados Let's Encrypt, criação da stack no Portainer, backup e restore do Postgres) e `infra/scripts/deploy-nginx.sh`.
- Adicionado IP público reservado, bucket de backups com retenção e IAM para instance principal no Terraform.
- Adicionado o workflow `Infra CI` (testes dos scripts, `nginx -t`, `shellcheck` e execução dos scripts no Oracle Linux 8, `terraform fmt` e `validate`).
- Adicionados o ADR 0006 e o runbook `docs/wiki/Deploy-em-Producao-OCI.md`; seção de ameaças da infraestrutura na modelagem de ameaças.

### Changed

- Os modais **Novo cargo**, **Editar cargo**, **Novo setor**, **Editar setor** e **Adicionar usuários** passam a seguir o padrão do modal "Gerenciar acesso": cabeçalho com o título e o ×, conteúdo em seções com título e descrição, e rodapé fixo com o estado à esquerda ("Nenhuma alteração pendente", "N selecionados"…) e as ações à direita. Ao editar um cargo ou setor, **Salvar** só habilita quando algo mudou.
- O modal "Gerenciar acesso" de Usuários deixa de alocar: mostra primeiro a lista de setores e cargos em leitura (com atalho para Setores), depois o papel de **Administrador** e, por último, Desativar/Reativar; **Salvar alterações** fecha o modal. Os cartões da lista de Usuários deixam de mostrar papel e setores. O botão "Usuários associados" de Setores só aparece para o administrador e para o gestor daquele setor.
- A tela "Perfis e permissões" (`/profiles`) virou **"Cargos e permissões"** (`/roles`), só do administrador, agora com criar, editar e excluir cargo. O menu do usuário mostra "Administrador", o nome do cargo (se for o mesmo em todos os setores) ou "Vários cargos". Quem não está alocado em nenhum setor não é gestor de nada: não vê Setores nem inventários de nenhum setor.
- Quem entra pela primeira vez com Google continua nascendo sem acesso, agora **sem alocação e sem ser administrador**; o gestor do setor (cargo com `MANAGE_SECTOR`) só aloca cargos sem gestão e não altera outro gestor nem um administrador. `GET /users` e a lista de membros do setor valem para o gestor só nos setores que ele gerencia.
- A tela de Usuários (item do menu e rota) passa a ser **só do administrador**; o gestor deixa de vê-la.
- A confirmação das telas administrativas (desativar/reativar usuário e setor, alterar perfil, alocar/desalocar) ganhou cabeçalho próprio para o título, nome em destaque, lista das consequências e botão com o verbo da ação (vermelho quando tira acesso ou desfaz algo); o foco inicial fica em "Cancelar" e saiu o botão "Fechar" redundante.
- Os modais administrativos (acesso do usuário, setor e usuários do setor) seguem o mesmo padrão da confirmação: cabeçalho fixo com o título e uma divisória, conteúdo com espaçamento próprio que rola quando é maior que a tela.
- O modal "Acesso de <usuário>" foi refeito: cabeçalho com avatar, estado e e-mail; perfil em cartões com descrição e a marca "Perfil atual"; setores como etiquetas e um campo "Adicionar setor" em forma de seleção com busca (lista completa ao clicar no campo vazio, filtro por nome ou sigla e inclusão ao clicar); perfil e setores viram alterações pendentes salvas de uma vez em "Salvar alterações" (rodapé com a contagem e "Cancelar"; só mexer no próprio perfil pede confirmação); e "Desativar conta" por último, numa caixa de destaque.
- Só quem tem a permissão `ADMIN` ativa ou desativa usuários (`PATCH /users/{id}/activate` e `/deactivate` respondem `403` ao gestor). O usuário criado no primeiro login com Google passa a nascer **sem perfil** (antes `CONSULTA`); um administrador atribui o perfil e os setores depois. Contas já existentes não mudam.
- A importação no frontend exige aprovação HTTP 200 explícita para o arquivo atual; troca/remoção invalida o resultado, respostas antigas são ignoradas e cliques repetidos não reenviam o arquivo. Erros HTTP, rede, leitura e contratos inesperados bloqueiam Processar sem inferência pelo texto.
- Relatórios de importação total/parcial ou totalmente recusada preservam contadores atuais/legados e detalhes das falhas. A limpeza confirmada bloqueia ações simultâneas e só atualiza itens/seleção após sucesso.
- O compose de produção passa a repassar `SYSTEM_ADMIN_*`, `FORWARD_HEADERS_STRATEGY` (padrão `native`), `AUTH_LOGIN_*` e `CORS_ALLOWED_ORIGINS`.
- Atualizados `.env.example`, `backend/.env.example` e o guia de execução e operação com as variáveis de produção.
- O NSG da aplicação deixa de expor a porta 9443: o Portainer escuta só em `127.0.0.1` e é publicado pelo Nginx com allowlist de IPs. `allowed_admin_cidrs` passa a recusar `0.0.0.0/0`.
- A VM da aplicação nasce sem IP efêmero e usa IPs privados fixos; o `user_data` vai comprimido e a VM ignora mudanças de cloud-init (atualizações do Nginx usam `deploy-nginx.sh`). O Terraform exige versão 1.12 ou superior.
- A modelagem de ameaças ganhou a revisão `2026-10-11` (ameaça 15 mitigada); a revisão `2026-09-14` volta a ser a original.
- O CORS da API passa a ser global e configurável por `CORS_ALLOWED_ORIGINS` (padrão `http://localhost:3000`), no lugar do `@CrossOrigin(origins = "*")` de cada controller. Apenas os headers `Authorization` e `Content-Type` são aceitos; `Retry-After` e `Content-Disposition` continuam legíveis pelo navegador.

### Removed

- Removidos `POST /users` e `DELETE /users/{id}`, o `AdminUserService` e a tela de cadastro e exclusão de usuário: usuário só nasce no primeiro login com Google e nunca é excluído, só desativado (o histórico é preservado).
- Removido o pré-cadastro de usuário: a migration `6.0.xml` apaga a coluna `pending_google_login` (e quaisquer pré-cadastros ainda pendentes e suas alocações) e devolve `ck_user_system_google` à forma estrita; o campo `pendingGoogleLogin` sai do `UserDto` e o selo "Aguardando primeiro acesso" sai da tela.

### Security

- Corrigidas as vulnerabilidades HIGH e CRITICAL do frontend apontadas pelo Trivy: `next` 16.3.8 → 16.4.1 (GHSA-2464-fhr4-w9xw, GHSA-673q-mjm6-r5xr e CVE-2026-94482), `follow-redirects` 1.16.0 → 1.16.1 (CVE-2026-111755 e CVE-2026-111756) e `zod` 3.25.76 → 4.6.7 (CVE-2023-54404, corrigida só na série 4). A migração do zod troca `required_error` por `error` e tipa `z.coerce.number<number>()`; foram adicionados testes das validações (`npm run test:unit`, executados no job de testes do frontend). Resta apenas `cookie` 0.4.2 (CVE-2024-47764, severidade LOW), dependência transitiva do `nookies`.
- O backend recusa `*` em `CORS_ALLOWED_ORIGINS` na inicialização e responde `403` a origens não listadas, no preflight e na requisição real (ameaça 15 da modelagem de ameaças passa a mitigada).
- Removido de `backend/docs/GOOGLE_AUTH_SETUP.md` um valor de exemplo de `SECURITY_TOKEN_SECRET`; se algum ambiente o usou, o segredo deve ser rotacionado.

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

## [v0.2.0] - 2026-09-14

### Added

- Adicionadas sidebar responsiva e breadcrumbs dinâmicos no frontend.
- Corrigidas dependências pelo Dependabot.

## [v0.1.1] - 2026-09-14

### Fixed

- Corrigidas vulnerabilidades e o runtime das imagens Docker de frontend e backend.
- Ajustado o scan de imagens do Trivy.
