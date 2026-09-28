package clp.inventory.repository;

import clp.inventory.model.Sector;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SectorRepository extends JpaRepository<Sector, Long> {

  List<Sector> findByOrganization_Id(long organizationId);
}
