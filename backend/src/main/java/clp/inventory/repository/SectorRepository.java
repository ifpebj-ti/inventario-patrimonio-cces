package clp.inventory.repository;

import clp.inventory.model.Sector;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface SectorRepository extends JpaRepository<Sector, Long> {

  // Setores em que o cargo do usuário dá leitura (`viewPermissions`: Consulta ou as de gestão).
  @Query(
      "select s from Sector s where s.id in "
          + "(select a.sector.id from SectorAllocation a join a.role r join r.permissions p "
          + "where a.user.id = :userId and p.name in :viewPermissions)")
  List<Sector> findViewableByUser(
      @Param("userId") long userId, @Param("viewPermissions") Collection<String> viewPermissions);

  boolean existsByName(String name);

  boolean existsByNameAndIdNot(String name, long id);
}
