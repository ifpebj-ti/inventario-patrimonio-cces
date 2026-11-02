package clp.inventory.repository;

import clp.inventory.model.Room;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface RoomRepository extends JpaRepository<Room, Long> {

  List<Room> findBySector_IdOrderByName(long sectorId);

  Optional<Room> findBySector_IdAndNormalizedName(long sectorId, String normalizedName);

  boolean existsBySector_Id(long sectorId);

  long countBySector_Id(long sectorId);

  void deleteBySector_Id(long sectorId);
}
