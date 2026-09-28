# Análise de Concorrência

Este documento registra uma análise comparativa inicial do Inventarium em relação a solucoes usadas ou aplicáveis a gestão patrimonial, inventário de ativos e apoio a operações de campo.

O objetivo não e apontar uma única solucao vencedora, mas entender onde o Inventarium se posiciona melhor: uma ferramenta academica, simples de operar, integrada ao fluxo do IFPE, com foco em tombamento, inventário patrimonial, leitura por QR Code/código de barras e documentação versionada.

## Escopo da Análise

Foram considerados concorrentes diretos e indiretos:

- sistemas institucionais de patrimônio usados no setor publico;
- suites administrativas amplas que possuem módulo patrimonial;
- ferramentas abertas ou SaaS de gestão de ativos;
- solucoes voltadas a inventário de TI que podem ser adaptadas para patrimônio.

## Critérios Comparados

| Criterio | O que foi observado |
| --- | --- |
| Aderencia ao contexto do IFPE | Capacidade de atender processos institucionais, inventário fisico, responsáveis, localização e rastreabilidade. |
| Operação em campo | Suporte a leitura por QR Code/código de barras, uso mobile, atualização rápida e redução de retrabalho. |
| Simplicidade de uso | Facilidade para equipes que precisam operar o inventário sem treinamento longo. |
| Integração e automação | APIs, relatórios, importação/exportação e encaixe com outros sistemas. |
| Governanca e auditoria | Histórico, responsabilidade, controle de acesso, evidências e apoio a prestacao de contas. |
| Custo e dependencia externa | Necessidade de licencas, fornecedor, infraestrutura própria ou customizacao. |

## Concorrentes e Alternativas

| Solucao | Tipo | Pontos fortes | Limitacoes percebidas | Oportunidade para o Inventarium |
| --- | --- | --- | --- | --- |
| SUAP - Módulo de Patrimônio | Sistema institucional usado em Institutos Federais | Aderente ao contexto administrativo dos IFs, com gestão patrimonial, localização de bens, transferências, inventários e relatórios. | Depende da disponibilidade, configuração e evolucao institucional do módulo; pode ser mais amplo e menos focado em uma experiencia leve de campo. | Atuar como ferramenta complementar, com foco em coleta, validação em campo, QR Code e fluxo simplificado para equipes locais. |
| SIPAC - Patrimônio Móvel | Suite administrativa pública | Cobre processos formais de patrimônio, movimentação, consulta, termos, responsabilidades e integração com outros fluxos administrativos. | Suite grande, com maior complexidade operacional e menor flexibilidade para experimentação academica rápida. | Oferecer uma camada mais simples para inventário e apoio ao tombamento, sem tentar substituir todos os processos administrativos. |
| Snipe-IT | Gestão aberta de ativos, principalmente TI | Open source, API REST, campos customizados, auditoria de ativos, suporte a codigos de barras e QR Codes. | Mais orientado a ativos de TI e operações de check-in/check-out; exige adaptacao para linguagem e regras de patrimônio publico. | Aprender com a maturidade de auditoria, API e customizacao, mantendo o foco em patrimônio institucional do IFPE. |
| GLPI | ITSM e gestão de ativos | Forte em inventário de infraestrutura, ativos de TI, service desk e automações de ambiente tecnico. | Mais alinhado a TI e suporte do que ao processo de tombamento patrimonial; pode ser complexo para usuários não tecnicos. | Diferenciar por dominio: inventário patrimonial, leitura em campo e fluxo simples para patrimônio. |
| Asset Panda | SaaS de asset tracking | Plataforma madura, mobile-first, leitura de código de barras/QR Code, auditoria, histórico, anexos e relatórios. | Dependencia de fornecedor, custo recorrente e possível desalinhamento com restrições academicas/públicas locais. | Usar como referencia de experiencia mobile e auditoria, mas preservar controle de código, custo e adaptabilidade institucional. |

## Posicionamento do Inventarium

O Inventarium deve ser posicionado como uma solucao de apoio ao inventário patrimonial academico, com foco em:

- cadastro e consulta de inventários patrimoniais;
- importação e exportação de planilhas;
- geração de etiquetas com QR Code;
- autenticação institucional via Google;
- operação web e mobile;
- documentação técnica versionada;
- evolucao por pull requests, issues e releases rastreaveis.

O diferencial principal não está em competir com suites administrativas completas, mas em entregar uma experiencia menor, clara e ajustavel para o problema de tombamento e validação fisica de bens no contexto do IFPE.

## Lacunas Atuais Frente ao Mercado

| Lacuna | Impacto | Possivel evolucao |
| --- | --- | --- |
| Auditoria funcional limitada | Dificulta comprovar quem alterou, validou, exportou ou enviou dados. | Registrar eventos de criação, edição, validação, exportação, geração de PDF e envio de e-mail. |
| Permissões ainda simples | Pode limitar uso por setores, campus, perfis e responsabilidades diferentes. | Criar modelo de papeis e ownership por usuário, setor ou unidade. |
| Fluxos de transferencia e acautelamento ausentes | O sistema apoia inventário, mas não cobre todo o ciclo administrativo de patrimônio. | Avaliar se esses fluxos pertencem ao escopo do Inventarium ou se devem permanecer em sistema institucional. |
| Mobile em evolucao | A operação em campo e uma vantagem esperada, mas precisa estar madura e alinhada a API. | Priorizar leitura, consulta, validação e sincronizacao ergonomica. |
| Observabilidade e backup ainda não documentados em profundidade | A operação em produção exigirá confiabilidade maior. | Documentar logs, métricas, backup, restauração e rotina operacional. |
| Integração institucional ainda indefinida | Pode haver retrabalho se o Inventarium precisar trocar dados com sistemas oficiais. | Mapear necessidades de importação/exportação e contratos de integração. |

## Recomendacoes

1. Manter o Inventarium como ferramenta focada, evitando tentar reproduzir todo um ERP publico.
2. Priorizar fluxo de campo com QR Code, consulta rápida, validação e registro de evidências.
3. Fortalecer auditoria, permissões e proteção contra acesso indevido a inventários de outros usuários.
4. Tratar planilhas como ponte de integração inicial, com validações claras e exportacoes padronizadas.
5. Documentar explicitamente quando o Inventarium complementa, e não substitui, sistemas institucionais como SUAP ou SIPAC.

## Fontes Consultadas

- [SUAP - Módulo de Patrimônio, Portal IFFluminense](https://portal1.iff.edu.br/comunidade/tic/catalogo-de-serviços-de-tic/sistemas-administrativos/suap-módulo-de-patrimônio)
- [SIPAC - Módulo de Patrimônio Móvel, manual UFG](https://files.cercomp.ufg.br/weby/up/452/o/SIPAC_Patrimonio_PortalAdministrativo.pdf)
- [Snipe-IT - Product Features](https://snipeitapp.com/)
- [GLPI - Inventory and Asset Management](https://www.glpi-project.org/)
- [Asset Panda - Asset Management Software](https://www.assetpanda.com/)

## Histórico

| Versão | Data | Descrição |
| --- | --- | --- |
| 1.0 | 2026-09-15 | Criação da análise de concorrência com comparativo inicial e recomendacoes de posicionamento. |
