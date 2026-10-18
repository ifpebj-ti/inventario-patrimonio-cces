# Telas administrativas

Implementação de setores, usuários, perfis e permissões. As rotas são `/sectors`, `/users` e `/profiles`, dentro do layout autenticado, e aparecem no menu lateral conforme o perfil confirmado pela API.

## Controle de acesso por perfil

`AdministrationAccessProvider` deriva o perfil das permissões da sessão recuperada em `/auth/me` (`src/lib/access.ts`). Nome e ID do perfil não são usados como autorização. A API verifica novamente a permissão em cada operação; a interface só esconde o que o perfil não pode usar.

| Perfil | Permissão | O que vê | O que faz |
| --- | --- | --- | --- |
| Administrador da Organização | `ADMIN` | Tudo, incluindo o Dashboard | Gerencia tudo: setores, usuários, perfis (consulta) e inventários |
| Gestor de Setor | `MANAGE_SECTOR` | Inventários e setores a que está vinculado | Cria, edita, finaliza e exclui inventários do seu setor, importa e limpa itens; atribui apenas Operadores de Campo e Consulta a usuários dos seus setores; não ativa nem desativa usuários |
| Operador de Campo | `MANAGE_ITEM` | Inventários do seu setor | Escaneia, valida itens e adiciona observações; não finaliza nem edita inventários ou dados dos itens |
| Consulta | nenhuma | Inventários do seu setor | Somente leitura (pode exportar a planilha) |

Quem não é administrador não vê o Dashboard: a tela inicial é Inventários, a primeira a que tem acesso (`src/lib/home-route.ts`). Quem entra pela primeira vez com Google nasce **sem perfil** (aparece como "Sem perfil", sem nenhuma permissão e sem setor) e um administrador atribui o perfil e os setores; não existe cadastro de usuário na tela nem na API. "Sem perfil" não é uma opção de atribuição: só se atribui um perfil real.

O catálogo padrão tem três permissões: `ADMIN`, `MANAGE_SECTOR` e `MANAGE_ITEM`. A tela de perfis consulta `/permissions` para mostrar a descrição de cada permissão. A composição dos perfis é fixa na aplicação e definida pelas migrations; não pode ser alterada pela interface nem pela API administrativa.

Ao alterar o próprio perfil de usuário, a sessão é consultada novamente. Durante a consulta, as páginas administrativas ficam sem interação; se o acesso for revogado, o conteúdo administrativo e seus menus são removidos. Falhas 403 em mutações também provocam nova verificação.

## Fluxos

- Setores: busca por nome ou código e filtro de situação (Todos, Ativos, Inativos) em filtros recolhíveis. Criação, edição, ativação e desativação são do ADMIN; o gestor consulta os seus setores e os usuários associados. Não existe exclusão de setor. Nome é enviado com trim; código é opcional. Desativação preserva alocações.
- Usuários (ADMIN e gestor): busca por nome ou e-mail, filtros de perfil, setor e situação (Todos, Ativos, Inativos). Não há **Novo usuário** nem **Excluir usuário**: o usuário nasce no primeiro login com Google (sem perfil) e nunca é apagado. Em cada linha, ao lado de **Gerenciar acesso**, o administrador tem o botão rápido **Desativar/Ativar**: desativar pede confirmação, reativar executa direto, e o resultado aparece num aviso (os erros também, já que o modal não está aberto). Em **Gerenciar acesso** (`userAccessModal.tsx`): cabeçalho com avatar, estado e e-mail; o **perfil** em cartões de escolha (nome, descrição e a marca "Perfil atual"); os **setores** como itens, com busca de setor ativo por nome ou sigla; e, por último, **Desativar/Reativar** (só administrador). Perfil e setores são **alterações pendentes**: o rodapé conta o que mudou e **Salvar alterações** envia tudo de uma vez, em sequência (perfil, depois setores a incluir e a remover), já que a API não tem endpoint em lote; se uma chamada falhar, as anteriores já valeram, a tela mostra o erro e o que sobrou continua pendente. **Cancelar** descarta. Só a alteração do **próprio perfil** pede confirmação antes de salvar. O gestor só altera perfil e setores de Operadores e Consulta que atuam nos seus setores.
- Desativar bloqueia o acesso imediatamente, inclusive de quem já está logado, e preserva o histórico. Só o administrador ativa ou desativa (o gestor vê "Somente o administrador ativa ou desativa usuários."), e ninguém mexe na própria conta.
- Perfis e permissões (ADMIN): tabela somente para consulta com perfil, descrição e permissões, busca por nome, descrição ou permissão. Não há criação, edição, exclusão ou configuração de permissões. Os nomes exibidos são amigáveis (`src/lib/access-labels.ts`); os códigos do servidor não mudam.
- Proteções: GET de perfis e permissões exige ADMIN ou MANAGE_SECTOR (o gestor usa o catálogo para cadastrar usuários); operador e consulta recebem 403. POST/PUT/PATCH/DELETE de perfis e associação ou remoção de permissões respondem 405 para ADMIN; sem ADMIN, respondem 403. A atribuição de perfil a um usuário continua em `PATCH /users/{id}/profile`, e o gestor só atribui perfis sem permissão de gestão.

