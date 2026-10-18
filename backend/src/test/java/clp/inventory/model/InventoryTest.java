package clp.inventory.model;

import static org.assertj.core.api.Assertions.*;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class InventoryTest {
  private final Inventory inventory = new Inventory();
  private final LocalDateTime completedAt = LocalDateTime.of(2026, 10, 13, 14, 30);

  @Test
  void startsInProgressWithoutCompletionDate() {
    assertThat(inventory.status()).isEqualTo(Inventory.Status.EM_ANDAMENTO);
    assertThat(inventory.completedAt()).isNull();
  }

  @Test
  void finishesAndRecordsDate() {
    inventory.finish(completedAt);
    assertThat(inventory.status()).isEqualTo(Inventory.Status.CONCLUIDO);
    assertThat(inventory.completedAt()).isEqualTo(completedAt);
  }

  @Test
  void cannotFinishTwiceOrOverwriteOriginalDate() {
    inventory.finish(completedAt);
    assertThatIllegalStateException().isThrownBy(() -> inventory.finish(completedAt.plusDays(1)));
    assertThat(inventory.completedAt()).isEqualTo(completedAt);
  }

  @Test
  void rejectsMissingDateWithoutChangingState() {
    assertThatIllegalArgumentException().isThrownBy(() -> inventory.finish(null));
    assertThat(inventory.status()).isEqualTo(Inventory.Status.EM_ANDAMENTO);
    assertThat(inventory.completedAt()).isNull();
  }

  @Test
  void rejectsDateBeforeCreation() {
    ReflectionTestUtils.setField(inventory, "createdAt", completedAt);
    assertThatIllegalArgumentException()
        .isThrownBy(() -> inventory.finish(completedAt.minusSeconds(1)));
    assertThat(inventory.completedAt()).isNull();
    assertThat(inventory.status()).isEqualTo(Inventory.Status.EM_ANDAMENTO);
  }
}
