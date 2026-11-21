package clp.inventory.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import clp.inventory.model.Inventory;
import clp.inventory.model.Item;
import clp.inventory.model.Sector;
import clp.inventory.model.User;
import clp.inventory.repository.InventoryRepository;
import clp.inventory.repository.ItemRepository;
import clp.inventory.repository.SectorAllocationRepository;
import clp.inventory.repository.SectorRepository;
import clp.inventory.repository.UserRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * A exclusão de item confere o histórico antes de tocar no banco. Se um item ganha histórico entre
 * a conferência e a gravação (uma validação entra no meio), a FK da auditoria recusa e o usuário
 * recebe 409, não um erro interno.
 */
class InventoryItemRemovalTest {
  private final InventoryRepository inventories = mock(InventoryRepository.class);
  private final ItemRepository items = mock(ItemRepository.class);
  private final UserRepository users = mock(UserRepository.class);
  private final InventoryService service =
      new InventoryService(
          inventories,
          items,
          mock(SectorRepository.class),
          mock(UserService.class),
          new AuthorizationService(users, mock(SectorAllocationRepository.class)),
          mock(RoomService.class),
          mock(InventoryAuditService.class));
  private Inventory inventory;
  private Item withHistory;
  private Item withoutHistory;

  @BeforeEach
  void setup() {
    User admin = new User();
    admin.setId(7L);
    admin.setAdministrator(true);
    when(users.findById(7L)).thenReturn(Optional.of(admin));
    Sector sector = mock(Sector.class);
    when(sector.id()).thenReturn(3L);
    inventory = new Inventory("Teste", "", LocalDate.now().plusDays(1), admin, sector);
    withHistory = new Item("A-1", "", "Mesa", 1000, null, "Maria");
    withHistory.setId(1L);
    withoutHistory = new Item("B-1", "", "Cadeira", 2000, null, "Joao");
    withoutHistory.setId(2L);
    inventory.addItem(withHistory);
    inventory.addItem(withoutHistory);
    when(inventories.findById(10L)).thenReturn(Optional.of(inventory));
    when(items.findIdsWithHistory(10L)).thenReturn(List.of(1L));
    when(items.hasHistory(1L)).thenReturn(true);
  }

  @Test
  void removingAnItemWithHistoryIsAConflictAndNothingIsSaved() {
    assertThatThrownBy(() -> service.removeItemFromInventory(10L, 1L, 7L))
        .isInstanceOfSatisfying(
            ResponseStatusException.class,
            error -> {
              assertThat(error.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
              assertThat(error.getReason()).contains("histórico de validação");
            });

    verify(inventories, never()).saveAndFlush(any());
    assertThat(inventory.items()).hasSize(2);
  }

  @Test
  void clearingKeepsTheItemWithHistory() {
    var result = service.clearInventoryItems(10L, 7L);

    assertThat(result.removed()).isEqualTo(1);
    assertThat(result.kept()).isEqualTo(1);
    assertThat(inventory.items()).containsExactly(withHistory);
  }

  @Test
  void aValidationThatLandsDuringTheClearIsReportedAsConflict() {
    when(inventories.saveAndFlush(inventory)).thenThrow(new DataIntegrityViolationException("fk"));

    assertThatThrownBy(() -> service.clearInventoryItems(10L, 7L))
        .isInstanceOfSatisfying(
            ResponseStatusException.class,
            error -> {
              assertThat(error.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
              assertThat(error.getReason()).contains("nada foi removido");
            });
  }

  @Test
  void aValidationThatLandsDuringTheRemovalIsReportedAsConflict() {
    when(inventories.saveAndFlush(inventory)).thenThrow(new DataIntegrityViolationException("fk"));

    assertThatThrownBy(() -> service.removeItemFromInventory(10L, 2L, 7L))
        .isInstanceOfSatisfying(
            ResponseStatusException.class,
            error -> assertThat(error.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
  }
}
