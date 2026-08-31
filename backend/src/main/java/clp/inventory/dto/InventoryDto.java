package clp.inventory.dto;

import clp.inventory.model.Inventory;

import java.time.format.DateTimeFormatter;

public record InventoryDto(
        long id,
        String name,
        String description,
        String createdAt
) {

    public static InventoryDto from(Inventory inventory) {

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy");

        String formattedDate = inventory.createdAt() != null
                ? inventory.createdAt().format(formatter)
                : null;

        return new InventoryDto(
                inventory.id(),
                inventory.name(),
                inventory.description(),
                formattedDate
        );
    }
}
