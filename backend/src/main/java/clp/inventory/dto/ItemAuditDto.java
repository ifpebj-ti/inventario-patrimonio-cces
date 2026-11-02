package clp.inventory.dto;

import clp.inventory.model.ItemAudit;

public record ItemAuditDto(
    long id,
    long itemId,
    Long inventoryId,
    long actorUserId,
    Long actorSectorId,
    Long targetSectorId,
    Long foundRoomId,
    String foundRoomName,
    Long expectedRoomId,
    String expectedRoomName,
    boolean divergent,
    String eventType,
    String status,
    String details,
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
