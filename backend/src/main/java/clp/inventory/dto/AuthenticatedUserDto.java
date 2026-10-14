package clp.inventory.dto;

import clp.inventory.model.User;
import java.util.List;

/** Dados da sessão que podem ser expostos ao próprio usuário autenticado. */
public record AuthenticatedUserDto(
    long id,
    String name,
    String email,
    Long profileId,
    List<Long> sectorIds,
    List<String> permissions) {

  public static AuthenticatedUserDto from(User user, List<Long> sectorIds) {
    List<String> permissions =
        user.getProfile() == null
            ? List.of()
            : user.getProfile().permissions().stream()
                .map(permission -> permission.name())
                .sorted()
                .toList();

    return new AuthenticatedUserDto(
        user.getId(),
        user.getName(),
        user.getEmail(),
        user.getProfileId(),
        sectorIds.stream().sorted().toList(),
        permissions);
  }
}
