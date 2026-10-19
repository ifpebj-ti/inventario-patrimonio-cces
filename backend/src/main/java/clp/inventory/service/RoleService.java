package clp.inventory.service;

import clp.inventory.dto.RoleDto;
import clp.inventory.dto.RoleRequest;
import clp.inventory.model.Permission;
import clp.inventory.model.Role;
import clp.inventory.repository.PermissionRepository;
import clp.inventory.repository.RoleRepository;
import clp.inventory.repository.SectorAllocationRepository;
import java.util.HashSet;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Cargos: o administrador cria, edita e exclui; o gestor só lê a lista, para escolher o cargo ao
 * alocar alguém no setor. As permissões que um cargo pode ter vêm do catálogo fixo (/permissions).
 * Mudar as permissões de um cargo vale na hora para todas as alocações dele.
 */
@Service
public class RoleService {
  private static final int MAX_NAME = 100;
  private static final int MAX_DESCRIPTION = 255;

  private final RoleRepository roleRepository;
  private final PermissionRepository permissionRepository;
  private final SectorAllocationRepository sectorAllocationRepository;
  private final AuthorizationService authorizationService;

  public RoleService(
      RoleRepository roleRepository,
      PermissionRepository permissionRepository,
      SectorAllocationRepository sectorAllocationRepository,
      AuthorizationService authorizationService) {
    this.roleRepository = roleRepository;
    this.permissionRepository = permissionRepository;
    this.sectorAllocationRepository = sectorAllocationRepository;
    this.authorizationService = authorizationService;
  }

  @Transactional(readOnly = true)
  public List<RoleDto> listRoles(long actingUserId) {
    authorizationService.requireAdminOrSectorManager(actingUserId);
    return roleRepository.findAll(Sort.by("id")).stream().map(this::toDto).toList();
  }

  @Transactional(readOnly = true)
  public RoleDto findRole(long id, long actingUserId) {
    authorizationService.requireAdmin(actingUserId);
    return toDto(findRoleInternal(id));
  }

  @Transactional
  public RoleDto createRole(RoleRequest request, long actingUserId) {
    authorizationService.requireAdmin(actingUserId);
    String name = validName(request);
    String description = validDescription(request);
    if (roleRepository.existsByName(name)) {
      throw new IllegalArgumentException("Já existe um cargo com o nome '" + name + "'.");
    }
    Role role = new Role(name, description);
    role.setPermissions(resolvePermissions(request));
    return toDto(roleRepository.save(role));
  }

  @Transactional
  public RoleDto updateRole(long id, RoleRequest request, long actingUserId) {
    authorizationService.requireAdmin(actingUserId);
    Role role = findRoleInternal(id);
    String name = validName(request);
    String description = validDescription(request);
    if (roleRepository.existsByNameAndIdNot(name, id)) {
      throw new IllegalArgumentException("Já existe um cargo com o nome '" + name + "'.");
    }
    role.setName(name);
    role.setDescription(description);
    role.setPermissions(resolvePermissions(request));
    return toDto(roleRepository.save(role));
  }

  /**
   * Só se exclui cargo sem nenhuma alocação e sem nenhuma permissão: com qualquer um dos dois, 409.
   * Para excluir um cargo que tem permissões, retire-as antes (edição).
   */
  @Transactional
  public void deleteRole(long id, long actingUserId) {
    authorizationService.requireAdmin(actingUserId);
    Role role = findRoleInternal(id);
    long allocations = sectorAllocationRepository.countByRole_Id(id);
    if (allocations > 0) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT,
          "O cargo está em uso por " + allocations + " alocação(ões) e não pode ser excluído.");
    }
    if (!role.permissions().isEmpty()) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, "O cargo possui permissões; remova-as antes de excluí-lo.");
    }
    roleRepository.delete(role);
  }

  private RoleDto toDto(Role role) {
    return RoleDto.from(
        role,
        authorizationService.isManagementRole(role),
        sectorAllocationRepository.countByRole_Id(role.id()),
        sectorAllocationRepository.countUsersWithRole(role.id()));
  }

  private Role findRoleInternal(long id) {
    return roleRepository
        .findById(id)
        .orElseThrow(() -> new NoSuchElementException("Role not found with id: " + id));
  }

  private String validName(RoleRequest request) {
    if (request == null || request.name() == null || request.name().trim().isEmpty()) {
      throw new IllegalArgumentException("Informe o nome do cargo.");
    }
    String name = request.name().trim();
    if (name.length() > MAX_NAME) {
      throw new IllegalArgumentException(
          "O nome do cargo pode ter até " + MAX_NAME + " caracteres.");
    }
    return name;
  }

  private String validDescription(RoleRequest request) {
    String description = request.description() == null ? null : request.description().trim();
    if (description != null && description.length() > MAX_DESCRIPTION) {
      throw new IllegalArgumentException(
          "A descrição do cargo pode ter até " + MAX_DESCRIPTION + " caracteres.");
    }
    return description == null || description.isEmpty() ? null : description;
  }

  private Set<Permission> resolvePermissions(RoleRequest request) {
    Set<Long> ids = request.permissionIds() == null ? Set.of() : request.permissionIds();
    List<Permission> found = permissionRepository.findAllById(ids);
    if (found.size() != ids.size()) {
      throw new IllegalArgumentException("Alguma das permissões informadas não existe.");
    }
    return new HashSet<>(found);
  }
}
