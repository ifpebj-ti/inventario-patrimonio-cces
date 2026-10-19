package clp.inventory.service;

import clp.inventory.model.Role;
import clp.inventory.model.Sector;
import clp.inventory.model.SectorAllocation;
import clp.inventory.model.User;
import clp.inventory.repository.RoleRepository;
import clp.inventory.repository.SectorAllocationRepository;
import clp.inventory.repository.SectorRepository;
import clp.inventory.repository.UserRepository;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SectorAllocationService {

  private final SectorAllocationRepository sectorAllocationRepository;
  private final UserRepository userRepository;
  private final SectorRepository sectorRepository;
  private final RoleRepository roleRepository;
  private final AuthorizationService authorizationService;

  public SectorAllocationService(
      SectorAllocationRepository sectorAllocationRepository,
      UserRepository userRepository,
      SectorRepository sectorRepository,
      RoleRepository roleRepository,
      AuthorizationService authorizationService) {
    this.sectorAllocationRepository = sectorAllocationRepository;
    this.userRepository = userRepository;
    this.sectorRepository = sectorRepository;
    this.roleRepository = roleRepository;
    this.authorizationService = authorizationService;
  }

  /**
   * Aloca o usuário no setor com o cargo informado; se ele já estiver alocado, troca o cargo
   * (idempotente para o mesmo cargo). O cargo é obrigatório.
   */
  @Transactional
  public User allocate(long userId, long sectorId, Long roleId, long actingUserId) {
    authorizationService.requireSectorPermission(actingUserId, sectorId);
    User user = findAllocatableUser(userId);
    Sector sector =
        sectorRepository
            .findById(sectorId)
            .orElseThrow(() -> new NoSuchElementException("Sector not found with id: " + sectorId));
    if (roleId == null) {
      throw new IllegalArgumentException("Selecione o cargo do usuário no setor.");
    }
    Role role =
        roleRepository
            .findById(roleId)
            .orElseThrow(() -> new IllegalArgumentException("Role not found with id: " + roleId));
    authorizationService.requireCanAllocate(actingUserId, sectorId, user, role);
    Optional<SectorAllocation> existing =
        sectorAllocationRepository.findByUser_IdAndSector_Id(userId, sectorId);
    if (existing.isPresent()) {
      existing.get().setRole(role);
      sectorAllocationRepository.save(existing.get());
    } else {
      sectorAllocationRepository.save(new SectorAllocation(user, sector, role));
    }
    return user;
  }

  @Transactional
  public User deallocate(long userId, long sectorId, long actingUserId) {
    authorizationService.requireSectorPermission(actingUserId, sectorId);
    User user = findAllocatableUser(userId);
    if (!sectorRepository.existsById(sectorId)) {
      throw new NoSuchElementException("Sector not found with id: " + sectorId);
    }
    authorizationService.requireCanAllocate(actingUserId, sectorId, user, null);
    sectorAllocationRepository.deleteByUser_IdAndSector_Id(userId, sectorId);
    return user;
  }

  // A conta de sistema responde como se não existisse.
  private User findAllocatableUser(long userId) {
    return userRepository
        .findById(userId)
        .filter(found -> !found.isSystemUser())
        .orElseThrow(() -> new NoSuchElementException("User not found with id: " + userId));
  }

  public List<SectorAllocation> allocationsOf(long userId) {
    return sectorAllocationRepository.findByUser_Id(userId);
  }

  public List<Long> sectorIdsOf(long userId) {
    return allocationsOf(userId).stream().map(allocation -> allocation.sector().id()).toList();
  }

  public List<User> usersAllocatedToSector(long sectorId) {
    return sectorAllocationRepository.findBySector_Id(sectorId).stream()
        .map(SectorAllocation::user)
        .filter(user -> !user.isSystemUser())
        .toList();
  }
}
