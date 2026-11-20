package clp.inventory.utils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import clp.inventory.dto.ItemDto;
import clp.inventory.model.Item;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

/** FIN-06: uma só leitura de valor, em centavos, estrita, para cadastro, edição e planilha. */
class MoneyTest {

  @ParameterizedTest
  @CsvSource(
      delimiter = '|',
      value = {
        "0|0",
        "0,00|0",
        "7|700",
        "1500|150000",
        "1500.5|150050",
        "1500.50|150050",
        "1500,5|150050",
        "1500,50|150050",
        "1.500,00|150000",
        "1.234.567,89|123456789",
        "R$ 1.500,00|150000",
        "R$1500|150000",
        "  25,50  |2550",
        "007|700",
        "999999999|99999999900",
        "999.999.999,99|99999999999"
      })
  void acceptsTheSupportedFormats(String text, long centavos) {
    assertThat(Money.parseToCentavos(text)).isEqualTo(centavos);
  }

  @Test
  void acceptsTheNonBreakingSpaceThatTheCurrencyFormatGenerates() {
    assertThat(Money.parseToCentavos("R$ 1.234,56")).isEqualTo(123456);
    assertThat(Money.parseToCentavos(" R$ 10,00 ")).isEqualTo(1000);
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {"   ", " "})
  void missingValueIsRejectedWithTheMissingMessage(String text) {
    assertThatThrownBy(() -> Money.parseToCentavos(text))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage(Money.MISSING_MESSAGE);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "abc",
        "R$",
        "R$ ",
        "-10",
        "+10",
        "10abc",
        "1500,555",
        "1500.555",
        "10,999",
        // Ambíguos: R$ 1.500 ou R$ 1,50? Recusados em vez de adivinhar.
        "1.500",
        "1,500.00",
        "1.234.567",
        "1,500",
        // Grupos de milhar fora do padrão.
        "1.50,00",
        "12.3456,00",
        "1..500,00",
        "1.500,",
        ",50",
        ".50",
        "1500.",
        "1 500,00",
        "1e3",
        "NaN",
        // Mais de nove dígitos inteiros.
        "1000000000",
        "99999999999999999999",
        "1.000.000.000,00"
      })
  void malformedValueIsRejectedWithTheInvalidMessage(String text) {
    assertThatThrownBy(() -> Money.parseToCentavos(text))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage(Money.INVALID_MESSAGE);
  }

  @ParameterizedTest
  @ValueSource(longs = {0, 1, 5, 99, 100, 150000, 123456, 99999999, 12345678901L, 99999999999L})
  void parsesBackWhatTheApiFormatsInTheListing(long centavos) {
    Item item = new Item("C-1", "", "Mesa", centavos, null, "Maria");

    // O front devolve ao PUT o texto que a listagem mostra ("R$ 1.234,56"): precisa voltar igual.
    assertThat(Money.parseToCentavos(ItemDto.from(item).price())).isEqualTo(centavos);
  }
}
