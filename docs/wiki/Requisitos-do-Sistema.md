# Requisitos do Sistema

Esta página consolida os requisitos do Inventarium a partir das issues, da documentação e da direção atual do produto. O GitHub Project e as issues continuam sendo a fonte operacional de prioridade e andamento.

## Escopo e Legenda

- **Implementado:** disponível no código atual.
- **Planejado:** requisito aprovado para evolução, mas ainda não entregue.
- **Fora de escopo:** não deve orientar novas implementações.

> A geração de QR Codes e etiquetas em PDF existe no código legado, mas está **fora de escopo** para o produto alvo. Ela não deve ser tratada como requisito funcional nem receber evolução sem nova decisão da equipe.

## Requisitos Funcionais

### RF-01 — Autenticação institucional

**Como** usuário do IFPE, **quero** entrar com minha conta Google, **para** acessar apenas os recursos do Inventarium autorizados para mim.

**Situação:** Implementado.

**Critérios de aceite:**

- O sistema deve aceitar um ID token emitido pelo Google e validá-lo no backend.
- O sistema deve criar a conta local no primeiro acesso e reutilizá-la nos acessos seguintes.
- O sistema deve rejeitar token inválido, expirado, com audiência incorreta ou e-mail não verificado.
- Quando configurado, o sistema deve recusar novos usuários fora de `GOOGLE_ALLOWED_DOMAINS`.

### RF-02 — Gestão de inventários

**Como** usuário autenticado, **quero** criar, consultar, editar e excluir meus inventários, **para** organizar o levantamento patrimonial sob minha responsabilidade.

**Situação:** Implementado.

**Critérios de aceite:**

- O nome do inventário deve ser obrigatório.
- Um usuário não pode criar dois inventários com o mesmo nome.
- O usuário deve visualizar apenas os inventários que lhe pertencem até que a autorização por setor seja implementada.
- Edição e exclusão devem exigir autenticação e ownership do inventário.

### RF-03 — Importação e gestão de itens

**Como** responsável por um inventário, **quero** importar e administrar itens por planilha, **para** reduzir o cadastro manual dos bens patrimoniais.

**Situação:** Implementado.

**Critérios de aceite:**

- O sistema deve aceitar a planilha no formato documentado e informar linhas inválidas.
- O código patrimonial deve ser obrigatório e não pode se repetir no mesmo inventário.
- Valores monetários inválidos ou negativos devem ser rejeitados.
- O usuário deve poder consultar, editar, remover e registrar observações sobre os itens autorizados.

### RF-04 — Leitura de código de barras para conferência patrimonial

**Como** usuário em campo, **quero** ler o código de barras de um patrimônio pela câmera ou por leitor compatível, **para** localizar o item rapidamente e registrar sua conferência sem digitação manual.

**Situação:** Planejado.

**Critérios de aceite:**

- A interface deve permitir iniciar e encerrar a leitura com feedback claro sobre a permissão de câmera.
- O sistema deve aceitar leitura por câmera e por entrada de leitor que se comporte como teclado.
- Após uma leitura válida, o sistema deve localizar o item pelo código patrimonial e exibir seu inventário, situação e dados essenciais.
- Quando o código não for encontrado, o sistema deve informar o resultado sem alterar dados.
- A conferência deve registrar data, usuário e resultado; no modelo alvo, também setor de origem e setor responsável.
- Falhas de câmera, código ilegível ou ausência de permissão devem oferecer alternativa de busca manual.

### RF-05 — Validação e acompanhamento de itens

**Como** responsável por um inventário, **quero** marcar itens como conferidos e acompanhar pendências, **para** saber o andamento do levantamento patrimonial.

**Situação:** Implementado parcialmente.

**Critérios de aceite:**

- A validação deve alterar o estado atual do item e registrar a data da validação.
- A tela deve distinguir itens validados de itens pendentes.
- Nenhum usuário não autorizado pode validar item de outro usuário; no modelo alvo, a regra deve considerar setor e perfil.
- Validações realizadas em outro setor devem gerar pendência, e não confirmar automaticamente o item.

### RF-06 — Exportação de dados patrimoniais

**Como** responsável por um inventário, **quero** exportar seus dados em planilha, **para** prestar contas e realizar análises externas.

**Situação:** Implementado.

**Critérios de aceite:**

- A exportação deve conter os dados dos itens do inventário autorizado.
- O arquivo deve preservar valores e textos de forma compatível com planilhas comuns.
- A exportação deve aplicar proteção contra injeção de fórmulas.

### RF-07 — Organização, setores, perfis e permissões

**Como** administrador, **quero** administrar organizações, setores, perfis e permissões, **para** estruturar o acesso institucional ao sistema.

**Situação:** Implementado parcialmente.

**Critérios de aceite:**

- Deve ser possível cadastrar e manter organizações, setores e a hierarquia opcional de setores.
- Deve ser possível associar um usuário a setor e perfil.
- Perfis devem possuir permissões granulares sem duplicidade.
- Antes de tornar setor e perfil obrigatórios, a migração deve preservar usuários já existentes.

### RF-08 — Autorização por setor e tratamento de pendências

**Como** gestor de setor, **quero** que inventários e itens sejam protegidos por setor e permissões, **para** impedir acesso indevido e resolver conferências entre setores.

**Situação:** Planejado.

**Critérios de aceite:**

- Todo inventário deve pertencer a um setor, além de manter seu usuário criador rastreável.
- Listagens e operações devem filtrar recursos pelo setor, exceto permissões administrativas explícitas.
- O sistema deve registrar validações entre setores com item, usuário, setores envolvidos, data, tipo e estado.
- O setor responsável deve poder confirmar ou rejeitar a pendência.

## Requisitos Não Funcionais

| ID | Requisito | Critérios de aceite | Situação |
| --- | --- | --- | --- |
| RNF-01 | Arquitetura web | Frontend em Next.js, API REST em Spring Boot e PostgreSQL devem compor a solução. | Implementado |
| RNF-02 | Evolução segura do banco | Todo schema deve ser criado ou alterado por changesets Liquibase revisáveis. | Implementado |
| RNF-03 | Segurança de autenticação | Rotas protegidas devem exigir JWT válido; o backend deve validar o ID token Google. | Implementado |
| RNF-04 | Segurança de autorização | O backend deve validar ownership; a evolução deve validar também setor e perfil. | Parcialmente implementado |
| RNF-05 | Proteção de segredos | Credenciais não podem ser versionadas; ambientes devem usar variáveis protegidas. | Implementado |
| RNF-06 | Qualidade contínua | PRs devem executar validação de commits, scan de segredos e checks de dependências/imagens aplicáveis. | Implementado |
| RNF-07 | Rastreabilidade | Cada entrega deve ser associável a issue, branch, PR, tag e release quando aplicável. | Implementado |
| RNF-08 | Entrega reprodutível | Imagens de produção devem usar tag de release imutável e permitir rollback para versão anterior. | Implementado operacionalmente |
| RNF-09 | Observabilidade e operação | Deploy, smoke test, backup, logs e alertas devem possuir procedimento documentado; automação completa é evolução futura. | Planejado |
| RNF-10 | Usabilidade em campo | A leitura de código deve ter retorno claro, alternativa manual e funcionamento em navegadores móveis compatíveis. | Planejado |

## Fora de Escopo

- Geração de QR Codes e etiquetas em PDF.
- Substituição completa de sistemas institucionais como SUAP ou SIPAC.
- Fluxos financeiros, contábeis, de baixa ou transferência formal de patrimônio.
