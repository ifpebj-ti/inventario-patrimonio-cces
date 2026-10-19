package clp.inventory.service;

import clp.inventory.model.Inventory;
import clp.inventory.model.Role;
import clp.inventory.model.SectorAllocation;
import clp.inventory.model.User;
import clp.inventory.repository.SectorAllocationRepository;
import clp.inventory.repository.UserRepository;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * Regras de acesso. O administrador (coluna {@code is_administrator}) vê e gerencia tudo. Para os
 * demais, quem decide é o cargo da alocação no setor em questão, então o mesmo usuário pode ser
 * gestor em um setor e só consulta em outro:
 *
 * <ul>
 *   <li>MANAGE_SECTOR: vê e gerencia os inventários do setor e aloca nele apenas usuários com
 *       cargos sem permissão de gestão.
 *   <li>MANAGE_ITEM: vê os inventários do setor e opera os itens (validar e observar), sem criar,
 *       editar ou finalizar inventários.
 *   <li>Cargo sem permissão: somente leitura nos inventários do setor.
 * </ul>
 *
 * Quais permissões cada cargo tem é dado (editável pelo administrador); o código só confere as
 * permissões, nunca o nome do cargo.
 */
@Service
public class AuthorizationService {
  public static final String MANAGE_SECTOR = "MANAGE_SECTOR";
  public static final String MANAGE_ITEM = "MANAGE_ITEM";

  private final UserRepository userRepository;
  private final SectorAllocationRepository sectorAllocationRepository;

  public AuthorizationService(
      UserRepository userRepository, SectorAllocationRepository sectorAllocationRepository) {
    this.userRepository = userRepository;
    this.sectorAllocationRepository = sectorAllocationRepository;
  }

  public boolean isAdmin(long userId) {
    return requireUser(userId).isAdministrator();
  }

