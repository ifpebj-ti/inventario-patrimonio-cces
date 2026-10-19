package clp.inventory.dto;

import clp.inventory.model.Permission;
import clp.inventory.model.Role;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * {@code management}: o cargo tem MANAGE_SECTOR. {@code allocationCount}/{@code userCount}: em
 * quantas alocações e para quantos usuários o cargo vale. {@code deletable}: sem alocação e sem
 * permissão, o único caso em que pode ser excluído.
 */
public record RoleDto(
    long id,
    String name,
    String description,
    Set<Long> permissionIds,
    boolean management,
    long allocationCount,
    long userCount,
    boolean deletable,
    String createdAt,
    String updatedAt) {

  public static RoleDto from(Role role, boolean management, long allocationCount, long userCount) {
    return new RoleDto(
        role.id(),
        role.name(),
        role.description(),
        role.permissions().stream().map(Permission::id).collect(Collectors.toSet()),
        management,
        allocationCount,
        userCount,
        allocationCount == 0 && role.permissions().isEmpty(),
        role.createdAt() != null ? role.createdAt().toString() : null,
        role.updatedAt() != null ? role.updatedAt().toString() : null);
  }
}
