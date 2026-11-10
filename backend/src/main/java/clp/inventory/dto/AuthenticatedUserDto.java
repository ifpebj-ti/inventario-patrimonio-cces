package clp.inventory.dto;

import clp.inventory.model.SectorAllocation;
import clp.inventory.model.User;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Comparator;
import java.util.List;

/**
 * Dados da sessão que podem ser expostos ao próprio usuário autenticado. {@code allocations} traz o
 * cargo (e as permissões dele) em cada setor; {@code sectorIds} é só a lista dos setores.
 */
@Schema(description = "Dados da sessão do usuário autenticado.")
public record AuthenticatedUserDto(
    @Schema(description = "Id do usuário.", example = "2") long id,
    @Schema(description = "Nome do usuário.", example = "Maria da Silva") String name,
    @Schema(
            description = "E-mail (ou identificador de login, na conta de sistema).",
            example = "maria@ifpe.edu.br")
        String email,
    @Schema(description = "Se o usuário é administrador (acesso total).") boolean administrator,
    @Schema(description = "Ids dos setores a que o usuário está alocado.", example = "[1, 4]")
        List<Long> sectorIds,
    @Schema(description = "Alocações com o cargo e as permissões em cada setor.")
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
