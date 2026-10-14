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
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class InventoryService {

  private final InventoryRepository inventoryRepository;
  private final ItemRepository itemRepository;
  private final SectorRepository sectorRepository;
  private final UserService userService;
  private final AuthorizationService authorizationService;

  public InventoryService(
      InventoryRepository inventoryRepository,
      ItemRepository itemRepository,
      SectorRepository sectorRepository,
      UserService userService,
      AuthorizationService authorizationService) {
    this.inventoryRepository = inventoryRepository;
    this.itemRepository = itemRepository;
    this.sectorRepository = sectorRepository;
    this.userService = userService;
    this.authorizationService = authorizationService;
  }

  @Transactional
  public Inventory finishInventory(long inventoryId, long userId) {
    Inventory inventory =
        inventoryRepository
            .findByIdForUpdate(inventoryId)
            .orElseThrow(
                () ->
                    new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Inventário não encontrado."));
    if (!canFinalize(inventory, userId)) {
      throw new ResponseStatusException(
          HttpStatus.FORBIDDEN,
          "Somente o admin ou o responsável pelo setor pode finalizar o inventário.");
    }
    if (inventory.status() == Inventory.Status.CONCLUIDO) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "O inventário já foi finalizado.");
    }
    inventory.finish(LocalDateTime.now());
    return inventoryRepository.save(inventory);
  }

  public boolean canFinalize(Inventory inventory, long userId) {
    return authorizationService.canManageSector(
        userId, inventory.sector() == null ? null : inventory.sector().id());
  }

  @Transactional(readOnly = true)
  public Inventory getAccessibleInventory(long inventoryId, long userId) {
    Inventory inventory =
        inventoryRepository
            .findById(inventoryId)
            .orElseThrow(
                () ->
                    new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Inventário não encontrado."));
    authorizationService.requireViewInventory(userId, inventory);
    return inventory;
  }

  public Inventory createInventory(InventoryDto inventoryDto, long userId) {
    validateInventoryDto(inventoryDto);
    // Um setor é obrigatório na criação para que o inventário possa compor os indicadores por
    // setor.
    if (inventoryDto.sectorId() == null) {
      throw new IllegalArgumentException("Sector must be selected");
    }

    User user = userService.findUserById(userId);
    validateInventoryUniqueness(inventoryDto.name(), userId);

    // O prazo nunca pode ser anterior ao dia de criação do inventário.
    LocalDate plannedEndDate = parsePlannedEndDate(inventoryDto.plannedEndDate());
    validatePlannedEndDate(plannedEndDate, LocalDate.now());
    Sector sector = findSector(inventoryDto.sectorId());
    authorizationService.requireSectorPermission(userId, sector.id());

    Inventory inventory =
        new Inventory(
            inventoryDto.name(), inventoryDto.description(), plannedEndDate, user, sector);

    return inventoryRepository.save(inventory);
  }

  @Transactional
  public void deleteInventory(long inventoryId, long userId) {
    Inventory inventory =
        inventoryRepository
            .findById(inventoryId)
            .orElseThrow(
                () ->
                    new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Inventário não encontrado."));
    authorizationService.requireManageInventory(userId, inventory);
    inventoryRepository.deleteById(inventoryId);
  }

  @Transactional
  public Inventory updateInventory(long inventoryId, InventoryDto inventoryDto, long userId) {
    validateInventoryDto(inventoryDto);
    Inventory inventory =
        inventoryRepository
            .findById(inventoryId)
            .orElseThrow(() -> new RuntimeException("Invalid inventory"));
    if (!canFinalize(inventory, userId)) {
      throw new ResponseStatusException(
          HttpStatus.FORBIDDEN, "Sem permissão para editar este inventário.");
    }
    LocalDate plannedEndDate = parsePlannedEndDate(inventoryDto.plannedEndDate());
    LocalDate createdAt =
        inventory.createdAt() != null ? inventory.createdAt().toLocalDate() : LocalDate.now();
    validatePlannedEndDate(plannedEndDate, createdAt);
    if (inventoryDto.sectorId() != null
        && (inventory.sector() == null || inventory.sector().id() != inventoryDto.sectorId())) {
      Sector sector = findSector(inventoryDto.sectorId());
      authorizationService.requireSectorPermission(userId, sector.id());
      if (!sector.active()) {
        throw new IllegalArgumentException("Selecione um setor ativo.");
      }
      inventory.setSector(sector);
    }
    inventory.setName(inventoryDto.name());
    inventory.setDescription(inventoryDto.description());
    inventory.setPlannedEndDate(plannedEndDate);
    return inventoryRepository.save(inventory);
  }

  /** Leitura de um inventário (e dos seus itens): admin ou alocado ao setor. */
  @Transactional(readOnly = true)
  public Inventory requireInventoryView(long inventoryId, long userId) {
    Inventory inventory = findInventoryOrNotFound(inventoryId);
    authorizationService.requireViewInventory(userId, inventory);
    return inventory;
  }

  /**
   * Importar, incluir, remover ou limpar itens: só admin ou gestor do setor, e somente enquanto o
   * inventário não foi finalizado.
   */
  @Transactional(readOnly = true)
  public Inventory requireItemStructureChange(long inventoryId, long userId) {
    Inventory inventory = findInventoryOrNotFound(inventoryId);
    authorizationService.requireManageInventory(userId, inventory);
    if (inventory.status() == Inventory.Status.CONCLUIDO) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, "Inventário finalizado: não é possível alterar os itens.");
    }
    return inventory;
  }

  /** Validar uma planilha exige poder importar: admin ou gestor de algum setor. */
  public void requireCanImport(long userId) {
    authorizationService.requireAnyPermission(
        userId, AuthorizationService.ADMIN, AuthorizationService.MANAGE_SECTOR);
  }

  /** Só existe uma importação por inventário: para importar outra, limpe os itens antes. */
  @Transactional(readOnly = true)
  public void requireImportAllowed(long inventoryId, long userId) {
    requireItemStructureChange(inventoryId, userId);
    if (itemRepository.countByInventory_Id(inventoryId) > 0) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT,
          "Este inventário já tem itens importados. Para importar outra planilha, exclua primeiro"
              + " todos os itens do inventário.");
    }
  }

  private Inventory findInventoryOrNotFound(long inventoryId) {
    return inventoryRepository
        .findById(inventoryId)
        .orElseThrow(
            () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Inventário não encontrado."));
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

  public boolean removeItemFromInventory(long inventoryId, long itemId, long userId) {
    Inventory inventory = requireItemStructureChange(inventoryId, userId);

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
  public boolean clearInventoryItems(long inventoryId, long userId) {
    Inventory inventory = requireItemStructureChange(inventoryId, userId);

    inventory.items().clear();
    inventoryRepository.save(inventory);

    return true;
  }

  @Transactional(readOnly = true)
  public List<Inventory> getUserInventories(long userId) {
    if (authorizationService.isAdmin(userId)) {
      return inventoryRepository.findAll();
    }
    return inventoryRepository.findVisibleToUser(userId);
  }

  // A conclusão é explícita; o atraso é um indicador derivado do prazo.
  public String getInventoryStatus(Inventory inventory) {
    if (inventory.status() == Inventory.Status.CONCLUIDO) {
      return "CONCLUIDO";
    }
    if (inventory.plannedEndDate() != null
        && inventory.plannedEndDate().isBefore(LocalDate.now())) {
      return "VENCIDO";
    }
    return "EM_ANDAMENTO";
  }

  // Inclui a contagem real de itens para evitar que o frontend precise carregar cada inventário
  // individualmente.
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

  public void addSingleItemToInventory(long inventoryId, Item item, long userId) {
    Inventory inventory = requireItemStructureChange(inventoryId, userId);
    try {
      inventory.addItem(item);
      inventoryRepository.save(inventory);
    } catch (Exception e) {
      throw new RuntimeException("Error adding item: " + e.getMessage());
    }
  }
}
