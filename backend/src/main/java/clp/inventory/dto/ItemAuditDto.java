package clp.inventory.dto;

import clp.inventory.model.ItemAudit;

public record ItemAuditDto(
        long id,
        long itemId,
        Long inventoryId,
        long actorUserId,
        Long actorSectorId,
        Long targetSectorId,
        String eventType,
        String status,
        String details,
        String occurredAt
) {

    public static ItemAuditDto from(ItemAudit audit) {
        return new ItemAuditDto(
                audit.id(),
                audit.item().id(),
                audit.inventory() != null ? audit.inventory().id() : null,
                audit.actorUser().getId(),
                audit.actorSector() != null ? audit.actorSector().id() : null,
                audit.targetSector() != null ? audit.targetSector().id() : null,
                audit.eventType(),
                audit.status(),
                audit.details(),
                audit.occurredAt() != null ? audit.occurredAt().toString() : null
        );
    }
}
