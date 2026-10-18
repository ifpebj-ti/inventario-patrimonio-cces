# Telas administrativas

Implementação de setores, usuários, perfis e permissões. As rotas são `/sectors`, `/users` e `/profiles`, dentro do layout autenticado, e aparecem no menu lateral conforme o perfil confirmado pela API.

## Controle de acesso por perfil

`AdministrationAccessProvider` deriva o perfil das permissões da sessão recuperada em `/auth/me` (`src/lib/access.ts`). Nome e ID do perfil não são usados como autorização. A API verifica novamente a permissão em cada operação; a interface só esconde o que o perfil não pode usar.

| Perfil | Permissão | O que vê | O que faz |
| --- | --- | --- | --- |
| Administrador da Organização | `ADMIN` | Tudo, incluindo o Dashboard | Gerencia tudo: setores, usuários, perfis (consulta) e inventários |
| Gestor de Setor | `MANAGE_SECTOR` | Inventários e setores a que está vinculado | Cria, edita, finaliza e exclui inventários do seu setor, importa e limpa itens; cadastra apenas Operadores de Campo e Consulta nos seus setores |
| Operador de Campo | `MANAGE_ITEM` | Inventários do seu setor | Escaneia, valida itens e adiciona observações; não finaliza nem edita inventários ou dados dos itens |
| Consulta | nenhuma | Inventários do seu setor | Somente leitura (pode exportar a planilha) |

Quem não é administrador não vê o Dashboard: a tela inicial é Inventários, a primeira a que tem acesso (`src/lib/home-route.ts`). Todo usuário tem perfil; não existe "Sem perfil" como opção de cadastro ou de atribuição. Quem entra pela primeira vez com Google nasce como Consulta.

O catálogo padrão tem três permissões: `ADMIN`, `MANAGE_SECTOR` e `MANAGE_ITEM`. O cadastro consulta `/permissions` e mostra a quantidade, a descrição e quais permissões serão herdadas do perfil selecionado. A composição dos perfis é fixa na aplicação e definida pelas migrations; não pode ser alterada pela interface nem pela API administrativa.

Ao alterar o próprio perfil de usuário, a sessão é consultada novamente. Durante a consulta, as páginas administrativas ficam sem interação; se o acesso for revogado, o conteúdo administrativo e seus menus são removidos. Falhas 403 em mutações também provocam nova verificação.

## Fluxos

- Setores: busca por nome ou código e filtro de situação (Todos, Ativos, Inativos) em filtros recolhíveis. Criação, edição, ativação e desativação são do ADMIN; o gestor consulta os seus setores e os usuários associados. Não existe exclusão de setor. Nome é enviado com trim; código é opcional. Desativação preserva alocações.
- Usuários (ADMIN e gestor): busca por nome ou e-mail, filtros de perfil, setor e situação (Todos, Ativos, Inativos). **Novo usuário** cadastra e-mail, nome opcional, perfil (obrigatório) e setores ativos em uma única transação via `POST /users`; o gestor só escolhe Operador de Campo ou Consulta e só vincula aos próprios setores (ao menos um). O primeiro acesso com Google preserva perfil e setores; até lá, o cartão mostra Aguardando primeiro acesso. Em **Gerenciar acesso**: troca de perfil e alocação ou desalocação de setores, ambas confirmadas, e **Desativar/Reativar** e **Excluir** usuário. O gestor só gerencia Operadores e Consulta que atuam nos seus setores.
- Desativar bloqueia o acesso imediatamente, inclusive de quem já está logado, e preserva o histórico. Excluir remove a conta e os vínculos com setores, mas não é permitido para quem criou inventários (a conta deve ser desativada). Ninguém desativa nem exclui a própria conta, e o gestor só remove quem atua exclusivamente nos setores dele.
- Perfis e permissões (ADMIN): tabela somente para consulta com perfil, descrição e permissões, busca por nome, descrição ou permissão. Não há criação, edição, exclusão ou configuração de permissões. Os nomes exibidos são amigáveis (`src/lib/access-labels.ts`); os códigos do servidor não mudam.
- Proteções: GET de perfis e permissões exige ADMIN ou MANAGE_SECTOR (o gestor usa o catálogo para cadastrar usuários); operador e consulta recebem 403. POST/PUT/PATCH/DELETE de perfis e associação ou remoção de permissões respondem 405 para ADMIN; sem ADMIN, respondem 403. A atribuição de perfil a um usuário continua em `PATCH /users/{id}/profile`, e o gestor só atribui perfis sem permissão de gestão.

