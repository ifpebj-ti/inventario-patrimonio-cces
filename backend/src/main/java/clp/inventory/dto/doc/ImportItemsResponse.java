package clp.inventory.dto.doc;

import clp.inventory.dto.ItemProcessingResult;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * Só documentação: forma do corpo de {@code POST /inventory/add-items}. Não é usada pelo código.
 */
@Schema(
    description =
        "Resultado da importação da planilha CSV, tudo ou nada: no 201 todos os itens entraram (failures 0); no 400 por linhas inválidas nada foi gravado, successes é 0 e failedItems traz só as linhas com erro (totalProcessed conta essas linhas). Atenção: os nomes de totalProcessed, successes, failures, successfulItems e failedItems terminam com uma quebra de linha (\\n), e é assim que a API os devolve.")
public record ImportItemsResponse(
    @Schema(
            name = "totalProcessed\n",
            description = "Linhas de dados gravadas (201) ou linhas com erro (400).",
            example = "50")
        int totalProcessed,
    @Schema(name = "successes\n", description = "Itens adicionados.", example = "48") int successes,
    @Schema(
            name = "failures\n",
            description = "Linhas inválidas (só no 400; nada é gravado).",
            example = "2")
        int failures,
    @Schema(name = "successfulItems\n", description = "Itens adicionados.")
        List<ItemProcessingResult> successfulItems,
    @Schema(name = "failedItems\n", description = "Linhas inválidas, com o motivo (só no 400).")
        List<ItemProcessingResult> failedItems,
    @Schema(description = "Quantas salas novas a importação criou no setor.", example = "1")
        long createdRooms,
    @Schema(
            description = "Resumo: todos adicionados ou nenhum adicionado.",
            example = "Todos os itens foram adicionados com sucesso!")
        String status) {}
