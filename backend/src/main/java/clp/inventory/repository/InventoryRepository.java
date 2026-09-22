package clp.inventory.repository;

import clp.inventory.model.Inventory;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface InventoryRepository extends JpaRepository<Inventory, Long> {

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select i from Inventory i where i.id = :id")
  Optional<Inventory> findByIdForUpdate(@Param("id") long id);

  boolean existsByNameAndUser_Id(String name, Long userId);

  boolean existsByUser_Id(Long userId);

  List<Inventory> findBySector_Id(long sectorId);

  // Quem não é admin enxerga os inventários dos setores a que está alocado. Inventários antigos
  // sem setor continuam visíveis apenas para quem os criou.
  @Query(
      "select i from Inventory i where i.sector.id in "
          + "(select a.sector.id from SectorAllocation a where a.user.id = :userId) "
          + "or (i.sector is null and i.user.id = :userId)")
  List<Inventory> findVisibleToUser(@Param("userId") long userId);
}
