package clp.inventory.service;

import clp.inventory.dto.ItemDto;
import clp.inventory.dto.ObservationDto;
import clp.inventory.model.Inventory;
import clp.inventory.model.Item;
import clp.inventory.model.Observation;
import clp.inventory.model.Room;
import clp.inventory.model.Sector;
import clp.inventory.repository.InventoryRepository;
import clp.inventory.repository.ItemRepository;
import clp.inventory.utils.Money;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ItemService {

  private final ItemRepository itemRepository;
  private final InventoryRepository inventoryRepository;
  private final AuthorizationService authorizationService;
  private final UserService userService;
  private final ItemAuditService itemAuditService;
  private final RoomService roomService;
  private final InventoryAuditService inventoryAuditService;

  // Cabe com folga nos 1000 caracteres da coluna de detalhes da auditoria.
  static final int MAX_NOT_FOUND_REASON = 255;

  public ItemService(
      ItemRepository itemRepository,
      InventoryRepository inventoryRepository,
      AuthorizationService authorizationService,
      UserService userService,
      ItemAuditService itemAuditService,
      RoomService roomService,
      InventoryAuditService inventoryAuditService) {
    this.itemRepository = itemRepository;
    this.inventoryRepository = inventoryRepository;
    this.authorizationService = authorizationService;
    this.userService = userService;
    this.itemAuditService = itemAuditService;
    this.roomService = roomService;
    this.inventoryAuditService = inventoryAuditService;
  }

  /** Leitura de um item: quem enxerga o inventário a que ele pertence. */
  @Transactional(readOnly = true)
  public Item findAccessibleItem(long itemId, long userId) {
    Item item = requireItem(itemId);
    authorizationService.requireViewInventory(userId, inventoryOf(item));
    return item;
  }

  /** Leitura de um inventário (por exemplo, as etiquetas). */
  @Transactional(readOnly = true)
  public void requireInventoryView(long inventoryId, long userId) {
    Inventory inventory =
        inventoryRepository
            .findById(inventoryId)
            .orElseThrow(
                () ->
                    new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Inventário não encontrado."));
    authorizationService.requireViewInventory(userId, inventory);
  }

  /**
   * Exportar a planilha do inventário ou enviá-la por e-mail: administrador ou gestor do setor do
   * inventário. A planilha traz o responsável e o valor de todos os bens, então não basta enxergar
   * o inventário. Devolve o inventário para quem precisa do setor (auditoria).
   */
  @Transactional(readOnly = true)
  public Inventory requireInventoryManage(long inventoryId, long userId) {
    Inventory inventory =
        inventoryRepository
            .findById(inventoryId)
            .orElseThrow(
                () ->
                    new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Inventário não encontrado."));
    authorizationService.requireManageInventory(userId, inventory);
    return inventory;
  }

  /** Leitura de vários itens (etiquetas): todos precisam pertencer a inventários visíveis. */
  @Transactional(readOnly = true)
  public void requireItemsView(List<Long> itemIds, long userId) {
    for (Item item : itemRepository.findAllById(itemIds)) {
      authorizationService.requireViewInventory(userId, inventoryOf(item));
    }
  }

  private Item requireItem(long itemId) {
    return itemRepository
        .findById(itemId)
        .orElseThrow(
            () ->
                new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "Item não encontrado com o ID: " + itemId));
  }

  private Inventory inventoryOf(Item item) {
    if (item.inventory() == null) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Sem acesso a este item.");
    }
    return item.inventory();
  }

  public Page<Item> listInventoryItems(long inventoryId, int page, int size) {
    Pageable pageable = PageRequest.of(page, size, Sort.by("code"));
    return itemRepository.findByInventory_Id(inventoryId, pageable);
  }

  public Optional<Item> findById(Long id) {
    return itemRepository.findById(id);
  }

  /**
   * Valida o item informando a sala em que ele foi encontrado, e registra a auditoria (com a sala
   * encontrada, a cadastrada e se divergem). Exige poder operar o inventário e não é permitido
   * depois da finalização. Em setor com salas cadastradas a sala é obrigatória (400); em setor sem
   * salas valida como antes. Validar de novo um item já validado só gera outro registro se a sala
   * informada for diferente da já registrada.
   */
  @Transactional
  public Item validateItem(long itemId, long userId, Long roomId) {
    Item item = requireItem(itemId);
    Inventory inventory = inventoryOf(item);
    authorizationService.requireOperateInventory(userId, inventory);
    requireOpen(inventory);
    if (item.validationStatus() == Item.ValidationStatus.NAO_LOCALIZADO) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT,
          "Item marcado como não localizado: peça ao gestor para desfazer a marcação antes de"
              + " validar.");
    }
    if (item.isValid() && sameFoundRoom(item, roomId)) {
      return item;
    }
    applyValidation(item, inventory, userId, roomId);
    return itemRepository.save(item);
  }

  /**
   * Marca o item como não localizado, com a justificativa obrigatória. Exige poder operar o
   * inventário, que ele esteja aberto e o item PENDENTE: um item validado ou já marcado só volta a
   * pendente pelo gestor (PATCH /item/{id}/validate com isValid false). Conta como conferido para
   * finalizar o inventário. A justificativa fica na auditoria (MARKED_NOT_FOUND) e vira uma
   * observação do item.
   */
  @Transactional
  public Item markNotFound(long itemId, long userId, String reason) {
    Item item = requireItem(itemId);
    Inventory inventory = inventoryOf(item);
    authorizationService.requireOperateInventory(userId, inventory);
    if (inventory.status() == Inventory.Status.CONCLUIDO) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT,
          "Inventário finalizado: não é possível marcar itens como não localizados.");
    }
    String justification = reason == null ? "" : reason.trim();
    if (justification.isEmpty()) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Informe o motivo de o item não ter sido localizado.");
    }
    if (justification.length() > MAX_NOT_FOUND_REASON) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST,
          "O motivo deve ter no máximo " + MAX_NOT_FOUND_REASON + " caracteres.");
    }
    if (item.validationStatus() != Item.ValidationStatus.PENDENTE) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT,
          "Só um item pendente pode ser marcado como não localizado; este já está "
              + (item.isValid() ? "validado" : "marcado como não localizado")
              + ".");
    }
    item.setValidationStatus(Item.ValidationStatus.NAO_LOCALIZADO);
    item.setValidatedAt(LocalDateTime.now());
    item.setFoundRoom(null);
    item.addObservation(new Observation("Não localizado: " + justification));
    itemRepository.save(item);
    itemAuditService.record(
        item,
        inventory,
        userService.findUserById(userId),
        actorSectorOf(userId, inventory),
        inventory.sector(),
        ItemAuditService.EVENT_MARKED_NOT_FOUND,
        ItemAuditService.STATUS_NOT_FOUND,
        justification,
        null);
    return item;
  }

  /**
   * Desvalida um item validado ou desfaz a marcação de não localizado, voltando-o a PENDENTE. Só
   * administrador ou gestor do setor, e só com o inventário aberto: é o jeito de destravar o item
   * para corrigir os dados dele ou conferi-lo de novo. Grava a auditoria (VALIDATION_REVOKED). 409
   * se o item já está pendente.
   */
  @Transactional
  public Item invalidateItem(long itemId, long userId) {
    Item item = requireItem(itemId);
    Inventory inventory = inventoryOf(item);
    if (!authorizationService.canManageInventory(userId, inventory)) {
      throw new ResponseStatusException(
          HttpStatus.FORBIDDEN, "Somente o admin ou o gestor do setor pode desvalidar um item.");
    }
    if (inventory.status() == Inventory.Status.CONCLUIDO) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT,
          "Inventário finalizado: não é possível desvalidar itens. Reabra o inventário.");
    }
    if (item.validationStatus() == Item.ValidationStatus.PENDENTE) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, "O item não está validado nem marcado como não localizado.");
    }
    revokeValidation(item, inventory, userId);
    return itemRepository.save(item);
  }

  /** Desfaz a validação e grava a auditoria com o que foi desfeito. */
  private void revokeValidation(Item item, Inventory inventory, long userId) {
    String details = revokedValidationDetails(item);
    item.setValidationStatus(Item.ValidationStatus.PENDENTE);
    item.setValidatedAt(null);
    item.setFoundRoom(null);
    itemRepository.save(item);
    itemAuditService.record(
        item,
        inventory,
        userService.findUserById(userId),
        actorSectorOf(userId, inventory),
        inventory.sector(),
        ItemAuditService.EVENT_VALIDATION_REVOKED,
        ItemAuditService.STATUS_REVOKED,
        details,
        null);
  }

  private static String revokedValidationDetails(Item item) {
    StringBuilder details = new StringBuilder();
    if (item.validatedAt() != null) {
      details
          .append(
              item.validationStatus() == Item.ValidationStatus.NAO_LOCALIZADO
                  ? "Marcado como não localizado em "
                  : "Validado em ")
          .append(item.validatedAt());
    }
    if (item.foundRoom() != null) {
      if (details.length() > 0) {
        details.append("; ");
      }
      details.append("sala encontrada: ").append(item.foundRoom().name());
    }
    return details.length() == 0 ? null : details.toString();
  }

  /** O setor do autor é o do inventário quando ele é alocado a ele; administrador fica sem. */
  private Sector actorSectorOf(long userId, Inventory inventory) {
    Sector sector = inventory.sector();
    return sector != null && authorizationService.isAllocatedTo(userId, sector.id())
        ? sector
        : null;
  }

  private static void requireOpen(Inventory inventory) {
    if (inventory.status() == Inventory.Status.CONCLUIDO) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, "Inventário finalizado: não é possível validar itens.");
    }
  }

  /** Sem sala informada num item já validado não há o que registrar de novo. */
  private static boolean sameFoundRoom(Item item, Long roomId) {
    return roomId == null
        || (item.foundRoom() != null && item.foundRoom().id() == roomId.longValue());
  }

  /** Marca o item como validado na sala informada e grava a auditoria. */
  private void applyValidation(Item item, Inventory inventory, long userId, Long roomId) {
    Sector targetSector = inventory.sector();
    Room foundRoom = resolveFoundRoom(inventory, roomId);

    // O setor de quem valida é o do inventário quando a pessoa está alocada a ele; administrador
    // não pertence a um setor, então a validação dele nunca é parcial.
    boolean sameSector =
        targetSector != null && authorizationService.isAllocatedTo(userId, targetSector.id());
    boolean crossSector =
        targetSector != null && !sameSector && !authorizationService.isAdmin(userId);
    Sector actorSector = sameSector ? targetSector : null;
    String eventType =
        crossSector ? ItemAuditService.EVENT_PARTIAL_VALIDATION : ItemAuditService.EVENT_VALIDATION;
    String status =
        crossSector
            ? ItemAuditService.STATUS_PENDING_TARGET_SECTOR
            : ItemAuditService.STATUS_VALIDATED;

    item.setValidationStatus(Item.ValidationStatus.VALIDADO);
    item.setValidatedAt(LocalDateTime.now());
    item.setFoundRoom(foundRoom);
    itemRepository.save(item);
    itemAuditService.record(
        item,
        inventory,
        userService.findUserById(userId),
        actorSector,
        targetSector,
        eventType,
        status,
        null,
        foundRoom);
  }

  /** A sala exigida: obrigatória em setor com salas, e sempre uma sala do setor do inventário. */
  private Room resolveFoundRoom(Inventory inventory, Long roomId) {
    Sector sector = inventory.sector();
    if (roomId != null) {
      if (sector == null) {
        throw new ResponseStatusException(
            HttpStatus.BAD_REQUEST, "O inventário não tem setor: não há salas para informar.");
      }
      return roomService.requireRoomOfSector(roomId, sector.id());
    }
    if (sector != null && roomService.sectorHasRooms(sector.id())) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST,
          "Informe a sala em que o item foi encontrado para validá-lo (roomId).");
    }
    return null;
  }

  /**
   * Atualiza os dados de um item. Código, descrição, responsável, valor e sala só mudam com o
   * inventário aberto e o item não validado (para corrigir um item validado, desvalide antes); e só
   * quem gerencia o inventário altera esses dados. Cada edição grava a auditoria (UPDATE_ITEM) com
   * o antes e o depois. Observações têm endpoint próprio e continuam livres.
   *
   * <p>A mudança de {@code isValid} segue as mesmas regras e a mesma auditoria de PATCH
   * /item/{id}/validate: validar exige poder operar e inventário aberto; desvalidar exige gestão.
   */
  @Transactional
  public Item updateItem(ItemDto updatedItem, long userId) {
    Item existingItem = requireItem(updatedItem.id());
    Inventory inventory = inventoryOf(existingItem);
    authorizationService.requireOperateInventory(userId, inventory);
    boolean validationChanged = updatedItem.isValid() != existingItem.isValid();

    // O DTO traz o valor em reais; a entidade persiste em centavos. A leitura é a mesma do cadastro
    // avulso e da planilha: valor ausente ou malformado é 400, não "mantém o anterior".
    long priceParseLong;
    try {
      priceParseLong = Money.parseToCentavos(updatedItem.price());
    } catch (IllegalArgumentException e) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    // A sala cadastrada muda por roomId (clientes novos) ou pelo texto de locale (clientes
    // antigos).
    Long currentRoomId = existingItem.room() != null ? existingItem.room().id() : null;
    boolean roomChanged =
        updatedItem.roomId() != null
            ? !Objects.equals(currentRoomId, updatedItem.roomId())
            : !Objects.equals(existingItem.locale(), updatedItem.locale());

    boolean dataChanged =
        !Objects.equals(existingItem.code(), updatedItem.code())
            || !Objects.equals(existingItem.description(), updatedItem.description())
            || roomChanged
            || !Objects.equals(existingItem.responsible(), updatedItem.responsible())
            || priceParseLong != existingItem.price();

    if (dataChanged) {
      if (!authorizationService.canManageInventory(userId, inventory)) {
        throw new ResponseStatusException(
            HttpStatus.FORBIDDEN, "Seu perfil só pode validar itens e registrar observações.");
      }
      requireDataEditable(inventory, existingItem);

      ItemSnapshot before = ItemSnapshot.of(existingItem);
      existingItem.setCode(updatedItem.code());
      existingItem.setName("");
      existingItem.setDescription(updatedItem.description());
      existingItem.setPrice(priceParseLong);
      if (roomChanged) {
        changeRoom(existingItem, inventory, updatedItem);
      }
      existingItem.setResponsible(updatedItem.responsible());
      itemRepository.save(existingItem);
      recordItemUpdate(existingItem, inventory, userId, before);
    }

    if (validationChanged) {
      return updatedItem.isValid()
          ? validateItem(existingItem.id(), userId, updatedItem.foundRoomId())
          : invalidateItem(existingItem.id(), userId);
    }
    return existingItem;
  }

  /** Dados do item só mudam com o inventário aberto e o item não validado. */
  private static void requireDataEditable(Inventory inventory, Item item) {
    if (inventory.status() == Inventory.Status.CONCLUIDO) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT,
          "Inventário finalizado: não é possível alterar os dados do item. Reabra o inventário.");
    }
    if (item.validationStatus() != Item.ValidationStatus.PENDENTE) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT,
          item.isValid()
              ? "Item validado: só é possível registrar observações. Desvalide o item para alterar os dados."
              : "Item marcado como não localizado: só é possível registrar observações. Desfaça a marcação para alterar os dados.");
    }
  }

  private void recordItemUpdate(Item item, Inventory inventory, long userId, ItemSnapshot before) {
    Sector actorSector = actorSectorOf(userId, inventory);
    inventoryAuditService.record(
        userService.findUserById(userId),
        actorSector == null ? null : actorSector.id(),
        inventory.id(),
        item.id(),
        InventoryAuditService.ACTION_UPDATE_ITEM,
        null,
        InventoryAuditService.STATUS_SUCCESS,
        before.diffTo(ItemSnapshot.of(item)));
  }

  /** Foto dos campos editáveis do item, para registrar o antes e o depois na auditoria. */
  private record ItemSnapshot(
      String code, String description, String responsible, long price, String room) {

    private static final int VALUE_LIMIT = 100;
    private static final int DETAILS_LIMIT = 1000;

    static ItemSnapshot of(Item item) {
      String room = item.room() != null ? item.room().name() : item.locale();
      return new ItemSnapshot(
          item.code(), item.description(), item.responsible(), item.price(), room);
    }

    /** Só os campos que mudaram, no formato {@code campo: "antes" -> "depois"}. */
    String diffTo(ItemSnapshot after) {
      List<String> changes = new ArrayList<>();
      addText(changes, "code", code, after.code);
      addText(changes, "description", description, after.description);
      addText(changes, "responsible", responsible, after.responsible);
      if (price != after.price) {
        changes.add("price: " + money(price) + " -> " + money(after.price));
      }
      addText(changes, "room", room, after.room);
      String details = String.join("; ", changes);
      return details.length() <= DETAILS_LIMIT
          ? details
          : details.substring(0, DETAILS_LIMIT - 3) + "...";
    }

    private static void addText(List<String> changes, String field, String before, String after) {
      if (!Objects.equals(before, after)) {
        changes.add(field + ": " + quote(before) + " -> " + quote(after));
      }
    }

    private static String quote(String value) {
      if (value == null) {
        return "null";
      }
      String shown =
          value.length() <= VALUE_LIMIT ? value : value.substring(0, VALUE_LIMIT - 3) + "...";
      return "\"" + shown + "\"";
    }

    private static String money(long cents) {
      return BigDecimal.valueOf(cents, 2).toPlainString();
    }
  }

  private void changeRoom(Item item, Inventory inventory, ItemDto updatedItem) {
    Sector sector = inventory.sector();
    Room room;
    if (updatedItem.roomId() != null) {
      room =
          sector == null
              ? null
              : roomService.requireRoomOfSector(updatedItem.roomId(), sector.id());
    } else {
      room = sector == null ? null : roomService.findOrCreate(sector, updatedItem.locale());
    }
    item.setRoom(room);
  }

  @Transactional
  public Item updateItemNotes(Long itemId, List<ObservationDto> newNotesStrings, long userId) {
    Item existingItem = requireItem(itemId);
    authorizationService.requireOperateInventory(userId, inventoryOf(existingItem));

    existingItem.observations().clear();

    if (newNotesStrings != null && !newNotesStrings.isEmpty()) {
      for (ObservationDto noteText : newNotesStrings) {
        if (noteText != null && !noteText.content().trim().isEmpty()) {
          Observation obs = new Observation(noteText.content().trim());
          existingItem.addObservation(obs);
        }
      }
    }

    return itemRepository.save(existingItem);
  }
}
