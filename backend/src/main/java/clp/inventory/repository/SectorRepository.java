package clp.inventory.repository;

import clp.inventory.model.Sector;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SectorRepository extends JpaRepository<Sector, Long> {

  boolean existsByName(String name);

  boolean existsByNameAndIdNot(String name, long id);
}
