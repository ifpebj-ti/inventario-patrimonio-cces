package clp.inventory.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Corpo de PATCH /item/{id}/validate: a sala em que o item foi encontrado e, para desvalidar,
 * isValid false.
 */
@Schema(description = "Sala em que o item foi encontrado, ou pedido de desvalidação.")
public record ValidateItemRequest(
    @Schema(
            description =
                "Id da sala (do setor do inventário). Obrigatória em setor que tem salas cadastradas.",
            example = "7")
        Long roomId,
    @Schema(
            description =
                "Omitido ou true valida o item. false desvalida: só administrador ou gestor do setor, com o inventário aberto e o item validado.",
            example = "false")
        Boolean isValid) {}
