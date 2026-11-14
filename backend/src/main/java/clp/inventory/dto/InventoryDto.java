package clp.inventory.dto;

import clp.inventory.model.Inventory;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.format.DateTimeFormatter;

// Contrato enxuto do inventário para a interface, incluindo indicadores já calculáveis no frontend.
@Schema(
    description =
        "Inventário. Na criação e na edição só name, description, plannedEndDate e sectorId são lidos; o restante é calculado pelo servidor.")
public record InventoryDto(
    @Schema(
            description = "Id do inventário (gerado pelo servidor).",
            example = "10",
            accessMode = Schema.AccessMode.READ_ONLY)
        long id,
    @Schema(
            description = "Nome do inventário. Obrigatório e único por usuário criador.",
            example = "Inventário 2026 — Laboratório")
        String name,
    @Schema(
            description = "Descrição livre.",
            example = "Conferência anual dos bens do laboratório.")
        String description,
    @Schema(
            description = "Data de criação, no formato dd/MM/yyyy.",
            example = "10/11/2026",
            accessMode = Schema.AccessMode.READ_ONLY)
        String createdAt,
    @Schema(
            description = "Data da última atualização, no formato dd/MM/yyyy.",
            example = "12/11/2026",
            accessMode = Schema.AccessMode.READ_ONLY)
        String updatedAt,
    @Schema(
            description =
                "Prazo previsto, no formato yyyy-MM-dd. Não pode ser anterior à data de criação.",
            example = "2026-12-31")
        String plannedEndDate,
    @Schema(
            description = "Data e hora da finalização (ISO-8601); nulo enquanto em andamento.",
            example = "2026-12-20T14:30:00",
            accessMode = Schema.AccessMode.READ_ONLY,
            nullable = true)
        String completedAt,
    @Schema(
            description =
                "Situação: EM_ANDAMENTO, VENCIDO (prazo passou sem finalizar) ou CONCLUIDO.",
            example = "EM_ANDAMENTO",
            accessMode = Schema.AccessMode.READ_ONLY,
            allowableValues = {"EM_ANDAMENTO", "VENCIDO", "CONCLUIDO"})
        String status,
    @Schema(description = "Id do setor dono do inventário. Obrigatório na criação.", example = "1")
        Long sectorId,
    @Schema(
            description = "Nome do setor.",
            example = "Laboratório de Informática",
            accessMode = Schema.AccessMode.READ_ONLY)
        String sectorName,
    @Schema(
            description = "Quantidade de itens do inventário.",
            example = "120",
            accessMode = Schema.AccessMode.READ_ONLY)
        long itemCount) {

  // Centraliza a conversão para manter datas, setor, status e total de itens consistentes nas
  // respostas.
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
        inventory.completedAt() != null ? inventory.completedAt().toString() : null,
        status,
        inventory.sector() != null ? inventory.sector().id() : null,
        inventory.sector() != null ? inventory.sector().name() : null,
        itemCount);
  }
}
