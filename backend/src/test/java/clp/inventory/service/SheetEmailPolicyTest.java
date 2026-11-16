package clp.inventory.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class SheetEmailPolicyTest {

  private final SheetEmailPolicy policy = new SheetEmailPolicy(List.of("ifpe.edu.br"));

  @Test
  void aceitaODominioExatoESubdominios() {
    assertThat(policy.isAllowed("maria@ifpe.edu.br")).isTrue();
    assertThat(policy.isAllowed("aluno@discente.ifpe.edu.br")).isTrue();
    assertThat(policy.isAllowed("a@b.recife.ifpe.edu.br")).isTrue();
  }

  @Test
  void ignoraCaixaEEspacosDoEndereco() {
    assertThat(policy.isAllowed("Maria@IFPE.EDU.BR")).isTrue();
    assertThat(policy.isAllowed("maria@ifpe.edu.br ")).isTrue();
  }

  @Test
  void recusaDominiosParecidosOuDeFora() {
    assertThat(policy.isAllowed("alvo@gmail.com")).isFalse();
    assertThat(policy.isAllowed("alvo@fakeifpe.edu.br")).isFalse();
    assertThat(policy.isAllowed("alvo@ifpe.edu.br.evil.com")).isFalse();
    assertThat(policy.isAllowed("ifpe.edu.br@evil.com")).isFalse();
    assertThat(policy.isAllowed("alvo@ifpe.edu")).isFalse();
  }

  @Test
  void recusaEnderecoSemDominioOuNulo() {
    assertThat(policy.isAllowed(null)).isFalse();
    assertThat(policy.isAllowed("")).isFalse();
    assertThat(policy.isAllowed("sem-arroba")).isFalse();
    assertThat(policy.isAllowed("maria@")).isFalse();
  }

  @Test
  void aceitaVariosDominiosConfigurados() {
    SheetEmailPolicy many = new SheetEmailPolicy(List.of(" ifpe.edu.br ", "", "exemplo.org"));

    assertThat(many.isAllowed("a@exemplo.org")).isTrue();
    assertThat(many.isAllowed("a@sub.exemplo.org")).isTrue();
    assertThat(many.isAllowed("a@outro.org")).isFalse();
  }

  @Test
  void listaVaziaNaoLiberaNinguem() {
    assertThat(new SheetEmailPolicy(List.of()).isAllowed("maria@ifpe.edu.br")).isFalse();
    assertThat(new SheetEmailPolicy(List.of("", " ")).isAllowed("maria@ifpe.edu.br")).isFalse();
  }
}