## Erros e segurança da interface

Os serviços reutilizam Axios com Bearer do projeto e rejeitam status HTTP de erro. Zod verifica os DTOs recebidos; corpo inesperado bloqueia o fluxo. Mensagens são escolhidas por status, sem depender de response.data.message (exceto os conflitos conhecidos, como excluir quem criou inventários). São tratados 400, 401, 403, 404, 405, 409, falhas do servidor e ausência de resposta. Formulários mantêm os valores quando a gravação falha; nenhuma alteração é aplicada localmente antes da confirmação do servidor.

Modais têm nomes acessíveis, Escape, foco contido e retorno de foco. Ao abrir uma confirmação sobre um editor, o editor fica inerte. Uma trava imediata evita mutações repetidas; as ações ficam desabilitadas durante a requisição.

## Regras que a API aplica

- A sessão expõe permissões e sectorIds; profileId não é usado como regra de autorização.
- Leitura por setor: admin vê tudo; os demais perfis só enxergam inventários, itens, exportações e setores a que estão vinculados. Sem vínculos, recebem listas vazias. A lista de membros de um setor exige ser admin ou gestor do setor.
- Gestão de inventários (criar, editar, finalizar, excluir, importar e limpar itens): admin ou gestor do setor. A importação é única por inventário; para importar outra planilha, os itens precisam ser removidos antes. Inventário finalizado não aceita escaneamento, importação, remoção nem limpeza de itens.
- Itens: o operador altera apenas a validação e as observações; dados do item são do gestor ou admin.
- `POST /users` exige ADMIN ou MANAGE_SECTOR, recebe `{ email, name?, profileId, sectorIds }` e retorna `201`. Não há edição de identidade nem CRUD do catálogo de permissões.
- Os endpoints não oferecem paginação. Busca e filtros são locais.
- O filtro "Sem perfil" da lista de usuários existe apenas para contas antigas sem perfil; ele busca a lista completa e filtra localmente.

## Testar

Use `npm run dev` em frontend e uma sessão válida, com a API configurada em NEXT_PUBLIC_API_URL. Configure o CORS da API para a origem do navegador. As migrations `3.0.xml` (pré-cadastros e unicidade de e-mail sem distinguir caixa) e `4.0.xml` (coluna `active` do usuário) são aplicadas na inicialização do backend.

Para testes isolados: `npm run test:e2e -- administration.spec.ts` ou `access-control.spec.ts` (interface por perfil). Playwright inicia Next em porta e cache isolados e simula a API; no Windows usa Edge instalado e em Linux usa Chromium. Os testes simulados verificam interface e contrato; a autorização real é coberta pelos testes do backend (`AccessControlE2ETest`).

## Arquivos principais

- `src/commons/models/administration.ts`: DTOs, tipos e validações de formulário.
- `src/services/administration.ts`: consumo da API e mensagens de erro.
- `src/contexts/AdministrationAccess.tsx`: perfil e capacidades locais verificadas pela API.
- `src/lib/access.ts`: perfis e capacidades; `src/lib/home-route.ts`: tela inicial por perfil.
- `src/components/organisms/administration/`: telas e componentes compartilhados.
- `src/app/(authenticated)/sectors|users|profiles/page.tsx`: entradas de página protegidas.
- `tests/e2e/administration.spec.ts` e `tests/e2e/access-control.spec.ts`: fluxos, perfis, proteção do administrador e erros HTTP.
