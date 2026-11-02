package clp.inventory.utils;

import java.text.Normalizer;
import java.util.Locale;

/**
 * Nome de sala para comparação: sem espaços nas pontas nem repetidos, em minúsculas e sem acento.
 * "Sala 105", "sala 105" e " SALA 105 " são a mesma sala. É a mesma regra da migration 9.0, que
 * converte os textos antigos de sala (im_item.locale) em salas por setor.
 */
public final class RoomNames {

  public static final int MAX_LENGTH = 100;

  private RoomNames() {}

  /** Nome para exibir: aparado e com os espaços repetidos reduzidos a um; vazio vira nulo. */
  public static String display(String name) {
    if (name == null) {
      return null;
    }
    String cleaned = name.trim().replaceAll("\\s+", " ");
    return cleaned.isEmpty() ? null : cleaned;
  }

  /** Chave de comparação do nome; vazio vira nulo. */
  public static String normalize(String name) {
    String display = display(name);
    if (display == null) {
      return null;
    }
    String decomposed = Normalizer.normalize(display.toLowerCase(Locale.ROOT), Normalizer.Form.NFD);
    return decomposed.replaceAll("\\p{M}+", "");
  }
}
