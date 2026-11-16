package clp.inventory.service;

import java.util.List;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Quem pode receber a planilha por e-mail: só endereços dos domínios permitidos ({@code
 * email.sheet.allowed-domains}, padrão {@code ifpe.edu.br}). Sem essa trava o envio serviria de
 * relé, com o remetente institucional, para qualquer endereço de fora.
 */
@Component
public class SheetEmailPolicy {

  private final List<String> allowedDomains;

  @Autowired
  public SheetEmailPolicy(
      @Value("${email.sheet.allowed-domains:ifpe.edu.br}") List<String> domains) {
    this.allowedDomains =
        domains.stream()
            .map(domain -> domain.trim().toLowerCase(Locale.ROOT))
            .filter(domain -> !domain.isEmpty())
            .toList();
  }

  /** O domínio do endereço é um dos permitidos ou subdomínio de um deles. */
  public boolean isAllowed(String email) {
    if (email == null) {
      return false;
    }
    int at = email.lastIndexOf('@');
    if (at < 0 || at == email.length() - 1) {
      return false;
    }
    String domain = email.substring(at + 1).trim().toLowerCase(Locale.ROOT);

    // O "." antes do domínio permitido é o que impede @fakeifpe.edu.br e @ifpe.edu.br.evil.com de
    // passarem, enquanto libera os subdomínios (@discente., @docente.). Lista vazia não libera
    // ninguém: diferente do login, aqui o seguro é negar.
    return allowedDomains.stream()
        .anyMatch(allowed -> domain.equals(allowed) || domain.endsWith("." + allowed));
  }
}
