# Changelog

Todas as mudanças relevantes do Inventarium são registradas neste arquivo.

O histórico é atualizado manualmente na própria pull request antes do merge em `main`.

## [Unreleased]

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
