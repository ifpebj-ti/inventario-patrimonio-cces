package clp.inventory.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(
    description = "Destinatário e conteúdo do e-mail que leva a planilha do inventário em anexo.")
public record EmailRequestDto(
    @Schema(
            description =
                "E-mail do destinatário. Só aceita os domínios permitidos (padrão ifpe.edu.br).",
            example = "gestor@ifpe.edu.br")
        @NotBlank
        @Email
        @Size(max = 254)
        String email,
    @Schema(description = "Assunto.", example = "Planilha do inventário 2026")
        @NotBlank
        @Size(max = 200)
        @Pattern(regexp = "[^\\r\\n]*", message = "não pode ter quebra de linha")
        String subject,
    @Schema(description = "Corpo da mensagem.", example = "Segue a planilha em anexo.")
        @Size(max = 5000)
        String message) {}
