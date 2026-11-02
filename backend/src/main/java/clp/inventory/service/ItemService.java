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
import java.math.BigDecimal;
import java.time.LocalDateTime;
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

  public ItemService(
      ItemRepository itemRepository,
      InventoryRepository inventoryRepository,
      AuthorizationService authorizationService,
      UserService userService,
      ItemAuditService itemAuditService,
      RoomService roomService) {
    this.itemRepository = itemRepository;
    this.inventoryRepository = inventoryRepository;
    this.authorizationService = authorizationService;
    this.userService = userService;
    this.itemAuditService = itemAuditService;
    this.roomService = roomService;
  }

  /** Leitura de um item: quem enxerga o inventário a que ele pertence. */
  @Transactional(readOnly = true)
  public Item findAccessibleItem(long itemId, long userId) {
    Item item = requireItem(itemId);
    authorizationService.requireViewInventory(userId, inventoryOf(item));
    return item;
  }

  /** Leitura de um inventário (exportar planilha, etiquetas, enviar por e-mail). */
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
    if (item.isValid() && sameFoundRoom(item, roomId)) {
      return item;
    }
    applyValidation(item, inventory, userId, roomId);
    return itemRepository.save(item);
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

    item.setValid(true);
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

  @Transactional
  public Item updateItem(ItemDto updatedItem, long userId) {
    Item existingItem = requireItem(updatedItem.id());
    Inventory inventory = inventoryOf(existingItem);
    authorizationService.requireOperateInventory(userId, inventory);
    boolean validationChanged = updatedItem.isValid() != existingItem.isValid();
    if (inventory.status() == Inventory.Status.CONCLUIDO && validationChanged) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, "Inventário finalizado: não é possível validar itens.");
    }

    // O DTO traz o valor em reais; a entidade persiste em centavos.
    // Trata moedas formatadas ("R$ 1.500,00"), decimais ou valores numéricos simples
    long priceParseLong = existingItem.price();
    if (updatedItem.price() != null && !updatedItem.price().trim().isEmpty()) {
      try {
        String cleanPrice = updatedItem.price().replaceAll("[^0-9,.]", "").replace(",", ".");
        if (!cleanPrice.isEmpty()) {
          priceParseLong = new BigDecimal(cleanPrice).multiply(BigDecimal.valueOf(100)).longValue();
        }
      } catch (Exception e) {
        priceParseLong = existingItem.price();
      }
    }

    // A sala cadastrada muda por roomId (clientes novos) ou pelo texto de locale (clientes
    // antigos).
    Long currentRoomId = existingItem.room() != null ? existingItem.room().id() : null;
    boolean roomChanged =
        updatedItem.roomId() != null
            ? !Objects.equals(currentRoomId, updatedItem.roomId())
            : !Objects.equals(existingItem.locale(), updatedItem.locale());

    if (!authorizationService.canManageInventory(userId, inventory)
        && !(Objects.equals(existingItem.code(), updatedItem.code())
            && Objects.equals(existingItem.description(), updatedItem.description())
            && !roomChanged
            && Objects.equals(existingItem.responsible(), updatedItem.responsible())
            && priceParseLong == existingItem.price())) {
      throw new ResponseStatusException(
          HttpStatus.FORBIDDEN, "Seu perfil só pode validar itens e registrar observações.");
    }

    existingItem.setCode(updatedItem.code());
    existingItem.setName("");
    existingItem.setDescription(updatedItem.description());
    existingItem.setPrice(priceParseLong);
    if (roomChanged) {
      changeRoom(existingItem, inventory, updatedItem);
    }
    existingItem.setResponsible(updatedItem.responsible());

    if (updatedItem.isValid() && !existingItem.isValid()) {
      // Marcar como validado pelo PUT segue a mesma regra e a mesma auditoria de PATCH /validate.
      applyValidation(existingItem, inventory, userId, updatedItem.foundRoomId());
    } else if (!updatedItem.isValid()) {
      existingItem.setValid(false);
      existingItem.setValidatedAt(null);
      existingItem.setFoundRoom(null);
    }

    return itemRepository.save(existingItem);
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
