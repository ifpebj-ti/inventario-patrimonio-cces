package clp.inventory.dto.doc;

import io.swagger.v3.oas.annotations.media.Schema;

/** Só documentação: corpo de erro simples ({@code {"message": "..."}}). Não é usada pelo código. */
@Schema(description = "Mensagem de erro.")
public record MessageResponse(
    @Schema(
            example =
                "Formato de arquivo inválido. O sistema aceita exclusivamente arquivos CSV (.csv).")
        String message) {}
