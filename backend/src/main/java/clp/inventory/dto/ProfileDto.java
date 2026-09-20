package clp.inventory.dto;

import clp.inventory.model.Profile;

public record ProfileDto(
        long id,
        String name,
        String description,
        String createdAt,
        String updatedAt
) {

    public static ProfileDto from(Profile profile) {
        return new ProfileDto(
                profile.id(),
                profile.name(),
                profile.description(),
                profile.createdAt() != null ? profile.createdAt().toString() : null,
                profile.updatedAt() != null ? profile.updatedAt().toString() : null
        );
    }
}
