package clp.inventory.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** Corpo de PATCH /item/{id}/not-found: o motivo de o item não ter sido localizado. */
@Schema(description = "Motivo de o item não ter sido localizado.")
public record MarkNotFoundRequest(
    @Schema(
            description =
                "Justificativa, obrigatória (até 255 caracteres). Fica na auditoria e vira uma observação do item.",
            example =
                "Procurado nas salas 101 e 102; o responsável diz que foi devolvido ao almoxarifado.")
        String reason) {}
