package clp.inventory.dto.doc;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * Só documentação: forma do 200 de {@code POST /inventory/validate-sheet}. Não é usada pelo código.
 */
@Schema(description = "Planilha válida, pronta para importar.")
public record ValidateSheetResponse(
    @Schema(example = "O arquivo é válido e está pronto para o processamento.") String message,
    @Schema(
            description =
                "Salas citadas que ainda não existem no setor (só quando inventoryId é informado).")
        List<String> newRooms,
    @Schema(
            description =
                "Salas citadas que já existem no setor (só quando inventoryId é informado).")
        List<String> existingRooms) {}
