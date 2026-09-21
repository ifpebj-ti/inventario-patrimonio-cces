# Mapa da Documentacao

Este mapa serve para saber rapidamente onde cada artefato esta. Ele nao substitui o Project, nem tenta transformar a wiki em backlog.

## Leitura Rapida

| Frente | Situacao |
| --- | --- |
| Visao do projeto | Coberta em [Documento de Visao](./Documento-de-Visao). |
| Concorrencia | Coberta em [Analise de Concorrencia](./Analise-de-Concorrencia). |
| Requisitos e backlog | Ficam no GitHub Project. A wiki registra apenas a visao e o contexto do produto. |
| Arquitetura | Coberta pelos diagramas C4, modelagem de dados e ADRs. |
| Seguranca | Coberta pela modelagem de ameacas e pelo guia de desenvolvimento seguro. |
| Infraestrutura | Coberta pelo guia operacional, conteinerizacao e pagina de CI/CD. |

## Engenharia de Software

| Artefato | Onde consultar | Nota |
| --- | --- | --- |
| Documento de Visao | [Documento de Visao](./Documento-de-Visao) | Resume problema, objetivo, publico, escopo e limites do produto. |
| Analise de Concorrencia | [Analise de Concorrencia](./Analise-de-Concorrencia) | Compara o Inventarium com alternativas institucionais e ferramentas de mercado. |
| Backlog e requisitos | GitHub Project | O Project e a fonte viva de tarefas, requisitos operacionais, prioridades e andamento. Duplicar isso na wiki tende a desatualizar. |
| Arquitetura | [C4 - Contexto](./C4-Contexto), [C4 - Containers](./C4-Containers), [C4 - Componentes](./C4-Componentes) | Os diagramas mostram atores, containers e componentes principais. |
| Modelagem de dados | [Documento de Modelagem de Dados](./Documento-de-Modelagem-de-Dados) | Registra entidades, relacionamentos, chaves e restricoes do schema atual. |
| Proposta de evolucao da modelagem | [Proposta de Modelagem: Organizacoes, Setores e Permissoes](./Proposta-de-Modelagem-Organizacoes-Setores-e-Permissoes) | Registra o modelo alvo para organizacoes, setores, perfis, permissoes e historico de validacao. |
| Decisoes tecnicas | [Architecture Decision Records](./Architecture-Decision-Records) | Guarda decisoes arquiteturais que precisam continuar rastreaveis. |

## Seguranca

| Artefato | Onde consultar | Nota |
| --- | --- | --- |
| Modelagem de ameacas | [Modelagem de Ameacas](./Modelagem-de-Ameacas) | Inclui o arquivo do OWASP Threat Dragon e a lista de riscos STRIDE. |
| Boas praticas de desenvolvimento seguro | [Guia de Boas Praticas de Desenvolvimento Seguro](./Guia-de-Boas-Praticas-de-Desenvolvimento-Seguro) | Cobre autenticacao, autorizacao, secrets, API, frontend, banco, supply chain e release. |
| Vulnerabilidades do Dependabot | Aba Security/Dependabot do GitHub | A evidencia vem do GitHub. Se houver vulnerabilidade critica aberta, esse item nao deve ser marcado como concluido. |

## Infraestrutura

| Artefato | Onde consultar | Nota |
| --- | --- | --- |
| Aplicacao conteinerizada | `docker-compose.yml`, `backend/Dockerfile`, `frontend/Dockerfile` | Compose sobe PostgreSQL, backend e frontend. |
| Execucao, configuracao e operacao | [Guia de Execucao, Configuracao e Operacao](./Guia-de-Execucao-Configuracao-e-Operacao) | Explica ambiente local, variaveis, GHCR, logs, validacao e troubleshooting. |
| Esteira de CI/CD | [Esteira de CI/CD](./Esteira-de-CI-CD) | Mostra o que existe hoje e o que ainda depende de evolucao para deploy, smoke tests e observabilidade. |

## O Que Ainda Nao Esta Fechado

| Item | Situacao |
| --- | --- |
| C4 de implantacao | Ainda nao ha pagina propria. Hoje a infraestrutura aparece no guia operacional e na esteira de CI/CD. |
| Deploy automatico em VM | As imagens sao publicadas no GHCR, mas o deploy ainda nao esta automatizado no repositorio. |
| Observabilidade e backup | Ainda precisam de definicao operacional quando o ambiente de producao estiver fechado. |
| Evidencia de zero vulnerabilidades criticas | Depende do estado real da aba Security/Dependabot do GitHub. |
| Implementacao do modelo por organizacao e setor | Proposta documentada, ainda dependente de implementacao no backend, migracoes, endpoints e testes de autorizacao. |

## Historico

| Versao | Data | Descricao |
| --- | --- | --- |
| 1.0 | 2026-09-15 | Criacao do mapa de artefatos da wiki com status por Engenharia, Seguranca e Infraestrutura. |
| 1.1 | 2026-09-15 | Ajuste do mapa para registrar a fonte dos requisitos no GitHub Project e incluir a esteira de CI/CD. |
| 1.2 | 2026-09-16 | Inclusao da proposta de modelagem para organizacoes, setores, perfis, permissoes e historico de validacao. |
