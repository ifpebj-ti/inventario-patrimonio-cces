# Changelog

Todas as mudanças relevantes do Inventarium são registradas neste arquivo.

O histórico é atualizado manualmente na própria pull request antes do merge em `main`.

## [Unreleased]

### Breaking Changes

- Os endpoints de validação e importação de itens passam a aceitar exclusivamente CSV; uploads XLS/XLSX deixam de ser aceitos. A exportação continua em XLSX.

### Added

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

- A confirmação das telas administrativas (desativar/reativar usuário e setor, alterar perfil, alocar/desalocar) ganhou cabeçalho próprio para o título, nome em destaque, lista das consequências e botão com o verbo da ação (vermelho quando tira acesso ou desfaz algo); o foco inicial fica em "Cancelar" e saiu o botão "Fechar" redundante.
- Os modais administrativos (acesso do usuário, setor e usuários do setor) seguem o mesmo padrão da confirmação: cabeçalho fixo com o título e uma divisória, conteúdo com espaçamento próprio que rola quando é maior que a tela.
- O modal "Acesso de <usuário>" foi refeito: cabeçalho com avatar, estado e e-mail; perfil em cartões com descrição e a marca "Perfil atual"; setores como itens com busca por nome ou sigla; perfil e setores viram alterações pendentes salvas de uma vez em "Salvar alterações" (rodapé com a contagem e "Cancelar"; só mexer no próprio perfil pede confirmação); e "Desativar conta" por último, numa caixa de destaque.
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
