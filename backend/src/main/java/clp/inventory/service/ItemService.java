package clp.inventory.service;

import clp.inventory.dto.ItemDto;
import clp.inventory.dto.ObservationDto;
import clp.inventory.model.Inventory;
import clp.inventory.model.Item;
import clp.inventory.model.Observation;
import clp.inventory.model.Sector;
import clp.inventory.model.User;
import clp.inventory.repository.InventoryRepository;
import clp.inventory.repository.ItemRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class ItemService {

    private final ItemRepository itemRepository;
    private final UserService userService;
    private final ItemAuditService itemAuditService;

    public ItemService(ItemRepository itemRepository, InventoryRepository inventoryRepository,
                        UserService userService, ItemAuditService itemAuditService) {
        this.itemRepository = itemRepository;
        this.userService = userService;
        this.itemAuditService = itemAuditService;
    }

    public Page<Item> listInventoryItems(long inventoryId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("code"));
        return itemRepository.findByInventory_Id(inventoryId, pageable);
    }

    public Optional<Item> findById(Long id) {
        return itemRepository.findById(id);
    }

    public Item updateItem(ItemDto updatedItem) {
        Item existingItem = itemRepository.findById(updatedItem.id())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Item não encontrado com o ID: " + updatedItem.id()));

        // O DTO traz o valor em reais; a entidade persiste em centavos.
        long priceParseLong = updatedItem.price() != null && !updatedItem.price().isEmpty()
                ? new BigDecimal(updatedItem.price()).multiply(BigDecimal.valueOf(100)).longValue()
                : 0L;

        existingItem.setCode(updatedItem.code());
        existingItem.setName("");
        existingItem.setDescription(updatedItem.description());
        existingItem.setPrice(priceParseLong);
        existingItem.setLocale(updatedItem.locale());
        existingItem.setResponsible(updatedItem.responsible());
        existingItem.setValid(updatedItem.isValid());

        return itemRepository.save(existingItem);
    }

    @Transactional
    public Item validateItem(long itemId, long userId) {
        Item item = itemRepository.findById(itemId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Item não encontrado com o ID: " + itemId));
        User actor = userService.findUserById(userId);

        Sector actorSector = actor.getSector();
        Sector targetSector = item.inventory() != null ? item.inventory().sector() : null;

        boolean crossSector = actorSector != null && targetSector != null && actorSector.id() != targetSector.id();
        String eventType = crossSector ? ItemAuditService.EVENT_PARTIAL_VALIDATION : ItemAuditService.EVENT_VALIDATION;
        String status = crossSector ? ItemAuditService.STATUS_PENDING_TARGET_SECTOR : ItemAuditService.STATUS_VALIDATED;

        item.setValid(true);
        item.setValidatedAt(LocalDateTime.now());
        itemRepository.save(item);

        itemAuditService.record(item, item.inventory(), actor, actorSector, targetSector, eventType, status, null);

        return item;
    }

    public Item updateItemNotes(Long itemId, List<ObservationDto> newNotesStrings) {
        Item existingItem = itemRepository.findById(itemId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Item não encontrado com o ID: " + itemId));

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
