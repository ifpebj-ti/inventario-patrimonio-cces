package clp.inventory.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import clp.inventory.model.*;
import clp.inventory.repository.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class InventoryCompletionTest {
  private final InventoryRepository inventories = mock(InventoryRepository.class);
  private final ItemRepository items = mock(ItemRepository.class);
  private final UserRepository users = mock(UserRepository.class);
  private final SectorAllocationRepository allocations = mock(SectorAllocationRepository.class);
  private final AuthorizationService authorization = new AuthorizationService(users, allocations);
  private final InventoryService service =
      new InventoryService(
          inventories, items, mock(SectorRepository.class), mock(UserService.class), authorization);
  private User actor;
  private Inventory inventory;

  @BeforeEach
  void setup() {
    actor = new User();
    actor.setId(7L);
    when(users.findById(7L)).thenReturn(Optional.of(actor));
    Sector sector = mock(Sector.class);
    when(sector.id()).thenReturn(3L);
    inventory = new Inventory("Teste", "", LocalDate.now().plusDays(1), actor, sector);
    when(inventories.findByIdForUpdate(10L)).thenReturn(Optional.of(inventory));
    when(inventories.findById(10L)).thenReturn(Optional.of(inventory));
    when(inventories.save(inventory)).thenReturn(inventory);
  }

  private void grant(String permission) {
    Profile profile = new Profile("TEST", "");
    profile.addPermission(new Permission(permission, ""));
    actor.setProfile(profile);
  }

  private void assertStatus(Runnable action, HttpStatus status) {
    assertThatThrownBy(action::run)
        .isInstanceOfSatisfying(
            ResponseStatusException.class,
            error -> assertThat(error.getStatusCode()).isEqualTo(status));
  }

  @Test
  void adminFinishesAndRecordsServerTime() {
    grant("ADMIN");
    LocalDateTime before = LocalDateTime.now();
    assertThat(service.finishInventory(10L, 7L)).isSameAs(inventory);
    assertThat(inventory.status()).isEqualTo(Inventory.Status.CONCLUIDO);
    assertThat(inventory.completedAt()).isBetween(before, LocalDateTime.now());
    verify(inventories).save(inventory);
    verifyNoInteractions(items);
  }

  @Test
  void managerOfInventorySectorCanFinish() {
    grant("MANAGE_SECTOR");
    when(allocations.existsByUser_IdAndSector_Id(7L, 3L)).thenReturn(true);
    service.finishInventory(10L, 7L);
    assertThat(inventory.status()).isEqualTo(Inventory.Status.CONCLUIDO);
  }

  @Test
  void managerOfOtherSectorCannotFinish() {
    grant("MANAGE_SECTOR");
    assertStatus(() -> service.finishInventory(10L, 7L), HttpStatus.FORBIDDEN);
    assertThat(inventory.completedAt()).isNull();
    verify(inventories, never()).save(any());
  }

  @Test
  void ownerWithoutRequiredPermissionCannotFinish() {
    grant("MANAGE_ITEM");
    when(allocations.existsByUser_IdAndSector_Id(7L, 3L)).thenReturn(true);
    assertStatus(() -> service.finishInventory(10L, 7L), HttpStatus.FORBIDDEN);
    verify(inventories, never()).save(any());
  }

  @Test
  void adminCanFinishLegacyInventoryWithoutSector() {
    grant("ADMIN");
    Inventory legacy = new Inventory("Legado", "", null, actor, null);
    when(inventories.findByIdForUpdate(10L)).thenReturn(Optional.of(legacy));
    service.finishInventory(10L, 7L);
    assertThat(legacy.status()).isEqualTo(Inventory.Status.CONCLUIDO);
  }

  @Test
  void managerCannotFinishInventoryWithoutSector() {
    grant("MANAGE_SECTOR");
    Inventory legacy = new Inventory("Legado", "", null, actor, null);
    when(inventories.findByIdForUpdate(10L)).thenReturn(Optional.of(legacy));
    assertStatus(() -> service.finishInventory(10L, 7L), HttpStatus.FORBIDDEN);
  }

  @Test
  void rejectsRepeatedFinalizationAndPreservesDate() {
    grant("ADMIN");
    inventory.finish(LocalDateTime.now().minusMinutes(1));
    LocalDateTime original = inventory.completedAt();
    assertStatus(() -> service.finishInventory(10L, 7L), HttpStatus.CONFLICT);
    assertThat(inventory.completedAt()).isEqualTo(original);
    verify(inventories, never()).save(any());
  }

  @Test
  void missingInventoryReturnsNotFound() {
    when(inventories.findByIdForUpdate(10L)).thenReturn(Optional.empty());
    assertStatus(() -> service.finishInventory(10L, 7L), HttpStatus.NOT_FOUND);
  }

  @Test
  void validatedItemsDoNotAutomaticallyConcludeInventory() {
    when(items.countByInventory_Id(inventory.id())).thenReturn(5L);
    when(items.countByInventory_IdAndIsValidTrue(inventory.id())).thenReturn(5L);
    assertThat(service.getInventoryStatus(inventory)).isEqualTo("EM_ANDAMENTO");
    verifyNoInteractions(items);
  }

  @Test
  void overdueInventoryRemainsInProgressUntilExplicitCompletion() {
    inventory.setPlannedEndDate(LocalDate.now().minusDays(1));
    assertThat(service.getInventoryStatus(inventory)).isEqualTo("VENCIDO");
    assertThat(inventory.status()).isEqualTo(Inventory.Status.EM_ANDAMENTO);
    inventory.finish(LocalDateTime.now());
    assertThat(service.getInventoryStatus(inventory)).isEqualTo("CONCLUIDO");
    assertThat(service.toDto(inventory).completedAt())
        .isEqualTo(inventory.completedAt().toString());
  }

  @Test
  void unrelatedUserCannotReadInventoryDetails() {
    User owner = new User();
    owner.setId(8L);
    inventory = new Inventory("Outro", "", null, owner, null);
    when(inventories.findById(10L)).thenReturn(Optional.of(inventory));
    assertStatus(() -> service.getAccessibleInventory(10L, 7L), HttpStatus.FORBIDDEN);
  }

  @Test
  void adminListIncludesAllInventories() {
    grant("ADMIN");
    when(inventories.findAll()).thenReturn(List.of(inventory));
    assertThat(service.getUserInventories(7L)).containsExactly(inventory);
    verify(inventories, never()).findVisibleToUser(anyLong());
  }

  @Test
  void managerListIncludesOnlyInventoriesOfAllocatedSectors() {
    grant("MANAGE_SECTOR");
    when(inventories.findVisibleToUser(7L)).thenReturn(List.of(inventory));
    assertThat(service.getUserInventories(7L)).containsExactly(inventory);
    verify(inventories, never()).findAll();
  }

  @Test
  void operatorAndConsultListAreRestrictedToAllocatedSectors() {
    grant("MANAGE_ITEM");
    when(inventories.findVisibleToUser(7L)).thenReturn(List.of(inventory));
    assertThat(service.getUserInventories(7L)).containsExactly(inventory);
    verify(inventories, never()).findAll();
  }
}
