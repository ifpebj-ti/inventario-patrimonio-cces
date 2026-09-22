package clp.inventory.repository;

import clp.inventory.model.ItemAudit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ItemAuditRepository extends JpaRepository<ItemAudit, Long> {

    List<ItemAudit> findByItem_Id(long itemId);

    List<ItemAudit> findByTargetSector_Id(long targetSectorId);
}
