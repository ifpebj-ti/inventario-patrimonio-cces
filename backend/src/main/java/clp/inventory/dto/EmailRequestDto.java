package clp.inventory.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(
    description = "Destinatário e conteúdo do e-mail que leva a planilha do inventário em anexo.")
public record EmailRequestDto(
    @Schema(description = "E-mail do destinatário.", example = "gestor@ifpe.edu.br") String email,
    @Schema(description = "Assunto.", example = "Planilha do inventário 2026") String subject,
    @Schema(description = "Corpo da mensagem.", example = "Segue a planilha em anexo.")
        String message) {}
