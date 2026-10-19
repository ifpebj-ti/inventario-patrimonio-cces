package clp.inventory.service;

import clp.inventory.model.Role;
import clp.inventory.model.Sector;
import clp.inventory.model.SectorAllocation;
import clp.inventory.model.User;
import clp.inventory.repository.RoleRepository;
import clp.inventory.repository.SectorAllocationRepository;
import clp.inventory.repository.SectorRepository;
import clp.inventory.repository.UserRepository;
import java.util.HashSet;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SectorAllocationService {

  /** Máximo de usuários por chamada de alocação em lote. */
  public static final int MAX_BATCH = 200;

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
    upsert(user, sector, role);
    return user;
  }

  /**
   * Aloca vários usuários no setor com o mesmo cargo, tudo ou nada: se um só usuário não puder ser
   * alocado (não existe, é a conta de sistema, ou o gestor não pode dar aquele cargo a ele), nenhum
   * é. Quem já está no setor tem o cargo trocado.
   */
  @Transactional
  public List<User> allocateMany(
      long sectorId, List<Long> userIds, Long roleId, long actingUserId) {
    authorizationService.requireSectorPermission(actingUserId, sectorId);
    Sector sector =
        sectorRepository
            .findById(sectorId)
            .orElseThrow(() -> new NoSuchElementException("Sector not found with id: " + sectorId));
    if (userIds == null || userIds.isEmpty()) {
      throw new IllegalArgumentException("Selecione ao menos um usuário.");
    }
    if (userIds.size() > MAX_BATCH) {
      throw new IllegalArgumentException("Selecione no máximo " + MAX_BATCH + " usuários por vez.");
    }
    if (userIds.contains(null) || new HashSet<>(userIds).size() != userIds.size()) {
      throw new IllegalArgumentException("A lista de usuários tem itens vazios ou repetidos.");
    }
    if (roleId == null) {
      throw new IllegalArgumentException("Selecione o cargo dos usuários no setor.");
    }
    Role role =
        roleRepository
            .findById(roleId)
            .orElseThrow(() -> new IllegalArgumentException("Role not found with id: " + roleId));
    for (Long userId : userIds) {
      User user = findAllocatableUser(userId);
      authorizationService.requireCanAllocate(actingUserId, sectorId, user, role);
      upsert(user, sector, role);
    }
    return usersAllocatedToSector(sectorId);
  }

  /** Usuários que podem ser alocados ao setor: ativos e que ainda não estão nele. */
  public List<User> assignableUsers(long sectorId, long actingUserId) {
    authorizationService.requireSectorPermission(actingUserId, sectorId);
    if (!sectorRepository.existsById(sectorId)) {
      throw new NoSuchElementException("Sector not found with id: " + sectorId);
    }
    return userRepository.findAssignableToSector(sectorId);
  }

  private void upsert(User user, Sector sector, Role role) {
    Optional<SectorAllocation> existing =
        sectorAllocationRepository.findByUser_IdAndSector_Id(user.getId(), sector.id());
    if (existing.isPresent()) {
      existing.get().setRole(role);
      sectorAllocationRepository.save(existing.get());
    } else {
      sectorAllocationRepository.save(new SectorAllocation(user, sector, role));
    }
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
