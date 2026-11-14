package clp.inventory.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** Corpo de PATCH /item/{id}/validate: a sala em que o item foi encontrado. */
@Schema(description = "Sala em que o item foi encontrado.")
public record ValidateItemRequest(
    @Schema(
            description =
                "Id da sala (do setor do inventário). Obrigatória em setor que tem salas cadastradas.",
            example = "7")
        Long roomId) {}
