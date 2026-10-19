package clp.inventory.repository;

import clp.inventory.model.SectorAllocation;
import clp.inventory.model.User;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface SectorAllocationRepository extends JpaRepository<SectorAllocation, Long> {

  boolean existsByUser_IdAndSector_Id(long userId, long sectorId);

  List<SectorAllocation> findByUser_Id(long userId);

  List<SectorAllocation> findBySector_Id(long sectorId);

  boolean existsBySector_Id(long sectorId);

  Optional<SectorAllocation> findByUser_IdAndSector_Id(long userId, long sectorId);

  long countByRole_Id(long roleId);

  @Query("select count(distinct a.user.id) from SectorAllocation a where a.role.id = :roleId")
  long countUsersWithRole(@Param("roleId") long roleId);

  @Query(
      "select distinct a.user from SectorAllocation a where a.role.id = :roleId "
          + "and a.user.systemUser = false")
  List<User> findUsersWithRole(@Param("roleId") long roleId);

  @Query(
      "select distinct a.user from SectorAllocation a where a.sector.id in :sectorIds "
          + "and a.role.id = :roleId and a.user.systemUser = false")
  List<User> findUsersAllocatedToAnyWithRole(
      @Param("sectorIds") Collection<Long> sectorIds, @Param("roleId") long roleId);

  void deleteByUser_IdAndSector_Id(long userId, long sectorId);

  @Query(
      "select distinct a.user from SectorAllocation a where a.sector.id in :sectorIds "
          + "and a.user.systemUser = false")
  List<User> findUsersAllocatedToAny(@Param("sectorIds") Collection<Long> sectorIds);
}
