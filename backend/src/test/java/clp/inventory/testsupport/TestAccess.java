package clp.inventory.testsupport;

import clp.inventory.model.Permission;
import clp.inventory.model.Role;
import clp.inventory.model.Sector;
import clp.inventory.model.SectorAllocation;
import clp.inventory.model.User;
import clp.inventory.repository.PermissionRepository;
import clp.inventory.repository.RoleRepository;
import clp.inventory.repository.SectorAllocationRepository;
import clp.inventory.repository.SectorRepository;
import clp.inventory.repository.UserRepository;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * Monta usuários de teste no modelo de cargo por alocação. {@code user(email, MANAGER)} cria o
 * usuário já "destinado" a esse cargo: ao alocá-lo em um setor sem dizer o cargo, ele recebe o
 * destinado (ou Consulta). {@link #ADMIN} marca o usuário como administrador. Os cargos são os
 * semeados pela migration 7.0.
 */
@Component
public class TestAccess {
  public static final String ADMIN = "@administrador";
  public static final String MANAGER = "Gestor de Setor";
  public static final String OPERATOR = "Operador de Campo";
  public static final String CONSULT = "Consulta";

  private final UserRepository users;
  private final RoleRepository roles;
  private final PermissionRepository permissions;
  private final SectorRepository sectors;
  private final SectorAllocationRepository allocations;
  private final Map<Long, String> intendedRole = new ConcurrentHashMap<>();

  public TestAccess(
      UserRepository users,
      RoleRepository roles,
      PermissionRepository permissions,
      SectorRepository sectors,
      SectorAllocationRepository allocations) {
    this.users = users;
    this.roles = roles;
    this.permissions = permissions;
    this.sectors = sectors;
    this.allocations = allocations;
  }

  public User user(String email, String roleName) {
    User user = new User();
    user.setName("Test User");
    user.setEmail(email);
    user.setGoogleId("google-" + email);
    boolean admin = ADMIN.equals(roleName);
    user.setAdministrator(admin);
    user = users.save(user);
    intendedRole.put(user.getId(), admin ? MANAGER : roleName);
    return user;
  }

  public User admin(String email) {
    return user(email, ADMIN);
  }

  public Role role(String name) {
    return roles.findAll().stream().filter(r -> name.equals(r.name())).findFirst().orElseThrow();
  }

  /**
   * Cria um cargo com exatamente estas permissões (direto no repositório, sem a normalização da
   * API). Com nenhuma permissão, é um cargo que não dá acesso aos dados.
   */
  public Role customRole(String name, String... permissionNames) {
    Role role = new Role(name, "Cargo de teste");
    role.setPermissions(
        java.util.Arrays.stream(permissionNames)
            .map(permissionName -> permissions.findByName(permissionName).orElseThrow())
            .collect(java.util.stream.Collectors.<Permission>toSet()));
    return roles.save(role);
  }

  public SectorAllocation allocate(User user, Sector sector) {
    return allocate(user, sector, intendedRole.getOrDefault(user.getId(), CONSULT));
  }

  public SectorAllocation allocate(long userId, long sectorId) {
    return allocate(users.findById(userId).orElseThrow(), sectors.findById(sectorId).orElseThrow());
  }

  public SectorAllocation allocate(User user, Sector sector, String roleName) {
    return allocations
        .findByUser_IdAndSector_Id(user.getId(), sector.id())
        .map(
            existing -> {
              existing.setRole(role(roleName));
              return allocations.save(existing);
            })
        .orElseGet(() -> allocations.save(new SectorAllocation(user, sector, role(roleName))));
  }
}
