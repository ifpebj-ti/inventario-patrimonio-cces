package clp.inventory.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Resposta de um login bem-sucedido.")
public record AuthResponseDTO(
    @Schema(
            description =
                "JWT da aplicação (HS256, válido por 24h) para o cabeçalho Authorization: Bearer.",
            example = "eyJhbGciOiJIUzI1NiJ9...")
        String token,
    @Schema(description = "Dados do usuário autenticado.") AuthenticatedUserDto user) {}
