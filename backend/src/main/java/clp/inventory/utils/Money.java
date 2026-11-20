package clp.inventory.utils;

import java.math.BigDecimal;
import java.util.regex.Pattern;

/**
 * Leitura de valor monetário, a única do sistema: cadastro avulso, edição de item e importação de
 * planilha. Recebe reais como texto e devolve centavos. Valor ausente ou malformado lança {@link
 * IllegalArgumentException}, nunca vira zero.
 *
 * <p>Aceita {@code 1500}, {@code 1500.50}, {@code 1500,50}, {@code 1.500}, {@code 1.500,00} e
 * {@code R$ 1.500,00}, com até duas casas decimais e no máximo 999.999.999,99. Recusa sinal e
 * letras. O ponto seguido de exatamente três dígitos é milhar ({@code 1.500} é mil e quinhentos),
 * porque como decimal teria três casas e o máximo são duas.
 */
public final class Money {

  public static final String MISSING_MESSAGE = "Informe o valor.";

  public static final String INVALID_MESSAGE =
      "Valor inválido. Use, por exemplo, 1500, 1500,50 ou 1.500,00 (até duas casas decimais, sem"
          + " sinal, no máximo 999.999.999,99).";

  // O NumberFormat pt-BR separa "R$" do número com o espaço não separável (0xA0); o estreito
  // (0x202F) aparece em outras localidades. Por código, e não literais, porque não se enxergam.
  private static final char NO_BREAK_SPACE = (char) 0xA0;

  private static final char NARROW_NO_BREAK_SPACE = (char) 0x202F;

  // Maior texto válido: "R$ 999.999.999,99". O limite barra entradas enormes antes da regex.
  private static final int MAX_LENGTH = 32;

  // Milhar sem decimais: 1.500 | 1.234.567 (grupos de exatamente três dígitos depois do primeiro).
  private static final Pattern THOUSANDS = Pattern.compile("\\d{1,3}(\\.\\d{3})+");

  // Já normalizado: ponto decimal, até 9 dígitos inteiros e 2 decimais.
  private static final Pattern NORMALIZED = Pattern.compile("\\d{1,9}(\\.\\d{1,2})?");

  private Money() {}

  /** Valor em reais (texto) para centavos. */
  public static long parseToCentavos(String text) {
    // Os espaços não separáveis viram espaço comum; strip() tira o das pontas.
    String value =
        text == null
            ? ""
            : text.replace(NO_BREAK_SPACE, ' ').replace(NARROW_NO_BREAK_SPACE, ' ').strip();
    if (value.isEmpty()) {
      throw new IllegalArgumentException(MISSING_MESSAGE);
    }
    if (value.length() > MAX_LENGTH) {
      throw new IllegalArgumentException(INVALID_MESSAGE);
    }
    if (value.startsWith("R$")) {
      value = value.substring(2).strip();
    }
    if (value.contains(",")) {
      // Com vírgula decimal, o ponto é milhar: 1.234,56 vira 1234.56.
      value = value.replace(".", "").replace(',', '.');
    } else if (THOUSANDS.matcher(value).matches()) {
      // Sem vírgula, ponto + três dígitos é milhar: 1.500 vira 1500.
      value = value.replace(".", "");
    }
    if (!NORMALIZED.matcher(value).matches()) {
      throw new IllegalArgumentException(INVALID_MESSAGE);
    }
    return new BigDecimal(value).movePointRight(2).longValueExact();
  }
}
