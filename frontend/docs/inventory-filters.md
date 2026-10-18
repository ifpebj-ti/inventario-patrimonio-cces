# Filtros de inventários

A listagem `/inventory` oferece somente quatro filtros: Nome do inventário, Status,
Setor e Ano. O ano considera a data de criação e as opções são extraídas dos
inventários acessíveis, em ordem decrescente. Todos os anos é a seleção inicial.

A busca por nome não diferencia acentos ou maiúsculas. Os filtros são combinados,
podem ser removidos individualmente.
O status recebido do Dashboard é preservado na abertura da página.

Status oferece exatamente Todos os status, Em andamento, Finalizados e Atrasados.
Em andamento considera somente registros com status EM_ANDAMENTO; atrasados ficam
no filtro VENCIDO. O parâmetro legado OPEN do Dashboard é convertido em EM_ANDAMENTO.
Setor oferece Todos os setores e o catálogo autorizado: ADMIN vê todos os setores;
gestores veem os vinculados, inclusive vínculos sem inventários. A conta de admin
não precisa de alocação pessoal para filtrar os setores. Não existe opção Sem setor.

O card pode ser recolhido. A tabela mostra a contagem de resultados e usa o rodapé
original, com página centralizada e setas apenas quando há outra página. Não há
seletor de ordenação nem de itens por página. A coluna Data de
finalização usa completedAt, gravado ao confirmar a finalização nos detalhes do
inventário, e substitui a coluna Atualização. Período, tipo de data,
data inicial e data final foram removidos do formulário e da lógica de filtragem.

Testes de navegador: `tests/e2e/inventory-filters.spec.ts`.

O modal Novo Inventário ignora cancelamentos de requisição ao carregar setores,
evitando um aviso falso durante a montagem do React em desenvolvimento. O modal
Editar Inventário mostra e pré-seleciona o setor atual, permite escolher outro setor
ativo e envia sectorId no PUT. ADMIN pode mudar o setor de inventários de outros
usuários; gestores só movem entre setores em que estão alocados. Erros de gravação
preservam o modal aberto para nova tentativa.
