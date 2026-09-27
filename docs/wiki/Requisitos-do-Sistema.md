# Requisitos do Sistema

Esta página consolida requisitos observados nas issues, na documentação e no comportamento implementado. O GitHub Project e as issues permanecem como fonte operacional de prioridade e andamento.

## Requisitos Funcionais

| ID | Requisito | Situação |
| --- | --- | --- |
| RF-01 | Autenticar usuários com conta Google e criar/localizar a conta local. | Implementado |
| RF-02 | Restringir acesso por domínios de e-mail configurados. | Implementado |
| RF-03 | Criar, listar, editar e excluir inventários do usuário autenticado. | Implementado |
| RF-04 | Importar itens por planilha, validando dados e duplicidades. | Implementado |
| RF-05 | Consultar, editar, validar e observar itens patrimoniais. | Implementado |
| RF-06 | Gerar QR Codes, etiquetas em PDF e exportações em planilha. | Implementado |
| RF-07 | Administrar organizações, setores, perfis e permissões. | Implementado parcialmente |
| RF-08 | Associar usuário a setor e perfil. | Implementado |
| RF-09 | Restringir recursos pelo setor e permissões. | Planejado |
| RF-10 | Registrar, resolver e notificar validações entre setores. | Planejado |

## Requisitos Não Funcionais

| ID | Requisito | Situação |
| --- | --- | --- |
| RNF-01 | Usar Spring Boot, Next.js e PostgreSQL. | Implementado |
| RNF-02 | Versionar o schema por Liquibase. | Implementado |
| RNF-03 | Proteger rotas com JWT emitido após validação do ID token Google. | Implementado |
| RNF-04 | Manter segredos fora do repositório. | Implementado |
| RNF-05 | Validar commits, segredos, dependências e imagens em pull requests. | Implementado |
| RNF-06 | Publicar imagens versionadas no GHCR durante releases. | Implementado |
| RNF-07 | Manter rastreabilidade por issue, branch, pull request, tag e release. | Implementado |
| RNF-08 | Usar `main` como única branch de integração. | Implementado |
| RNF-09 | Permitir rollback por tags imutáveis. | Implementado operacionalmente |
| RNF-10 | Automatizar deploy, smoke test e observabilidade. | Planejado |

## Critérios de Qualidade e Segurança

- A autorização deve ser validada no backend; no modelo alvo, por ownership, setor e perfil.
- Planilhas devem ser validadas antes da persistência.
- A pipeline deve falhar em vulnerabilidades altas ou críticas.
- Produção deve usar imagens identificadas por tags de release, não tags móveis.
