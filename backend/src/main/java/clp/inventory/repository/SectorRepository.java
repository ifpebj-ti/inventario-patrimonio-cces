package clp.inventory.repository;

import clp.inventory.model.Sector;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface SectorRepository extends JpaRepository<Sector, Long> {

  @Query(
      "select s from Sector s where s.id in (select a.sector.id from SectorAllocation a where a.user.id = :userId)")
  List<Sector> findAllocatedToUser(@Param("userId") long userId);

  boolean existsByName(String name);

  boolean existsByNameAndIdNot(String name, long id);
}
