package clp.inventory.dto;

import clp.inventory.model.Sector;

public record SectorDto(
    long id,
    String name,
    String code,
    boolean active,
    String createdAt,
    String updatedAt) {

  public static SectorDto from(Sector sector) {
    return new SectorDto(
        sector.id(),
        sector.name(),
        sector.code(),
        sector.active(),
        sector.createdAt() != null ? sector.createdAt().toString() : null,
        sector.updatedAt() != null ? sector.updatedAt().toString() : null);
  }
}
