package clp.inventory.utils;

import java.math.BigDecimal;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Leitura de valor monetário, a única do sistema: cadastro avulso, edição de item e importação de
 * planilha chamam esta função, então o mesmo texto vale os mesmos centavos em qualquer caminho. O
 * valor entra em reais, como texto, e sai em centavos (é assim que o item o guarda).
 *
 * <p>É estrita de propósito: valor ausente ou malformado lança {@link IllegalArgumentException} em
 * vez de virar zero ou de manter o valor anterior. Formatos aceitos, com um {@code R$} opcional na
 * frente e no máximo duas casas decimais:
 *
 * <ul>
 *   <li>{@code 1500}, {@code 1500.5}, {@code 1500.50} (ponto decimal, como o JSON e o front
 *       enviam);
 *   <li>{@code 1500,5}, {@code 1500,50} (vírgula decimal);
 *   <li>{@code 1.500,00} e {@code R$ 1.234.567,89} (ponto de milhar, que só vale com vírgula
 *       decimal).
 * </ul>
 *
 * <p>Recusados: sinal, letras, mais de duas casas, mais de nove dígitos inteiros (o máximo é
 * 999.999.999,99) e separadores ambíguos como {@code 1.500} (R$ 1.500 ou R$ 1,50?) e {@code
 * 1,500.00}.
 */
public final class Money {

  public static final String MISSING_MESSAGE = "Informe o valor.";

  public static final String INVALID_MESSAGE =
      "Valor inválido. Use, por exemplo, 1500, 1500,50 ou 1.500,00 (até duas casas decimais, sem"
          + " sinal, no máximo 999.999.999,99).";

  // "R$" no começo, com o espaço comum ou o não separável que o NumberFormat pt-BR gera.
  private static final Pattern CURRENCY_PREFIX = Pattern.compile("^R\\$[\\s\\u00A0\\u202F]*");

  private static final Pattern EDGE_SPACES =
      Pattern.compile("^[\\s\\u00A0\\u202F]+|[\\s\\u00A0\\u202F]+$");

  // 1500 | 1500.5 | 1500.50
  private static final Pattern DOT_DECIMAL = Pattern.compile("(\\d{1,9})(?:\\.(\\d{1,2}))?");

  // 1500,5 | 1500,50 | 1.500,50 | 1.234.567,89 (o ponto de milhar exige grupos de três dígitos)
  private static final Pattern COMMA_DECIMAL =
      Pattern.compile("(\\d{1,3}(?:\\.\\d{3}){0,2}|\\d{1,9}),(\\d{1,2})");

  private Money() {}

  /** Valor em reais (texto) para centavos. Lança {@link IllegalArgumentException} se inválido. */
  public static long parseToCentavos(String text) {
    if (text == null) {
      throw new IllegalArgumentException(MISSING_MESSAGE);
    }
    String value = EDGE_SPACES.matcher(text).replaceAll("");
    if (value.isEmpty()) {
      throw new IllegalArgumentException(MISSING_MESSAGE);
    }
    value = CURRENCY_PREFIX.matcher(value).replaceFirst("");

    Matcher dot = DOT_DECIMAL.matcher(value);
    if (dot.matches()) {
      return toCentavos(dot.group(1), dot.group(2));
    }
    Matcher comma = COMMA_DECIMAL.matcher(value);
    if (comma.matches()) {
      return toCentavos(comma.group(1).replace(".", ""), comma.group(2));
    }
    throw new IllegalArgumentException(INVALID_MESSAGE);
  }

  private static long toCentavos(String integerPart, String decimalPart) {
    BigDecimal reais =
        new BigDecimal(integerPart + "." + (decimalPart == null ? "0" : decimalPart));
    // No máximo 9 dígitos inteiros e 2 decimais: a conversão é exata e cabe em long.
    return reais.movePointRight(2).longValueExact();
  }
}
