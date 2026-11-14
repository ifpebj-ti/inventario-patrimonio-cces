package clp.inventory.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** Corpo de {@code PUT /users/{id}/sectors/{sectorId}}: o cargo do usuário nesse setor. */
@Schema(description = "Cargo do usuário em um setor.")
public record AllocateSectorDto(
    @Schema(description = "Id do cargo que o usuário terá no setor.", example = "2") Long roleId) {}
