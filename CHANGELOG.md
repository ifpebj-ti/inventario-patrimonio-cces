# Changelog

Todas as mudanças relevantes do Inventarium são registradas neste arquivo.

O histórico é atualizado manualmente na própria pull request antes do merge em `main`.

## [Unreleased]

### Added

- Adicionado o `docker-compose.production.yml` para a VM de produção, com imagens do GHCR selecionadas por `IMAGE_TAG`, nome de projeto e de volume fixos, portas do backend e do frontend apenas em loopback e healthcheck do frontend.
- Adicionados testes (`npm run test:github`) que validam o compose de produção com `docker compose config`.

### Changed

- O compose de produção passa a repassar `SYSTEM_ADMIN_*`, `FORWARD_HEADERS_STRATEGY` (padrão `native`), `AUTH_LOGIN_*` e `CORS_ALLOWED_ORIGINS`.
- Atualizados `.env.example`, `backend/.env.example` e o guia de execução e operação com as variáveis de produção.

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
