package clp.inventory.dto;

import clp.inventory.model.Item;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;

public record ItemDto(
        long id,
        String code,
        String description,
        String responsible,
        String price,
        String locale,
        boolean isValid,
        List<ObservationDto> observations
) {

    public static ItemDto from(Item item) {
        NumberFormat currencyFormat = NumberFormat.getCurrencyInstance(new Locale("pt", "BR"));
        String formattedPrice;

        try {
            // price vem em centavos; divide por 100 para exibir em reais.
            formattedPrice = currencyFormat.format(
                    new BigDecimal(item.price()).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP)
            );
        } catch (NumberFormatException e) {
            formattedPrice = "R$ 0,00";
        }

        List<ObservationDto> observationsDto = item.observations().stream()
                .map(ObservationDto::from)
                .toList();

        return new ItemDto(
                item.id(),
                item.code(),
                item.description(),
                item.responsible(),
                formattedPrice,
                item.locale(),
                item.isValid(),
                observationsDto
        );
    }
}
