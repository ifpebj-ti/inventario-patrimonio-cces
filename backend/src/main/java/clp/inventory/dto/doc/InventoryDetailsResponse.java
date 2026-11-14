package clp.inventory.dto.doc;

import clp.inventory.dto.InventoryDto;
import io.swagger.v3.oas.annotations.media.Schema;

/** Só documentação: forma do corpo de {@code GET /inventory/{id}}. Não é usada pelo código. */
@Schema(description = "Inventário com a indicação de se o usuário pode finalizá-lo.")
public record InventoryDetailsResponse(
    InventoryDto inventory,
    @Schema(
            description =
                "Verdadeiro para administrador ou gestor (MANAGE_SECTOR) do setor do inventário.")
        boolean canFinalize) {}
