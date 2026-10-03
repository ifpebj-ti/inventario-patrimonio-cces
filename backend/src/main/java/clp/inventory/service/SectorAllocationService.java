package clp.inventory.service;

import clp.inventory.model.Sector;
import clp.inventory.model.SectorAllocation;
import clp.inventory.model.User;
import clp.inventory.repository.SectorAllocationRepository;
import clp.inventory.repository.SectorRepository;
import clp.inventory.repository.UserRepository;
import java.util.List;
import java.util.NoSuchElementException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SectorAllocationService {

  private final SectorAllocationRepository sectorAllocationRepository;
  private final UserRepository userRepository;
  private final SectorRepository sectorRepository;
  private final AuthorizationService authorizationService;

  public SectorAllocationService(
      SectorAllocationRepository sectorAllocationRepository,
      UserRepository userRepository,
      SectorRepository sectorRepository,
      AuthorizationService authorizationService) {
    this.sectorAllocationRepository = sectorAllocationRepository;
    this.userRepository = userRepository;
    this.sectorRepository = sectorRepository;
    this.authorizationService = authorizationService;
  }

  @Transactional
  public User allocate(long userId, long sectorId, long actingUserId) {
    authorizationService.requireSectorPermission(actingUserId, sectorId);
    User user =
        userRepository
            .findById(userId)
            .orElseThrow(() -> new NoSuchElementException("User not found with id: " + userId));
    Sector sector =
        sectorRepository
            .findById(sectorId)
            .orElseThrow(
                () -> new NoSuchElementException("Sector not found with id: " + sectorId));
    if (!sectorAllocationRepository.existsByUser_IdAndSector_Id(userId, sectorId)) {
      sectorAllocationRepository.save(new SectorAllocation(user, sector));
    }
    return user;
  }

  @Transactional
  public User deallocate(long userId, long sectorId, long actingUserId) {
    authorizationService.requireSectorPermission(actingUserId, sectorId);
    User user =
        userRepository
            .findById(userId)
            .orElseThrow(() -> new NoSuchElementException("User not found with id: " + userId));
    if (!sectorRepository.existsById(sectorId)) {
      throw new NoSuchElementException("Sector not found with id: " + sectorId);
    }
    sectorAllocationRepository.deleteByUser_IdAndSector_Id(userId, sectorId);
    return user;
  }

  public List<Long> sectorIdsOf(long userId) {
    return sectorAllocationRepository.findByUser_Id(userId).stream()
        .map(allocation -> allocation.sector().id())
        .toList();
  }

  public List<User> usersAllocatedToSector(long sectorId) {
    return sectorAllocationRepository.findBySector_Id(sectorId).stream()
        .map(SectorAllocation::user)
        .toList();
  }
}
