package clp.inventory.repository;

import clp.inventory.model.SectorAllocation;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SectorAllocationRepository extends JpaRepository<SectorAllocation, Long> {

  boolean existsByUser_IdAndSector_Id(long userId, long sectorId);

  List<SectorAllocation> findByUser_Id(long userId);

  List<SectorAllocation> findBySector_Id(long sectorId);

  void deleteByUser_IdAndSector_Id(long userId, long sectorId);
}
