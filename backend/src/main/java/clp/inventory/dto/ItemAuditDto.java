package clp.inventory.dto;

import clp.inventory.model.ItemAudit;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Registro de auditoria de uma validação ou da desvalidação de um item.")
public record ItemAuditDto(
    @Schema(description = "Id do registro.", example = "55") long id,
    @Schema(description = "Item auditado.", example = "300") long itemId,
    @Schema(description = "Inventário do item.", example = "10") Long inventoryId,
    @Schema(description = "Usuário que fez a ação.", example = "2") long actorUserId,
    @Schema(
            description =
                "Setor do autor, quando ele é alocado ao setor do inventário; nulo para administrador ou outro setor.",
            example = "1")
        Long actorSectorId,
    @Schema(description = "Setor dono do inventário.", example = "1") Long targetSectorId,
    @Schema(description = "Sala em que o item foi encontrado.", example = "7") Long foundRoomId,
    @Schema(description = "Nome da sala em que foi encontrado.", example = "Sala 101")
        String foundRoomName,
    @Schema(description = "Sala em que o item está cadastrado.", example = "7") Long expectedRoomId,
    @Schema(description = "Nome da sala cadastrada.", example = "Sala 101") String expectedRoomName,
    @Schema(description = "Verdadeiro quando a sala encontrada difere da cadastrada.")
        boolean divergent,
    @Schema(
            description =
                "VALIDATION (validação pelo próprio setor), PARTIAL_VALIDATION (validação por usuário de outro setor) ou VALIDATION_REVOKED (desvalidação do item).",
            example = "VALIDATION",
            allowableValues = {"VALIDATION", "PARTIAL_VALIDATION", "VALIDATION_REVOKED"})
        String eventType,
    @Schema(
            description =
                "VALIDATED, PENDING_TARGET_SECTOR quando a validação parcial ainda aguarda o setor de destino, ou REVOKED na desvalidação.",
            example = "VALIDATED",
            allowableValues = {"VALIDATED", "PENDING_TARGET_SECTOR", "REVOKED"})
        String status,
    @Schema(description = "Detalhes adicionais, quando houver.") String details,
    @Schema(description = "Data e hora do evento (ISO-8601).", example = "2026-11-10T09:15:00")
        String occurredAt) {

  public static ItemAuditDto from(ItemAudit audit) {
    return new ItemAuditDto(
        audit.id(),
        audit.item().id(),
        audit.inventory() != null ? audit.inventory().id() : null,
        audit.actorUser().getId(),
        audit.actorSector() != null ? audit.actorSector().id() : null,
        audit.targetSector() != null ? audit.targetSector().id() : null,
        audit.foundRoom() != null ? audit.foundRoom().id() : null,
        audit.foundRoom() != null ? audit.foundRoom().name() : null,
        audit.expectedRoom() != null ? audit.expectedRoom().id() : null,
        audit.expectedRoom() != null ? audit.expectedRoom().name() : null,
        audit.divergent(),
        audit.eventType(),
        audit.status(),
        audit.details(),
        audit.occurredAt() != null ? audit.occurredAt().toString() : null);
  }
}
