package clp.inventory.service;

import clp.inventory.model.Inventory;
import clp.inventory.model.Profile;
import clp.inventory.model.User;
import clp.inventory.repository.SectorAllocationRepository;
import clp.inventory.repository.UserRepository;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * Regras de acesso por perfil:
 *
 * <ul>
 *   <li>ADMIN (ADMIN_ORGANIZATION): vê e gerencia tudo.
 *   <li>MANAGE_SECTOR (GESTOR_SETOR): vê e gerencia os inventários dos setores a que está alocado e
 *       cadastra apenas usuários sem permissão de gestão (operadores e consulta).
 *   <li>MANAGE_ITEM (OPERADOR_CAMPO): vê os inventários do seu setor e opera os itens (validar e
 *       observar), sem criar, editar ou finalizar inventários.
 *   <li>Sem permissão (CONSULTA): somente leitura nos inventários do seu setor.
 * </ul>
 */
@Service
public class AuthorizationService {
  public static final String ADMIN = "ADMIN";
  public static final String MANAGE_SECTOR = "MANAGE_SECTOR";
  public static final String MANAGE_ITEM = "MANAGE_ITEM";

  private final UserRepository userRepository;
  private final SectorAllocationRepository sectorAllocationRepository;

  public AuthorizationService(
      UserRepository userRepository, SectorAllocationRepository sectorAllocationRepository) {
    this.userRepository = userRepository;
    this.sectorAllocationRepository = sectorAllocationRepository;
  }

  public boolean hasPermission(long userId, String permissionName) {
    User user = requireUser(userId);
    return user.getProfile() != null
        && user.getProfile().permissions().stream().anyMatch(p -> p.name().equals(permissionName));
  }

  public void requirePermission(long userId, String permissionName) {
    if (!hasPermission(userId, permissionName)) {
      throw new ResponseStatusException(
          HttpStatus.FORBIDDEN, "Requer a permissao " + permissionName);
    }
  }

  public void requireAnyPermission(long userId, String... permissionNames) {
    boolean allowed = Arrays.stream(permissionNames).anyMatch(p -> hasPermission(userId, p));
    if (!allowed) {
      throw new ResponseStatusException(
          HttpStatus.FORBIDDEN, "Requer uma das permissoes: " + String.join(", ", permissionNames));
    }
  }

  public void requireSectorPermission(long actingUserId, long sectorId) {
    if (!canManageSector(actingUserId, sectorId)) {
      throw new ResponseStatusException(
          HttpStatus.FORBIDDEN, "Requer ADMIN ou MANAGE_SECTOR no setor informado");
    }
  }

  public boolean canManageSector(long actingUserId, Long sectorId) {
    return hasPermission(actingUserId, "ADMIN")
        || (sectorId != null
            && hasPermission(actingUserId, "MANAGE_SECTOR")
            && sectorAllocationRepository.existsByUser_IdAndSector_Id(actingUserId, sectorId));
  }

  public boolean isAdmin(long userId) {
    return hasPermission(userId, ADMIN);
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

  /** Leitura de setor: admin ou alocado ao setor (qualquer perfil). */
  public boolean canViewSector(long userId, long sectorId) {
    return isAdmin(userId) || isAllocatedTo(userId, sectorId);
  }

  public void requireViewSector(long userId, long sectorId) {
    if (!canViewSector(userId, sectorId)) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Sem acesso a este setor.");
    }
  }

  /** Gestor só vincula ao seu setor usuários sem permissão de gestão. */
  public void requireAllocatable(long actingUserId, User target) {
    if (!isAdmin(actingUserId) && isManagementProfile(target.getProfile())) {
      throw new ResponseStatusException(
          HttpStatus.FORBIDDEN,
          "Seu perfil só pode vincular Operadores de campo e Consulta ao setor.");
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
          HttpStatus.FORBIDDEN, "Seu perfil não permite alterar este inventário.");
    }
  }

  /** Validar itens e registrar observações: quem gerencia o inventário ou o operador do setor. */
  public boolean canOperateInventory(long userId, Inventory inventory) {
    return canManageInventory(userId, inventory)
        || (hasPermission(userId, MANAGE_ITEM) && isAllocatedTo(userId, sectorIdOf(inventory)));
  }

  public void requireOperateInventory(long userId, Inventory inventory) {
    if (!canOperateInventory(userId, inventory)) {
      throw new ResponseStatusException(
          HttpStatus.FORBIDDEN, "Seu perfil não permite validar itens deste inventário.");
    }
  }

  /** Perfil de gestão: possui ADMIN ou MANAGE_SECTOR. */
  public boolean isManagementProfile(Profile profile) {
    return profile != null
        && profile.permissions().stream()
            .anyMatch(p -> p.name().equals(ADMIN) || p.name().equals(MANAGE_SECTOR));
  }

  /** Admin atribui qualquer perfil; gestor atribui apenas perfis sem permissão de gestão. */
  public boolean canAssignProfile(long actingUserId, Profile target) {
    if (isAdmin(actingUserId)) {
      return true;
    }
    return hasPermission(actingUserId, MANAGE_SECTOR) && !isManagementProfile(target);
  }

  public void requireAssignProfile(long actingUserId, Profile target) {
    if (!canAssignProfile(actingUserId, target)) {
      throw new ResponseStatusException(
          HttpStatus.FORBIDDEN,
          "Seu perfil só pode cadastrar Operadores de campo e Consulta, nunca gestores ou"
              + " administradores.");
    }
  }

  /**
   * Admin gerencia qualquer usuário; gestor gerencia apenas usuários sem permissão de gestão que
   * compartilham ao menos um setor com ele.
   */
  public boolean canManageUser(long actingUserId, User target) {
    if (isAdmin(actingUserId)) {
      return true;
    }
    if (!hasPermission(actingUserId, MANAGE_SECTOR) || isManagementProfile(target.getProfile())) {
      return false;
    }
    Set<Long> mine = allocatedSectorIds(actingUserId);
    return allocatedSectorIds(target.getId()).stream().anyMatch(mine::contains);
  }

  /**
   * Desativar ou excluir afeta todos os setores do usuário: o admin pode sempre; o gestor só quando
   * o usuário atua exclusivamente nos setores dele.
   */
  public void requireRemoveUser(long actingUserId, User target) {
    requireManageUser(actingUserId, target);
    if (!isAdmin(actingUserId)
        && !allocatedSectorIds(actingUserId).containsAll(allocatedSectorIds(target.getId()))) {
      throw new ResponseStatusException(
          HttpStatus.FORBIDDEN,
          "Este usuário também atua em outros setores. Peça a um administrador.");
    }
  }

  public void requireManageUser(long actingUserId, User target) {
    if (!canManageUser(actingUserId, target)) {
      throw new ResponseStatusException(
          HttpStatus.FORBIDDEN, "Seu perfil não pode gerenciar este usuário.");
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
