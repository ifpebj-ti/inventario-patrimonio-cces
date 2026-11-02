package clp.inventory.utils;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class RoomNamesTest {

  @Test
  void displayTrimsAndCollapsesSpaces() {
    assertThat(RoomNames.display("  Sala   105 ")).isEqualTo("Sala 105");
    assertThat(RoomNames.display("Laboratório\t 1")).isEqualTo("Laboratório 1");
  }

  @Test
  void blankBecomesNull() {
    assertThat(RoomNames.display("   ")).isNull();
    assertThat(RoomNames.display(null)).isNull();
    assertThat(RoomNames.normalize("")).isNull();
    assertThat(RoomNames.normalize(null)).isNull();
  }

  @Test
  void normalizeIgnoresCaseAccentsAndSpaces() {
    assertThat(RoomNames.normalize(" SALA   105 ")).isEqualTo("sala 105");
    assertThat(RoomNames.normalize("Laboratório 1")).isEqualTo("laboratorio 1");
    assertThat(RoomNames.normalize("LABORATÓRIO  1"))
        .isEqualTo(RoomNames.normalize("laboratorio 1"));
    assertThat(RoomNames.normalize("Coordenação Pedagógica ÃÕÇ"))
        .isEqualTo("coordenacao pedagogica aoc");
  }

  @Test
  void differentNamesStayDifferent() {
    assertThat(RoomNames.normalize("Sala 105")).isNotEqualTo(RoomNames.normalize("Sala 106"));
  }
}
