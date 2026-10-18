package clp.inventory.service;

import clp.inventory.model.Permission;
import clp.inventory.repository.PermissionRepository;
import java.util.List;
import java.util.NoSuchElementException;
import org.springframework.stereotype.Service;

/**
 * Permissões são somente leitura pela API e exigem ADMIN. Elas são definidas em código (só ADMIN,
 * MANAGE_SECTOR e MANAGE_ITEM têm efeito); uma permissão nova entra por migration do Liquibase.
 */
@Service
public class PermissionService {

  private final PermissionRepository permissionRepository;
  private final AuthorizationService authorizationService;

  public PermissionService(
      PermissionRepository permissionRepository, AuthorizationService authorizationService) {
    this.permissionRepository = permissionRepository;
    this.authorizationService = authorizationService;
  }

  public Permission findPermissionById(long id, long actingUserId) {
    authorizationService.requirePermission(actingUserId, "ADMIN");
    return permissionRepository
        .findById(id)
        .orElseThrow(() -> new NoSuchElementException("Permission not found with id: " + id));
  }

  public List<Permission> listAllPermissions(long actingUserId) {
    authorizationService.requireAnyPermission(
        actingUserId, AuthorizationService.ADMIN, AuthorizationService.MANAGE_SECTOR);
    return permissionRepository.findAll();
  }
}
