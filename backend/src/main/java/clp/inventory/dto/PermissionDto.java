package clp.inventory.dto;

import clp.inventory.model.Permission;

public record PermissionDto(
        long id,
        String name,
        String description
) {

    public static PermissionDto from(Permission permission) {
        return new PermissionDto(permission.id(), permission.name(), permission.description());
    }
}
