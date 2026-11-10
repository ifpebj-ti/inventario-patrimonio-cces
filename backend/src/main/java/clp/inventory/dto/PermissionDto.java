package clp.inventory.dto;

import clp.inventory.model.Permission;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Permissão do sistema.")
public record PermissionDto(
    @Schema(description = "Id da permissão.", example = "1") long id,
    @Schema(
            description = "Nome (usado pelo código para conferir acesso).",
            example = "MANAGE_SECTOR")
        String name,
    @Schema(
            description = "Descrição da permissão.",
            example = "Gerencia os inventários e os membros do setor.")
        String description) {

  public static PermissionDto from(Permission permission) {
    return new PermissionDto(permission.id(), permission.name(), permission.description());
  }
}
