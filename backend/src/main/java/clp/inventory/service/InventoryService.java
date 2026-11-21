package clp.inventory.service;

import clp.inventory.dto.InventoryDto;
import clp.inventory.dto.NewItemRequest;
import clp.inventory.dto.RoomsPreviewDto;
import clp.inventory.model.Inventory;
import clp.inventory.model.Item;
import clp.inventory.model.Room;
import clp.inventory.model.Sector;
import clp.inventory.model.User;
import clp.inventory.repository.InventoryRepository;
import clp.inventory.repository.ItemRepository;
import clp.inventory.repository.SectorRepository;
import clp.inventory.utils.RoomNames;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.dao.DataIntegrityViolationException;
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
  private final RoomService roomService;
  private final InventoryAuditService inventoryAuditService;

  public InventoryService(
      InventoryRepository inventoryRepository,
      ItemRepository itemRepository,
      SectorRepository sectorRepository,
      UserService userService,
      AuthorizationService authorizationService,
      RoomService roomService,
      InventoryAuditService inventoryAuditService) {
    this.inventoryRepository = inventoryRepository;
    this.itemRepository = itemRepository;
    this.sectorRepository = sectorRepository;
    this.userService = userService;
    this.authorizationService = authorizationService;
    this.roomService = roomService;
    this.inventoryAuditService = inventoryAuditService;
  }

  @Transactional
  public Inventory finishInventory(long inventoryId, long userId) {
    Inventory inventory =
        inventoryRepository
            .findById(inventoryId)
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
    requireAllItemsChecked(inventory);
    inventory.finish(LocalDateTime.now());
    return inventoryRepository.save(inventory);
  }

  /**
   * Só se finaliza um inventário com itens e sem nenhum PENDENTE: todo item foi validado ou marcado
   * como não localizado. Vale para o administrador também; um bem perdido não trava o fechamento,
   * porque marcá-lo como não localizado (com justificativa) já o conta como conferido.
   */
  private void requireAllItemsChecked(Inventory inventory) {
    long total = itemRepository.countByInventory_Id(inventory.id());
    if (total == 0) {
      throw new InventoryNotReadyException(
          "O inventário não tem itens: importe ou cadastre os itens antes de finalizar.", 0);
    }
    long pending =
        itemRepository.countByInventory_IdAndValidationStatus(
            inventory.id(), Item.ValidationStatus.PENDENTE);
    if (pending > 0) {
      throw new InventoryNotReadyException(
          (pending == 1 ? "1 item ainda está pendente" : pending + " itens ainda estão pendentes")
              + ": valide ou marque como não localizado antes de finalizar o inventário.",
          pending);
    }
  }

  /**
   * Reabre um inventário finalizado (volta para em andamento) para que o gestor corrija o que for
   * preciso. É o único caminho de correção depois do fechamento, e o ato fica na trilha de
   * auditoria. Mesma permissão de finalizar: administrador ou gestor do setor.
   */
  @Transactional
  public Inventory reopenInventory(long inventoryId, long userId) {
    Inventory inventory =
        inventoryRepository
            .findById(inventoryId)
            .orElseThrow(
                () ->
                    new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Inventário não encontrado."));
    if (!canFinalize(inventory, userId)) {
      throw new ResponseStatusException(
          HttpStatus.FORBIDDEN,
          "Somente o admin ou o responsável pelo setor pode reabrir o inventário.");
    }
    if (inventory.status() != Inventory.Status.CONCLUIDO) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "O inventário não está finalizado.");
    }
    LocalDateTime completedAt = inventory.completedAt();
    inventory.reopen();
    Inventory saved = inventoryRepository.save(inventory);

    Long sectorId = inventory.sector() == null ? null : inventory.sector().id();
    Long actorSectorId =
        sectorId != null && authorizationService.isAllocatedTo(userId, sectorId) ? sectorId : null;
    inventoryAuditService.record(
        userService.findUserById(userId),
        actorSectorId,
        inventory.id(),
        InventoryAuditService.ACTION_REOPEN_INVENTORY,
        null,
        InventoryAuditService.STATUS_SUCCESS,
        completedAt == null ? null : "Finalizado em " + completedAt);
    return saved;
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

  @Transactional
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

    // saveAndFlush: o INSERT sai aqui, dentro do método, e não só no commit. Se outra requisição
    // criou o mesmo nome entre a conferência e a gravação, o índice único recusa e vira 400.
    try {
      return inventoryRepository.saveAndFlush(inventory);
    } catch (DataIntegrityViolationException e) {
      throw duplicateName(inventoryDto.name());
    }
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
    // O nome é único por responsável: o responsável é quem criou, não quem está editando.
    if (!inventoryDto.name().equals(inventory.name())
        && inventoryRepository.existsByNameAndUser_IdAndIdNot(
            inventoryDto.name(), inventory.User().getId(), inventoryId)) {
      throw duplicateName(inventoryDto.name());
    }
    inventory.setName(inventoryDto.name());
    inventory.setDescription(inventoryDto.description());
    inventory.setPlannedEndDate(plannedEndDate);
    try {
      return inventoryRepository.saveAndFlush(inventory);
    } catch (DataIntegrityViolationException e) {
      throw duplicateName(inventoryDto.name());
    }
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
    authorizationService.requireAdminOrSectorManager(userId);
  }

  /**
   * Conferência antecipada da importação, sem lock: devolve 403, 404 ou 409 antes de ler o arquivo.
   * Só existe uma importação por inventário: para importar outra, limpe os itens antes. A
   * conferência que vale é a de {@link #importItems}, feita com o inventário travado.
   */
  @Transactional(readOnly = true)
  public void requireImportAllowed(long inventoryId, long userId) {
    checkImportAllowed(findInventoryOrNotFound(inventoryId), userId);
  }

  private void checkImportAllowed(Inventory inventory, long userId) {
    authorizationService.requireManageInventory(userId, inventory);
    if (inventory.status() == Inventory.Status.CONCLUIDO) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, "Inventário finalizado: não é possível alterar os itens.");
    }
    if (itemRepository.countByInventory_Id(inventory.id()) > 0) {
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

  /** Resultado de uma importação concluída: itens gravados e salas novas criadas no setor. */
  public record ImportResult(int imported, long createdRooms) {}

  /**
   * Importa a planilha inteira ou nada. Roda numa transação só e trava o inventário (SELECT ... FOR
   * UPDATE) antes de conferir: duas importações do mesmo inventário não se sobrepõem, e a segunda
   * encontra os itens da primeira e recebe 409. Qualquer falha desfaz tudo, inclusive as salas
   * criadas, em vez de deixar o inventário meio importado. A planilha já deve ter sido validada
   * (códigos, valores, repetições); o índice único (inventário, código) é a última barreira.
   */
  @Transactional
  public ImportResult importItems(long inventoryId, List<Item> items, long userId) {
    Inventory inventory =
        inventoryRepository
            .findByIdForUpdate(inventoryId)
            .orElseThrow(
                () ->
                    new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Inventário não encontrado."));
    checkImportAllowed(inventory, userId);

    long roomsBefore = countRoomsOf(inventory);
    for (Item item : items) {
      attachRoom(inventory, item);
      inventory.addItem(item);
    }
    try {
      inventoryRepository.saveAndFlush(inventory);
    } catch (DataIntegrityViolationException e) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT,
          "Os itens do inventário mudaram durante a importação; nada foi importado. Tente de novo.");
    }
    return new ImportResult(items.size(), countRoomsOf(inventory) - roomsBefore);
  }

  private long countRoomsOf(Inventory inventory) {
    return inventory.sector() == null ? 0 : roomService.countRooms(inventory.sector().id());
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
    return inventoryRepository.findVisibleToUser(userId, AuthorizationService.VIEW_PERMISSIONS);
  }

  /** Inventários de um setor; exige poder consultar o setor. */
  @Transactional(readOnly = true)
  public List<Inventory> getSectorInventories(long sectorId, long userId) {
    authorizationService.requireViewSector(userId, sectorId);
    return inventoryRepository.findBySector_Id(sectorId);
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
      throw duplicateName(inventoryName);
    }
  }

  private IllegalArgumentException duplicateName(String inventoryName) {
    return new IllegalArgumentException(
        "Inventory with name '" + inventoryName + "' already exists for this user");
  }

  /**
   * Cria um item novo no inventário. A entrada é um DTO (nunca a entidade): o item nasce sem id,
   * então não há como atualizar um item existente, de outro inventário ou de outro setor.
   */
  @Transactional
  public Item addSingleItemToInventory(long inventoryId, NewItemRequest request, long userId) {
    Inventory inventory = requireItemStructureChange(inventoryId, userId);
    try {
      if (request.code() == null || request.code().isBlank()) {
        throw new IllegalArgumentException("Item code cannot be null or empty");
      }
      Item item = request.toNewItem();
      if (itemRepository.existsByInventory_IdAndCode(inventoryId, item.code())) {
        throw new IllegalArgumentException(duplicateCodeMessage(item.code()));
      }
      attachRoom(inventory, item);
      inventory.addItem(item);
      inventoryRepository.saveAndFlush(inventory);
      return item;
    } catch (DataIntegrityViolationException e) {
      throw new RuntimeException("Error adding item: " + duplicateCodeMessage(request.code()));
    } catch (Exception e) {
      throw new RuntimeException("Error adding item: " + e.getMessage());
    }
  }

  private static String duplicateCodeMessage(String code) {
    return "Já existe um item com o código '" + code + "' neste inventário.";
  }

  /**
   * A sala do item vem como texto (coluna "Sala" da planilha ou campo locale do cadastro avulso):
   * acha a sala do setor do inventário pelo nome, sem diferenciar caixa, acento e espaços, ou cria.
   */
  private void attachRoom(Inventory inventory, Item item) {
    if (inventory.sector() == null) {
      return;
    }
    Room room = roomService.findOrCreate(inventory.sector(), item.locale());
    if (room != null) {
      item.setRoom(room);
    }
  }

  /** Quantas salas o setor do inventário tem hoje (para informar quantas a importação criou). */
  @Transactional(readOnly = true)
  public long roomCountOf(long inventoryId) {
    return countRoomsOf(findInventoryOrNotFound(inventoryId));
  }

  /**
   * Pré-validação da planilha: separa as salas citadas em "novas" (serão criadas) e "existentes" no
   * setor do inventário, sem criar nada. Nomes que só diferem em caixa, acento ou espaços contam
   * como uma sala só. Exige poder alterar os itens do inventário.
   */
  @Transactional(readOnly = true)
  public RoomsPreviewDto previewRooms(long inventoryId, List<Item> items, long userId) {
    Inventory inventory = requireItemStructureChange(inventoryId, userId);
    if (inventory.sector() == null) {
      return new RoomsPreviewDto(List.of(), List.of());
    }
    Set<String> known = new HashSet<>();
    for (Room room : roomService.roomsOf(inventory.sector().id())) {
      known.add(room.normalizedName());
    }
    Map<String, String> cited = new LinkedHashMap<>();
    for (Item item : items) {
      String display = RoomNames.display(item.locale());
      if (display != null) {
        cited.putIfAbsent(RoomNames.normalize(display), display);
      }
    }
    List<String> newRooms = new ArrayList<>();
    List<String> existingRooms = new ArrayList<>();
    cited.forEach(
        (normalized, display) ->
            (known.contains(normalized) ? existingRooms : newRooms).add(display));
    return new RoomsPreviewDto(newRooms, existingRooms);
  }
}
