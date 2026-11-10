package clp.inventory.dto;

import clp.inventory.model.Sector;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Setor.")
public record SectorDto(
    @Schema(description = "Id do setor (gerado pelo servidor; ignorado ao criar).", example = "1")
        long id,
    @Schema(description = "Nome do setor. Obrigatório.", example = "Laboratório de Informática")
        String name,
    @Schema(description = "Código/sigla do setor.", example = "LAB-INF") String code,
    @Schema(description = "Se o setor está ativo.") boolean active,
    @Schema(
            description = "Data e hora de criação (ISO-8601).",
            example = "2026-01-15T10:00:00",
            accessMode = Schema.AccessMode.READ_ONLY)
        String createdAt,
    @Schema(
            description = "Data e hora da última alteração (ISO-8601).",
            example = "2026-02-01T08:30:00",
            accessMode = Schema.AccessMode.READ_ONLY)
        String updatedAt,
    @Schema(
            description =
                "Verdadeiro quando o setor não tem usuário, inventário nem auditoria e pode ser excluído.",
            accessMode = Schema.AccessMode.READ_ONLY)
        boolean deletable) {

  /** {@code deletable}: o setor não tem usuário, inventário nem auditoria e pode ser excluído. */
  public static SectorDto from(Sector sector, boolean deletable) {
    return new SectorDto(
        sector.id(),
        sector.name(),
        sector.code(),
        sector.active(),
        sector.createdAt() != null ? sector.createdAt().toString() : null,
        sector.updatedAt() != null ? sector.updatedAt().toString() : null,
        deletable);
  }
}
