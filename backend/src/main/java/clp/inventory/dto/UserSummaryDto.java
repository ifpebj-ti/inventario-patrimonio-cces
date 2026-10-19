package clp.inventory.dto;

import clp.inventory.model.User;

/** Só o necessário para escolher quem alocar: sem cargos, setores nem papel de administrador. */
public record UserSummaryDto(long id, String name, String email) {

  public static UserSummaryDto from(User user) {
    return new UserSummaryDto(user.getId(), user.getName(), user.getEmail());
  }
}
