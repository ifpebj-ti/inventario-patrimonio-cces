# Mapa da Documentacao

Esta pagina acompanha os artefatos esperados para o projeto e indica o que ja existe na wiki, o que esta parcialmente coberto e o que ainda precisa de complemento.

## Engenharia de Software

| Artefato esperado | Status | Onde esta | Observacao |
| --- | --- | --- | --- |
| Documento de Visao do Projeto | Pendente na wiki | [Home](./Home) contem uma visao geral resumida | Criar pagina propria `Documento-de-Visao` com objetivo, escopo, publico-alvo, restricoes, stakeholders e criterios de sucesso. |
| Analise de Concorrencia | Disponivel | [Analise de Concorrencia](./Analise-de-Concorrencia) | Criada com comparativo inicial entre SUAP, SIPAC, Snipe-IT, GLPI e Asset Panda. |
| Backlog do Produto e Requisitos no Project | Pendente na wiki | Pagina planejada: `Documento-de-Requisitos` | A wiki deve registrar pelo menos um resumo dos epicos/requisitos e apontar para o Project quando ele for a fonte operacional. |
| Arquitetura e Modelagem de Dados | Disponivel parcialmente | [C4 - Contexto](./C4-Contexto), [C4 - Containers](./C4-Containers), [C4 - Componentes](./C4-Componentes), [Documento de Modelagem de Dados](./Documento-de-Modelagem-de-Dados), [Architecture Decision Records](./Architecture-Decision-Records) | Falta criar ou consolidar uma pagina de implantacao C4 se esse nivel for exigido como artefato separado. |

## Seguranca

| Artefato esperado | Status | Onde esta | Observacao |
| --- | --- | --- | --- |
| Modelagem de Ameacas | Disponivel | [Modelagem de Ameacas](./Modelagem-de-Ameacas) | Inclui arquivo importavel no OWASP Threat Dragon e resumo dos riscos STRIDE. |
| Guia de Boas Praticas de Desenvolvimento Seguro | Disponivel | [Guia de Boas Praticas de Desenvolvimento Seguro](./Guia-de-Boas-Praticas-de-Desenvolvimento-Seguro) | Inclui autenticacao, autorizacao, secrets, backend, frontend, banco, supply chain, checks e fluxo de versionamento/release. |
| Zero vulnerabilidades criticas aferidas pelo Dependabot do GitHub | Parcial | Dependabot configurado em `.github/dependabot.yml`; checks de imagem usam Trivy | A evidencia final precisa vir da aba Security/Dependabot do GitHub ou de captura/relatorio do repositorio, pois esse status nao fica totalmente verificavel apenas pelos arquivos versionados. |

## Infraestrutura

| Artefato esperado | Status | Onde esta | Observacao |
| --- | --- | --- | --- |
| Aplicacao conteinerizada | Disponivel | `docker-compose.yml`, `backend/Dockerfile`, `frontend/Dockerfile`, [Guia de Execucao, Configuracao e Operacao](./Guia-de-Execucao-Configuracao-e-Operacao) | Compose sobe PostgreSQL, backend e frontend; mobile segue via Expo em desenvolvimento. |
| Guia de Execucao, Configuracao e Operacao | Disponivel | [Guia de Execucao, Configuracao e Operacao](./Guia-de-Execucao-Configuracao-e-Operacao) | O guia existia em `docs/execucao-configuracao-operacao.md` e foi publicado tambem em `docs/wiki`. |
| Inicio da esteira de CI/CD | Disponivel parcialmente | [Guia de Boas Praticas de Desenvolvimento Seguro](./Guia-de-Boas-Praticas-de-Desenvolvimento-Seguro), workflows em `.github/workflows` | Ha Quality, validacao de PR, sync com issue, build/scan de imagens, publicacao de imagens de development, release e publicacao da wiki. Ainda pode ser util criar uma pagina especifica de CI/CD com diagrama da esteira. |

## Proximos Itens Recomendados

1. Criar `Documento-de-Visao` como pagina propria da wiki.
2. Criar `Documento-de-Requisitos` com resumo do backlog e link para o Project.
3. Criar ou completar `C4-Implantacao` caso o artefato de infraestrutura exija esse nivel.
4. Registrar evidencia de Dependabot sem vulnerabilidades criticas.
5. Criar uma pagina dedicada de CI/CD se a avaliacao exigir a esteira separada do guia de seguranca.

## Historico

| Versao | Data | Descricao |
| --- | --- | --- |
| 1.0 | 2026-09-15 | Criacao do mapa de artefatos da wiki com status por Engenharia, Seguranca e Infraestrutura. |
