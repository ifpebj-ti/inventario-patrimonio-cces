# Changelog

Todas as mudanças relevantes do Inventarium são registradas neste arquivo.

O histórico é atualizado manualmente na própria pull request antes do merge em `main`.

## [Unreleased]

### Breaking Changes

- Os endpoints de validação e importação de itens passam a aceitar exclusivamente CSV; uploads XLS/XLSX deixam de ser aceitos. A exportação continua em XLSX.

### Added

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

- O compose de produção passa a repassar `SYSTEM_ADMIN_*`, `FORWARD_HEADERS_STRATEGY` (padrão `native`), `AUTH_LOGIN_*` e `CORS_ALLOWED_ORIGINS`.
- Atualizados `.env.example`, `backend/.env.example` e o guia de execução e operação com as variáveis de produção.
- O NSG da aplicação deixa de expor a porta 9443: o Portainer escuta só em `127.0.0.1` e é publicado pelo Nginx com allowlist de IPs. `allowed_admin_cidrs` passa a recusar `0.0.0.0/0`.
- A VM da aplicação nasce sem IP efêmero e usa IPs privados fixos; o `user_data` vai comprimido e a VM ignora mudanças de cloud-init (atualizações do Nginx usam `deploy-nginx.sh`). O Terraform exige versão 1.12 ou superior.
- A modelagem de ameaças ganhou a revisão `2026-10-11` (ameaça 15 mitigada); a revisão `2026-09-14` volta a ser a original.
- O CORS da API passa a ser global e configurável por `CORS_ALLOWED_ORIGINS` (padrão `http://localhost:3000`), no lugar do `@CrossOrigin(origins = "*")` de cada controller. Apenas os headers `Authorization` e `Content-Type` são aceitos; `Retry-After` e `Content-Disposition` continuam legíveis pelo navegador.

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
