# Validação de Planilha

Esta página registra o comportamento da branch `feat/validacao-planilha`, comparado com `origin/main`, e as limitações encontradas na revisão de 2026-10-12. A validação descrita aqui é a do arquivo de importação; não é a conferência de um patrimônio pelo leitor de código de barras.

## Histórico e compatibilidade

- Os endpoints `/inventory/validate-sheet` e `/inventory/add-items`, a validação de código/preço e a importação sequencial já existiam antes desta branch, usando Excel (`.xls` e `.xlsx`).
- O commit `436bdc5`, de CamilleMariaa, introduziu CSV no frontend e backend, verificação de cabeçalho, modelo para download, mensagens de importação e limpeza dos itens do inventário. O título do commit descreve apenas parte dessas alterações.
- O commit `9d2eb7b`, também de CamilleMariaa, sincronizou a branch com a `main`.
- Nesta branch, os dois endpoints de entrada aceitam exclusivamente `.csv`. O método privado `parseExcelFile` permanece no controller, mas não é chamado por esses endpoints. Clientes que enviavam Excel precisam migrar; trata-se de mudança incompatível. A exportação continua em XLSX.
- A documentação de requisitos já previa informar linhas inválidas. Os documentos C4 ainda descrevem entrada XLSX; essa descrição precisa ser revista quando a migração for aprovada.
- Não foi encontrada implementação de lote/paralelismo neste fluxo no código atual nem no histórico pesquisado por `parallelStream` e `saveAll`.

## Fluxo em duas etapas

| Etapa | Frontend | Backend | Persistência |
| --- | --- | --- | --- |
| Seleção e validação | `FileUploadComponent` verifica extensão e cabeçalho; `handleSpreadsheetValidation` envia o arquivo. | `POST /inventory/validate-sheet`, multipart com `file`: lê CSV e verifica código vazio, código com mais de 30 caracteres e preço negativo. | Não grava itens. |
| Processamento | O botão `Processar` chama `handleFileUpload`, que envia novamente o arquivo e atualiza a tabela após a importação. | `POST /inventory/add-items`, multipart com `file` e `inventoryId`: lê o CSV novamente e chama `addItemToInventory` por item. | Grava cada item aceito; pode haver sucesso parcial. |

As etapas são duas requisições independentes. Não existe identificador de validação nem vínculo no servidor entre o arquivo validado e o importado. O endpoint de importação não chama `validateItems`; a validação de campos precisa ser repetida no servidor para proteger chamadas diretas e alterações entre as etapas.

O estado atual do frontend usa a presença de `validationErrors` para decidir entre `Ver erros` e `Processar`. Erros gerais apresentados apenas por toast não bloqueiam necessariamente o processamento. Não existe estado explícito de validação aprovada associado ao arquivo.

## Formato CSV

- Modelo: `Código`, `Descrição`, `Responsável`, `Valor`, `Sala`, `Observações`; download com BOM UTF-8 e separador `;`.
- O backend remove o BOM, normaliza acentos e caixa dos cabeçalhos e reconhece aliases. Apenas a coluna de código é obrigatória no parser; as demais são opcionais, mesmo que apareçam no modelo.
- O backend escolhe `;`, depois `,`, depois tabulação; o frontend escolhe `;`, depois tabulação, depois `,`. Essa diferença pode produzir interpretações divergentes.
- O parser aceita delimitadores dentro de aspas e aspas duplicadas na mesma linha. Não suporta campos entre aspas contendo quebras de linha e não rejeita explicitamente aspas sem fechamento.
- Linhas vazias são ignoradas. Os resultados usam o índice dos itens mais dois, não a linha física original; depois de uma linha vazia, a indicação de linha pode ficar incorreta.
- Valores são convertidos de reais para centavos. `parsePriceToCentavos` remove o sinal negativo e converte falhas para zero, enfraquecendo a validação de preço negativo e ocultando valores inválidos.

## Duplicatas

| Situação | Comportamento atual |
| --- | --- |
| Dois códigos iguais no mesmo CSV | A validação prévia não detecta. Na importação sequencial, se o primeiro item for persistido, o seguinte encontra o código no inventário e é rejeitado. |
| Código já existente no inventário | `InventoryService.addItemToInventory` percorre os itens do inventário e rejeita o código com `Item already exists in inventory`. |
| Validação antes de importar | `/validate-sheet` não recebe `inventoryId` e não consulta os itens existentes. |
| Variações de caixa | A comparação usa `String.equals`; não há normalização explícita de caixa para a regra de duplicidade. Tokens CSV têm espaços externos removidos. |
| Requisições concorrentes | A consulta seguida de gravação não garante exclusividade. Falta uma política de concorrência e uma garantia de unicidade por inventário no banco. |

