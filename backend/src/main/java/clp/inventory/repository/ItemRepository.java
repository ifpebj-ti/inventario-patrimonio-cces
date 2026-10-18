package clp.inventory.repository;

import clp.inventory.model.Item;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ItemRepository extends JpaRepository<Item, Long> {

  Page<Item> findByInventory_Id(Long inventoryId, Pageable pageable);

  // Contagens usadas para o status do inventário e os indicadores do dashboard.
  long countByInventory_Id(Long inventoryId);

  long countByInventory_IdAndIsValidTrue(Long inventoryId);
}
