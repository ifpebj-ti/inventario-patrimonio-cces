package clp.inventory.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Define se o usuário é administrador.")
public record SetAdministratorDto(
    @Schema(
            description = "true concede e false revoga o papel de administrador. Obrigatório.",
            example = "true")
        Boolean administrator) {}
