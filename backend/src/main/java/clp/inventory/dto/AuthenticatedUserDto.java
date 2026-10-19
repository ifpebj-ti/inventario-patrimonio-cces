package clp.inventory.dto;

import clp.inventory.model.SectorAllocation;
import clp.inventory.model.User;
import java.util.Comparator;
import java.util.List;

/**
 * Dados da sessão que podem ser expostos ao próprio usuário autenticado. {@code allocations} traz o
 * cargo (e as permissões dele) em cada setor; {@code sectorIds} é só a lista dos setores.
 */
public record AuthenticatedUserDto(
    long id,
    String name,
    String email,
    boolean administrator,
    List<Long> sectorIds,
    List<AllocationDto> allocations) {

  public static AuthenticatedUserDto from(User user, List<SectorAllocation> allocations) {
    List<AllocationDto> dtos =
        allocations.stream()
            .map(AllocationDto::from)
            .sorted(Comparator.comparingLong(AllocationDto::sectorId))
            .toList();
    return new AuthenticatedUserDto(
        user.getId(),
        user.getName(),
        user.getEmail(),
        user.isAdministrator(),
        dtos.stream().map(AllocationDto::sectorId).toList(),
        dtos);
  }
}
