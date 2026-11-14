package clp.inventory.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Resultado do processamento de uma linha da planilha importada.")
public record ItemProcessingResult(
    @Schema(description = "Código do item da linha.", example = "2023001234") String code,
    @Schema(description = "Número da linha na planilha (a 1 é o cabeçalho).", example = "2")
        int line,
    @Schema(description = "Se o item foi adicionado.") boolean success,
    @Schema(
            description = "Mensagem de sucesso ou o motivo da falha.",
            example = "Item adicionado com sucesso")
        String message) {}
