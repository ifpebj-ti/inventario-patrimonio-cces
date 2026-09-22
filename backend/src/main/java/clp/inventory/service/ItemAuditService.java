package clp.inventory.service;

import clp.inventory.model.Inventory;
import clp.inventory.model.Item;
import clp.inventory.model.ItemAudit;
import clp.inventory.model.Sector;
import clp.inventory.model.User;
import clp.inventory.repository.ItemAuditRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ItemAuditService {

    public static final String EVENT_VALIDATION = "VALIDATION";
    public static final String EVENT_PARTIAL_VALIDATION = "PARTIAL_VALIDATION";
    public static final String STATUS_VALIDATED = "VALIDATED";
    public static final String STATUS_PENDING_TARGET_SECTOR = "PENDING_TARGET_SECTOR";

    private final ItemAuditRepository itemAuditRepository;

    public ItemAuditService(ItemAuditRepository itemAuditRepository) {
        this.itemAuditRepository = itemAuditRepository;
    }

    public ItemAudit record(Item item, Inventory inventory, User actorUser, Sector actorSector,
                             Sector targetSector, String eventType, String status, String details) {
        ItemAudit audit = new ItemAudit(item, inventory, actorUser, actorSector, targetSector, eventType, status, details);
        return itemAuditRepository.save(audit);
    }

    public List<ItemAudit> listAudits(Long itemId, Long targetSectorId) {
        if (itemId != null) {
            return itemAuditRepository.findByItem_Id(itemId);
        }
        if (targetSectorId != null) {
            return itemAuditRepository.findByTargetSector_Id(targetSectorId);
        }
        return itemAuditRepository.findAll();
    }
}
