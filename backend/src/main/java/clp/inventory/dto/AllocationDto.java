package clp.inventory.dto;

import clp.inventory.model.Permission;
import clp.inventory.model.SectorAllocation;
import java.util.List;

/**
 * Alocação do usuário em um setor, com o cargo que ele tem nesse setor e as permissões do cargo.
 */
public record AllocationDto(long sectorId, long roleId, String roleName, List<String> permissions) {

  public static AllocationDto from(SectorAllocation allocation) {
    return new AllocationDto(
        allocation.sector().id(),
        allocation.role().id(),
        allocation.role().name(),
        allocation.role().permissions().stream().map(Permission::name).sorted().toList());
  }
}
