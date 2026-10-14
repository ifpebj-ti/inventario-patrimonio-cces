package clp.inventory.service;

import clp.inventory.dto.InventoryDto;
import clp.inventory.dto.ItemProcessingResult;
import clp.inventory.model.Inventory;
import clp.inventory.model.Item;
import clp.inventory.model.Sector;
import clp.inventory.model.User;
import clp.inventory.repository.InventoryRepository;
import clp.inventory.repository.ItemRepository;
import clp.inventory.repository.SectorRepository;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InventoryService {

  private final InventoryRepository inventoryRepository;
  private final ItemRepository itemRepository;
  private final SectorRepository sectorRepository;
  private final UserService userService;

  public InventoryService(
      InventoryRepository inventoryRepository,
      ItemRepository itemRepository,
      SectorRepository sectorRepository,
      UserService userService) {
    this.inventoryRepository = inventoryRepository;
    this.itemRepository = itemRepository;
    this.sectorRepository = sectorRepository;
    this.userService = userService;
  }

  public Inventory createInventory(InventoryDto inventoryDto, long userId) {
    validateInventoryDto(inventoryDto);
    // Um setor é obrigatório na criação para que o inventário possa compor os indicadores por setor.
    if (inventoryDto.sectorId() == null) {
      throw new IllegalArgumentException("Sector must be selected");
    }

    User user = userService.findUserById(userId);
    validateInventoryUniqueness(inventoryDto.name(), userId);

    // O prazo nunca pode ser anterior ao dia de criação do inventário.
    LocalDate plannedEndDate = parsePlannedEndDate(inventoryDto.plannedEndDate());
    validatePlannedEndDate(plannedEndDate, LocalDate.now());
    Sector sector = findSector(inventoryDto.sectorId());

    Inventory inventory =
        new Inventory(
            inventoryDto.name(),
            inventoryDto.description(),
            plannedEndDate,
            user,
            sector);

    return inventoryRepository.save(inventory);
  }

  @Transactional
  public void deleteInventory(long inventoryId, long userId) {
    User user = userService.findUserById(userId);
    Inventory inventory =
        inventoryRepository
            .findById(inventoryId)
            .orElseThrow(() -> new RuntimeException("Invalid inventory"));
    if (!inventory.User().equals(user)) {
      throw new RuntimeException("Invalid user");
    }
    inventoryRepository.deleteById(inventoryId);
  }

  @Transactional
  public Inventory updateInventory(long inventoryId, InventoryDto inventoryDto, long userId) {
    validateInventoryDto(inventoryDto);
    Inventory inventory =
        inventoryRepository
            .findById(inventoryId)
            .orElseThrow(() -> new RuntimeException("Invalid inventory"));
    if (!inventory.User().equals(userService.findUserById(userId))) {
      throw new RuntimeException("Invalid user");
    }
    LocalDate plannedEndDate = parsePlannedEndDate(inventoryDto.plannedEndDate());
    LocalDate createdAt =
        inventory.createdAt() != null ? inventory.createdAt().toLocalDate() : LocalDate.now();
    validatePlannedEndDate(plannedEndDate, createdAt);
    inventory.setName(inventoryDto.name());
    inventory.setDescription(inventoryDto.description());
    inventory.setPlannedEndDate(plannedEndDate);
    return inventoryRepository.save(inventory);
  }

  public ItemProcessingResult addItemToInventory(long inventoryId, Item item, int line) {
    try {
      Inventory inventory =
          inventoryRepository
              .findById(inventoryId)
              .orElseThrow(
                  () -> new RuntimeException("Inventory not found with id: " + inventoryId));

      boolean itemExist =
          inventory.items().stream()
              .anyMatch(existingItem -> existingItem.code().equals(item.code()));

      if (itemExist) {
        return new ItemProcessingResult(
            item.code(), line, false, "Item already exists in inventory");
      }

      inventory.addItem(item);
      inventoryRepository.save(inventory);

      return new ItemProcessingResult(
          item.code(), line, true, "Item added successfully to inventory");
    } catch (Exception e) {
      return new ItemProcessingResult(
          item.code(), line, false, "Error processing item: " + e.getMessage());
    }
  }

  public boolean removeItemFromInventory(long inventoryId, long itemId) {
    Inventory inventory =
        inventoryRepository
            .findById(inventoryId)
            .orElseThrow(() -> new RuntimeException("Inventory not found with id: " + inventoryId));

    Optional<Item> itemToRemove =
        inventory.items().stream().filter(existingItem -> existingItem.id() == itemId).findFirst();

    if (itemToRemove.isEmpty()) {
      return false;
    }

    inventory.removeItem(itemToRemove.get());
    inventoryRepository.save(inventory);

    return true;
  }

  @Transactional
  public boolean clearInventoryItems(long inventoryId) {
    Inventory inventory =
        inventoryRepository
            .findById(inventoryId)
            .orElseThrow(() -> new RuntimeException("Inventory not found with id: " + inventoryId));

    inventory.items().clear();
    inventoryRepository.save(inventory);

    return true;
  }

  public List<Inventory> getUserInventories(long userId) {
    return inventoryRepository.findByUser_Id(userId);
  }

  // Status calculado a partir da validação dos itens e do prazo previsto de finalização.
  public String getInventoryStatus(Inventory inventory) {
    long totalItems = itemRepository.countByInventory_Id(inventory.id());
    long validatedItems = itemRepository.countByInventory_IdAndIsValidTrue(inventory.id());

    if (totalItems > 0 && totalItems == validatedItems) {
      return "CONCLUIDO";
    }
    if (inventory.plannedEndDate() != null
        && inventory.plannedEndDate().isBefore(LocalDate.now())) {
      return "VENCIDO";
    }
    return "EM_ANDAMENTO";
  }

  // Inclui a contagem real de itens para evitar que o frontend precise carregar cada inventário individualmente.
  public InventoryDto toDto(Inventory inventory) {
    return InventoryDto.from(
        inventory,
        getInventoryStatus(inventory),
        itemRepository.countByInventory_Id(inventory.id()));
  }

  private void validateInventoryDto(InventoryDto inventoryDto) {
    if (inventoryDto.name() == null || inventoryDto.name().trim().isEmpty()) {
      throw new IllegalArgumentException("Inventory name cannot be null or empty");
    }
    if (inventoryDto.plannedEndDate() == null || inventoryDto.plannedEndDate().isBlank()) {
      throw new IllegalArgumentException("Planned end date cannot be null or empty");
    }
    parsePlannedEndDate(inventoryDto.plannedEndDate());
  }

  // Garante que a associação gravada no inventário referencie um setor existente.
  private Sector findSector(Long sectorId) {
    return sectorRepository
        .findById(sectorId)
        .orElseThrow(() -> new IllegalArgumentException("Sector not found"));
  }

  private LocalDate parsePlannedEndDate(String plannedEndDate) {
    try {
      return LocalDate.parse(plannedEndDate);
    } catch (DateTimeParseException e) {
      throw new IllegalArgumentException("Planned end date must use the yyyy-MM-dd format");
    }
  }

  // Impede prazos retroativos em relação à data de criação.
  private void validatePlannedEndDate(LocalDate plannedEndDate, LocalDate createdAt) {
    if (plannedEndDate.isBefore(createdAt)) {
      throw new IllegalArgumentException(
          "Planned end date cannot be earlier than the inventory creation date");
    }
  }

  private void validateInventoryUniqueness(String inventoryName, long userId) {
    if (inventoryRepository.existsByNameAndUser_Id(inventoryName, userId)) {
      throw new RuntimeException(
          "Inventory with name '" + inventoryName + "' already exists for this user");
    }
  }

  public void addSingleItemToInventory(long inventoryId, Item item) {
    try {
      Inventory inventory =
          inventoryRepository
              .findById(inventoryId)
              .orElseThrow(
                  () -> new RuntimeException("Inventory not found with id: " + inventoryId));

      inventory.addItem(item);
      inventoryRepository.save(inventory);
    } catch (Exception e) {
      throw new RuntimeException("Error adding item: " + e.getMessage());
    }
  }
}
