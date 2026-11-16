package clp.inventory.repository;

import clp.inventory.model.InventoryAudit;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface InventoryAuditRepository extends JpaRepository<InventoryAudit, Long> {

  List<InventoryAudit> findByInventoryIdOrderByIdAsc(long inventoryId);
}
