package clp.inventory.service;

import clp.inventory.dto.ItemDto;
import clp.inventory.dto.ObservationDto;
import clp.inventory.model.Item;
import clp.inventory.model.Observation;
import clp.inventory.repository.InventoryRepository;
import clp.inventory.repository.ItemRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ItemService {

  private final ItemRepository itemRepository;

  public ItemService(ItemRepository itemRepository, InventoryRepository inventoryRepository) {
    this.itemRepository = itemRepository;
  }

  public Page<Item> listInventoryItems(long inventoryId, int page, int size) {
    Pageable pageable = PageRequest.of(page, size, Sort.by("code"));
    return itemRepository.findByInventory_Id(inventoryId, pageable);
  }

  public Optional<Item> findById(Long id) {
    return itemRepository.findById(id);
  }

  public Item updateItem(ItemDto updatedItem) {
    Item existingItem =
        itemRepository
            .findById(updatedItem.id())
            .orElseThrow(
                () ->
                    new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Item não encontrado com o ID: " + updatedItem.id()));

    // O DTO traz o valor em reais; a entidade persiste em centavos.
    // Trata moedas formatadas ("R$ 1.500,00"), decimais ou valores numéricos simples
    long priceParseLong = existingItem.price();
    if (updatedItem.price() != null && !updatedItem.price().trim().isEmpty()) {
      try {
        String cleanPrice = updatedItem.price().replaceAll("[^0-9,.]", "").replace(",", ".");
        if (!cleanPrice.isEmpty()) {
          priceParseLong =
              new BigDecimal(cleanPrice).multiply(BigDecimal.valueOf(100)).longValue();
        }
      } catch (Exception e) {
        priceParseLong = existingItem.price();
      }
    }

    existingItem.setCode(updatedItem.code());
    existingItem.setName("");
    existingItem.setDescription(updatedItem.description());
    existingItem.setPrice(priceParseLong);
    existingItem.setLocale(updatedItem.locale());
    existingItem.setResponsible(updatedItem.responsible());
    existingItem.setValid(updatedItem.isValid());
    if (updatedItem.isValid()) {
      if (existingItem.validatedAt() == null) {
        existingItem.setValidatedAt(LocalDateTime.now());
      }
    } else {
      existingItem.setValidatedAt(null);
    }

    return itemRepository.save(existingItem);
  }

  public Item updateItemNotes(Long itemId, List<ObservationDto> newNotesStrings) {
    Item existingItem =
        itemRepository
            .findById(itemId)
            .orElseThrow(
                () ->
                    new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Item não encontrado com o ID: " + itemId));

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
