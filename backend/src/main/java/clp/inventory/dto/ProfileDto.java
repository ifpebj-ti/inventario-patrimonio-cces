package clp.inventory.dto;

import clp.inventory.model.Permission;
import clp.inventory.model.Profile;

import java.util.Set;
import java.util.stream.Collectors;

public record ProfileDto(
        long id,
        String name,
        String description,
        Set<Long> permissionIds,
        String createdAt,
        String updatedAt
) {

    public static ProfileDto from(Profile profile) {
        return new ProfileDto(
                profile.id(),
                profile.name(),
                profile.description(),
                profile.permissions().stream().map(Permission::id).collect(Collectors.toSet()),
                profile.createdAt() != null ? profile.createdAt().toString() : null,
                profile.updatedAt() != null ? profile.updatedAt().toString() : null
        );
    }
}
