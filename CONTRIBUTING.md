# Contribuindo com o Inventarium

Obrigado por contribuir. Este arquivo resume o fluxo de trabalho; os detalhes ficam em [docs/contributing.md](docs/contributing.md) e em [docs/branching-strategy.md](docs/branching-strategy.md).

## Fluxo resumido

1. **Abra uma issue** pelo template `Tarefa`, com contexto, objetivo, escopo e critérios de aceite.
2. **Crie uma branch curta a partir da `main`** atualizada, com prefixo de contexto: `feat/`, `fix/`, `docs/`, `ci/`, `infra/` ou `hotfix/` (só para correção urgente).
3. **Faça commits no padrão Conventional Commits**: `tipo(escopo opcional): mensagem curta no imperativo`. Os tipos aceitos estão em [docs/contributing.md](docs/contributing.md#commits). O Husky valida a mensagem localmente e o Commitlint valida de novo no PR.
4. **Rode as validações locais** antes de abrir o PR (veja abaixo).
5. **Abra o pull request para a `main`**, preencha o template, referencie exatamente uma issue com `Closes #número` e marque exatamente um tipo de release (`patch`, `minor`, `major` ou `sem release`).
6. **Atualize o `CHANGELOG.md` na própria PR** quando a mudança for entregável.
7. **Aguarde os checks e a revisão.** Responda e resolva todas as conversas antes do merge.

Nunca envie alterações direto para a `main`, nem de documentação ou CI.

## Validações locais

```bash
npm install             # instala as ferramentas da raiz e os hooks do Husky
npm run secretlint
npm run lint:web
npm run test:github
```

Backend, com Java 21:

```bash
cd backend
./gradlew spotlessCheck
./gradlew test
```

## Definição de pronto

Um PR está pronto para revisão quando:

- [ ] está ligado a uma issue e o checklist do template foi preenchido;
- [ ] os testes passam e a mudança tem testes quando altera comportamento;
- [ ] o Semgrep, o Trivy e o Secretlint passam, sem segredos nem credenciais no diff;
- [ ] a documentação afetada foi atualizada (README, wiki e ADR, se houve decisão arquitetural);
- [ ] a pessoa revisora consegue entender o que mudou e por quê.

## Decisões de arquitetura

Decisões relevantes, como escolha de banco, autenticação ou estratégia de publicação, são registradas como ADR em [docs/architecture/adr](docs/architecture/adr), usando o [template](docs/architecture/adr/template.md).

## Segurança

Não abra issue pública para vulnerabilidades. Siga a [Política de Segurança](SECURITY.md). Não commite `.env`, chaves, certificados nem tokens; versione apenas arquivos `.env.example`.
