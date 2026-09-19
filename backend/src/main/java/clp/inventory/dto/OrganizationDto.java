package clp.inventory.dto;

import clp.inventory.model.Organization;

public record OrganizationDto(
        long id,
        String name,
        String acronym,
        String domain,
        String createdAt,
        String updatedAt
) {

    public static OrganizationDto from(Organization organization) {
        return new OrganizationDto(
                organization.id(),
                organization.name(),
                organization.acronym(),
                organization.domain(),
                organization.createdAt() != null ? organization.createdAt().toString() : null,
                organization.updatedAt() != null ? organization.updatedAt().toString() : null
        );
    }
}
