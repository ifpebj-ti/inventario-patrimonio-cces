package clp.inventory.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Credenciais da conta de sistema ou de teste.")
public record LoginDto(
    @Schema(description = "Identificador de login. Obrigatório.", example = "admin")
        String username,
    @Schema(description = "Senha. Obrigatória.", example = "********", format = "password")
        String password) {}
