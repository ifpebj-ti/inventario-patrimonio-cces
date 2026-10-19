# Telas administrativas

Implementação de setores, usuários, cargos e permissões. As rotas são `/sectors`, `/users` e `/roles`, dentro do layout autenticado, e aparecem no menu lateral conforme o acesso confirmado pela API.

## Controle de acesso por cargo

O **administrador** (`administrator` na sessão) tem acesso total. Quem não é administrador tem um **cargo em cada setor** em que está alocado, e as permissões do cargo valem **só naquele setor**: o mesmo usuário pode ser gestor em um setor e só consulta em outro. `AdministrationAccessProvider` deriva as capacidades da sessão recuperada em `/auth/me` (`administrator` e `allocations`, cada uma com `sectorId`, `roleName` e `permissions`; `src/lib/access.ts`). O nome do cargo não é usado como autorização, só as permissões. A API verifica novamente a permissão em cada operação; a interface só esconde o que o usuário não pode usar.

| Quem | O que vê | O que faz |
| --- | --- | --- |
| Administrador | Tudo, incluindo o Dashboard | Gerencia tudo: setores, usuários (cargo por setor, administrador), cargos e inventários |
| Cargo com `MANAGE_SECTOR` (ex.: Gestor de Setor), no setor | Inventários e o setor em que está alocado; o item Setores do menu | Cria, edita, finaliza e exclui inventários do setor, importa e limpa itens e edita os dados dos itens; não acessa Usuários nem Cargos |
| Cargo com `MANAGE_ITEM` (ex.: Operador de Campo), no setor | Inventários do setor | Escaneia, valida itens e adiciona observações; não finaliza nem edita inventários ou dados dos itens |
| Cargo sem permissões (ex.: Consulta), no setor | Inventários do setor | Somente leitura (pode exportar a planilha) |

As telas de inventário e de item decidem **por setor**: `capabilitiesOf` expõe `canManageInventoryIn(sectorId)` e `canOperateItemsIn(sectorId)`, e as telas usam o setor do inventário aberto (`useInventorySector`). Na lista de inventários, Editar e Excluir aparecem só nas linhas dos setores que o usuário gerencia. O item **Setores** do menu aparece para o administrador e para quem é gestor (`MANAGE_SECTOR`) de ao menos um setor; quem não está alocado em nenhum setor não vê Setores nem Inventários de nenhum setor.

Quem não é administrador não vê o Dashboard: a tela inicial é Inventários, a primeira a que tem acesso (`src/lib/home-route.ts`). Quem entra pela primeira vez com Google nasce **sem alocação e sem ser administrador**: sem acesso a nenhum setor, até que um administrador (ou o gestor do setor) o aloque com um cargo. Não existe cadastro de usuário na tela nem na API.

O catálogo de permissões é fixo e tem duas: `MANAGE_SECTOR` e `MANAGE_ITEM` (a permissão `ADMIN` saiu: administrador é uma coluna do usuário). A tela de cargos consulta `/permissions` para mostrar a descrição de cada permissão ao montar um cargo.

Durante a atualização do acesso (por exemplo, depois de salvar as próprias alocações), as páginas administrativas ficam sem interação; se o acesso for revogado, o conteúdo administrativo e seus menus são removidos. Falhas 403 em mutações também provocam nova verificação.

## Fluxos

