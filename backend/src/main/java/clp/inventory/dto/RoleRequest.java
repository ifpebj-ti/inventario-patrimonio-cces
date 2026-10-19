package clp.inventory.dto;

import java.util.Set;

/** Corpo de {@code POST /roles} e {@code PUT /roles/{id}}. */
public record RoleRequest(String name, String description, Set<Long> permissionIds) {}