## Processamento e respostas

A importação percorre um `for` no controller. Para cada item, o serviço busca o inventário, verifica sua coleção, adiciona o item e chama `inventoryRepository.save`. Não há `saveAll`, chunks, configuração de JDBC batch ou executor paralelo neste fluxo. Receber muitos itens na mesma requisição não equivale a persistir em lote.

| Endpoint | Resposta |
| --- | --- |
| `/validate-sheet` | 200: mensagem de validade; 400: erro geral ou `ValidationResponse` com erros por item; 500: falha não tratada no processamento. |
| `/add-items` | 201: todos aceitos; 206: sucesso parcial; 400: todos falharam ou entrada inválida; 500: falha não tratada. |

A resposta de importação inclui `totalProcessed`, `successes`, `failures`, `successfulItems` e `failedItems`, além das chaves legadas com `\n`. O frontend aceita as duas variantes dos contadores.

`validateSpreadsheetData` usa `validateStatus: () => true` e devolve apenas o corpo. O chamador tenta reconhecer erros pelo texto da mensagem; respostas 401/403 e corpos inesperados podem cair na mensagem de sucesso. O contrato precisa preservar o status HTTP e ter um resultado tipado.

## Melhorias identificadas

Estas melhorias são pendências, não funcionalidades entregues nesta branch. O acompanhamento operacional está na [issue #220](https://github.com/ifpebj-ti/inventario-patrimonio-cces/issues/220), vinculada ao PR.

1. Bloquear `Processar` até a aprovação explícita do arquivo atual; invalidar o resultado ao trocar/remover arquivo e tratar 400/401/403/500 sem inferência pelo texto.
2. Repetir as validações de campos em `/add-items`, preservar números de linha físicos e rejeitar preços inválidos/negativos, aspas malformadas e estrutura inconsistente.
3. Detectar duplicatas no arquivo com uma estrutura de busca, informar as linhas envolvidas e consultar os códigos existentes no inventário durante a pré-validação. Definir a política para espaços, caixa e zeros à esquerda.
4. Validar acesso ao inventário também nos endpoints de importação e limpeza; ambos recebem o identificador sem conferir o proprietário nesses métodos.
5. Definir tamanho do lote, limites de arquivo/linhas, transações e política de sucesso parcial antes de substituir as gravações individuais. Medir tempo, memória e consultas em comparação com o fluxo sequencial.
6. Avaliar paralelismo somente com concorrência limitada e transações/contextos de persistência isolados. Não compartilhar a entidade `Inventory`, sua coleção ou `EntityManager` entre threads. Garantir duplicidade sob requisições concorrentes e resultados determinísticos.
7. Criar testes específicos de validação/importação, hoje ausentes, cobrindo CSV válido, erro geral, campos inválidos, duplicatas, importação parcial, concorrência e arquivos grandes.

## Código de referência

Os caminhos abaixo são relativos à raiz do repositório:

- `frontend/src/components/molecules/fileUploadComponet/index.tsx`: seleção, cabeçalho e botão de processamento.
- `frontend/src/app/(authenticated)/inventory/[id]/page.tsx`: handlers das duas etapas e feedback.
- `frontend/src/services/inventory.ts`: chamadas multipart e tratamento de status.
- `frontend/src/components/organisms/csvTemplateModal/index.tsx` e `frontend/src/commons/utils/downloadCsvTemplate.ts`: instruções e modelo CSV.
- `backend/src/main/java/clp/inventory/controller/InventoryController.java`: endpoints, parsing, validação e loop de importação.
- `backend/src/main/java/clp/inventory/service/InventoryService.java`: duplicidade, gravação individual e limpeza.

## Evidências para revisão

Não há teste automatizado específico desse fluxo em `backend/src/test`. A leitura do código confirma as etapas e suas limitações, mas não comprova comportamento em navegador, concorrência nem ganho de desempenho. O PR deve registrar os checks efetivamente executados e manter as pendências visíveis antes do merge.
