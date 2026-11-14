package clp.inventory.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Corpo de criação e de renomeação de sala.")
public record RoomRequest(
    @Schema(
            description =
                "Nome da sala. Obrigatório; único no setor, sem diferenciar caixa, acento e espaços.",
            example = "Sala 101")
        String name) {}
