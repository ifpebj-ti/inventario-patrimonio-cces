package clp.inventory.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * Salas citadas na planilha, para o gestor conferir antes de importar: as que ainda não existem no
 * setor (a importação vai criá-las) e as que já existem.
 */
@Schema(description = "Salas citadas na planilha, para o gestor conferir antes de importar.")
public record RoomsPreviewDto(
    @Schema(
            description = "Salas que ainda não existem no setor (a importação vai criá-las).",
            example = "[\"Sala 103\"]")
        List<String> newRooms,
    @Schema(
            description = "Salas que já existem no setor.",
            example = "[\"Sala 101\", \"Sala 102\"]")
        List<String> existingRooms) {}