- Setores: busca por nome ou código e filtro de situação (Todos, Ativos, Inativos) em filtros recolhíveis. Criação, edição, ativação, desativação e **exclusão** são do administrador; o gestor consulta os seus setores. O botão **Excluir** (com confirmação) só aparece para o administrador e só em setor com `deletable: true`, isto é, sem usuário, inventário nem auditoria associados; setor com vínculo só é ativado ou desativado, e se a API recusar (409) a tela avisa e recarrega a lista. Nome é enviado com trim; código é opcional. Desativação preserva alocações. **Usuários associados** abre uma **barra lateral à direita** com os usuários do setor **agrupados por cargo** (só para o administrador e para o gestor daquele setor; em um setor em que o usuário só opera ou consulta, o setor aparece sem o botão). Nela, quem gerencia o setor **troca o cargo** de um usuário (seletor na linha) e o **remove** do setor (com confirmação); o gestor não vê esses controles nas linhas de outro gestor ou de administrador e só tem cargos sem gestão para escolher. **Adicionar usuários** abre um modal com os usuários ativos do sistema que ainda não estão no setor (busca por nome ou e-mail, caixa por usuário e "Selecionar todos os filtrados"), um seletor de **cargo** (o de menos permissões vem escolhido) e o botão **Alocar N usuários**, que aloca todos os marcados de uma vez com aquele cargo (tudo ou nada, `PUT /sectors/{id}/members`).
- Usuários (**somente administrador**: o item do menu e a rota ficam disponíveis só para o administrador; os demais veem "Acesso restrito" mesmo pelo endereço): busca por nome ou e-mail, filtros de cargo (todos os cargos, Administradores, Sem acesso, ou um cargo), setor e situação (Todos, Ativos, Inativos). Cada cartão mostra só o nome, o e-mail e a situação; papel, setores e cargos ficam no modal "Gerenciar acesso". Não há **Novo usuário** nem **Excluir usuário**: o usuário nasce no primeiro login com Google e nunca é apagado. Em cada linha, ao lado de **Gerenciar acesso**, o administrador tem o botão rápido **Desativar/Ativar**: desativar pede confirmação, reativar executa direto, e o resultado aparece num aviso. Em **Gerenciar acesso** (`userAccessModal.tsx`): cabeçalho com avatar, estado e e-mail; primeiro a lista **só leitura** dos setores e cargos do usuário, com o atalho para Setores → Usuários associados (**alocar, trocar o cargo e remover não se faz mais aqui**); depois o **Administrador** como caixa de seleção (desabilitada na própria conta); e, por último, **Desativar/Reativar**. O papel de administrador é uma **alteração pendente**: o rodapé conta a alteração e **Salvar alterações** envia o `PATCH /users/{id}/administrator` e **fecha o modal** (se falhar, ele fica aberto com o erro e a alteração pendente). **Cancelar** descarta.
- Desativar bloqueia o acesso imediatamente, inclusive de quem já está logado, e preserva o histórico. Só o administrador ativa ou desativa, e ninguém mexe na própria conta.
- Cargos e permissões (`/roles`, **somente administrador**): tabela com cargo, descrição, permissões associadas e uso ("N usuários em M alocações" ou "Sem uso"), com busca por nome, descrição ou permissão. **Novo cargo** e **Editar** abrem um modal com nome, descrição e as permissões em caixas de seleção. Mudar as permissões de um cargo **em uso** pede confirmação mostrando o impacto, porque vale na hora para todas as alocações dele. **Excluir** (com confirmação) só aparece em cargo com `deletable: true`, isto é, sem nenhuma alocação **e** sem nenhuma permissão; se a API recusar (409) a tela avisa em um toast e recarrega a lista. Para excluir um cargo que tem permissões, edite-o antes e desmarque todas.
- Proteções: `GET /roles` e `GET /permissions` exigem administrador ou gestor de algum setor (o gestor usa a lista para escolher o cargo ao alocar); operador e consulta recebem 403. Criar, editar e excluir cargo exigem administrador. O gestor só atribui cargos sem `MANAGE_SECTOR`.

## Erros e segurança da interface

Os serviços reutilizam Axios com Bearer do projeto e rejeitam status HTTP de erro. Zod verifica os DTOs recebidos; corpo inesperado bloqueia o fluxo. Mensagens são escolhidas por status, sem depender de response.data.message (exceto conflitos conhecidos informados pela própria tela). São tratados 400, 401, 403, 404, 405, 409, falhas do servidor e ausência de resposta. Formulários mantêm os valores quando a gravação falha; nenhuma alteração é aplicada localmente antes da confirmação do servidor.

