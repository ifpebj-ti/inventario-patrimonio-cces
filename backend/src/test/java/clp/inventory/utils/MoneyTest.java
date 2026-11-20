package clp.inventory.utils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import clp.inventory.dto.ItemDto;
import clp.inventory.model.Item;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

/** FIN-06: uma só leitura de valor, em centavos, estrita, para cadastro, edição e planilha. */
class MoneyTest {

  // Espaço não separável comum (0xA0) e estreito (0x202F), por código porque não se enxergam.
  private static final String NBSP = String.valueOf((char) 0xA0);
  private static final String NNBSP = String.valueOf((char) 0x202F);

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
        "1.500|150000",
        "1.234.567|123456700",
        "999.999.999|99999999900",
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
    assertThat(Money.parseToCentavos("R$" + NBSP + "1.234,56")).isEqualTo(123456);
    assertThat(Money.parseToCentavos(NBSP + "R$" + NNBSP + "10,00" + NBSP)).isEqualTo(1000);
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {"   ", "\t\n"})
  void missingValueIsRejectedWithTheMissingMessage(String text) {
    assertThatThrownBy(() -> Money.parseToCentavos(text))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage(Money.MISSING_MESSAGE);
  }

  @Test
  void onlyNonBreakingSpacesIsAMissingValue() {
    assertThatThrownBy(() -> Money.parseToCentavos(NBSP + NNBSP + NBSP))
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
        // Formato americano e vírgula com três casas.
        "1,500.00",
        "1,500",
        // Ponto + três dígitos só é milhar sem decimais; com mais de um grupo quebrado, não.
        "1.50.000",
        "1.5000",
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

  @Test
  void hugeInputsAreRejectedQuicklyWithoutBacktracking() {
    // CodeQL java/polynomial-redos: "[\\s]+$" sobre muitos espaços ou tabs seguidos de lixo era
    // quadrático. Hoje a aparagem é linear e o tamanho é limitado antes de qualquer regex.
    int n = 2_000_000;
    String tabs = "\t".repeat(n);
    String spaces = " ".repeat(n);
    String[] inputs = {
      tabs + "x",
      "x" + tabs + "x",
      "R$" + spaces + "x",
      "R$" + spaces + "1" + spaces + "x",
      "1".repeat(n),
      "1".repeat(n) + ",5",
      "1.".repeat(n)
    };

    assertTimeoutPreemptively(
        Duration.ofSeconds(5),
        () -> {
          for (String input : inputs) {
            assertThatThrownBy(() -> Money.parseToCentavos(input))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(Money.INVALID_MESSAGE);
          }
          // Só espaços, por maior que seja, continua sendo valor ausente.
          assertThatThrownBy(() -> Money.parseToCentavos(tabs + spaces))
              .isInstanceOf(IllegalArgumentException.class)
              .hasMessage(Money.MISSING_MESSAGE);
        });
  }

  @Test
  void acceptsTheLongestValidTextAndPaddingAroundIt() {
    assertThat(Money.parseToCentavos("R$ 999.999.999,99")).isEqualTo(99999999999L);
    assertThat(Money.parseToCentavos(" ".repeat(1000) + "R$   10,00" + "\t".repeat(1000)))
        .isEqualTo(1000);
  }

  @ParameterizedTest
  @ValueSource(longs = {0, 1, 5, 99, 100, 150000, 123456, 99999999, 12345678901L, 99999999999L})
  void parsesBackWhatTheApiFormatsInTheListing(long centavos) {
    Item item = new Item("C-1", "", "Mesa", centavos, null, "Maria");

    // O front devolve ao PUT o texto que a listagem mostra ("R$ 1.234,56"): precisa voltar igual.
    assertThat(Money.parseToCentavos(ItemDto.from(item).price())).isEqualTo(centavos);
  }
}
