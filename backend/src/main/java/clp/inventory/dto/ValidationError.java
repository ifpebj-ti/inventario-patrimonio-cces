package clp.inventory.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "Erros de uma linha inválida da planilha.")
public record ValidationError(
    @Schema(description = "Código do item da linha.", example = "2023001234") String code,
    @Schema(description = "Número da linha na planilha (a 1 é o cabeçalho).", example = "5")
        int line,
    @Schema(
            description = "Problemas encontrados na linha.",
            example = "[\"Preço não pode ser negativo\"]")
        List<String> errors) {}
