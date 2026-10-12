# Validação de Planilha

Esta página registra o comportamento da branch `feat/validacao-planilha`, comparado com `origin/main`, e as limitações encontradas na revisão de 2026-10-12. A validação descrita aqui é a do arquivo de importação; não é a conferência de um patrimônio pelo leitor de código de barras.

## Histórico e compatibilidade

- Os endpoints `/inventory/validate-sheet` e `/inventory/add-items`, a validação de código/preço e a importação sequencial já existiam antes desta branch, usando Excel (`.xls` e `.xlsx`).
- O commit `436bdc5`, de CamilleMariaa, introduziu CSV no frontend e backend, verificação de cabeçalho, modelo para download, mensagens de importação e limpeza dos itens do inventário. O título do commit descreve apenas parte dessas alterações.
- O commit `9d2eb7b`, também de CamilleMariaa, sincronizou a branch com a `main`.
- Nesta branch, os dois endpoints de entrada aceitam exclusivamente `.csv`. O método privado `parseExcelFile` permanece no controller, mas não é chamado por esses endpoints. Clientes que enviavam Excel precisam migrar; trata-se de mudança incompatível. A exportação continua em XLSX.
- A documentação de requisitos já previa informar linhas inválidas. Os documentos C4 foram atualizados para distinguir importação CSV de exportação XLSX.
- Não foi encontrada implementação de lote/paralelismo neste fluxo no código atual nem no histórico pesquisado por `parallelStream` e `saveAll`.

## Fluxo em duas etapas

| Etapa | Frontend | Backend | Persistência |
| --- | --- | --- | --- |
| Seleção e validação | `FileUploadComponent` apresenta o estado de `useSpreadsheetImport`; `inspectCsv` verifica o arquivo localmente e o serviço envia o mesmo arquivo para validação remota. | `POST /inventory/validate-sheet`, multipart com `file`: lê CSV e verifica código vazio, código com mais de 30 caracteres e preço negativo. | Não grava itens. |
| Processamento | O botão `Processar` só fica habilitado no estado `valid`; o hook envia o arquivo aprovado e atualiza a tabela após a tentativa. | `POST /inventory/add-items`, multipart com `file` e `inventoryId`: lê o CSV novamente e chama `addItemToInventory` por item. | Grava cada item aceito; pode haver sucesso parcial. |

As etapas são duas requisições independentes. Não existe identificador de validação nem vínculo no servidor entre o arquivo validado e o importado. O endpoint de importação não chama `validateItems`; a validação de campos precisa ser repetida no servidor para proteger chamadas diretas e alterações entre as etapas.

O frontend mantém os estados `idle`, `validating`, `valid`, `invalid`, `importing` e `completed`, associados ao objeto File atual. Apenas `valid` habilita Processar. Trocar/remover arquivo ou inventário invalida a aprovação, aborta a validação HTTP pendente e ignora respostas antigas. Uma trava imediata impede cliques duplicados; durante a importação, seleção, remoção e limpeza ficam bloqueadas. Após qualquer tentativa é necessária nova validação para importar novamente.

## Formato CSV

- Modelo: `Código`, `Descrição`, `Responsável`, `Valor`, `Sala`, `Observações`; download com BOM UTF-8 e separador `;`.
- O backend remove o BOM, normaliza acentos e caixa dos cabeçalhos e reconhece aliases. Apenas a coluna de código é obrigatória no parser; as demais são opcionais, mesmo que apareçam no modelo.
- Frontend e backend escolhem `;`, depois `,`, depois tabulação. O frontend lê o arquivo completo, verifica aspas e usa linhas físicas para seus próprios erros.
- O parser aceita delimitadores dentro de aspas e aspas duplicadas na mesma linha. Não suporta campos entre aspas contendo quebras de linha e não rejeita explicitamente aspas sem fechamento.
- Linhas vazias são ignoradas. Os resultados usam o índice dos itens mais dois, não a linha física original; depois de uma linha vazia, a indicação de linha pode ficar incorreta.
- Valores são convertidos de reais para centavos. `parsePriceToCentavos` remove o sinal negativo e converte falhas para zero, enfraquecendo a validação de preço negativo e ocultando valores inválidos.

## Duplicatas

| Situação | Comportamento atual |
| --- | --- |
| Dois códigos iguais no mesmo CSV | O frontend bloqueia antes da chamada remota, informa a linha repetida e a primeira ocorrência. O servidor ainda não detecta na pré-validação; sua importação sequencial rejeita depois que o primeiro for persistido. |
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

