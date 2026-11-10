package clp.inventory.dto.doc;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Só documentação: forma do corpo de {@code POST /inventory/add-single-item}, que na prática é lido
 * direto na entidade {@code Item}. Não é usada pelo código.
 */
@Schema(description = "Item avulso a incluir no inventário.")
public record NewItemRequest(
    @Schema(
            description = "Código de patrimônio (tombamento), até 30 caracteres. Obrigatório.",
            example = "2023001234")
        String code,
    @Schema(description = "Nome do bem.", example = "Notebook") String name,
    @Schema(description = "Descrição do bem.", example = "Notebook Dell Latitude 5420")
        String description,
    @Schema(description = "Preço em centavos (não em reais).", example = "450000") long price,
    @Schema(
            description =
                "Nome da sala; a sala do setor do inventário é encontrada (sem diferenciar caixa, acento e espaços) ou criada.",
            example = "Sala 101")
        String locale,
    @Schema(description = "Responsável pela carga do bem.", example = "João Souza")
        String responsible) {}
