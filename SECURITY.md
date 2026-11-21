# Política de Segurança

Este documento descreve como relatar vulnerabilidades no Inventarium e o que esperar depois do relato.

## Versões suportadas

O Inventarium é um projeto acadêmico com uma única linha de entrega. Só a versão mais recente (a última tag `vMAJOR.MINOR.PATCH` e o código atual da `main`) recebe correções de segurança.

| Versão | Suporte a correções de segurança |
| --- | --- |
| Última release e `main` | Sim |
| Releases anteriores | Não |

## Como relatar uma vulnerabilidade

**Não abra uma issue pública descrevendo a falha**, nem publique o exploit em PR, comentário ou discussão.

1. Abra a aba **Security** do repositório e use **Report a vulnerability** (relato privado de vulnerabilidade do GitHub): <https://github.com/ifpebj-ti/inventario-patrimonio-cces/security/advisories/new>
2. Se essa opção não estiver disponível, abra uma issue com o título "Contato privado sobre segurança" e **sem nenhum detalhe técnico da falha**. Alguém do time responde combinando um canal privado.

Para agilizar a análise, inclua no relato:

- o que foi encontrado e qual o impacto esperado;
- onde está o problema (endpoint, tela, arquivo ou dependência) e a versão ou commit afetado;
- passos para reproduzir, com payloads, requisições ou prints;
- se possível, uma sugestão de correção.

Não inclua dados reais de inventário, credenciais ou tokens de terceiros no relato.

## O que esperar

Metas do time, sem garantia contratual por se tratar de um projeto acadêmico:

| Etapa | Meta |
| --- | --- |
| Confirmar o recebimento | até 5 dias úteis |
| Avaliar e classificar a severidade | até 10 dias úteis |
| Corrigir falhas críticas e altas | na sprint seguinte à classificação |

Depois da correção, a falha é registrada no `CHANGELOG.md` e, quando fizer sentido, o crédito é dado a quem relatou (se essa pessoa quiser).

## Escopo

Dentro do escopo:

- API backend (`backend/`) e aplicação web (`frontend/`);
- autenticação com Google e emissão e revogação do JWT;
- importação e exportação de planilhas, envio de e-mail e controle de acesso por setor e perfil;
- dependências diretas e imagens de contêiner do projeto;
- segredos expostos no repositório ou no histórico do Git.

Fora do escopo:

- serviços de terceiros (Google, GitHub, Gmail, provedor de nuvem);
- ataques de negação de serviço, engenharia social e ataques físicos;
- achados de scanners sem prova de impacto no Inventarium;
- falhas que exijam acesso físico ou o dispositivo da vítima já comprometido.

## Controles de segurança já adotados

O repositório executa, em todo pull request para a `main`:

- **SAST:** Semgrep (`p/default`), bloqueante;
- **SCA e contêineres:** Trivy, que bloqueia segredos e vulnerabilidades `HIGH` e `CRITICAL`, e Dependabot para npm, Gradle e GitHub Actions;
- **Segredos:** Secretlint, bloqueante, e hook de pre-commit local;
- **Commits e rastreabilidade:** Commitlint e validação de issue vinculada no PR.

Mais detalhes em:

- [Guia de Boas Práticas de Desenvolvimento Seguro](docs/wiki/Guia-de-Boas-Praticas-de-Desenvolvimento-Seguro.md)
- [Modelagem de Ameaças (STRIDE)](docs/wiki/Modelagem-de-Ameacas.md)
- [Esteira de CI/CD](docs/wiki/Esteira-de-CI-CD.md)
- [ADR 0010 — Gates bloqueantes de Semgrep e cobertura](docs/architecture/adr/0010-gates-bloqueantes-de-semgrep-e-cobertura.md)
