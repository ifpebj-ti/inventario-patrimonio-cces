package clp.inventory.dto;

import clp.inventory.model.Permission;
import clp.inventory.model.SectorAllocation;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * Alocação do usuário em um setor, com o cargo que ele tem nesse setor e as permissões do cargo.
 */
@Schema(description = "Alocação do usuário em um setor, com o cargo e as permissões dele ali.")
public record AllocationDto(
    @Schema(description = "Id do setor.", example = "1") long sectorId,
    @Schema(description = "Id do cargo no setor.", example = "2") long roleId,
    @Schema(description = "Nome do cargo.", example = "Gestor de Setor") String roleName,
    @Schema(
            description = "Nomes das permissões do cargo, em ordem alfabética.",
            example = "[\"MANAGE_ITEM\", \"MANAGE_SECTOR\", \"VIEW_SECTOR\"]")
        List<String> permissions) {

  public static AllocationDto from(SectorAllocation allocation) {
    return new AllocationDto(
        allocation.sector().id(),
        allocation.role().id(),
        allocation.role().name(),
        allocation.role().permissions().stream().map(Permission::name).sorted().toList());
  }
}