## Erros e segurança da interface

Os serviços reutilizam Axios com Bearer do projeto e rejeitam status HTTP de erro. Zod verifica os DTOs recebidos; corpo inesperado bloqueia o fluxo. Mensagens são escolhidas por status, sem depender de response.data.message (exceto conflitos conhecidos informados pela própria tela). São tratados 400, 401, 403, 404, 405, 409, falhas do servidor e ausência de resposta. Formulários mantêm os valores quando a gravação falha; nenhuma alteração é aplicada localmente antes da confirmação do servidor.

Modais têm nomes acessíveis, Escape, foco contido e retorno de foco. Ao abrir uma confirmação sobre um editor, o editor fica inerte. Uma trava imediata evita mutações repetidas; as ações ficam desabilitadas durante a requisição.

## Regras que a API aplica

- A sessão expõe permissões e sectorIds; profileId não é usado como regra de autorização.
- Leitura por setor: admin vê tudo; os demais perfis só enxergam inventários, itens, exportações e setores a que estão vinculados. Sem vínculos, recebem listas vazias. A lista de membros de um setor exige ser admin ou gestor do setor.
- Gestão de inventários (criar, editar, finalizar, excluir, importar e limpar itens): admin ou gestor do setor. A importação é única por inventário; para importar outra planilha, os itens precisam ser removidos antes. Inventário finalizado não aceita escaneamento, importação, remoção nem limpeza de itens.
- Itens: o operador altera apenas a validação e as observações; dados do item são do gestor ou admin.
- `PATCH /users/{id}/activate` e `/deactivate` exigem ADMIN. Não há `POST /users` nem `DELETE /users/{id}`, edição de identidade ou CRUD do catálogo de permissões.
- Os endpoints não oferecem paginação. Busca e filtros são locais.
- O filtro "Sem perfil" da lista de usuários mostra quem entrou pelo Google e ainda não recebeu perfil; ele busca a lista completa e filtra localmente.

## Testar

Use `npm run dev` em frontend e uma sessão válida, com a API configurada em NEXT_PUBLIC_API_URL. Configure o CORS da API para a origem do navegador. As migrations `3.0.xml` (unicidade de e-mail sem distinguir caixa), `4.0.xml` (coluna `active` do usuário) e `6.0.xml` (remove o pré-cadastro e a coluna `pending_google_login`) são aplicadas na inicialização do backend.

Para testes isolados: `npm run test:e2e -- administration.spec.ts` ou `access-control.spec.ts` (interface por perfil). Playwright inicia Next em porta e cache isolados e simula a API; no Windows usa Edge instalado e em Linux usa Chromium. Os testes simulados verificam interface e contrato; a autorização real é coberta pelos testes do backend (`AccessControlE2ETest`).

## Arquivos principais

- `src/commons/models/administration.ts`: DTOs, tipos e validações de formulário.
- `src/services/administration.ts`: consumo da API e mensagens de erro.
- `src/contexts/AdministrationAccess.tsx`: perfil e capacidades locais verificadas pela API.
- `src/lib/access.ts`: perfis e capacidades; `src/lib/home-route.ts`: tela inicial por perfil.
- `src/components/organisms/administration/`: telas e componentes compartilhados.
- `src/app/(authenticated)/sectors|users|profiles/page.tsx`: entradas de página protegidas.
- `tests/e2e/administration.spec.ts` e `tests/e2e/access-control.spec.ts`: fluxos, perfis, proteção do administrador e erros HTTP.
