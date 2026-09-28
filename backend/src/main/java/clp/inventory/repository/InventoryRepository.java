package clp.inventory.repository;

import clp.inventory.model.Inventory;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface InventoryRepository extends JpaRepository<Inventory, Long> {

  boolean existsByNameAndUser_Id(String name, Long userId);

  List<Inventory> findByUser_Id(Long userId);
}
