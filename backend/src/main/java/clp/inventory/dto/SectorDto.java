package clp.inventory.dto;

import clp.inventory.model.Sector;

public record SectorDto(
        long id,
        String name,
        String code,
        long organizationId,
        Long parentSectorId,
        String createdAt,
        String updatedAt
) {

    public static SectorDto from(Sector sector) {
        return new SectorDto(
                sector.id(),
                sector.name(),
                sector.code(),
                sector.organization().id(),
                sector.parentSector() != null ? sector.parentSector().id() : null,
                sector.createdAt() != null ? sector.createdAt().toString() : null,
                sector.updatedAt() != null ? sector.updatedAt().toString() : null
        );
    }
}
