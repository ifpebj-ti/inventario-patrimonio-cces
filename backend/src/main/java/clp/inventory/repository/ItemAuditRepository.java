package clp.inventory.repository;

import clp.inventory.model.ItemAudit;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ItemAuditRepository extends JpaRepository<ItemAudit, Long> {

  List<ItemAudit> findByItem_Id(long itemId);

  List<ItemAudit> findByTargetSector_Id(long targetSectorId);
}