  public void requireAdmin(long userId) {
    if (!isAdmin(userId)) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Requer o papel de administrador");
    }
  }

  /** Cargo do usuário no setor, se ele estiver alocado nele. */
  public Optional<Role> roleIn(long userId, Long sectorId) {
    if (sectorId == null) {
      return Optional.empty();
    }
    return sectorAllocationRepository
        .findByUser_IdAndSector_Id(userId, sectorId)
        .map(SectorAllocation::role);
  }

  /** O cargo do usuário naquele setor tem a permissão (administrador não é considerado aqui). */
  public boolean hasPermissionInSector(long userId, Long sectorId, String permissionName) {
    return roleIn(userId, sectorId).map(role -> role.hasPermission(permissionName)).orElse(false);
  }

  /** Cargo de gestão: possui MANAGE_SECTOR. */
  public boolean isManagementRole(Role role) {
    return role != null && role.hasPermission(MANAGE_SECTOR);
  }

  /** Setores em que o cargo do usuário tem MANAGE_SECTOR. */
  public Set<Long> managedSectorIds(long userId) {
    return sectorAllocationRepository.findByUser_Id(userId).stream()
        .filter(allocation -> isManagementRole(allocation.role()))
        .map(allocation -> allocation.sector().id())
        .collect(Collectors.toSet());
  }

  /** Administrador, ou gestor (MANAGE_SECTOR) de ao menos um setor. */
  public boolean isAdminOrSectorManager(long userId) {
    return isAdmin(userId) || !managedSectorIds(userId).isEmpty();
  }

  public void requireAdminOrSectorManager(long userId) {
    if (!isAdminOrSectorManager(userId)) {
      throw new ResponseStatusException(
          HttpStatus.FORBIDDEN, "Requer administrador ou gestor de ao menos um setor");
    }
  }

  public void requireSectorPermission(long actingUserId, long sectorId) {
    if (!canManageSector(actingUserId, sectorId)) {
      throw new ResponseStatusException(
          HttpStatus.FORBIDDEN, "Requer administrador ou gestor do setor informado");
    }
  }

  public boolean canManageSector(long actingUserId, Long sectorId) {
    return isAdmin(actingUserId) || hasPermissionInSector(actingUserId, sectorId, MANAGE_SECTOR);
  }

  public boolean isAllocatedTo(long userId, Long sectorId) {
    return sectorId != null
        && sectorAllocationRepository.existsByUser_IdAndSector_Id(userId, sectorId);
  }

  public Set<Long> allocatedSectorIds(long userId) {
    return sectorAllocationRepository.findByUser_Id(userId).stream()
        .map(allocation -> allocation.sector().id())
        .collect(Collectors.toSet());
  }

  /** Leitura de setor: admin ou alocado ao setor (qualquer cargo). */
  public boolean canViewSector(long userId, long sectorId) {
    return isAdmin(userId) || isAllocatedTo(userId, sectorId);
  }

  public void requireViewSector(long userId, long sectorId) {
    if (!canViewSector(userId, sectorId)) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Sem acesso a este setor.");
    }
  }

  /**
   * Alocar, trocar o cargo ou desalocar alguém em um setor. O administrador pode tudo; o gestor do
   * setor só mexe com usuários comuns e só atribui cargos sem permissão de gestão (nunca promove
   * nem rebaixa outro gestor, nem mexe em administrador). {@code newRole} é nulo ao desalocar.
   */
  public void requireCanAllocate(long actingUserId, long sectorId, User target, Role newRole) {
    requireSectorPermission(actingUserId, sectorId);
    if (isAdmin(actingUserId)) {
      return;
    }
    if (target.isAdministrator()
        || isManagementRole(roleIn(target.getId(), sectorId).orElse(null))) {
      throw new ResponseStatusException(
          HttpStatus.FORBIDDEN, "Seu cargo não pode alterar um gestor ou administrador.");
    }
    if (isManagementRole(newRole)) {
      throw new ResponseStatusException(
          HttpStatus.FORBIDDEN, "Seu cargo só pode atribuir cargos sem permissão de gestão.");
    }
  }

  /** Leitura: admin ou alocado ao setor. Inventário antigo sem setor: admin ou quem o criou. */
  public boolean canViewInventory(long userId, Inventory inventory) {
    if (isAdmin(userId)) {
      return true;
    }
    if (inventory.sector() == null) {
      return inventory.User().getId().equals(userId);
    }
    return isAllocatedTo(userId, inventory.sector().id());
  }

  public void requireViewInventory(long userId, Inventory inventory) {
    if (!canViewInventory(userId, inventory)) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Sem acesso a este inventário.");
    }
  }

  /** Criar, editar, finalizar, importar e remover itens: admin ou gestor do setor do inventário. */
  public boolean canManageInventory(long userId, Inventory inventory) {
    return canManageSector(userId, sectorIdOf(inventory));
  }

  public void requireManageInventory(long userId, Inventory inventory) {
    if (!canManageInventory(userId, inventory)) {
      throw new ResponseStatusException(
          HttpStatus.FORBIDDEN, "Seu cargo não permite alterar este inventário.");
    }
  }

  /** Validar itens e registrar observações: quem gerencia o inventário ou tem MANAGE_ITEM nele. */
  public boolean canOperateInventory(long userId, Inventory inventory) {
    return canManageInventory(userId, inventory)
        || hasPermissionInSector(userId, sectorIdOf(inventory), MANAGE_ITEM);
  }

  public void requireOperateInventory(long userId, Inventory inventory) {
    if (!canOperateInventory(userId, inventory)) {
      throw new ResponseStatusException(
          HttpStatus.FORBIDDEN, "Seu cargo não permite validar itens deste inventário.");
    }
  }

  private Long sectorIdOf(Inventory inventory) {
    return inventory.sector() == null ? null : inventory.sector().id();
  }

  private User requireUser(long userId) {
    return userRepository
        .findById(userId)
        .orElseThrow(
            () -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Usuario nao encontrado"));
  }
}
