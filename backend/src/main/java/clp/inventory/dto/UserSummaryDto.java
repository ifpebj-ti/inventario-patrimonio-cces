package clp.inventory.dto;

import clp.inventory.model.User;
import io.swagger.v3.oas.annotations.media.Schema;

/** Só o necessário para escolher quem alocar: sem cargos, setores nem papel de administrador. */
@Schema(description = "Resumo de usuário para escolher quem alocar.")
public record UserSummaryDto(
    @Schema(description = "Id do usuário.", example = "2") long id,
    @Schema(description = "Nome.", example = "Maria da Silva") String name,
    @Schema(description = "E-mail.", example = "maria@ifpe.edu.br") String email) {

  public static UserSummaryDto from(User user) {
    return new UserSummaryDto(user.getId(), user.getName(), user.getEmail());
  }
}
