package clp.inventory.repository;

import clp.inventory.model.Room;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface RoomRepository extends JpaRepository<Room, Long> {

  List<Room> findBySector_IdOrderByName(long sectorId);

  Optional<Room> findBySector_IdAndNormalizedName(long sectorId, String normalizedName);

  boolean existsBySector_Id(long sectorId);

  long countBySector_Id(long sectorId);

  void deleteBySector_Id(long sectorId);

  /**
   * Cria a sala se ainda não houver uma com o mesmo nome normalizado no setor; devolve 0 quando já
   * existe (ou outra requisição acabou de criar). Não lança na corrida, então a transação não é
   * abortada e quem chama pode reler a sala.
   */
  @Modifying
  @Query(
      value =
          "insert into im_room (id, id_sector, name, normalized_name) "
              + "values (nextval('im_room_id'), :sectorId, :name, :normalizedName) "
              + "on conflict (id_sector, normalized_name) do nothing",
      nativeQuery = true)
  int insertIfAbsent(
      @Param("sectorId") long sectorId,
      @Param("name") String name,
      @Param("normalizedName") String normalizedName);
}
