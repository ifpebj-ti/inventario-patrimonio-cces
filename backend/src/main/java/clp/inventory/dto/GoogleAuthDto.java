package clp.inventory.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Credencial entregue pelo Google Identity Services.")
public record GoogleAuthDto(
    @Schema(
            description = "ID token (JWT) emitido pelo Google. Obrigatório.",
            example = "eyJhbGciOiJSUzI1NiIs...")
        String credential) {}
