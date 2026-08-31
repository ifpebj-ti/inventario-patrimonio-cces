package clp.inventory.service;

import clp.inventory.dto.InventoryDto;
import clp.inventory.dto.ItemProcessingResult;
import clp.inventory.model.Inventory;
import clp.inventory.model.Item;
import clp.inventory.model.User;
import clp.inventory.repository.InventoryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class InventoryService {

    private final InventoryRepository inventoryRepository;
    private final UserService userService;

    public InventoryService(InventoryRepository inventoryRepository, UserService userService) {
        this.inventoryRepository = inventoryRepository;
        this.userService = userService;
    }

    public Inventory createInventory(InventoryDto inventoryDto, long userId) {
        validateInventoryDto(inventoryDto);

        User user = userService.findUserById(userId);
        validateInventoryUniqueness(inventoryDto.name(), userId);

        Inventory inventory = new Inventory(
                inventoryDto.name(),
                inventoryDto.description(),
                user
        );

        return inventoryRepository.save(inventory);
    }

    @Transactional
    public void deleteInventory(long inventoryId, long userId) {
        User user = userService.findUserById(userId);
        Inventory inventory = inventoryRepository.findById(inventoryId).orElseThrow(() -> new RuntimeException("Invalid inventory"));
        if (!inventory.User().equals(user)) {
            throw new RuntimeException("Invalid user");
        }
        inventoryRepository.deleteById(inventoryId);
    }

    @Transactional
    public Inventory updateInventory(long inventoryId, InventoryDto inventoryDto, long userId) {
        Inventory inventory = inventoryRepository.findById(inventoryId).orElseThrow(() -> new RuntimeException("Invalid inventory"));
        if (!inventory.User().equals(userService.findUserById(userId))) {
            throw new RuntimeException("Invalid user");
        }
        inventory.setName(inventoryDto.name());
        inventory.setDescription(inventoryDto.description());
        return inventoryRepository.save(inventory);
    }

    public ItemProcessingResult addItemToInventory(long inventoryId, Item item, int line) {
        try {
            Inventory inventory = inventoryRepository.findById(inventoryId)
                    .orElseThrow(() -> new RuntimeException("Inventory not found with id: " + inventoryId));

            boolean itemExist = inventory.items().stream()
                    .anyMatch(existingItem -> existingItem.code().equals(item.code()));

            if (itemExist) {
                return new ItemProcessingResult(
                        item.code(),
                        line,
                        false,
                        "Item already exists in inventory"
                );
            }

            inventory.addItem(item);
            inventoryRepository.save(inventory);

            return new ItemProcessingResult(
                    item.code(),
                    line,
                    true,
                    "Item added successfully to inventory"
            );
        } catch (Exception e) {
            return new ItemProcessingResult(
                    item.code(),
                    line,
                    false,
                    "Error processing item: " + e.getMessage()
            );
        }
    }

    public boolean removeItemFromInventory(long inventoryId, long itemId) {
        Inventory inventory = inventoryRepository.findById(inventoryId)
                .orElseThrow(() -> new RuntimeException("Inventory not found with id: " + inventoryId));

        Optional<Item> itemToRemove = inventory.items().stream()
                .filter(existingItem -> existingItem.id() == itemId)
                .findFirst();

        if (itemToRemove.isEmpty()) {
            return false;
        }

        inventory.removeItem(itemToRemove.get());
        inventoryRepository.save(inventory);

        return true;
    }

    public List<Inventory> getUserInventories(long userId) {
        return inventoryRepository.findByUser_Id(userId);
    }

    private void validateInventoryDto(InventoryDto inventoryDto) {
        if (inventoryDto.name() == null || inventoryDto.name().trim().isEmpty()) {
            throw new IllegalArgumentException("Inventory name cannot be null or empty");
        }
    }

    private void validateInventoryUniqueness(String inventoryName, long userId) {
        if (inventoryRepository.existsByNameAndUser_Id(inventoryName, userId)) {
            throw new RuntimeException(
                    "Inventory with name '" + inventoryName + "' already exists for this user"
            );
        }
    }

    public void addSingleItemToInventory(long inventoryId, Item item) {
        try {
            Inventory inventory = inventoryRepository.findById(inventoryId)
                    .orElseThrow(() -> new RuntimeException("Inventory not found with id: " + inventoryId));

            inventory.addItem(item);
            inventoryRepository.save(inventory);
        } catch (Exception e) {
            throw new RuntimeException("Error adding item: " + e.getMessage());
        }
    }
}
