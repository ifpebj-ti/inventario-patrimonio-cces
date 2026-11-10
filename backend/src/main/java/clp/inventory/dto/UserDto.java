package clp.inventory.dto;

import clp.inventory.model.SectorAllocation;
import clp.inventory.model.User;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Comparator;
import java.util.List;

@Schema(description = "Usuário com as alocações em setores.")
public record UserDto(
    @Schema(description = "Id do usuário.", example = "2") long id,
    @Schema(description = "Nome.", example = "Maria da Silva") String name,
    @Schema(description = "E-mail.", example = "maria@ifpe.edu.br") String email,
    @Schema(description = "Ids dos setores a que está alocado.", example = "[1, 4]")
        List<Long> sectorIds,
    @Schema(description = "Alocações com o cargo e as permissões em cada setor.")
        List<AllocationDto> allocations,
    @Schema(description = "Se é administrador.") boolean administrator,
    @Schema(description = "Se a conta está ativa (conta desativada não entra nem mantém a sessão).")
        boolean active) {

  public static UserDto from(User user, List<SectorAllocation> allocations) {
    List<AllocationDto> dtos =
        allocations.stream()
            .map(AllocationDto::from)
            .sorted(Comparator.comparingLong(AllocationDto::sectorId))
            .toList();
    return new UserDto(
        user.getId(),
        user.getName(),
        user.getEmail(),
        dtos.stream().map(AllocationDto::sectorId).toList(),
        dtos,
        user.isAdministrator(),
        user.isActive());
  }
}
