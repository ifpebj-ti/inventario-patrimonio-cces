package clp.inventory.dto;

import clp.inventory.model.Inventory;
import java.time.format.DateTimeFormatter;

// Contrato enxuto do inventário para a interface, incluindo indicadores já calculáveis no frontend.
public record InventoryDto(
    long id,
    String name,
    String description,
    String createdAt,
    String updatedAt,
    String plannedEndDate,
    String status,
    Long sectorId,
    String sectorName,
    long itemCount) {

  // Centraliza a conversão para manter datas, setor, status e total de itens consistentes nas respostas.
  public static InventoryDto from(Inventory inventory, String status, long itemCount) {

    DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    String formattedDate =
        inventory.createdAt() != null ? inventory.createdAt().format(formatter) : null;

    return new InventoryDto(
        inventory.id(),
        inventory.name(),
        inventory.description(),
        formattedDate,
        inventory.updatedAt() != null ? inventory.updatedAt().format(formatter) : null,
        inventory.plannedEndDate() != null ? inventory.plannedEndDate().toString() : null,
        status,
        inventory.sector() != null ? inventory.sector().id() : null,
        inventory.sector() != null ? inventory.sector().name() : null,
        itemCount);
  }
}
