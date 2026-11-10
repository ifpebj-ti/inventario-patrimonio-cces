package clp.inventory.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "Resultado da validação de uma planilha com erros.")
public record ValidationResponse(
    @Schema(description = "Verdadeiro quando há ao menos uma linha inválida.") boolean hasErrors,
    @Schema(description = "Linhas inválidas.") List<ValidationError> errors) {}
