package clp.inventory.dto;

import clp.inventory.model.Item;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;

@Schema(
    description =
        "Item do patrimônio. No PUT /item/{id} só alguns campos são aplicados; veja a operação.")
public record ItemDto(
    @Schema(description = "Id do item.", example = "300") long id,
    @Schema(
            description = "Código de patrimônio (tombamento), até 30 caracteres.",
            example = "2023001234")
        String code,
    @Schema(description = "Descrição do bem.", example = "Notebook Dell Latitude 5420")
        String description,
    @Schema(description = "Responsável pela carga do bem.", example = "João Souza")
        String responsible,
    @Schema(
            description =
                "Preço já formatado em reais (R$), calculado a partir dos centavos armazenados. Na edição (PUT) é obrigatório e aceita o mesmo texto que a listagem devolve e os formatos 4500, 4500,50, 4500.50, 4.500 e 4.500,00 (até duas casas decimais, sem sinal); ausente ou malformado devolve 400 e o preço não muda.",
            example = "R$ 4.500,00")
        String price,
    @Schema(
            description = "Nome da sala cadastrada (ou o texto recebido na importação).",
            example = "Sala 101")
        String locale,
    @Schema(description = "Id da sala cadastrada.", example = "7") Long roomId,
    @Schema(description = "Nome da sala cadastrada.", example = "Sala 101") String roomName,
    @Schema(
            description = "Id da sala em que o item foi encontrado na última validação.",
            example = "7")
        Long foundRoomId,
    @Schema(description = "Nome da sala em que foi encontrado.", example = "Sala 101")
        String foundRoomName,
    @Schema(description = "Verdadeiro quando foi validado numa sala diferente da cadastrada.")
        boolean divergent,
    @Schema(description = "Se o item foi validado (encontrado e conferido) no inventário.")
        boolean isValid,
    @Schema(
            description =
                "Situação do item: PENDENTE (ainda não conferido), VALIDADO ou NAO_LOCALIZADO (conferido e não encontrado). O inventário só é finalizado sem itens PENDENTE. Na edição (PUT) é ignorada: a situação muda por PATCH /item/{id}/validate e PATCH /item/{id}/not-found.",
            example = "PENDENTE",
            allowableValues = {"PENDENTE", "VALIDADO", "NAO_LOCALIZADO"})
        String validationStatus,
    @Schema(
            description =
                "Data e hora da conferência (ISO-8601): validação ou marcação como não localizado; nulo se pendente.",
            example = "2026-11-10T09:15:00")
        String validatedAt,
    @Schema(
            description = "Identificador (UUID) gravado no QR Code da etiqueta.",
            example = "3f1c2d4e-5a6b-4c7d-8e9f-0a1b2c3d4e5f")
        String qrCode,
    @Schema(description = "Observações do item.") List<ObservationDto> observations) {

  public static ItemDto from(Item item) {
    NumberFormat currencyFormat = NumberFormat.getCurrencyInstance(new Locale("pt", "BR"));
    String formattedPrice;

    try {
      // price vem em centavos; divide por 100 para exibir em reais.
      formattedPrice =
          currencyFormat.format(
              new BigDecimal(item.price())
                  .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP));
    } catch (NumberFormatException e) {
      formattedPrice = "R$ 0,00";
    }

    List<ObservationDto> observationsDto =
        item.observations().stream().map(ObservationDto::from).toList();

    return new ItemDto(
        item.id(),
        item.code(),
        item.description(),
        item.responsible(),
        formattedPrice,
        item.locale(),
        item.room() != null ? item.room().id() : null,
        item.room() != null ? item.room().name() : null,
        item.foundRoom() != null ? item.foundRoom().id() : null,
        item.foundRoom() != null ? item.foundRoom().name() : null,
        item.divergent(),
        item.isValid(),
        item.validationStatus().name(),
        item.validatedAt() != null ? item.validatedAt().toString() : null,
        item.qrCode(),
        observationsDto);
  }
}