`validateSpreadsheetData` mantém a rejeição padrão do Axios para erros HTTP e exige status 200 com corpo compatível, sem hasErrors verdadeiro nem lista de erros. Não interpreta palavras da mensagem para aprovar. Erros 400/401/403/500, rede e respostas inesperadas bloqueiam Processar. Os resultados são tipados; Zod verifica o contrato em execução. Na importação, status 201/206 e contadores consistentes distinguem sucesso total/parcial. Erro 400 com failedItems preserva o relatório mesmo quando nada foi importado. Chaves atuais e legadas com `\n` são aceitas. Falhas exibem mensagem persistente no card e toast; relatórios mostram código, linha e mensagens. A limpeza exige confirmação e só atualiza itens/seleção após resposta bem-sucedida.

## Entrega frontend e pendências de servidor

A [issue #220](https://github.com/ifpebj-ti/inventario-patrimonio-cces/issues/220) acompanha a entrega frontend: aprovação explícita por arquivo, HTTP sem inferência textual, relatórios, verificação local de CSV, duplicatas internas, valores inválidos/negativos, aspas, linhas físicas e testes no navegador.

As garantias de servidor permanecem na [issue #222](https://github.com/ifpebj-ti/inventario-patrimonio-cces/issues/222): revalidar importações diretas, corrigir preço/linhas no parser Java, verificar códigos já persistidos na pré-validação, autorização, unicidade concorrente, limites, testes de API e avaliação medida de lotes/paralelismo. Nenhum código Java/Spring foi alterado nesta correção de frontend.

A verificação local usa código com espaços externos removidos e comparação exata, preservando caixa e zeros à esquerda. Ela exige a quantidade de colunas do cabeçalho, preços não negativos com até duas casas decimais e rejeita campos multilinha, que o backend não suporta. Não verifica todos os itens existentes no inventário, nem oferece garantias contra chamadas diretas à API. Linhas retornadas pelo backend são apresentadas como recebidas; somente erros locais têm numeração física assegurada.

## Testes de frontend

Em `frontend`, execute `npm ci` e `npm run test:e2e`. No Windows, a configuração usa Edge instalado; em Linux/CI, instale o Chromium com `npx playwright install --with-deps chromium`. O workflow Quality executa a suíte e guarda traces em caso de falha.

Playwright inicia Next em `127.0.0.1:3100`, com cache isolado em `.next/e2e`, e intercepta chamadas à API. Nenhum login real, dado de produção ou processo Java é necessário. A suíte cobre validação antes de importar, HTTP 400/401/403/500, respostas inesperadas, rede/leitura, relatório, troca/remoção e respostas tardias, cliques repetidos, CSV inválido, BOM, separadores, modelo para download, contadores atuais/legados, sucesso parcial, falha total, limpeza com confirmação e tela mobile.

Esses testes verificam a interface e o consumo do contrato com respostas simuladas; não comprovam persistência, autorização, concorrência ou desempenho do servidor. As pendências correspondentes estão preservadas na #222.

## Código de referência

Os caminhos abaixo são relativos à raiz do repositório:

- `frontend/src/components/molecules/fileUploadComponet/index.tsx`: seleção controlada, estado explícito, mensagens e botão de processamento.
- `frontend/src/app/(authenticated)/inventory/[id]/page.tsx`: integração do hook, tabela e limpeza confirmada.
- `frontend/src/services/inventory.ts`: chamadas multipart e tratamento de status.
- `frontend/src/components/organisms/csvTemplateModal/index.tsx` e `frontend/src/commons/utils/downloadCsvTemplate.ts`: instruções e modelo CSV.
- `backend/src/main/java/clp/inventory/controller/InventoryController.java`: endpoints, parsing, validação e loop de importação.
- `backend/src/main/java/clp/inventory/service/InventoryService.java`: duplicidade, gravação individual e limpeza.
- `frontend/src/hooks/useSpreadsheetImport.ts`: ciclo das duas etapas, cancelamento, respostas antigas e trava de submissão.
- `frontend/src/commons/utils/spreadsheet.ts` e `frontend/src/commons/models/spreadsheet.ts`: verificação local, tipos e contratos.

## Evidências para revisão

Há testes específicos de interface em `frontend/tests/e2e/spreadsheet.spec.ts`, além de verificação TypeScript e lint. Não há teste automatizado específico desse fluxo em `backend/src/test`; os 156 testes gerais passaram na revisão anterior. O PR registra a execução frontend e distingue o que foi testado de garantias ainda pendentes no servidor.