Modais têm nomes acessíveis, Escape, foco contido e retorno de foco. Ao abrir uma confirmação sobre um editor, o editor fica inerte. Uma trava imediata evita mutações repetidas; as ações ficam desabilitadas durante a requisição.

## Regras que a API aplica

- A sessão expõe `administrator`, `sectorIds` e `allocations` (cargo e permissões por setor); não há perfil global.
- Leitura por setor: o administrador vê tudo; os demais só enxergam inventários, itens, exportações e setores em que estão alocados. Sem alocação, recebem listas vazias. A lista de membros de um setor exige ser administrador ou gestor daquele setor.
- Gestão de inventários (criar, editar, finalizar, excluir, importar e limpar itens): administrador ou gestor do setor do inventário. A importação é única por inventário; para importar outra planilha, os itens precisam ser removidos antes. Inventário finalizado não aceita escaneamento, importação, remoção nem limpeza de itens.
- Itens: quem tem `MANAGE_ITEM` no setor altera apenas a validação e as observações; dados do item são do gestor do setor ou do administrador.
- `PATCH /users/{id}/activate`, `/deactivate` e `/administrator` exigem administrador. Não há `POST /users` nem `DELETE /users/{id}`. Alocar um usuário usa `PUT /users/{id}/sectors/{sectorId}` com o `roleId` (obrigatório), que também troca o cargo se ele já estiver no setor; alocar vários de uma vez usa `PUT /sectors/{id}/members`, e a lista de quem pode ser alocado vem de `GET /sectors/{id}/assignable-users` (só nome e e-mail).
- Os endpoints não oferecem paginação. Busca e filtros são locais, exceto o filtro de cargo da lista de usuários, que envia `?roleId=`.
- O filtro "Sem acesso" da lista de usuários mostra quem não é administrador e não está alocado em nenhum setor (por exemplo, quem acabou de entrar pelo Google); ele filtra localmente.

## Testar

Use `npm run dev` em frontend e uma sessão válida, com a API configurada em NEXT_PUBLIC_API_URL. Configure o CORS da API para a origem do navegador. As migrations `3.0.xml` (unicidade de e-mail sem distinguir caixa), `4.0.xml` (coluna `active` do usuário) `6.0.xml` (remove o pré-cadastro e a coluna `pending_google_login`) e `7.0.xml` (cargo por alocação e administrador como coluna: move o perfil do usuário para o cargo de cada alocação e remove o perfil de administrador) são aplicadas na inicialização do backend.

Para testes isolados: `npm run test:e2e -- administration.spec.ts` ou `access-control.spec.ts` (interface por cargo e por setor). Playwright inicia Next em porta e cache isolados e simula a API; no Windows usa Edge instalado e em Linux usa Chromium. Os testes simulados verificam interface e contrato; a autorização real é coberta pelos testes do backend (`AccessControlE2ETest`).

## Arquivos principais

- `src/commons/models/administration.ts`: DTOs, tipos e validações de formulário.
- `src/services/administration.ts`: consumo da API e mensagens de erro.
- `src/contexts/AdministrationAccess.tsx`: capacidades locais (por setor) verificadas pela API.
- `src/lib/access.ts`: capacidades por cargo e por setor; `src/lib/home-route.ts`: tela inicial; `src/hooks/useInventorySector.ts`: setor do inventário aberto.
- `src/components/organisms/administration/`: telas e componentes compartilhados; `sectorMembersSheet.tsx` (barra lateral do setor) e `allocateUsersModal.tsx` (alocar vários usuários).
- `src/app/(authenticated)/sectors|users|roles/page.tsx`: entradas de página protegidas.
- `tests/e2e/administration.spec.ts` e `tests/e2e/access-control.spec.ts`: fluxos, cargos, cargo diferente em cada setor, proteção do administrador e erros HTTP.
