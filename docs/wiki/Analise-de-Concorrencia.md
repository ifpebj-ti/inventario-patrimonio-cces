# Analise de Concorrencia

Este documento registra uma analise comparativa inicial do Inventarium em relacao a solucoes usadas ou aplicaveis a gestao patrimonial, inventario de ativos e apoio a operacoes de campo.

O objetivo nao e apontar uma unica solucao vencedora, mas entender onde o Inventarium se posiciona melhor: uma ferramenta academica, simples de operar, integrada ao fluxo do IFPE, com foco em tombamento, inventario patrimonial, leitura por QR Code/codigo de barras e documentacao versionada.

## Escopo da Analise

Foram considerados concorrentes diretos e indiretos:

- sistemas institucionais de patrimonio usados no setor publico;
- suites administrativas amplas que possuem modulo patrimonial;
- ferramentas abertas ou SaaS de gestao de ativos;
- solucoes voltadas a inventario de TI que podem ser adaptadas para patrimonio.

## Criterios Comparados

| Criterio | O que foi observado |
| --- | --- |
| Aderencia ao contexto do IFPE | Capacidade de atender processos institucionais, inventario fisico, responsaveis, localizacao e rastreabilidade. |
| Operacao em campo | Suporte a leitura por QR Code/codigo de barras, uso mobile, atualizacao rapida e reducao de retrabalho. |
| Simplicidade de uso | Facilidade para equipes que precisam operar o inventario sem treinamento longo. |
| Integracao e automacao | APIs, relatorios, importacao/exportacao e encaixe com outros sistemas. |
| Governanca e auditoria | Historico, responsabilidade, controle de acesso, evidencias e apoio a prestacao de contas. |
| Custo e dependencia externa | Necessidade de licencas, fornecedor, infraestrutura propria ou customizacao. |

## Concorrentes e Alternativas

| Solucao | Tipo | Pontos fortes | Limitacoes percebidas | Oportunidade para o Inventarium |
| --- | --- | --- | --- | --- |
| SUAP - Modulo de Patrimonio | Sistema institucional usado em Institutos Federais | Aderente ao contexto administrativo dos IFs, com gestao patrimonial, localizacao de bens, transferencias, inventarios e relatorios. | Depende da disponibilidade, configuracao e evolucao institucional do modulo; pode ser mais amplo e menos focado em uma experiencia leve de campo. | Atuar como ferramenta complementar, com foco em coleta, validacao em campo, QR Code e fluxo simplificado para equipes locais. |
| SIPAC - Patrimonio Movel | Suite administrativa publica | Cobre processos formais de patrimonio, movimentacao, consulta, termos, responsabilidades e integracao com outros fluxos administrativos. | Suite grande, com maior complexidade operacional e menor flexibilidade para experimentacao academica rapida. | Oferecer uma camada mais simples para inventario e apoio ao tombamento, sem tentar substituir todos os processos administrativos. |
| Snipe-IT | Gestao aberta de ativos, principalmente TI | Open source, API REST, campos customizados, auditoria de ativos, suporte a codigos de barras e QR Codes. | Mais orientado a ativos de TI e operacoes de check-in/check-out; exige adaptacao para linguagem e regras de patrimonio publico. | Aprender com a maturidade de auditoria, API e customizacao, mantendo o foco em patrimonio institucional do IFPE. |
| GLPI | ITSM e gestao de ativos | Forte em inventario de infraestrutura, ativos de TI, service desk e automacoes de ambiente tecnico. | Mais alinhado a TI e suporte do que ao processo de tombamento patrimonial; pode ser complexo para usuarios nao tecnicos. | Diferenciar por dominio: inventario patrimonial, leitura em campo e fluxo simples para patrimonio. |
| Asset Panda | SaaS de asset tracking | Plataforma madura, mobile-first, leitura de codigo de barras/QR Code, auditoria, historico, anexos e relatorios. | Dependencia de fornecedor, custo recorrente e possivel desalinhamento com restricoes academicas/publicas locais. | Usar como referencia de experiencia mobile e auditoria, mas preservar controle de codigo, custo e adaptabilidade institucional. |

## Posicionamento do Inventarium

O Inventarium deve ser posicionado como uma solucao de apoio ao inventario patrimonial academico, com foco em:

- cadastro e consulta de inventarios patrimoniais;
- importacao e exportacao de planilhas;
- geracao de etiquetas com QR Code;
- autenticacao institucional via Google;
- operacao web e mobile;
- documentacao tecnica versionada;
- evolucao por pull requests, issues e releases rastreaveis.

O diferencial principal nao esta em competir com suites administrativas completas, mas em entregar uma experiencia menor, clara e ajustavel para o problema de tombamento e validacao fisica de bens no contexto do IFPE.

## Lacunas Atuais Frente ao Mercado

| Lacuna | Impacto | Possivel evolucao |
| --- | --- | --- |
| Auditoria funcional limitada | Dificulta comprovar quem alterou, validou, exportou ou enviou dados. | Registrar eventos de criacao, edicao, validacao, exportacao, geracao de PDF e envio de e-mail. |
| Permissoes ainda simples | Pode limitar uso por setores, campus, perfis e responsabilidades diferentes. | Criar modelo de papeis e ownership por usuario, setor ou unidade. |
| Fluxos de transferencia e acautelamento ausentes | O sistema apoia inventario, mas nao cobre todo o ciclo administrativo de patrimonio. | Avaliar se esses fluxos pertencem ao escopo do Inventarium ou se devem permanecer em sistema institucional. |
| Mobile em evolucao | A operacao em campo e uma vantagem esperada, mas precisa estar madura e alinhada a API. | Priorizar leitura, consulta, validacao e sincronizacao ergonomica. |
| Observabilidade e backup ainda nao documentados em profundidade | A operacao em producao exigira confiabilidade maior. | Documentar logs, metricas, backup, restauracao e rotina operacional. |
| Integracao institucional ainda indefinida | Pode haver retrabalho se o Inventarium precisar trocar dados com sistemas oficiais. | Mapear necessidades de importacao/exportacao e contratos de integracao. |

## Recomendacoes

1. Manter o Inventarium como ferramenta focada, evitando tentar reproduzir todo um ERP publico.
2. Priorizar fluxo de campo com QR Code, consulta rapida, validacao e registro de evidencias.
3. Fortalecer auditoria, permissoes e protecao contra acesso indevido a inventarios de outros usuarios.
4. Tratar planilhas como ponte de integracao inicial, com validacoes claras e exportacoes padronizadas.
5. Documentar explicitamente quando o Inventarium complementa, e nao substitui, sistemas institucionais como SUAP ou SIPAC.

## Fontes Consultadas

- [SUAP - Modulo de Patrimonio, Portal IFFluminense](https://portal1.iff.edu.br/comunidade/tic/catalogo-de-servicos-de-tic/sistemas-administrativos/suap-modulo-de-patrimonio)
- [SIPAC - Modulo de Patrimonio Movel, manual UFG](https://files.cercomp.ufg.br/weby/up/452/o/SIPAC_Patrimonio_PortalAdministrativo.pdf)
- [Snipe-IT - Product Features](https://snipeitapp.com/)
- [GLPI - Inventory and Asset Management](https://www.glpi-project.org/)
- [Asset Panda - Asset Management Software](https://www.assetpanda.com/)

## Historico

| Versao | Data | Descricao |
| --- | --- | --- |
| 1.0 | 2026-09-15 | Criacao da analise de concorrencia com comparativo inicial e recomendacoes de posicionamento. |
