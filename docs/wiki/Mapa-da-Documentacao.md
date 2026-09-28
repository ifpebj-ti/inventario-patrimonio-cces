# Mapa da Documentação

Este mapa serve para saber rapidamente onde cada artefato está. Ele não substitui o Project, nem tenta transformar a wiki em backlog.

## Leitura Rápida

| Frente | Situação |
| --- | --- |
| Visão do projeto | Coberta em [Documento de Visão](./Documento-de-Visão). |
| Concorrência | Coberta em [Análise de Concorrência](./Análise-de-Concorrência). |
| Requisitos e backlog | [Requisitos do Sistema](./Requisitos-do-Sistema) consolida a visão atual; o GitHub Project continua como fonte operacional. |
| Arquitetura | Coberta pelos diagramas C4, modelagem de dados e ADRs. |
| Segurança | Coberta pela modelagem de ameaças e pelo guia de desenvolvimento seguro. |
| Infraestrutura | Coberta pelo guia operacional, conteinerizacao e pagina de CI/CD. |

## Engenharia de Software

| Artefato | Onde consultar | Nota |
| --- | --- | --- |
| Documento de Visão | [Documento de Visão](./Documento-de-Visão) | Resume problema, objetivo, publico, escopo e limites do produto. |
| Análise de Concorrência | [Análise de Concorrência](./Análise-de-Concorrência) | Compara o Inventarium com alternativas institucionais e ferramentas de mercado. |
| Requisitos do sistema | [Requisitos do Sistema](./Requisitos-do-Sistema) | Consolida requisitos funcionais e não funcionais, distinguindo o que está implementado do que é planejado. |
| Arquitetura | [C4 - Contexto](./C4-Contexto), [C4 - Containers](./C4-Containers), [C4 - Componentes](./C4-Componentes) e [Diagrama de Classes](./Diagrama-de-Classes) | Os diagramas mostram atores, containers, componentes e o modelo de classes persistido. |
| Modelagem de dados | [Documento de Modelagem de Dados](./Documento-de-Modelagem-de-Dados) | Registra entidades, relacionamentos, chaves e restrições do schema atual. |
| Modelo de dados | [Documento de Modelagem de Dados](./Documento-de-Modelagem-de-Dados) | Registra o schema implementado e as evoluções pendentes para organizações, setores, perfis, permissões e histórico de validação. |
| Decisões técnicas | [Architecture Decision Records](./Architecture-Decision-Records) | Guarda decisões arquiteturais que precisam continuar rastreaveis. |

## Segurança

| Artefato | Onde consultar | Nota |
| --- | --- | --- |
| Modelagem de ameaças | [Modelagem de Ameaças](./Modelagem-de-Ameaças) | Inclui o arquivo do OWASP Threat Dragon e a lista de riscos STRIDE. |
| Boas praticas de desenvolvimento seguro | [Guia de Boas Praticas de Desenvolvimento Seguro](./Guia-de-Boas-Praticas-de-Desenvolvimento-Seguro) | Cobre autenticação, autorização, secrets, API, frontend, banco, supply chain e release. |
| Achados de análise estática | Aba Security/Code scanning do GitHub | Centraliza alertas de CodeQL, Semgrep e Trivy enviados em SARIF. A evidência detalhada temporária fica nos artifacts da execução. |
| Vulnerabilidades do Dependabot | Aba Security/Dependabot do GitHub | A evidência vem do GitHub. Se houver vulnerabilidade crítica aberta, esse item não deve ser marcado como concluído. |

## Infraestrutura

| Artefato | Onde consultar | Nota |
| --- | --- | --- |
| Aplicação conteinerizada | `docker-compose.yml`, `backend/Dockerfile`, `frontend/Dockerfile` | Compose sobe PostgreSQL, backend e frontend. |
| Execução, configuração e operação | [Guia de Execução, Configuração e Operação](./Guia-de-Execução-Configuração-e-Operação) | Explica ambiente local, variáveis, GHCR, logs, validação e troubleshooting. |
| Esteira de CI/CD | [Esteira de CI/CD](./Esteira-de-CI-CD) | Mostra o que existe hoje e o que ainda depende de evolucao para deploy, smoke tests e observabilidade. |

## O Que Ainda Nao Está Fechado

| Item | Situação |
| --- | --- |
| C4 de implantacao | Ainda não ha pagina própria. Hoje a infraestrutura aparece no guia operacional e na esteira de CI/CD. |
| Deploy automatico em VM | As imagens são publicadas no GHCR, mas o deploy ainda não está automatizado no repositório. |
| Observabilidade e backup | Ainda precisam de definição operacional quando o ambiente de produção estiver fechado. |
| Evidencia de zero vulnerabilidades críticas | Depende do estado real da aba Security/Dependabot do GitHub. |
| Aderência completa ao modelo por organização e setor | A base de organizações, setores, perfis e permissões está implementada; faltam vínculo setorial do inventário, autorização por setor e histórico de validações. |

## Histórico

| Versão | Data | Descrição |
| --- | --- | --- |
| 1.0 | 2026-09-15 | Criação do mapa de artefatos da wiki com status por Engenharia, Segurança e Infraestrutura. |
| 1.1 | 2026-09-15 | Ajuste do mapa para registrar a fonte dos requisitos no GitHub Project e incluir a esteira de CI/CD. |
| 1.2 | 2026-09-16 | Inclusao da proposta de modelagem para organizações, setores, perfis, permissões e histórico de validação. |
