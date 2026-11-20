package clp.inventory.dto;

import clp.inventory.model.Item;
import clp.inventory.utils.Money;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Corpo de {@code POST /inventory/add-single-item}. Só estes campos são lidos: qualquer outro que
 * venha no JSON ({@code id}, {@code qrCode}, {@code isValid}, {@code validatedAt}, {@code
 * divergent}, {@code room}, {@code inventory}, {@code observations}...) é ignorado, de modo que o
 * cliente nunca escolhe a identidade do item nem atualiza um item existente.
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
    @Schema(
            description =
                "Valor em reais, como texto, no mesmo formato da edição e da planilha: 4500, 4500,50, 4500.50 ou 4.500,00 (aceita R$ na frente; até duas casas decimais, sem sinal). Obrigatório; ausente ou malformado devolve 400.",
            example = "4500,00")
        String price,
    @Schema(
            description =
                "Nome da sala; a sala do setor do inventário é encontrada (sem diferenciar caixa, acento e espaços) ou criada.",
            example = "Sala 101")
        String locale,
    @Schema(description = "Responsável pela carga do bem.", example = "João Souza")
        String responsible) {

  /**
   * Sempre um item novo: sem id, com QR Code gerado pelo servidor e não validado. Lança {@link
   * IllegalArgumentException} se o valor estiver ausente ou malformado.
   */
  public Item toNewItem() {
    return new Item(code, name, description, Money.parseToCentavos(price), locale, responsible);
  }
}
